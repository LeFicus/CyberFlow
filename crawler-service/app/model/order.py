"""
订单数据模型 — orders 表的 ORM 映射

存储从电商平台支付系统抓取到的订单记录，包括订单金额、货币、支付状态、
客户信息等字段，并冗余存储站点维度信息以便快速统计分析。

注意事项:
    - id 字段使用外部订单 ID（来自电商平台），配合 autoincrement=False
    - amount 使用 Numeric(10,2) 精确存储金额，避免浮点精度问题
    - admin_name / theme_name / product_category 为冗余字段，来自 site_info 关联
"""

from datetime import datetime
from sqlalchemy import Column, Integer, String, DateTime, Numeric, JSON
from app.core.database import Base


class Order(Base):
    """
    订单表 — 存储电商平台订单流水

    字段说明:
        id                 : 主键，直接使用外部电商平台的订单ID（非自增）
        amount             : 订单金额（使用 Numeric 精确存储，避免浮点陷阱）
        currency           : 货币代码（如 USD、EUR、CNY）
        create_time        : 订单创建时间（来自电商平台）
        product_host       : 商品所在站点的域名
        pay_status_text    : 支付状态文本描述
        customer_ip_country: 客户 IP 所属国家
        shipping_email     : 收货邮箱地址
        admin_name         : 关联的管理员名称（冗余，便于直接查询）
        theme_name         : 站点主题名称（冗余，便于直接查询）
        product_category   : 商品分类（冗余，便于直接查询）
    """
    __tablename__ = 'orders'

    # 主键使用外部订单 ID，auto-increment 设为 False
    # 如果外部订单 ID 为字符串，需将 Integer 改为 String
    id = Column(
        Integer,
        primary_key=True,
        index=True,
        unique=True,
        autoincrement=False,
        comment='订单ID（直接使用外部电商平台订单号，非自增）'
    )

    amount = Column(
        Numeric(10, 2),
        comment='订单金额（DECIMAL 类型，精确到分）'
    )

    currency = Column(
        String(50),
        comment='货币代码（如 USD、EUR、CNY）'
    )

    create_time = Column(
        DateTime,
        default=datetime.now,
        comment='订单创建时间（来自电商平台）'
    )

    product_host = Column(
        String(255),
        comment='商品所在站点域名'
    )

    pay_status_text = Column(
        String(50),
        comment='支付状态文本描述'
    )

    customer_ip_country = Column(
        String(50),
        comment='客户 IP 所属国家'
    )

    shipping_email = Column(
        String(50),
        comment='收货邮箱地址'
    )

    # ========== 冗余维度字段 ==========
    # 以下字段来自 site_info 表关联，冗余存储以加速查询、减少 JOIN 操作
    admin_name = Column(
        String(50),
        index=True,
        comment='管理员名称（冗余自 site_info，带索引）'
    )

    theme_name = Column(
        String(50),
        index=True,
        comment='站点主题名称（冗余自 site_info，带索引）'
    )

    product_category = Column(
        String(50),
        index=True,
        comment='商品分类（冗余自 site_info，带索引）'
    )

    product_info = Column(
        JSON,
        comment='订单爬取结果中的商品详情数组'
    )
