from fastapi import FastAPI
from app.api.crawler import router

app = FastAPI(title="Crawler Platform")

app.include_router(router)