from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional


class Settings(BaseSettings):
    # 数据库配置
    DATABASE_URL: str = "mysql+pymysql://root:123456@localhost:3306/cyberflow"

    # Redis 配置 (仅 Scrapy SKU 去重)
    REDIS_URL: str = "redis://127.0.0.1:6379/0"

    # 默认爬虫账号密码（从环境变量读取）
    CRAWLER_USERNAME: Optional[str] = None
    CRAWLER_PASSWORD: Optional[str] = None

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


settings = Settings()
