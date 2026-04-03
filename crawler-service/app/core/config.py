from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional


class Settings(BaseSettings):
    # 数据库配置
    DATABASE_URL: str = "mysql+pymysql://root:123456@localhost:3306/cyberflow"

    # Redis 配置
    REDIS_URL: str = "redis://127.0.0.1:6379/0"
    BROKER_URL: str = "redis://127.0.0.1:6379/1"
    BACKEND_URL: str = "redis://127.0.0.1:6379/2"

    # 默认爬虫账号密码（从环境变量读取，若无则为 None）
    CRAWLER_USERNAME: Optional[str] = None
    CRAWLER_PASSWORD: Optional[str] = None

    # 任务队列名称
    QUEUE_NAME: str = "crawler"

    # Pydantic Settings 内部配置
    model_config = SettingsConfigDict(
        env_file=".env",  # 指定读取的 .env 文件
        env_file_encoding="utf-8",
        extra="ignore"  # 忽略环境变量中多余的字段
    )


# 实例化
settings = Settings()