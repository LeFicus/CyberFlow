from fastapi import HTTPException, Header
from app.core.config import settings


async def verify_internal_token(x_internal_token: str = Header(None)):
    """验证内部服务间调用 Token，用于保护 /crawler 端点不被外部直接访问"""
    if x_internal_token != settings.INTERNAL_API_TOKEN:
        raise HTTPException(status_code=403, detail="Invalid internal token")
