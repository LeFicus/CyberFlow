"""Generic independent-store crawler using the WooCommerce selector profile.

Shopify keeps its specialised API spider. BigCommerce, OpenCart, Magento,
PrestaShop and other storefront engines use sitemap discovery and the same
configurable selectors as WooCommerce until dedicated adapters are introduced.
"""

import hashlib
import json
import re
from html import unescape
from urllib.parse import urlparse

import scrapy
from bs4 import BeautifulSoup


class PlatformCrawlSpider(scrapy.Spider):
    name = "platform_crawl"
    SUPPORTED = {
        "woocommerce", "bigcommerce", "opencart", "magento", "prestashop",
        "shopline", "ecwid", "wix", "squarespace", "custom",
    }
    SITEMAP_PATHS = ("/sitemap_index.xml", "/sitemap.xml", "/wp-sitemap.xml")

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
        "site_map": "//*[local-name()='sitemap']/*[local-name()='loc']/text()",
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
        return scrapy.Request(
            f"{self.domain}{self.SITEMAP_PATHS[index]}",
            callback=self.parse_sitemap,
            errback=self.sitemap_failed,
            meta={"sitemap_attempt": index},
            dont_filter=True,
        )

    def sitemap_failed(self, failure):
        index = failure.request.meta.get("sitemap_attempt", 0) + 1
        if index < len(self.SITEMAP_PATHS):
            yield self._sitemap_request(index)
        else:
            self.logger.error("No accessible sitemap found for %s", self.domain)

    def parse_sitemap(self, response):
        sitemap_urls = response.xpath(self.selectors["site_map"]).getall()
        if sitemap_urls:
            product_maps = [url.strip() for url in sitemap_urls if "product" in url.lower()]
            targets = product_maps or [url.strip() for url in sitemap_urls]
            for url in targets[:200]:
                yield scrapy.Request(response.urljoin(url), callback=self.parse_sitemap, dont_filter=True)
            return

        product_urls = response.xpath("//*[local-name()='url']/*[local-name()='loc']/text()").getall()
        if not product_urls:
            self.logger.warning("Sitemap contains no product URLs: %s", response.url)
            return
        for url in product_urls:
            if len(self.seen_product_urls) >= self.max_items:
                break
            url = response.urljoin(url.strip())
            if url and url not in self.seen_product_urls:
                self.seen_product_urls.add(url)
                yield scrapy.Request(url, callback=self.parse_product_detail)

    def parse_product_detail(self, response):
        name = self._first(response, "title")
        price = self._price(response)
        image = self._first(response, "images")
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
        return next((BeautifulSoup(unescape(value), "html.parser").get_text(" ", strip=True) for value in values if value.strip()), "")

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
        soup = BeautifulSoup(unescape(value or ""), "html.parser")
        for tag in soup.find_all(["script", "style", "img", "video", "iframe", "svg"]):
            tag.decompose()
        return soup.get_text(" ", strip=True)
