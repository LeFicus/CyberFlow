"""
站点爬虫服务模块 — 从远程管理系统抓取并同步站点信息

本模块实现站点信息的数据采集全流程：
    1. 登录远程管理系统，获取 Bearer Token 认证
    2. 构建站点映射表（Site Map），缓存站点维度的详细数据
    3. 分页遍历域名列表，关联站点映射表中的维度信息
    4. 过滤掉排除账号、非激活状态的域名
    5. 将有效站点数据去重后写入 MySQL site_info 表

数据流:
    远程 API → 站点映射表 (内存) → 域名列表分页遍历 → 关联合并 → site_info 表
"""

import time

import httpx
from loguru import logger

from app.core.database import SessionLocal
from app.model.site_info import SiteInfo

# ========== 常量配置 ==========
# 远程管理系统基础地址
BASE_URL = "http://104.233.194.18"
# 需要排除的管理员账号名称列表（这些账号的站点不采集）
EXCLUDE_NAMES = ["super"]


class SiteCrawler:
    """
    站点爬虫 — 登录远程管理系统并同步站点信息到本地数据库

    工作流程:
        1. login()          — 调用 /adminapi/login 获取 Token
        2. fetch_site_map() — 遍历 /adminapi/site/site/list 构建站点维度映射
        3. run()            — 遍历 /adminapi/domain/domain/list 获取域名列表，
                             与站点映射关联后保存到 MySQL
        4. save_to_mysql()  — 去重入库（新增或更新）

    属性:
        username (str): 远程管理系统登录用户名
        password (str): 远程管理系统登录密码
        log (Logger)  : 绑定了用户名的 loguru Logger 实例
        client (httpx.Client): HTTP 客户端（复用连接，支持 HTTPS 证书跳过）
    """

    def __init__(self, username, password):
        """
        初始化站点爬虫

        Args:
            username (str): 远程管理系统登录用户名
            password (str): 远程管理系统登录密码
        """
        self.username = username
        self.password = password
        # bind 会在后续所有 self.log 的输出中自动附带 [user: username]
        self.log = logger.bind(user=username)
        self.client = httpx.Client(verify=False, timeout=30)

    def save_to_mysql(self, data_list):
        """
        将站点数据批量写入 MySQL 数据库

        入库逻辑:
            - 遍历数据列表，按域名查询 site_info 表
            - 已有域名：更新 theme_name、product_category、admin_name 维度字段
            - 新域名：创建新的 SiteInfo 记录
            - 异常时回滚事务，确保数据一致性

        Args:
            data_list (list[dict]): 站点数据列表，每条记录包含:
                site_domain, admin_name, theme_name, product_category, created_at

        Raises:
            Exception: 数据库操作异常，事务回滚后向上抛出
        """
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
        """
        登录远程管理系统并获取认证 Token

        调用 /adminapi/login 接口进行身份验证，
        成功后将 Bearer Token 注入 HTTP 客户端请求头中。

        Raises:
            ValueError: 响应中未包含 access_token
            httpx.HTTPError: HTTP 请求失败
        """
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
        """
        从远程接口分页获取所有站点信息，构建域名 → 维度数据的映射表

        访问 /adminapi/site/site/list 接口，分页获取所有站点数据，
        过滤掉 EXCLUDE_NAMES 中的管理员站点，以域名为键构建字典。

        Returns:
            dict: 站点映射表，格式为 {域名: {site_domain, admin_name, theme_name, ...}}
        """
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
        """
        执行完整的站点爬虫任务（主入口方法）

        执行步骤:
            1. 登录远程管理系统获取认证 Token
            2. 构建站点映射表（含站点详细维度信息）
            3. 分页遍历域名列表，与站点映射关联
            4. 过滤条件: 排除 EXCLUDE_NAMES 中的管理员、排除非 status=2 的域名
            5. 最终将数据批量写入 MySQL

        异常处理和资源清理:
            - 捕获所有异常记录日志
            - finally 块确保 HTTP 客户端被关闭，释放网络资源
        """
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
