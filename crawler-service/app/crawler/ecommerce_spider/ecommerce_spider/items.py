"""
Scrapy Item 模型定义 — ecommerce_spider 项目的 Item 类

定义爬虫产出的数据项结构。当前项目中实际的 Item 是字典格式
（在 spider 中直接构造 dict），此类保留用于扩展场景。

参考文档: https://docs.scrapy.org/en/latest/topics/items.html
"""

import scrapy


class EcommerceSpiderItem(scrapy.Item):
    """
    电商爬虫 Item 基类

    预留的 Item 模型，可在此定义爬虫产出的标准字段结构。
    当前项目中爬虫直接产出字典格式的数据，此类作为扩展点保留。

    示例字段定义:
        SKU           = scrapy.Field()  # 商品唯一库存编码
        Name          = scrapy.Field()  # 商品名称
        Description   = scrapy.Field()  # 商品描述
        Regular_price = scrapy.Field()  # 商品价格
        currency = scrapy.Field()  # 原始货币，入库前统一换算为 USD
        Categories    = scrapy.Field()  # 商品分类
        Images        = scrapy.Field()  # 商品图片URL
        cf_opingts    = scrapy.Field()  # 商品属性选项
        自定义分类    = scrapy.Field()  # 业务自定义分类
        原站域名      = scrapy.Field()  # 原始站点域名
    """
    pass
