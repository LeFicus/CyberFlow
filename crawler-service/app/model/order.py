from datetime import datetime
from sqlalchemy import Column, Integer, String, DateTime, Numeric
from app.core.database import Base

class Order(Base):
    __tablename__ = 'orders'
    # 假设外部订单 ID 也是数字。如果有字母，请保持 String。这里加上 unique=True
    id = Column(Integer, primary_key=True, index=True, unique=True, autoincrement=False)
    amount = Column(Numeric(10, 2)) # 建议金额使用 Numeric 而不是 String
    currency = Column(String(50))
    create_time = Column(DateTime, default=datetime.now)
    product_host = Column(String(255))
    pay_status_text = Column(String(50))
    customer_ip_country = Column(String(50))
    shipping_email = Column(String(50))
    admin_name = Column(String(50),index=True)
    theme_name = Column(String(50),index=True)
    product_category = Column(String(50),index=True)