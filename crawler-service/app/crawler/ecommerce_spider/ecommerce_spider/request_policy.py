"""Bounded public-store requests with proxy support and challenge detection."""

import re
import threading
import time
from pathlib import Path
from urllib.parse import urljoin, urlparse

import requests
from scrapy import signals
from scrapy.core.downloader.contextfactory import BrowserLikeContextFactory
from scrapy.exceptions import IgnoreRequest
from scrapy.http import Headers, HtmlResponse, TextResponse
from scrapy.responsetypes import responsetypes
from twisted.internet import error as network_error
from twisted.internet.threads import deferToThread
from twisted.internet.ssl import Certificate, optionsForClientTLS, trustRootFromCertificates
from ecommerce_spider.platforms import SPIDERS


PRODUCT_SPIDERS = set(SPIDERS.values())


def same_store(url, domain):
    target, source = urlparse(url), urlparse(domain)
    if target.scheme not in {"http", "https"} or target.username or target.password:
        return False
    try:
        target_port, source_port = target.port, source.port
    except ValueError:
        return False
    return (
        bool(target.hostname) and bool(source.hostname)
        and target.hostname.lower().removeprefix("www.") == source.hostname.lower().removeprefix("www.")
        and target_port == source_port
        and not (source.scheme == "https" and target.scheme != "https")
    )


def is_verification_page(response):
    if not isinstance(response, TextResponse):
        return False
    text = response.text[:150000].lower()
    title = re.search(r"<title[^>]*>(.*?)</title>", text, re.S)
    title = title.group(1) if title else ""
    challenge_title = any(value in title for value in (
        "just a moment", "attention required", "verify you are human", "security verification", "access denied",
    ))
    markers = any(value in text for value in (
        "/cdn-cgi/challenge-platform/", "cf-chl-", "challenge-form", "g-recaptcha", "hcaptcha", "cf-turnstile",
    ))
    return bool(challenge_title and markers)


def configured_proxy(settings):
    proxy = str(settings.get("PRODUCT_CRAWL_PROXY_URL") or "").strip()
    if proxy:
        parsed = urlparse(proxy)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname or parsed.query or parsed.fragment:
            raise ValueError("PRODUCT_CRAWL_PROXY_URL must be an HTTP(S) proxy URL")
        try:
            parsed.port
        except ValueError as exc:
            raise ValueError("Invalid product proxy port") from exc
    return proxy


class ProductTLSContextFactory(BrowserLikeContextFactory):
    """Validate TLS on both transports, optionally using the same private CA bundle."""

    @classmethod
    def from_crawler(cls, crawler, *args, **kwargs):
        instance = super().from_crawler(crawler, *args, **kwargs)
        # OpenSSL's platform CA locations can be empty on Windows. Requests
        # already uses certifi, so use the same verified trust roots in Scrapy.
        path = crawler.settings.get("PRODUCT_CRAWL_CA_BUNDLE") or requests.certs.where()
        instance.trust_root = None
        if path:
            certificates = re.findall(
                rb"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", Path(path).read_bytes(), re.S
            )
            if not certificates:
                raise ValueError("PRODUCT_CRAWL_CA_BUNDLE contains no PEM certificates")
            instance.trust_root = trustRootFromCertificates([Certificate.loadPEM(cert) for cert in certificates])
        return instance

    def creatorForNetloc(self, hostname, port):
        if self.trust_root is None:
            return super().creatorForNetloc(hostname, port)
        return optionsForClientTLS(hostname.decode("ascii"), trustRoot=self.trust_root,
                                   extraCertificateOptions={"method": self._ssl_method})


class ProductRequestPolicyMiddleware:
    def __init__(self, crawler):
        self.crawler = crawler
        self.proxy_url = configured_proxy(crawler.settings)

    @classmethod
    def from_crawler(cls, crawler):
        return cls(crawler)

    def process_request(self, request, spider):
        if spider.name not in PRODUCT_SPIDERS:
            return None
        allowed = getattr(spider, "request_allowed", lambda url: same_store(url, spider.domain))
        if not allowed(request.url):
            raise IgnoreRequest("Product request left the configured store origin")
        if self.proxy_url:
            request.meta["proxy"] = self.proxy_url

    def process_response(self, request, response, spider):
        if spider.name in PRODUCT_SPIDERS and is_verification_page(response):
            self.crawler.stats.inc_value("product/verification_pages")
            request.meta["dont_retry"] = True
            spider.logger.error("站点返回验证页面，需要人工确认或检查出口网络: %s", request.url)
            return response.replace(status=403, flags=[*response.flags, "verification-required"])
        return response


