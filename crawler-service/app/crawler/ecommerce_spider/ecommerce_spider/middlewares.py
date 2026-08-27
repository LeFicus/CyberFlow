"""
Scrapy 中间件模块 — 爬虫中间件和下载器中间件

本模块定义了 Scrapy 框架的中间件组件，用于在请求/响应/Item 的
处理流程中插入自定义逻辑：

中间件类型:
    1. SpidersMiddleware  — 爬虫中间件（处理输入/输出/异常）
    2. DownloaderMiddleware — 下载器中间件（处理请求/响应/异常）
    3. CustomUserAgentMiddleware — 自定义 User-Agent 轮换中间件

优先级说明:
    数值越小越靠近引擎（0-1000），下载器中间件的标准范围是 400-600。

参考文档:
    https://docs.scrapy.org/en/latest/topics/spider-middleware.html
    https://docs.scrapy.org/en/latest/topics/downloader-middleware.html
"""

import random
from scrapy import signals
from itemadapter import ItemAdapter


class EcommerceSpiderSpiderMiddleware:
    """
    爬虫中间件 — 在 Spider 处理流程的各阶段插入自定义逻辑

    爬虫中间件的工作原理:
        输入阶段: Engine -> (SpiderMiddleware.process_spider_input) -> Spider
        输出阶段: Spider -> (SpiderMiddleware.process_spider_output) -> Engine
        异常阶段: Spider/中间件异常 -> process_spider_exception

    并非每个方法都必须实现，Scrapy 会检测方法是否存在并只调用已定义的方法。
    """

    @classmethod
    def from_crawler(cls, crawler):
        """
        从 Crawler 对象创建中间件实例（Scrapy 工厂方法）

        这是 Scrapy 创建中间件的标准方式，通过 crawler 对象可以
        访问 settings 和 signals，比 __init__ 具有更丰富的上下文。

        Args:
            crawler: Scrapy Crawler 对象，提供设置和信号系统访问

        Returns:
            EcommerceSpiderSpiderMiddleware: 中间件实例
        """
        s = cls()
        # 连接爬虫开启信号，当任何 spider 启动时自动调用 spider_opened
        crawler.signals.connect(s.spider_opened, signal=signals.spider_opened)
        return s

    def process_spider_input(self, response, spider):
        """
        处理进入 Spider 的每个 Response（下载结果）

        在下载器返回 Response 后、传递给 Spider 回调函数前被调用。

        Args:
            response: 下载器返回的 Response 对象
            spider : 目标 Spider 实例

        Returns:
            None                      : 继续正常处理流程
            或 raise 异常              : 触发 process_spider_exception 链
        """
        return None

    def process_spider_output(self, response, result, spider):
        """
        处理 Spider 产出的每个结果（Item 或 Request）

        在 Spider 回调函数返回结果后被调用，可以在此过滤、修改
        或包装 Spider 的输出。

        Args:
            response: 产生当前结果的 Response 对象
            result  : Spider 回调返回的迭代器（包含 Item 或 Request）
            spider  : Spider 实例

        Yields:
            Item 或 Request: 经过处理后继续向下传递的数据项
        """
        for i in result:
            yield i

    def process_spider_exception(self, response, exception, spider):
        """
        处理 Spider 或其他中间件抛出的异常

        可以在此捕获异常、记录日志、生成新的 Request 重试或生成 Item。

        Args:
            response : 触发异常时的 Response 对象
            exception: 被抛出的异常对象
            spider   : Spider 实例

        Returns:
            None 或可迭代的 Request/Item 对象
        """
        pass

    async def process_start(self, start):
        """
        异步处理 Spider 的 start() 方法返回的初始请求

        Scrapy 2.13+ 引入的异步支持，用于在 Spider 启动前处理初始请求流。

        Args:
            start: Spider start() 方法返回的异步迭代器

        Yields:
            Item 或 Request: 待处理的初始请求
        """
        async for item_or_request in start:
            yield item_or_request

    def spider_opened(self, spider):
        """
        Spider 开启时的回调（由信号触发）

        可用于记录启动日志、初始化资源等。

        Args:
            spider: 刚启动的 Spider 实例
        """
        spider.logger.info("Spider opened: %s" % spider.name)


