from app.core.celery_app import celery_app
from app.services.order_crawler_service import OrderCrawler

@celery_app.task(
    bind=True,
    name="run_order_crawler",  # 明确命名
    retry_backoff=True,
    max_retries=3
)
def run_order_crawler(self, username, password, start_time, end_time):
    try:
        crawler = OrderCrawler(username, password)
        crawler.run(start_time, end_time)
        return {"status": "success", "type": "order"}
    except Exception as e:
        raise self.retry(exc=e)