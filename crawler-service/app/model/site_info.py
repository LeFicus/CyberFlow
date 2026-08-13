"""
站点信息数据模型 — site_info 与 site_indexing_history 表的 ORM 映射

本模块定义了两张核心业务表：
    1. SiteInfo             — 站点基本信息（域名、管理员、主题、分类）
    2. SiteIndexingHistory  — 站点收录历史记录（Google 收录数、商品数）

用途:
    - SiteCrawler 服务爬取远程管理系统的站点数据后写入 site_info
    - SiteIndexCrawler 服务定期采集站点收录统计后写入 site_indexing_history
"""

from datetime import datetime
from sqlalchemy import Column, String, Integer, DateTime
from app.core.database import Base


class SiteInfo(Base):
    """
    站点信息表 — 存储电商站点的基本维度信息

    字段说明:
        id               : 主键，自增唯一标识
        username         : 电商平台用户名（关联的运维账号）
        site_domain      : 站点域名（唯一约束，带索引以便快速查询）
        admin_name       : 站点的管理员名称
        theme_name       : 站点使用的主题模板名称
        product_category : 站点主营商品分类
        created_at       : 记录创建时间
    """
    __tablename__ = 'site_info'

    id = Column(Integer, primary_key=True, index=True, comment='主键自增ID')
    username = Column(String(50), comment='电商平台用户名')
    site_domain = Column(String(100), unique=True, index=True, comment='站点域名（唯一，带索引加速查询）')
    admin_name = Column(String(50), comment='管理员名称')
    theme_name = Column(String(50), comment='站点主题名称')
    product_category = Column(String(50), comment='商品分类')
    created_at = Column(DateTime, default=datetime.now, comment='记录创建时间')


class SiteIndexingHistory(Base):
    """
    站点收录历史表 — 记录站点在搜索引擎中的收录情况

    用于跟踪站点在 Google 等搜索引擎中的索引数量和商品数量随时间的变化趋势。
    同一站点每天仅有唯一条记录，后续采集会更新该记录的数据。

    字段说明:
        id            : 主键，自增唯一标识
        site_domain   : 目标站点域名（带索引）
        index_count   : Google 收录的索引数量
        product_count : 站点上的商品总数
        recorded_at   : 数据采集记录时间
        created_at    : 记录首次创建时间
        admin_name    : 关联的管理员名称（带索引，冗余来自 site_info）
    """
    __tablename__ = 'site_indexing_history'

    id = Column(Integer, primary_key=True, index=True, comment='主键自增ID')
    site_domain = Column(String(100), index=True, comment='站点域名（带索引）')
    index_count = Column(Integer, default=0, comment='Google 收录索引数量')
    product_count = Column(Integer, index=True, comment='站点商品总数')
    recorded_at = Column(DateTime, default=datetime.now, comment='数据采集记录时间')
    created_at = Column(DateTime, default=datetime.now, comment='记录首次创建时间')
    admin_name = Column(String(50), index=True, comment='管理员名称（冗余自 site_info，带索引）')
