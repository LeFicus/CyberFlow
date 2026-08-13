"""
站点爬虫请求模型 — SiteRequest 请求体定义

用于站点爬虫和收录统计爬虫 API 的请求参数验证。

字段说明:
    username : 远程管理系统的登录用户名
    password : 远程管理系统的登录密码
"""

from pydantic import BaseModel


class SiteRequest(BaseModel):
    """
    站点爬虫请求体

    定义调用站点爬虫 API 时需要的认证信息，
    用于登录远程管理系统并获取访问 Token。

    属性:
        username (str): 远程管理系统登录用户名
        password (str): 远程管理系统登录密码
    """
    username: str
    password: str
