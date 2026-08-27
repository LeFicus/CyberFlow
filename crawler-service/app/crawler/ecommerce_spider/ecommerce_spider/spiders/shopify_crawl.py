"""Shopify public catalog discovery with one database row per stable variant.

Public JSON emits every returned variant. An authorized Storefront token enables
cursor pagination; suspected public variant truncation is reported as failure.
Currency conversion and quality filtering happen once in the shared pipeline.
"""

import json
import scrapy
import re
from urllib.parse import urlparse

from bs4 import BeautifulSoup
from ecommerce_spider.crawl_result import get_metrics
from ecommerce_spider.crawl_options import CrawlOptions, currency_code, resolve_currency
from ecommerce_spider.shopify_variants import ShopifyVariants


class ShopifyCrawlFastSpider(scrapy.Spider):
    """Shopify catalog and complete, independently priced variant collection."""
    name = "shopify_crawl_fast"

    def __init__(self, domain=None, category="未知分类", product_role="main", export_file=None,
                 crawl_options_json=None, config_json=None, *args, **kwargs):
        """
        初始化 Shopify Spider

        Args:
            domain      (str): 目标 Shopify 站点 URL（必填，如 "https://example.com"）
            category    (str): 业务自定义分类名称（默认 "未知分类"）
            export_file (str): 导出文件路径（可选，预留扩展）

        Raises:
            ValueError: 未提供 domain 参数
        """
        super().__init__(*args, **kwargs)

        # 参数验证
        if not domain:
            raise ValueError("❌ 必须提供domain参数")
        if not domain.startswith(("http://", "https://")):
            domain = f"https://{domain}"

        self.domain = domain.rstrip("/")
        self.export_file = export_file or f"{urlparse(self.domain).netloc}_products.xlsx"
        self.custom_category = category.strip() or "未知分类"
        self.product_role = "supplement" if str(product_role or "").strip().lower() == "supplement" else "main"
        self.page = 1
        self.limit = 250
        self.crawl_options = CrawlOptions.from_json(crawl_options_json)
        self.selectors = json.loads(config_json) if config_json else {}
        self.shop_currency = ""
        self.logger.setLevel(1)
        # 记录已处理的产品ID，避免重复
        self.processed_product_ids = set()
        self.processed_variant_ids = set()
        self.variant_cursors = set()
        self.storefront_token = ""

    @classmethod
    def from_crawler(cls, crawler, *args, **kwargs):
        spider = super().from_crawler(crawler, *args, **kwargs)
        configs = crawler.settings.get("PRODUCT_PLATFORM_CONFIGS") or {}
        configs = json.loads(configs) if isinstance(configs, str) else configs
        config = {**configs.get(urlparse(spider.domain).hostname, {}), **spider.selectors.get("platform_config", {})}
        spider.storefront_token = str(config.get("storefront_token") or "")
        return spider

    def clean_text_regex(self, text):
        """
        使用正则表达式清理文本中的特殊字符和空格

        处理内容:
            - 移除各类引号（单引号、双引号、中文引号）
            - 移除控制字符（ASCII 0-31, 127-159）
            - 合并连续空白字符为单个空格

        Args:
            text (str): 原始文本

        Returns:
            str: 清理后的干净文本，空输入返回空字符串
        """
        if not text:
            return ""
        text = str(text)
        # 移除引号和控制字符
        text = re.sub(r"['\"`''""]", "", text)
        text = re.sub(r"[\x00-\x1F\x7F-\x9F]", "", text)
        # 合并空格
        return re.sub(r"\s+", " ", text).strip()

    def clean_variant_options(self, variant):
        """
        清洗变体选项字符串（options1/option2/option3）

        处理逻辑:
            - 过滤掉 "Default Title" 和 "None" 等无效选项
            - 去重并保持顺序
            - 每个选项首字母大写

        Args:
            variant (dict): Shopify 变体数据（含 option1/option2/option3 字段）

        Returns:
            str: 清洗后的变体选项字符串（空格分隔）
        """
        options = []
        for i in range(1, 4):
            opt = str(variant.get(f"option{i}", "")).strip()
            if opt and opt.lower() not in ["default title", "none"]:
                options.append(opt)

        # 去重并格式化
        unique_options = []
        seen = set()
        for opt in options:
            opt_lower = opt.lower()
            if opt_lower not in seen:
                seen.add(opt_lower)
                unique_options.append(opt.title())

        return self.clean_text_regex(" ".join(unique_options))

    def clean_description(self, html_content):
        """
        深度清洗 HTML 商品描述内容

        清洗策略:
            1. 移除 HTML 注释
            2. 标签白名单控制：仅保留 div, span, p, br, table 等结构标签
            3. 彻底移除: a（保留文字文本）, img, video, script（完全删除）
            4. 表格特殊优化: 移除所有宽度/高度/内联样式，仅保留纯净结构
            5. 单元格保留 colspan/rowspan 合并属性
            6. 移除无文本内容的空标签

        Args:
            html_content (str): 原始 HTML 内容

        Returns:
            str: 清洗后的 HTML 字符串，保留基础结构标签
        """
        if not html_content:
            return ""

        # 清除常见的 HTML 注释
        html_content = re.sub(r'', '', html_content, flags=re.DOTALL)

        soup = BeautifulSoup(html_content, "html.parser")

        # 1. 定义白名单
        allowed_tags = [
            'div', 'span', 'p', 'br', 'strong', 'b', 'em', 'i',
            'table', 'thead', 'tbody', 'tr', 'th', 'td',
            'ul', 'ol', 'li', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6'
        ]

        # 2. 遍历并处理
        for tag in soup.find_all(True):
            if tag.name not in allowed_tags:
                # 链接标签：保留文字，拆除外壳
                if tag.name == 'a':
                    tag.unwrap()
                # 媒体/脚本标签：彻底删除
                else:
                    tag.decompose()
            else:
                # 3. 针对保留标签的属性清理
                if tag.name == 'table':
                    # 表格特殊处理：赋予基础属性，移除所有内联尺寸
                    tag.attrs = {'border': '1', 'cellspacing': '0', 'cellpadding': '0',
                                 'style': 'width:100%; border-collapse:collapse;'}
                elif tag.name in ['td', 'th']:
                    # 单元格保留合并属性 (colspan/rowspan)，移除其他
                    new_attrs = {}
                    if tag.has_attr('colspan'): new_attrs['colspan'] = tag['colspan']
                    if tag.has_attr('rowspan'): new_attrs['rowspan'] = tag['rowspan']
                    tag.attrs = new_attrs
                else:
                    # 其他标签：清空所有属性（如 class, id, style）
                    tag.attrs = {}

        # 4. 移除空标签（可选：防止出现大量的空 p 或空 div）
        for empty_tag in soup.find_all(['p', 'div', 'span']):
            if not empty_tag.get_text(strip=True):
                empty_tag.decompose()

        # 5. 最终清理：将处理后的对象转回字符串，并压缩空白
        cleaned_html = str(soup)
        cleaned_html = re.sub(r'\s+', ' ', cleaned_html)  # 压缩多余空格
        return cleaned_html.strip()

    def generate_unique_sku(self, product_id, variant_id):
        """Stable within source_domain; independent of category or merchant SKU."""
        product_id = str(product_id).rsplit("/", 1)[-1]
        variant_id = str(variant_id).rsplit("/", 1)[-1]
        if not product_id.isdigit() or not variant_id.isdigit():
            raise ValueError("Invalid Shopify product/variant ID")
        return f"SHOPIFY-{product_id}-{variant_id}"

    def start_requests(self):
        """
        爬虫起始入口 — 首先请求 meta.json 获取店铺货币信息

        Scrapy 会首先调用此方法获取初始请求列表。
        优先使用高优先级 (priority=10) 请求 meta.json，
        获取货币信息后再请求 products.json。
        """
        self.logger.info(f"🔍 开始爬取: {self.domain}")
        self.logger.info(f"📦 自定义分类: {self.custom_category}")
        self.logger.warning("Shopify 以变体 ID 独立保存；旧版聚合商品不会自动删除，上线前请按升级文档核查历史记录")

        # 先请求meta获取货币信息
        yield scrapy.Request(
            f"{self.domain}/meta.json",
            callback=self.parse_meta,
            errback=self.meta_failed,
            dont_filter=True,
            priority=10
        )

    def parse_meta(self, response):
        """
        解析 meta.json 响应 — 提取店铺货币代码

        Shopify 的 meta.json 返回格式:
            {"currency": "USD", ...}

        Args:
            response (scrapy.http.Response): meta.json 的 HTTP 响应
        """
        try:
            meta_data = json.loads(response.text)
            self.shop_currency = currency_code(meta_data.get("currency"))
            self.logger.info(f"💱 店铺货币: {self.shop_currency}")
        except Exception as e:
            self.logger.warning(f"⚠️  解析meta失败: {e}")

        # 请求产品数据
        yield from self.request_page()

    def meta_failed(self, failure):
        """
        处理 meta.json 请求失败的错误回调

        如果 meta.json 请求失败，继续采集并尝试任务/域名配置；不推测为 USD。

        Args:
            failure (scrapy.http.Failure): 请求失败信息
        """
        self.logger.warning("获取店铺币种失败，将检查任务/站点币种配置")
        yield from self.request_page()

    def request_page(self):
        """
        请求指定页码的产品数据

        构造 Shopify products.json API 请求 URL:
            {domain}/products.json?limit={limit}&page={page}

        使用 dont_filter=True 确保不因 URL 重复请求而被过滤。
        """
        url = f"{self.domain}/products.json?limit={self.limit}&page={self.page}"
        self.logger.info(f"📄 请求第 {self.page} 页: {url}")

        yield scrapy.Request(
            url,
            callback=self.parse_products,
            errback=self.products_failed,
            dont_filter=True,
            meta={'page': self.page}
        )

    @property
    def metrics(self):
        return get_metrics(self.crawler)

    def products_failed(self, failure):
        self.metrics.counts["requests_failed"] += 1
        self.metrics.error("products_request", failure.getErrorMessage())

    def format_shopify_options(self, options):
        """
        将 Shopify product options 列表转换为统一的属性格式字符串

        输出格式:
            Name1^Val1#Val2|||Name2^Val3

        示例:
            Size^S#M#L|||Color^Red#Blue

        处理逻辑:
            - 过滤掉 "Title" 和 "Default Title" 等无效选项名
            - 每个选项的值去重并保持顺序
            - 使用 "^" 连接选项名和值，使用 "#" 分隔多个值
            - 使用 "|||" 连接多个选项

        Args:
            options (list[dict]): Shopify product options 列表，
                                  每条含 name 和 values 字段

        Returns:
            str: 格式化后的属性字符串，空列表返回空字符串
        """
        if not options:
            return ""

        option_segments = []

        for opt in options:
            # 1. 清理属性名称
            raw_name = opt.get("name", "")
            opt_name = self.clean_text_regex(raw_name)

            # 过滤掉 Shopify 默认的无效标题
            if not opt_name or opt_name.lower() in ['title', 'default title']:
                continue

            # 2. 获取并清理属性值（去重）
            raw_values = opt.get("values", [])
            # 确保 raw_values 是列表，如果是字符串则转为列表
            if isinstance(raw_values, str):
                raw_values = [raw_values]

            cleaned_values = []
            for v in raw_values:
                val = self.clean_text_regex(v)
                if val:
                    cleaned_values.append(val)

            # 去重并保持顺序（Python 3.7+ dict.fromkeys 可实现）
            unique_values = list(dict.fromkeys(cleaned_values))

            # 3. 构造单个属性段: Name^Val1#Val2
            if unique_values:
                values_str = "#".join(unique_values)
                option_segments.append(f"{opt_name}^{values_str}")

        # 4. 用 ||| 连接所有属性段
        return "|||".join(option_segments)

    # Variant parsing lives in an adapter shared by public JSON and Storefront GraphQL.
    parse_products = ShopifyVariants.parse_products
    emit_variants = ShopifyVariants.emit_variants
    variant_request = ShopifyVariants.variant_request
    parse_variant_page = ShopifyVariants.parse_variant_page

    def build_item(self, sku, name, desc, price, category, image, attr_str):
        """
        构建标准格式的商品 Item 字典

        Item 字段说明:
            SKU          : 站点内稳定的商品/变体编码（与来源域名组成唯一身份）
            Name         : 商品名称
            Description  : 清洗后的 HTML 描述
            Regular price: 来源币种价格（由 Pipeline 统一换算为 USD）
            Categories   : 商品分类（来自 Shopify product_type）
            Images       : 首图 URL
            cf_opingts   : 商品属性选项（格式: Name^Val1#Val2|||...）
            自定义分类    : 业务自定义分类
            原站域名      : 原始站点域名（使用 urlparse 提取）
            分布网站识别  : 固定值 0（预留字段）
            语言          : 默认 "en"

        Args:
            sku      (str) : 商品 SKU 编码
            name     (str) : 商品名称
            desc     (str) : 商品描述 HTML
            price    (float): 商品价格
            category (str) : 商品分类
            image    (str) : 首图 URL
            attr_str (str) : 商品属性字符串

        Returns:
            dict: 标准 Item 字典
        """
        currency, currency_source = resolve_currency(self, preferred=self.shop_currency)
        item = {
            "SKU": sku,
            "Name": name,
            "Description": desc,
            "Regular price": round(price, 2),
            "Categories": category,
            "Images": image,
            "cf_opingts": attr_str,
            "自定义分类": self.custom_category,
            "产品标签": self.product_role,
            "原站域名": urlparse(self.domain).netloc,  # 使用urlparse更安全
            "分布网站识别": 0,
            "语言": "en",
            "货币": currency,
            "币种来源": currency_source,
        }

        # 调试信息
        # self.logger.info(f"{item}")

        return item
