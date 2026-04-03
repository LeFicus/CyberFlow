from pydantic import BaseModel


class OrderRequest(BaseModel):
    start_time: str
    end_time: str