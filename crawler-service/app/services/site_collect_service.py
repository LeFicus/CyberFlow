import datetime
import time

import httpx
from loguru import logger
from sqlalchemy import func

from app.core.database import SessionLocal
from app.model.site_info import SiteInfo, SiteIndexingHistory

BASE_URL = "http://104.233.194.18"
EXCLUDE_NAMES = ["super"]


class SiteIndexCrawler:

    def __init__(self, username, password):
        self.username = username
        self.password = password
        # bind 会在后续所有 self.log 的输出中自动附带 [user: username]
        self.log = logger.bind(user=username)
        self.client = httpx.Client(verify=False, timeout=30)

    def save_to_mysql(self, data_list):
        db = SessionLocal()
        try:
            total_count = len(data_list)
            self.log.info(f"💾 数据库同步开始：准备处理 {total_count} 条原始记录")

            sites = db.query(SiteInfo.site_domain, SiteInfo.admin_name, SiteInfo.theme_name,
                             SiteInfo.product_category).all()
            # 转成字典格式: {"domain.com": {"admin": "张三", "theme": "模板A"}, ...}
            site_map = {
                s.site_domain.strip().lower(): {"admin": s.admin_name, "theme": s.theme_name,
                                                "product_category": s.product_category}
                for s in sites if s.site_domain
            }

            # 2. 过滤
            new_objects = []
            today_date = datetime.date.today()
            for item in data_list:
                # 同样对抓取到的数据进行清理
                raw_domain = str(item.get("site_domain", "")).strip().lower()

                if not raw_domain:
                    continue
                existing = db.query(SiteIndexingHistory).filter(
                    SiteIndexingHistory.site_domain == raw_domain,
                    func.date(SiteIndexingHistory.recorded_at) == today_date
                ).first()
                domain = item.get("site_domain").replace("www.", '')
                site_meta = site_map.get(domain, {"admin": "未知", "theme": "未知", "product_category": "未知"})
                if existing:
                    existing.index_count = item["google_count"]
                    existing.product_count = item["total_product"]
                    existing.recorded_at = datetime.datetime.now()  # 更新为最新时间
                else:
                    new_objects.append(SiteIndexingHistory(
                        site_domain=item["site_domain"],
                        index_count=item["google_count"],
                        product_count=item["total_product"],
                        recorded_at=item["recorded_at"],
                        created_at=item["created_at"],
                        admin_name = site_meta['admin']
                    ))

            # 3. 提交
            new_count = len(new_objects)
            if new_objects:
                db.bulk_save_objects(new_objects)
                db.commit()
                self.log.success(f"✅ 数据库写入成功：新增 {new_count} 条，忽略重复 {total_count - new_count} 条")
            else:
                self.log.info("ℹ️ 数据库未更新：抓取的数据已全部存在")

        except Exception as e:
            db.rollback()
            self.log.error(f"❌ 数据库操作异常: {str(e)}")
            raise e
        finally:
            db.close()

    def login(self):
        try:
            self.log.info(f"🔑 正在尝试登录管理系统...")
            resp = self.client.post(
                f"{BASE_URL}/adminapi/login",
                json={"username": self.username, "password": self.password},
            )
            resp.raise_for_status()  # 检查 HTTP 状态码

            token = resp.json().get("data", {}).get("access_token")
            if not token:
                raise ValueError("响应中未包含 access_token")

            self.client.headers.update({"Authorization": f"Bearer {token}"})
            self.log.success("🔓 登录成功，Token 已注入 Header")
        except Exception as e:
            self.log.error(f"🚫 登录认证失败: {str(e)}")
            raise e




    def fetch_site_map(self):
        self.log.info("🗺️ 开始获取站点收录情况")
        site_map = {}
        page = 1

        while True:
            try:
                resp = self.client.get(
                    f"{BASE_URL}/adminapi/statistics/theme/list",
                    params={"page": page, "pageSize": 100, "theme_id": "0"},
                )
                data = resp.json().get("data", {}).get("data", [])

                if not data:
                    self.log.debug(f"正在获取站点收录信息,共计 {len(site_map)} 个站点")
                    break

                for item in data:
                    site_map[item.get("site_domain")] = item
                self.log.debug(f"收录列表：已处理第 {page} 页")

                if len(data) < 100:
                    break

                page += 1
                time.sleep(0.2)
            except Exception as e:
                self.log.warning(f"⚠️ 抓取站点列表第 {page} 页失败: {str(e)}")
                break
        return site_map

    def run(self):
        self.log.info("🚀 爬虫任务正式启动")
        try:
            self.login()
            site_map = self.fetch_site_map()

            if not site_map:
                self.log.warning("🔍 未获取到任何站点数据")
                return

            final_data = []

            # 遍历字典的所有值
            for domain, item in site_map.items():
                # 2. 提取并清洗数据
                # 注意：确保字段名与 API 返回以及 save_to_mysql 期望的一致
                total_product = item.get("total_product", 0)
                google_count = item.get("google_count", 0)

                final_data.append({
                    "site_domain": domain,
                    "total_product": total_product,
                    "google_count": google_count,
                    "recorded_at": datetime.datetime.now(),
                    "created_at":item.get("adddate")
                })

            # 3. 执行入库
            if final_data:
                self.save_to_mysql(final_data)
            else:
                self.log.warning("🔍 任务结束：过滤后未发现符合条件的域名数据")

        except Exception as e:
            self.log.critical(f"💥 爬虫任务因不可预知错误中断: {str(e)}")
        finally:
            self.client.close()
            self.log.info("🏁 任务线程资源已释放")