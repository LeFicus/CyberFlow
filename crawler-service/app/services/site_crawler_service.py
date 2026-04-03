import time

import httpx
from loguru import logger

from app.core.database import SessionLocal
from app.model.site_info import SiteInfo

BASE_URL = "http://104.233.194.18"
EXCLUDE_NAMES = ["super"]


class SiteCrawler:

    def __init__(self, username, password):
        self.username = username
        self.password = password
        # bind 会在后续所有 self.log 的输出中自动附带 [user: username]
        self.log = logger.bind(user=username)
        self.client = httpx.Client(verify=False, timeout=30)

    def save_to_mysql(self, data_list):
        db = SessionLocal()
        try:
            self.log.info(f"💾 准备处理 {len(data_list)} 条记录")
            for item in data_list:
                domain = str(item.get("site_domain", "")).strip().lower()
                if not domain: continue

                # 查找数据库中是否已有该域名
                existing_site = db.query(SiteInfo).filter(SiteInfo.site_domain == domain).first()

                if existing_site:
                    # 如果已存在，更新维度信息（防止之前是空的）
                    existing_site.theme_name = item.get("theme_name")
                    existing_site.product_category = item.get("product_category")
                    existing_site.admin_name = item.get("admin_name")
                else:
                    # 如果不存在，新建
                    new_site = SiteInfo(
                        username=self.username,
                        site_domain=item["site_domain"],
                        admin_name=item["admin_name"],
                        theme_name=item.get("theme_name"),
                        product_category=item.get("product_category"),
                        created_at=item["created_at"],
                    )
                    db.add(new_site)

            db.commit()
            self.log.success("✅ 站点信息同步完成（已处理新增与更新）")

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
        self.log.info("🗺️ 开始构建站点映射表 (Site Map)...")
        site_map = {}
        page = 1

        while True:
            try:
                resp = self.client.get(
                    f"{BASE_URL}/adminapi/site/site/list",
                    params={"page": page, "pageSize": 100, "server_id": "0"},
                )
                data = resp.json().get("data", {}).get("data", [])

                if not data:
                    self.log.debug(f"站点地图构建完成，共计 {len(site_map)} 个有效站点")
                    break

                for item in data:
                    admin_name = item.get("admin_name")
                    if admin_name in EXCLUDE_NAMES:
                        continue
                    site_map[item.get("site_domain")] = item

                self.log.debug(f"站点列表：已处理第 {page} 页")

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

            final_data = []
            page = 1

            while True:
                self.log.info(f"🛰️ 正在抓取域名列表：第 {page} 页")
                resp = self.client.get(
                    f"{BASE_URL}/adminapi/domain/domain/list",
                    params={"page": page, "pageSize": 100},
                )
                data = resp.json().get("data", {}).get("data", [])

                if not data:
                    self.log.warning(f"第 {page} 页数据为空，停止抓取")
                    break

                current_page_saved = 0
                for item in data:
                    domain = item.get("domain")
                    admin_name = item.get("admin_name")
                    add_time = item.get("add_time")

                    # 过滤逻辑日志可以根据需要设为 debug
                    if admin_name in EXCLUDE_NAMES or item.get("status") != 2:
                        continue

                    site_info = site_map.get(domain, {})
                    final_data.append({
                        "site_domain": domain,
                        "admin_name": site_info.get("admin_name") or admin_name,
                        "theme_name" : site_info.get("theme_name"),
                        "product_category" : site_info.get("product_category",''),
                        "created_at": add_time,

                    })
                    current_page_saved += 1

                self.log.debug(f"第 {page} 页处理完毕：入库候选 {current_page_saved}/{len(data)} 条")

                if len(data) < 100:
                    self.log.info("已到达域名列表最后一页")
                    break

                page += 1
                time.sleep(0.2)

            if final_data:
                self.save_to_mysql(final_data)
            else:
                self.log.warning("🔍 任务结束：未发现符合条件的域名数据")

        except Exception as e:
            self.log.critical(f"💥 爬虫任务因不可预知错误中断: {str(e)}")
        finally:
            self.client.close()
            self.log.info("🏁 任务线程资源已释放")