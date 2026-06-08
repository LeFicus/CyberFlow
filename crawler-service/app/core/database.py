# 数据库连接地址：mysql+驱动://用户名:密码@地址:端口/数据库名
from sqlalchemy import create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

from app.core.config import settings

# 创建引擎
engine = create_engine(
    settings.DATABASE_URL,
    pool_recycle=3600, # 每小时回收连接
    pool_pre_ping=True)

# 创建会话工厂
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# 创建模型基类
Base = declarative_base()

# 获取数据库连接的依赖项
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()