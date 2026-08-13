"""WooCommerce-style independent-store crawler.

The spider starts from the site's sitemap, discovers product detail URLs, and
parses each product with configurable WooCommerce selectors. BigCommerce has a
dedicated spider because its Schema.org Product JSON-LD is the primary source.
"""

import hashlib
import json
import re
from html import unescape
from urllib.parse import unquote, urlparse, urlunparse

import scrapy


class PlatformCrawlSpider(scrapy.Spider):
    name = "platform_crawl"
    custom_settings = {
        # Generic storefronts often expose large sitemap indexes. Keep a slow
        # or unreachable endpoint from holding the whole task indefinitely.
        "DOWNLOAD_TIMEOUT": 30,
        "RETRY_TIMES": 1,
        "CONCURRENT_REQUESTS": 32,
        "CONCURRENT_REQUESTS_PER_DOMAIN": 8,
        "DOWNLOAD_DELAY": 0.1,
        # Keep the complete operational log without Scrapy's per-request DEBUG
        # noise; accepted/rejected URL totals are logged explicitly below.
        "LOG_LEVEL": "INFO",
    }
    SUPPORTED = {
        "woocommerce", "opencart", "magento", "prestashop",
        "shopline", "ecwid", "wix", "squarespace", "custom",
    }
    SITEMAP_PATHS = ("/sitemap_index.xml", "/sitemap.xml", "/wp-sitemap.xml")
    SITEMAP_INDEX_XPATH = "//*[local-name()='sitemap']/*[local-name()='loc']/text()"
    PRODUCT_PATH_SEGMENTS = {"product", "products", "item", "items", "p"}
    PRODUCT_PATH_PREFIXES = {"shop"}
    NON_PRODUCT_PATH_SEGMENTS = {
        "article", "articles", "blog", "blogs", "blogposts", "news", "press",
        "author", "authors", "tag", "tags", "events", "videos", "gallery",
        "galleries", "faq", "faqs", "contact", "about", "careers", "account",
        "login", "register", "search", "cart", "checkout", "wishlist",
    }

    DEFAULT_SELECTORS = {
        "title": (
            "//h1[contains(@class,'product_title')]/text() | "
            "//h1[contains(@class,'product-title')]/text() | "
            "//h1[contains(@class,'productView-title')]/text() | "
            "//main//h1/text() | //meta[@property='og:title']/@content"
        ),
        "sku": (
            "//span[contains(@class,'sku')]/text() | //meta[@itemprop='sku']/@content | "
            "//*[@data-product-sku]/@data-product-sku | //*[@itemprop='sku']/text()"
        ),
        "price": (
            "//p[contains(@class,'price')]//*[contains(@class,'amount')]/text() | "
            "//*[contains(@class,'product') and contains(@class,'price')]//text() | "
            "//*[@itemprop='price']/@content | //meta[@property='product:price:amount']/@content"
        ),
        "price_regex": r"[\d.,]+",
        "description": (
            "//*[contains(@class,'product') and contains(@class,'description')]//text() | "
            "//*[@itemprop='description']//text() | //meta[@property='og:description']/@content"
        ),
        "images": (
            "//*[contains(@class,'product') and contains(@class,'gallery')]//img/@data-large_image | "
            "//*[contains(@class,'product') and contains(@class,'gallery')]//img/@src | "
            "//*[contains(@class,'productView-images')]//img/@src | "
            "//meta[@property='og:image']/@content"
        ),
        "breadcrumb_links": (
            "//nav[contains(@class,'breadcrumb')]//a//text() | "
            "//*[contains(@class,'breadcrumbs')]//a//text() | "
            "//ul[contains(@class,'breadcrumb')]//a//text()"
        ),
        "breadcrumb_last": (
            "//nav[contains(@class,'breadcrumb')]//*[last()]//text() | "
            "//*[contains(@class,'breadcrumbs')]//*[last()]//text()"
        ),
        "site_map": SITEMAP_INDEX_XPATH,
        "currency": "USD",
    }

    def __init__(self, domain=None, category="未知分类", platform=None,
                 selector_profile="woocommerce", config_json=None,
                 mode="prod", *args, **kwargs):
        super().__init__(*args, **kwargs)
        platform = str(platform or "").lower().strip()
        if not domain or platform not in self.SUPPORTED:
            raise ValueError(f"domain and a supported non-Shopify platform are required: {sorted(self.SUPPORTED)}")
        if selector_profile != "woocommerce":
            raise ValueError("Non-Shopify engines currently require selector_profile=woocommerce")

        raw_domain = domain if str(domain).startswith(("http://", "https://")) else f"https://{domain}"
        parsed = urlparse(raw_domain)
        self.domain = f"{parsed.scheme}://{parsed.netloc}".rstrip("/")
        self.allowed_domains = [parsed.netloc]
        self.platform = platform
        self.selector_profile = "woocommerce"
        self.custom_category = str(category or "未知分类").strip() or "未知分类"
        self.mode = mode
        self.max_items = 10 if mode == "dev" else 20000
        self.seen_sitemap_urls = set()
        self.seen_product_urls = set()
        self.selectors = dict(self.DEFAULT_SELECTORS)
        if config_json:
            try:
                custom = json.loads(config_json)
                self.selectors.update({key: value for key, value in custom.items() if value})
            except (TypeError, json.JSONDecodeError) as exc:
                raise ValueError(f"Invalid selector config JSON: {exc}") from exc

    async def start(self):
        yield self._sitemap_request(0)

    def _sitemap_request(self, index):
        url = f"{self.domain}{self.SITEMAP_PATHS[index]}"
        self.seen_sitemap_urls.add(url)
        return scrapy.Request(
            url,
            callback=self.parse_sitemap,
            errback=self.sitemap_failed,
            meta={"sitemap_attempt": index, "product_sitemap": False},
            dont_filter=True,
        )

    def sitemap_failed(self, failure):
        index = failure.request.meta.get("sitemap_attempt", 0) + 1
        if index < len(self.SITEMAP_PATHS):
            yield self._sitemap_request(index)
        else:
            self.logger.error("No accessible sitemap found for %s", self.domain)

    def parse_sitemap(self, response):
        # Standard XML sitemap discovery is a platform rule, not a configurable
        # product-page selector. Retain an old custom sitemap selector only as
        # a fallback for non-standard storefronts.
        sitemap_urls = response.xpath(self.SITEMAP_INDEX_XPATH).getall()
        custom_sitemap_xpath = self.selectors.get("site_map")
        if (not sitemap_urls and custom_sitemap_xpath
                and custom_sitemap_xpath != self.SITEMAP_INDEX_XPATH):
            sitemap_urls = response.xpath(custom_sitemap_xpath).getall()
        if sitemap_urls:
            product_maps = [url.strip() for url in sitemap_urls if self._is_product_sitemap_url(url)]
            if not product_maps:
                attempt = response.meta.get("sitemap_attempt")
                if attempt is not None and attempt + 1 < len(self.SITEMAP_PATHS):
                    self.logger.warning(
                        "地图索引未发现商品子地图，跳过 %d 个非商品地图并尝试下一入口: %s",
                        len(sitemap_urls),
                        response.url,
                    )
                    yield self._sitemap_request(attempt + 1)
                else:
                    self.logger.error(
                        "地图索引未发现商品子地图，已跳过 %d 个非商品地图: %s",
                        len(sitemap_urls),
                        response.url,
                    )
                return
            for url in product_maps[:200]:
                url = response.urljoin(url)
                if url not in self.seen_sitemap_urls:
                    self.seen_sitemap_urls.add(url)
                    yield scrapy.Request(
                        url,
                        callback=self.parse_sitemap,
                        meta={
                            "product_sitemap": (
                                response.meta.get("product_sitemap", False)
                                or self._is_product_sitemap_url(url)
                            )
                        },
                    )
            return

        product_urls = response.xpath("//*[local-name()='url']/*[local-name()='loc']/text()").getall()
        if not product_urls:
            attempt = response.meta.get("sitemap_attempt")
            if attempt is not None and attempt + 1 < len(self.SITEMAP_PATHS):
                self.logger.warning(
                    "Sitemap is empty or invalid, trying next candidate: %s", response.url
                )
                yield self._sitemap_request(attempt + 1)
            else:
                self.logger.warning("Sitemap contains no product URLs: %s", response.url)
            return
        trusted_product_sitemap = response.meta.get("product_sitemap", False)
        accepted = 0
        rejected = 0
        for raw_url in product_urls:
            if len(self.seen_product_urls) >= self.max_items:
                break
            url = self._canonical_product_url(response.urljoin(raw_url.strip()))
            if not self._is_product_detail_url(url, trusted_product_sitemap):
                rejected += 1
                continue
            if url and url not in self.seen_product_urls:
                accepted += 1
                self.seen_product_urls.add(url)
                yield scrapy.Request(url, callback=self.parse_product_detail)
        self.logger.info(
            "商品 URL 过滤 → %s | 接受 %d | 排除非商品 %d",
            response.url,
            accepted,
            rejected,
        )

    @staticmethod
    def _is_product_sitemap_url(url):
        """Identify sitemap shards explicitly labelled as product data."""
        parsed = urlparse(str(url).strip())
        marker = f"{parsed.path}?{parsed.query}".lower()
        return bool(re.search(r"(?:^|[/_.?=&-])products?(?:[/_.?=&-]|$)", marker))

    def _is_product_detail_url(self, url, trusted_product_sitemap=False):
        """Reject content/category URLs before issuing expensive detail requests."""
        parsed = urlparse(url)
        if parsed.netloc.lower() not in {domain.lower() for domain in self.allowed_domains}:
            return False

        path = unquote(parsed.path).lower().strip("/")
        if not path or path.endswith((".xml", ".xml.gz", ".txt")):
            return False
        segments = [segment for segment in path.split("/") if segment]
        if any(segment in self.NON_PRODUCT_PATH_SEGMENTS for segment in segments):
            return False

        for index, segment in enumerate(segments[:-1]):
            if segment in self.PRODUCT_PATH_SEGMENTS:
                return True
            if segment in self.PRODUCT_PATH_PREFIXES and len(segments) > index + 1:
                return True

        # Product-specific sitemap shards are authoritative and support
        # BigCommerce stores whose product URLs are SEO slugs at the root.
        return bool(trusted_product_sitemap)

    @staticmethod
    def _canonical_product_url(url):
        """Remove fragments/variant queries so one product is requested once."""
        parsed = urlparse(url)
        return urlunparse((parsed.scheme, parsed.netloc, parsed.path, "", "", ""))

    def parse_product_detail(self, response):
        name = self._first(response, "title")
        price = self._price(response)
        image = self._first_url(response, "images")
        if not name or price <= 0 or not image:
            return

        original_sku = self._first(response, "sku")
        identity = f"{urlparse(response.url).netloc}|{response.url}|{original_sku}"
        sku = original_sku or f"{self.platform[:4].upper()}-{hashlib.md5(identity.encode()).hexdigest()[:12].upper()}"
        description_parts = response.xpath(self.selectors["description"]).getall()
        description = self._clean_description(" ".join(part.strip() for part in description_parts if part.strip()))
        breadcrumbs = [value.strip() for value in response.xpath(self.selectors["breadcrumb_links"]).getall() if value.strip()]
        breadcrumbs = [value for value in breadcrumbs if value.lower() not in {"home", name.lower()}]
        categories = "|||".join(breadcrumbs[:2]) or "Others"

        yield {
            "SKU": sku,
            "Name": name,
            "Description": description,
            "Regular price": f"{price:.2f}",
            "Categories": categories,
            "Images": response.urljoin(image),
            "cf_opingts": "",
            "自定义分类": self.custom_category,
            "原站域名": urlparse(response.url).netloc,
            "分布网站识别": 0,
            "语言": "en",
        }
        self.logger.info("成功生成商品 → %s | %s", sku, name[:60])

    def _first(self, response, key):
        values = response.xpath(self.selectors[key]).getall()
        return next((self._clean_text(value) for value in values if value.strip()), "")

    def _first_url(self, response, key):
        """Return a URL selector value verbatim instead of parsing it as HTML."""
        values = response.xpath(self.selectors[key]).getall()
        return next((unescape(value).strip() for value in values if value.strip()), "")

    @staticmethod
    def _clean_text(value):
        """Normalize selector/JSON-LD text without constructing an HTML parser."""
        value = unescape(value or "")
        value = re.sub(r"(?is)<(script|style|svg|video|iframe)\b[^>]*>.*?</\1\s*>", " ", value)
        value = re.sub(r"(?is)<[^>]+>", " ", value)
        return re.sub(r"\s+", " ", value).strip()

    def _price(self, response):
        pattern = self.selectors.get("price_regex") or r"[\d.,]+"
        for text in response.xpath(self.selectors["price"]).getall():
            match = re.search(pattern, text)
            if not match:
                continue
            value = match.group(0).replace(" ", "")
            if value.count(",") == 1 and "." not in value and len(value.rsplit(",", 1)[1]) == 2:
                value = value.replace(",", ".")
            else:
                value = value.replace(",", "")
            try:
                price = float(value)
                if price > 0:
                    return price
            except ValueError:
                continue
        return 0.0

    @staticmethod
    def _clean_description(value):
        value = unescape(value or "")
        value = re.sub(r"(?is)<(script|style|svg|video|iframe)\b[^>]*>.*?</\1\s*>", " ", value)
        value = re.sub(r"(?is)<(?:img|source)\b[^>]*?/?>", " ", value)
        value = re.sub(r"(?is)<[^>]+>", " ", value)
        return re.sub(r"\s+", " ", value).strip()
