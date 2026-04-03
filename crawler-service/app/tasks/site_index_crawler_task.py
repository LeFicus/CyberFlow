from app.core.celery_app import celery_app
from app.services.site_collect_service import SiteIndexCrawler

@celery_app.task(
    bind=True,
    name="run_site_index_crawler",  # 明确命名
    retry_backoff=True,
    max_retries=3
)
def run_site_index_crawler(self, username, password):
    try:
        crawler = SiteIndexCrawler(username, password)
        crawler.run()
        return {"status": "success", "type": "site_collect", "username": username}
    except Exception as e:
        raise self.retry(exc=e)
    # print(f"DEBUG: 任务开始执行! 用户名: {username}")  # 用 print 确保能看到
    # from app.services.site_collect_service import SiteIndexCrawler
    # crawler = SiteIndexCrawler(username, password)
    # crawler.run()
    # return "Done"