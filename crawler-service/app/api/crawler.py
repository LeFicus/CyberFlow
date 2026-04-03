from celery.result import AsyncResult
from fastapi import APIRouter, HTTPException

from app.core.celery_app import celery_app
from app.core.config import settings
from app.model.request.order_resquest import OrderRequest
from app.model.request.site_resquest import SiteRequest
from app.tasks.order_crawler_task import run_order_crawler
# 导入两个不同的任务
from app.tasks.site_crawler_task import run_site_crawler
from app.tasks.site_index_crawler_task import run_site_index_crawler

router = APIRouter(prefix="/crawler", tags=["crawler"])

# ➡️ 接口 1：启动站点信息爬虫
@router.post("/site/start")
async def start_site_crawler(request: SiteRequest ):
    try:
        username = request.username
        password = request.password
        task = run_site_crawler.delay(username, password)
        return {"task_id": task.id, "crawler_type": "site", "status": "Task dispatched"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Site crawler dispatch error: {str(e)}")

@router.post("/site/collect")
async def collect_site_crawler(request: SiteRequest ):
    try:
        username = request.username
        password = request.password
        task = run_site_index_crawler.delay(username, password)
        return {"task_id": task.id, "crawler_type": "site_index", "status": "Task dispatched"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Site crawler dispatch error: {str(e)}")
# ➡️ 接口 2：启动订单爬虫 (增加了 start_time 和 end_time 参数)
@router.post("/order/start")
async def start_order_crawler(request: OrderRequest ):
    try:
        username = settings.CRAWLER_USERNAME
        password = settings.CRAWLER_PASSWORD
        start_time = request.start_time
        end_time = request.end_time
        # 传入所有必需的参数
        task = run_order_crawler.delay(username, password, start_time, end_time)
        return {"task_id": task.id, "crawler_type": "order", "status": "Task dispatched"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Order crawler dispatch error: {str(e)}")


# ➡️ 状态查询接口 (保持通用，不需要拆分)
@router.get("/status/{task_id}")
async def status(task_id: str):
    task = AsyncResult(task_id, app=celery_app)
    result_data = str(task.result) if isinstance(task.result, Exception) else task.result
    return {
        "task_id": task_id,
        "state": task.state,
        "result": result_data
    }


