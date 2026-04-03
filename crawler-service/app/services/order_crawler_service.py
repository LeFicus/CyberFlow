import time
from datetime import datetime

import httpx
from loguru import logger

from app.core.database import SessionLocal
from app.model.order import Order
from app.model.site_info import SiteInfo


class OrderCrawler:
    def __init__(self, account, password):
        self.base_url = "https://c4partypay.com"
        self.account = account
        self.password = password
        self.log = logger.bind(user=account)
        self.headers = {
            "Accept": "application/json, text/plain, */*",
            "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
        }

    def login(self, client):
        """登录获取 Token"""
        url = f"{self.base_url}/platformapi/login/account"
        payload = {"account": self.account, "password": self.password, "googleCode": "", "terminal": 1}

        self.log.info("🔑 正在请求登录接口...")
        resp = client.post(url, json=payload)
        resp.raise_for_status()
        token = resp.json().get("data", {}).get("token")
        if not token:
            raise ValueError("登录响应异常，未获取到 Token")

        client.headers.update({"token": token})
        self.log.success("🔓 登录成功，Token 已就绪")

    def save_to_db(self, data_list):
        """批量去重并写入数据库（高性能版）"""
        if not data_list:
            return

        with SessionLocal() as db:
            try:

                sites = db.query(SiteInfo.site_domain, SiteInfo.admin_name, SiteInfo.theme_name,SiteInfo.product_category).all()
                # 转成字典格式: {"domain.com": {"admin": "张三", "theme": "模板A"}, ...}
                site_map = {
                    s.site_domain.strip().lower(): {"admin": s.admin_name, "theme": s.theme_name,"product_category":s.product_category}
                    for s in sites if s.site_domain
                }
                # 1. 提取本次抓取的所有订单 ID
                incoming_ids = [item.get("id") for item in data_list if item.get("id")]

                # 2. 一次性查出数据库中已存在的订单 ID（集合操作，速度极快）
                existing_records = db.query(Order.id).filter(Order.id.in_(incoming_ids)).all()
                existing_ids = {record[0] for record in existing_records}

                # 3. 过滤出需要新增的订单
                new_orders = []
                for item in data_list:
                    order_id = item.get("id")
                    if order_id in existing_ids:
                        continue
                    domain = item.get("product_host").replace("www.",'')
                    site_meta = site_map.get(domain, {"admin": "未知", "theme": "未知","product_category":"未知"})
                    c_time = item.get("create_time")
                    if isinstance(c_time, str):
                        c_time = datetime.strptime(c_time, "%Y-%m-%d %H:%M:%S")

                    new_orders.append(Order(
                        id=order_id,
                        amount=item.get("amount"),  # 模型层已改为 Numeric
                        currency=item.get("currency"),
                        create_time=c_time,
                        product_host=domain,
                        pay_status_text=item.get("pay_status_text"),
                        customer_ip_country=item.get("customer_ip_country"),
                        shipping_email=item.get("shipping_email"),
                        # 存入冗余字段
                        admin_name=site_meta["admin"],
                        theme_name=site_meta["theme"],
                        product_category=site_meta["product_category"],
                    ))

                # 4. 批量保存
                if new_orders:
                    db.bulk_save_objects(new_orders)
                    db.commit()
                    self.log.success(
                        f"✅ 成功入库 {len(new_orders)} 条新记录，忽略重复 {len(data_list) - len(new_orders)} 条")
                else:
                    self.log.info("ℹ️ 数据已全部存在，无需新增")
            except Exception as e:
                db.rollback()
                self.log.error(f"❌ 数据库写入失败: {e}")
                raise

    def run(self, start_time, end_time):
        self.log.info(f"🚀 订单抓取启动 | 时间范围: {start_time} 至 {end_time}")

        # 使用上下文管理器自动处理 HTTP 客户端的开启和关闭
        with httpx.Client(verify=False, timeout=30, headers=self.headers) as client:
            try:
                self.login(client)
                all_data = []
                page = 1
                exclude_names = {"测试", "ig-3", "test-mutiwp"}  # 改为集合，查找效率 O(1)
                card_exclude = {"400000******0000", "411111******1111"}

                while True:
                    self.log.info(f"🛰️ 正在抓取第 {page} 页订单...")
                    resp = client.get(
                        f"{self.base_url}/platformapi/pay.pay_order/lists",
                        params={"tenant_id": 95, "start_time": start_time, "end_time": end_time, "page_no": page,
                                "page_size": 100},
                    )
                    resp.raise_for_status()

                    # 1. 先拿到原始 JSON
                    json_data = resp.json()

                    # 2. 【关键修复】立即检查 json_data 是否为字典
                    if not isinstance(json_data, dict):
                        self.log.error(f"❌ 接口返回异常：预期字典，实际收到 {type(json_data)}。内容: {json_data}")
                        break  # 如果不是字典，说明结构全乱了，直接跳出循环防止崩溃

                    # 3. 现在可以安全地使用 .get() 了
                    data_content = json_data.get("data")

                    # 4. 继续检查内部的 data 字段
                    if not isinstance(data_content, dict):
                        self.log.error(f"⚠️ data 字段结构异常：{data_content}")
                        break

                    lists = data_content.get("lists", [])
                    if not isinstance(lists, list):
                        self.log.error(f"⚠️ lists 字段结构异常")
                        break

                    if not lists:
                        break

                    for item in lists:
                        # 确保循环中的 item 也是字典
                        if not isinstance(item, dict):
                            continue
                        if item.get("pay_channel_name") in exclude_names or item.get("cardNumber") in card_exclude:
                            continue
                        all_data.append(item)

                    if len(lists) < 100:
                        break

                    page += 1
                    time.sleep(0.5)  # 防止请求过快被封禁

                if all_data:
                    self.save_to_db(all_data)
                else:
                    self.log.warning("🔍 未发现符合条件的订单记录")
                    return {"file_path": None, "count": 0}

            except Exception as e:
                self.log.critical(f"💥 任务异常终止: {e}")
                raise