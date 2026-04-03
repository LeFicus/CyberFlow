from datetime import datetime
from sqlalchemy import Column, String, Integer, DateTime
from app.core.database import Base

class SiteInfo(Base):
    __tablename__ = 'site_info'
    id = Column(Integer, primary_key=True, index=True)
    username = Column(String(50))
    site_domain = Column(String(100), unique=True, index=True) # 域名应该唯一且带索引
    admin_name = Column(String(50))
    theme_name = Column(String(50))
    product_category = Column(String(50))
    created_at = Column(DateTime, default=datetime.now)

class SiteIndexingHistory(Base):
    __tablename__ = 'site_indexing_history'
    id = Column(Integer, primary_key=True, index=True)
    site_domain = Column(String(100), index=True)  # 域名应该唯一且带索引
    index_count = Column(Integer, default=0)
    product_count = Column(Integer, index=True)
    recorded_at = Column(DateTime, default=datetime.now)
    created_at = Column(DateTime, default=datetime.now)
    admin_name = Column(String(50), index=True)

