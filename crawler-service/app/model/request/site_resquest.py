from pydantic import BaseModel


class SiteRequest(BaseModel):
    username: str
    password: str
