"""
配置管理模块 — 集中管理所有应用配置项

使用 Pydantic Settings 从环境变量和 .env 文件中加载配置，
支持类型校验和默认值，确保应用启动时配置完整。

配置项:
    DATABASE_URL  : MySQL 数据库连接地址（使用 PyMySQL 驱动）
    REDIS_URL     : Redis 连接地址（用于 Scrapy SKU 去重）
    CRAWLER_*     : 默认爬虫登录账号密码（从环境变量注入，保护敏感信息）
"""

from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional


class Settings(BaseSettings):
    """
    全局配置类

    使用 Pydantic BaseSettings 自动从以下来源加载配置（优先级从高到低）：
        1. 环境变量
        2. .env 文件
        3. 类属性默认值
    """

    # ========== 数据库配置 ==========
    # 数据库连接字符串，格式: mysql+pymysql://用户名:密码@地址:端口/数据库名
    DATABASE_URL: str = "mysql+pymysql://root:123456@localhost:3306/cyberflow"

    # ========== Redis 配置 ==========
    # Redis 连接地址，主要用于 Scrapy 爬虫中的 SKU 去重（Redis Set 集合）
    REDIS_URL: str = "redis://127.0.0.1:6379/0"

    # ========== 默认爬虫账号密码 ==========
    # 从环境变量读取，避免将敏感信息硬编码在配置文件中
    # 若未设置则为 None，调用方需自行处理认证逻辑
    CRAWLER_USERNAME: Optional[str] = None
    CRAWLER_PASSWORD: Optional[str] = None

    # ========== Pydantic Settings 元配置 ==========
    model_config = SettingsConfigDict(
        env_file=".env",           # 从项目根目录的 .env 文件读取配置
        env_file_encoding="utf-8", # 使用 UTF-8 编码读取环境文件
        extra="ignore",            # 忽略 .env 中未定义的额外键
    )


# ========== 全局配置单例 ==========
# 模块加载时自动实例化，后续所有模块通过 import settings 使用统一配置
settings = Settings()
