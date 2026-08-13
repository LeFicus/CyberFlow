"""
数据库连接模块 — SQLAlchemy ORM 基础设施

本模块封装了 MySQL 数据库的连接管理，提供：
    1. 数据库引擎 (Engine)          — 连接池管理
    2. 会话工厂 (SessionLocal)      — 线程安全的数据库会话
    3. 模型基类 (Base)              — ORM 声明式基类
    4. 依赖注入函数 (get_db)        — FastAPI 依赖项，自动管理会话生命周期

数据库连接地址格式: mysql+pymysql://用户名:密码@地址:端口/数据库名
"""

from sqlalchemy import create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

from app.core.config import settings

# ========== 创建数据库引擎 ==========
# create_engine 创建 SQLAlchemy 引擎，负责底层数据库连接
# pool_recycle=3600    : 每小时回收一次连接（防止 MySQL 8小时空闲超时）
# pool_pre_ping=True   : 每次使用连接前先 ping 测试，确保连接有效
engine = create_engine(
    settings.DATABASE_URL,
    pool_recycle=3600,  # 每小时回收连接
    pool_pre_ping=True  # 连接前测试有效性
)

# ========== 创建会话工厂 ==========
# sessionmaker 是线程安全的会话工厂类
# autocommit=False : 禁止自动提交，所有写操作需显式 commit
# autoflush=False  : 禁止自动刷新，手动控制何时同步数据到数据库
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# ========== 创建模型基类 ==========
# 所有 ORM 模型类都需要继承 Base，以自动关联到数据库引擎
Base = declarative_base()


# ========== 数据库会话依赖注入 ==========
def get_db():
    """
    获取数据库连接的依赖项（FastAPI 风格生成器）

    用于 FastAPI 依赖注入系统，每次请求自动创建并管理会话生命周期：
        - 请求进入时创建数据库会话
        - 请求处理期间通过 yield 提供会话对象
        - 请求结束时（无论成功或异常）自动关闭会话

    使用方式:
        @app.get("/items")
        def read_items(db: Session = Depends(get_db)):
            ...

    Yields:
        Session: SQLAlchemy 数据库会话对象
    """
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()  # 确保会话在请求结束后被关闭，防止连接泄漏