class BigCommerceRequestsFallbackMiddleware:
    FALLBACK_STATUSES = {403, 429, 503}
    NETWORK_ERRORS = (
        network_error.DNSLookupError, network_error.TimeoutError,
        network_error.TCPTimedOutError, network_error.ConnectionLost,
        network_error.ConnectionRefusedError, OSError,
    )

    def __init__(self, crawler):
        self.crawler = crawler
        self.proxy_url = configured_proxy(crawler.settings)
        self.verify = crawler.settings.get("PRODUCT_CRAWL_CA_BUNDLE") or True
        self.local = threading.local()
        self.sessions = []
        self.session_lock = threading.Lock()
        crawler.signals.connect(self.close, signal=signals.spider_closed)

    @classmethod
    def from_crawler(cls, crawler):
        return cls(crawler)

    def _eligible(self, request, spider):
        return (
            spider.name == "bigcommerce_crawl" and request.method == "GET"
            and not request.meta.get("requests_fallback_attempted")
        )

    def process_response(self, request, response, spider):
        if self._eligible(request, spider) and (
            response.status in self.FALLBACK_STATUSES or is_verification_page(response)
        ):
            return self._fallback(request, spider)
        return response

    def process_exception(self, request, exception, spider):
        if self._eligible(request, spider) and isinstance(exception, self.NETWORK_ERRORS):
            return self._fallback(request, spider)

    def _fallback(self, request, spider):
        request.meta["requests_fallback_attempted"] = True
        request.meta["dont_retry"] = True
        self.crawler.stats.inc_value("product/requests_fallback_attempts")
        spider.logger.warning("BigCommerce 访问受阻，执行一次 requests 回退: %s", request.url)
        deferred = deferToThread(self._download, request, spider)
        deferred.addCallback(self._record_result)
        return deferred

    def _record_result(self, response):
        key = "success" if response.status == 200 and not is_verification_page(response) else "failed"
        self.crawler.stats.inc_value(f"product/requests_fallback_{key}")
        return response

    def _session(self):
        if not hasattr(self.local, "session"):
            session = requests.Session()
            session.trust_env = False
            self.local.session = session
            with self.session_lock:
                self.sessions.append(session)
        return self.local.session

    def _download(self, request, spider):
        headers = {
            key.decode("latin1"): b", ".join(values).decode("latin1")
            for key, values in request.headers.items()
            if key.lower() not in {b"host", b"proxy-authorization", b"content-length", b"accept-encoding"}
        }
        headers["Accept-Encoding"] = "gzip, deflate"
        proxies = {"http": self.proxy_url, "https": self.proxy_url} if self.proxy_url else {}
        deadline = time.monotonic() + 60
        url = request.url
        try:
            for _ in range(6):
                if not same_store(url, spider.domain):
                    raise ValueError("Off-site redirect refused")
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise TimeoutError("Fallback deadline exceeded")
                with self._session().get(
                    url, headers=headers, timeout=(min(10, remaining), min(30, remaining)),
                    verify=self.verify, proxies=proxies, allow_redirects=False, stream=True,
                ) as result:
                    if result.status_code in {301, 302, 303, 307, 308} and result.headers.get("Location"):
                        url = urljoin(url, result.headers["Location"])
                        continue
                    chunks = []
                    size = 0
                    for chunk in result.iter_content(65536):
                        size += len(chunk)
                        if size > 20 * 1024 * 1024 or time.monotonic() > deadline:
                            raise ValueError("Fallback response limit exceeded")
                        chunks.append(chunk)
                    body = b"".join(chunks)
                    response_headers = Headers({
                        key: value for key, value in result.headers.items()
                        if key.lower() not in {"content-encoding", "content-length", "transfer-encoding"}
                    })
                    response_class = responsetypes.from_args(headers=response_headers, url=url, body=body)
                    return response_class(url=url, status=result.status_code, headers=response_headers,
                                          body=body, request=request, flags=["requests-fallback"])
            raise ValueError("Fallback redirect limit exceeded")
        except (requests.RequestException, ValueError, OSError) as exc:
            spider.logger.warning("BigCommerce 回退失败 (%s)，请检查网络、代理或 TLS 配置", type(exc).__name__)
            return HtmlResponse(request.url, status=503, request=request, flags=["requests-fallback-failed"])

    def close(self, spider, reason):
        for session in self.sessions:
            session.close()
