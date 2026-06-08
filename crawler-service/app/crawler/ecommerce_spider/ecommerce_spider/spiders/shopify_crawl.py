import hashlib
import json
import os
import scrapy
import re
from urllib.parse import urlparse

from bs4 import BeautifulSoup


class ShopifyCrawlFastSpider(scrapy.Spider):
    """
    Shopify快速爬虫
    特点：
    1. 爬取products.json接口，速度快
    2. 自动处理变体和属性
    3. 生成唯一SKU
    4. 支持多币种转换
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

    def __init__(self, domain=None, category="未知分类", export_file=None, *args, **kwargs):
        super().__init__(*args, **kwargs)

        # 参数验证
        if not domain:
            raise ValueError("❌ 必须提供domain参数")
        if not domain.startswith(("http://", "https://")):
            domain = f"https://{domain}"

        self.domain = domain.rstrip("/")
        self.export_file = export_file or f"{urlparse(self.domain).netloc}_products.xlsx"
        self.custom_category = category.strip() or "未知分类"
        self.page = 1
        self.limit = 250
        self.shop_currency = "USD"
        self.exchange_rates = self.load_exchange_rates()
        self.logger.setLevel(1)
        # 记录已处理的产品ID，避免重复
        self.processed_product_ids = set()

    def load_exchange_rates(self):
        """加载汇率文件"""
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
        """通用正则清洗"""
        if not text:
            return ""
        text = str(text)
        # 移除引号和控制字符
        text = re.sub(r"['\"`‘’“”]", "", text)
        text = re.sub(r"[\x00-\x1F\x7F-\x9F]", "", text)
        # 合并空格
        return re.sub(r"\s+", " ", text).strip()

    def clean_variant_options(self, variant):
        """清洗变体选项"""
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
        深度清洗描述内容：
        1. 标签白名单控制
        2. 彻底移除 a, img, video, script 等
        3. 特殊优化表格：移除所有宽度、高度及内联样式，仅保留纯净结构
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
        """生成唯一SKU（解决原SKU重复问题）"""
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
        """开始请求"""
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
        """解析meta信息"""
        try:
            meta_data = json.loads(response.text)
            self.shop_currency = meta_data.get("currency", "USD").upper()
            self.logger.info(f"💱 店铺货币: {self.shop_currency}")
        except Exception as e:
            self.logger.warning(f"⚠️  解析meta失败: {e}")

        # 请求产品数据
        yield from self.request_page()

    def meta_failed(self, failure):
        """meta请求失败的处理"""
        self.logger.warning(f"⚠️  获取meta信息失败，使用默认货币USD")
        yield from self.request_page()

    def request_page(self):
        """请求指定页码的产品数据"""
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
        将 options 列表转换为 Shopify 格式字符串
        例如: Size^S#XS#L|||Color^Red#Blue
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
        """解析产品数据 - 优化版：确保所有变体均被抓取"""
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
        """构建Item"""
        item = {
            "SKU": sku,
            "Name": name,
            "Description": desc,
            "Regular price": round(price, 2),
            "Categories": category,
            "Images": image,
            "cf_opingts": attr_str,
            "自定义分类": self.custom_category,
            "原站域名": urlparse(self.domain).netloc,  # 使用urlparse更安全
            "分布网站识别": 0,
            "语言": "en"
        }

        # 调试信息
        # self.logger.info(f"{item}")

        return item