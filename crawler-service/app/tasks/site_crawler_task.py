from app.core.celery_app import celery_app
from app.services.site_crawler_service import SiteCrawler

@celery_app.task(
    bind=True,
    name="run_site_crawler",  # 明确命名
    retry_backoff=True,
    max_retries=3
)
def run_site_crawler(self, username, password):
    try:
        crawler = SiteCrawler(username, password)
        crawler.run()
        return {"status": "success", "type": "site", "username": username}
    except Exception as e:
        raise self.retry(exc=e)