class EcommerceSpiderDownloaderMiddleware:
    """
    下载器中间件 — 在 HTTP 请求/响应的传输管道中插入逻辑

    下载器中间件是 Scrapy 最常用的扩展点，可以在请求发出前修改请求、
    在响应到达后处理响应、或在下载异常时执行重试逻辑。

    生命周期:
        Engine -> process_request() -> Downloader -> process_response() -> Engine
                                                       \-> process_exception()
    """

    @classmethod
    def from_crawler(cls, crawler):
        """
        从 Crawler 对象创建中间件实例（Scrapy 工厂方法）

        Args:
            crawler: Scrapy Crawler 对象

        Returns:
            EcommerceSpiderDownloaderMiddleware: 中间件实例
        """
        s = cls()
        crawler.signals.connect(s.spider_opened, signal=signals.spider_opened)
        return s

    def process_request(self, request, spider):
        """
        处理即将发出的每个 Request（在下载之前）

        可以在此修改请求头、代理设置、cookie 等。

        Args:
            request: 待发出的 Request 对象
            spider : 发起请求的 Spider 实例

        Returns:
            None           : 继续处理（交给下一个中间件或下载器）
            Response 对象   : 跳过下载，直接返回该 Response
            Request 对象    : 用新 Request 替换当前请求
            或 raise IgnoreRequest: 停止处理链
        """
        return None

    def process_response(self, request, response, spider):
        """
        处理下载器返回的每个 Response

        可以在此检查 HTTP 状态码、处理重定向、记录响应时间等。

        Args:
            request : 发出请求的 Request 对象
            response: 下载器返回的 Response 对象
            spider  : Spider 实例

        Returns:
            Response 对象  : 正常传递响应
            Request 对象    : 发起新的请求（如重试）
            或 raise IgnoreRequest: 丢弃该响应
        """
        return response

    def process_exception(self, request, exception, spider):
        """
        处理下载过程中抛出的异常

        当下载过程发生异常（如超时、连接拒绝、DNS 失败）时被调用。

        Args:
            request  : 触发异常的 Request 对象
            exception: 被抛出的异常对象
            spider   : Spider 实例

        Returns:
            None           : 继续向上传递异常
            Response 对象   : 中断异常链，用该响应继续处理
            Request 对象    : 中断异常链，发起新的重试请求
        """
        pass

    def spider_opened(self, spider):
        """
        Spider 开启时的回调

        Args:
            spider: 刚启动的 Spider 实例
        """
        spider.logger.info("Spider opened: %s" % spider.name)


class CustomUserAgentMiddleware:
    """
    自定义 User-Agent 中间件 — 为每个请求随机设置 User-Agent

    替代第三方库 scrapy-fake-useragent，减少依赖的同时提供更可控的 UA 列表。

    工作原理:
        1. 维护一个主流浏览器的 User-Agent 列表
        2. 每次 process_request 被调用时，随机选择一个 UA 设置到请求头
        3. 模拟真实用户行为，降低被目标站点封禁的风险

    优先级别:
        在启动脚本中设为 400（标准下载器中间件优先级范围）
        同时需将内置的 UserAgentMiddleware 设为 None 以禁用默认行为
    """

    # ========== User-Agent 池 ==========
    # 包含 Chrome / Edge / Safari 等主流浏览器的桌面版 UA
    # 定期更新以确保 UA 版本不会过于陈旧
    USER_AGENTS = [
        # Chrome 120 — Windows 10
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        # Chrome 119 — Windows 10
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
        # Chrome 120 — macOS
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        # Edge 120 — Windows 10
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Edge/120.0.0.0 Safari/537.36",
        # Safari 17 — macOS
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
    ]

    def process_request(self, request, spider):
        """
        为每个 HTTP 请求随机设置 User-Agent 头

        在请求发出前自动调用，从 UA 池中随机选择一个设置到请求头中。
        这将覆盖原有的 User-Agent 默认值。

        Args:
            request: 待发出的 Scrapy Request 对象
            spider : 发起请求的 Spider 实例
        """
        request.headers['User-Agent'] = random.choice(self.USER_AGENTS)
