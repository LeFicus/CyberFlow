from celery import Celery
from app.core.config import settings

celery_app = Celery(
    "crawler",
    broker=settings.BROKER_URL,
    backend=settings.BACKEND_URL,
)

# 建议在任务定义处显式指定 name，例如 @celery_app.task(name="run_site_index_crawler")
celery_app.conf.update(
    # 修正：补齐逗号，删掉重复的 include 块
    include=[
        'app.tasks.site_crawler_task',
        'app.tasks.order_crawler_task',
        'app.tasks.site_index_crawler_task',
    ],
    task_serializer='json',
    accept_content=['json'],
    result_serializer='json',
    timezone='Asia/Shanghai',
    enable_utc=False,
    task_track_started=True,
    worker_max_tasks_per_child=50,
    # 修正路由名称，确保和任务的 name 对应
    task_routes={
        "run_site_crawler": {"queue": settings.QUEUE_NAME},
        "run_order_crawler": {"queue": settings.QUEUE_NAME},
        "run_site_index_crawler": {"queue": settings.QUEUE_NAME},
    }
)