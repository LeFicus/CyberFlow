"""
Shopify 平台商品爬虫 — 通过 products.json API 高速采集商品数据

本爬虫专为 Shopify 电商平台设计，利用 Shopify 内置的 products.json
和 meta.json 接口获取完整的商品信息，避免了逐页解析 HTML 的低效方式。

核心特性:
    1. 高速采集   — 通过 /products.json 接口批量获取商品（每页 250 条）
    2. 自动变体   — 解析 product options 生成 cf_opingts 属性字符串
    3. 唯一 SKU   — 基于分类前缀 + MD5 哈希生成全局唯一 SKU
    4. 多币种支持  — 通过 meta.json 获取店铺货币 + exchange_rates.json 汇率换算
    5. 深度清洗   — BeautifulSoup 白名单过滤 + 表格结构保留描述内容
    6. 合并策略   — 每个产品只产出 1 个 Item（不含变体拆分）

数据流:
    meta.json (货币) → products.json (商品列表) → 逐产品构建 Item
        → clean_description() 清洗描述 → generate_unique_sku() 生成SKU
        → format_shopify_options() 格式化属性 → build_item() 组装 Item
"""

import hashlib
import json
import os
import scrapy
import re
from urllib.parse import urlparse

from bs4 import BeautifulSoup


class ShopifyCrawlFastSpider(scrapy.Spider):
    """
    Shopify 快速商品爬虫

    通过 Shopify 标准 API 接口采集商品信息：
        - /meta.json      — 获取店铺货币代码
        - /products.json  — 分页获取商品列表（含变体、选项、图片等完整数据）

    特点:
        - 速度极快（绕过 HTML 解析，直接获取 JSON 结构化数据）
        - 自动处理变体和属性，生成统一的 cf_opingts 格式
        - 基于 MD5 哈希生成全局唯一 SKU（避免不同站点 SKU 冲突）
        - 支持多币种汇率转换（USD 基准）

    属性:
        domain              (str) : 目标 Shopify 站点 URL
        export_file         (str) : 导出文件路径（预留）
        custom_category     (str) : 业务自定义分类名称
        page                (int) : 当前分页页码（从 1 开始）
        limit               (int) : 每页商品数（默认 250）
        shop_currency       (str) : 店铺货币代码（如 USD、EUR）
        exchange_rates      (dict): 汇率字典（货币代码 → 汇率倍率）
        processed_product_ids (set): 已处理产品 ID 集合（去重）
    """
    name = "shopify_crawl_fast"

    # 分类SKU前缀映射
    CATEGORY_SKU_MAP = {
        "五金/硬件": "HARD",
        "交通工具/汽车/飞机/船舶": "VEH",
        "体育用品": "SPORT",
        "保健/美容/卫生/护理": "CARE",
        "办公用品": "OFFC",
        "动物/宠物用品": "PET",
        "商业/工业": "IND",
        "婴幼儿用品": "BABY",
        "媒体": "MEDIA",
        "家具": "FURN",
        "家居与园艺": "HOME",
        "成人": "ADULT",
        "服饰与配饰": "APP",
        "玩具/游戏": "TOY",
        "电子产品": "ELEC",
        "箱包": "BAG",
        "艺术与娱乐": "ART",
        "饮食/烟酒": "FOOD",
    }

    def __init__(self, domain=None, category="未知分类", product_role="main", export_file=None, *args, **kwargs):
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
        self.shop_currency = "USD"
        self.exchange_rates = self.load_exchange_rates()
        self.logger.setLevel(1)
        # 记录已处理的产品ID，避免重复
        self.processed_product_ids = set()

    def load_exchange_rates(self):
        """
        从本地 exchange_rates.json 文件加载汇率数据

        汇率文件位于与爬虫脚本同目录下，JSON 格式：
            {"USD": 1.0, "EUR": 1.08, "GBP": 1.27, ...}

        若文件不存在或加载失败，回退为 {"USD": 1.0}。

        Returns:
            dict: 货币代码 → 对美元汇率的映射字典
        """
        try:
            base_dir = os.path.dirname(os.path.abspath(__file__))
            rate_path = os.path.join(base_dir, "exchange_rates.json")
            if os.path.exists(rate_path):
                with open(rate_path, "r", encoding="utf-8") as f:
                    return json.load(f)
        except Exception as e:
            self.logger.warning(f"⚠️  加载汇率文件失败: {e}")
        return {"USD": 1.0}

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

    def generate_unique_sku(self, product_id, variant_id=None):
        """
        生成全局唯一 SKU 编码

        SKU 格式:
            {分类前缀}-{产品MD5前6位}[-{变体MD5前3位}]

        示例:
            ELEC-A1B2C3       (无变体)
            ELEC-A1B2C3-D4E   (有变体)

        Args:
            product_id  (int/str): Shopify 产品 ID
            variant_id  (int/str): Shopify 变体 ID（可选）

        Returns:
            str: 全局唯一的 SKU 字符串
        """
        # 基础前缀
        prefix = self.CATEGORY_SKU_MAP.get(self.custom_category, 'GEN')

        # 产品哈希
        product_hash = hashlib.md5(str(product_id).encode()).hexdigest()[:6].upper()

        # 如果有变体ID，添加变体标识
        if variant_id:
            variant_hash = hashlib.md5(str(variant_id).encode()).hexdigest()[:3].upper()
            return f"{prefix}-{product_hash}-{variant_hash}"
        else:
            return f"{prefix}-{product_hash}"

    def start_requests(self):
        """
        爬虫起始入口 — 首先请求 meta.json 获取店铺货币信息

        Scrapy 会首先调用此方法获取初始请求列表。
        优先使用高优先级 (priority=10) 请求 meta.json，
        获取货币信息后再请求 products.json。
        """
        self.logger.info(f"🔍 开始爬取: {self.domain}")
        self.logger.info(f"📦 自定义分类: {self.custom_category}")

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
            self.shop_currency = meta_data.get("currency", "USD").upper()
            self.logger.info(f"💱 店铺货币: {self.shop_currency}")
        except Exception as e:
            self.logger.warning(f"⚠️  解析meta失败: {e}")

        # 请求产品数据
        yield from self.request_page()

    def meta_failed(self, failure):
        """
        处理 meta.json 请求失败的错误回调

        如果 meta.json 请求失败，使用默认货币 USD 继续爬取。

        Args:
            failure (scrapy.http.Failure): 请求失败信息
        """
        self.logger.warning(f"⚠️  获取meta信息失败，使用默认货币USD")
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
            dont_filter=True,
            meta={'page': self.page}
        )

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

    def parse_products(self, response):
        """
        解析 products.json 响应 — 遍历产品列表并构建 Item

        处理流程:
            1. 解析 JSON 获取 products 列表
            2. 遍历每个产品，使用 processed_product_ids 去重
            3. 提取标题、描述、分类、首图、价格（含汇率换算）
            4. 使用 format_shopify_options() 格式化产品属性
            5. 调用 build_item() 组装 Scrapy Item
            6. 若当前页满（== limit），继续请求下一页

        Args:
            response (scrapy.http.Response): products.json 的 HTTP 响应
        """
        try:
            data = json.loads(response.text)
        except json.JSONDecodeError as e:
            self.logger.error(f"❌ JSON解析失败: {e}")
            return

        products = data.get("products", [])
        current_page = response.meta.get('page', 1)

        if not products:
            self.logger.info(f"📭 第 {current_page} 页没有产品数据，爬取完成")
            return

        rate = self.exchange_rates.get(self.shop_currency, 1.0)

        for product in products:
            product_id = product.get("id")
            if product_id in self.processed_product_ids:
                continue
            self.processed_product_ids.add(product_id)

            # 1. 提取基础信息
            title = self.clean_text_regex(product.get("title", ""))
            raw_desc = product.get("body_html", "")
            desc = self.clean_description(raw_desc)
            category = self.clean_text_regex(product.get("product_type", "")) or "Others"
            # ✅ 修正：更健壮的图片提取逻辑
            images_list = product.get("images", [])
            if images_list:
                # 获取第一张图的 src
                default_image = images_list[0].get("src", "")
            else:
                # 兜底方案：尝试旧的单数形式
                default_image = product.get("image", {}).get("src", "")

            if default_image == '':
                continue
            variants = product.get("variants", [])
            options = product.get("options", [])
            final_options = self.format_shopify_options(options)


            # 处理变体价格
            variant_price = float(variants[0].get("price") or 0) * rate




            # 每一条变体都生成一个 Item
            yield self.build_item(
                sku=self.generate_unique_sku(product_id),
                name=title,
                desc=desc,
                price=variant_price,
                category=category,
                image=default_image,
                attr_str=final_options,
            )

        # 4. 翻页逻辑
        if len(products) == self.limit:
            self.page += 1
            yield from self.request_page()

    def build_item(self, sku, name, desc, price, category, image, attr_str):
        """
        构建标准格式的商品 Item 字典

        Item 字段说明:
            SKU          : 全局唯一库存编码（由 generate_unique_sku 生成）
            Name         : 商品名称
            Description  : 清洗后的 HTML 描述
            Regular price: 汇率换算后的价格（保留 2 位小数）
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
            "货币": "USD",
        }

        # 调试信息
        # self.logger.info(f"{item}")

        return item
