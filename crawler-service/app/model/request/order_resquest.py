"""
订单爬虫请求模型 — OrderRequest 请求体定义

用于订单爬虫 API 的请求参数验证。

字段说明:
    start_time : 订单查询起始时间（格式: YYYY-MM-DD HH:MM:SS）
    end_time   : 订单查询截止时间（格式: YYYY-MM-DD HH:MM:SS）
"""

from pydantic import BaseModel


class OrderRequest(BaseModel):
    """
    订单爬虫请求体

    定义调用订单爬虫 API 时需要的时间范围参数，
    用于限定订单数据的抓取时间窗口。

    属性:
        start_time (str): 查询起始时间（字符串格式）
        end_time   (str): 查询截止时间（字符串格式）
    """
    start_time: str
    end_time: str
