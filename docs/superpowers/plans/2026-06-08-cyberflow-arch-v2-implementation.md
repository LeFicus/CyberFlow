# CyberFlow v2.0 架构升级 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 CyberFlow 从 FastAPI+Celery 三层调用链升级为 Spring Boot+RabbitMQ+Python asyncio 消费者的解耦架构，实现增量爬取、选择器模板库和任务持久化追踪。

**Architecture:** Spring Boot 通过 RabbitMQ 直接下发爬取任务，Python 纯 asyncio 消费者执行爬取逻辑，结果通过 task.result 队列回流。Quartz 定时触发站点/订单增量爬取，用户手动触发商品爬取。选择器模板以 MySQL 表存储，多模板选择器用 XPath `|` 合并。

**Tech Stack:** Spring Boot 3.4.1, Spring AMQP, Quartz, MyBatis-Plus, Python 3.12, pika, aiohttp, aiomysql, Scrapy 2.x, RabbitMQ 3.x, MySQL 8.0

---

## File Structure

### Spring Boot (backend-admin/) — 新建文件
```
src/main/java/com/cyberflow/admin/
├── crawler/
│   ├── config/
│   │   ├── RabbitMQConfig.java          # RabbitMQ 连接、Exchange、Queue 声明
│   │   └── QuartzConfig.java            # Quartz Scheduler 配置
│   ├── messaging/
│   │   ├── TaskMessagePublisher.java    # 发布任务到 MQ
│   │   └── TaskResultConsumer.java      # 消费 task.result 更新 task_history + cursor
│   ├── scheduler/
│   │   ├── SiteCrawlJob.java            # Quartz Job: 触发站点爬取
│   │   └── OrderCrawlJob.java           # Quartz Job: 触发订单爬取
│   ├── selector/
│   │   ├── controller/
│   │   │   └── SelectorTemplateController.java  # 选择器模板 CRUD API
│   │   ├── service/
│   │   │   └── SelectorTemplateService.java
│   │   ├── mapper/
│   │   │   └── SelectorTemplateMapper.java
│   │   └── entity/
│   │       └── SelectorTemplate.java
│   ├── siteconfig/
│   │   ├── controller/
│   │   │   └── SiteConfigController.java        # 站点注册 CRUD API
│   │   ├── service/
│   │   │   └── SiteConfigService.java
│   │   ├── mapper/
│   │   │   ├── CrawlSiteConfigMapper.java
│   │   │   └── SiteTemplateMappingMapper.java
│   │   └── entity/
│   │       ├── CrawlSiteConfig.java
│   │       └── SiteTemplateMapping.java
│   └── task/
│       ├── controller/
│       │   └── TaskHistoryController.java       # 任务历史查询 API
│       ├── service/
│       │   └── TaskHistoryService.java
│       ├── mapper/
│       │   └── TaskHistoryMapper.java
│       └── entity/
│           ├── TaskHistory.java
│           └── CrawlCursor.java
```

### Spring Boot — 修改文件
```
src/main/java/com/cyberflow/admin/
├── crawler/controller/CrawlerController.java    # 改为 RabbitMQ 发布 + task_history 查询
├── crawler/service/CrawlerService.java           # 简化，MQ 发布 + 状态查询
├── crawler/client/CrawlerApiClient.java          # 废弃，后续删除
└── src/main/resources/application.yml            # 新增 RabbitMQ、Quartz 配置
```

### Python (crawler-consumer/) — 新建项目
```
crawler-consumer/
├── requirements.txt
├── main.py                          # 入口，启动所有 Consumer
├── config.py                        # 环境变量配置
├── consumers/
│   ├── __init__.py
│   ├── base_consumer.py             # 基类：连接管理、ACK/NACK
│   ├── site_consumer.py             # 站点爬取 Consumer
│   ├── order_consumer.py            # 订单爬取 Consumer
│   └── product_consumer.py          # 商品爬取 Consumer (调度 Scrapy)
├── crawlers/
│   ├── __init__.py
│   ├── site_crawler.py              # 原 SiteCrawler 逻辑 async 重写
│   ├── order_crawler.py             # 原 OrderCrawler 逻辑 async 重写
│   └── product_crawler.py           # Scrapy 子进程管理 + 选择器合并
├── db/
│   ├── __init__.py
│   ├── models.py                    # 游标、配置数据模型
│   └── repository.py                # CRUD 操作
└── scrapy_app/                      # 从 crawler-service/app/crawler/ecommerce_spider 复制
    ├── scrapy.cfg
    └── ecommerce_spider/
        ├── settings.py
        ├── pipelines.py
        └── spiders/
            ├── shopify_crawl.py
            └── woo_crawl.py
```

### 数据库迁移 SQL
```
docs/init_v2_tables.sql    # DDL for task_history, crawl_cursor, selector_template, crawl_site_config, site_template_mapping
```

---

## Phase 1: 基础设施

### Task 1: 添加 RabbitMQ 到 docker-compose

**Files:**
- Modify: `docker-compose.yml`

```yaml
version: '3.8'
services:
  rabbitmq:
    image: rabbitmq:3.13-management
    container_name: cyberflow-rabbitmq
    ports:
      - "5672:5672"    # AMQP
      - "15672:15672"  # Management UI
    environment:
      RABBITMQ_DEFAULT_USER: admin
      RABBITMQ_DEFAULT_PASS: admin123
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "check_port_connectivity"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  rabbitmq_data:
```

### Task 2: Spring Boot 添加 Maven 依赖

**Files:**
- Modify: `backend-admin/pom.xml`

在 `<dependencies>` 内 `</dependencies>` 前添加：

```xml
<!-- RabbitMQ (Spring AMQP) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>

<!-- Quartz Scheduler -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-quartz</artifactId>
</dependency>
```

### Task 3: 配置 application.yml 新增 RabbitMQ 和 Quartz

**Files:**
- Modify: `backend-admin/src/main/resources/application.yml`

在文件末尾追加：

```yaml
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:admin}
    password: ${RABBITMQ_PASS:admin123}
    listener:
      simple:
        retry:
          enabled: true
          max-attempts: 3
        default-requeue-rejected: false

cyberflow:
  crawler-api:
    base-url: ${CRAWLER_API_BASE_URL:http://localhost:8000}
    internal-token: ${INTERNAL_API_TOKEN:change-me-in-production}
  jwt:
    secret: ${JWT_SECRET:cyberflow-admin-jwt-secret-key-2026}
    expiration: 14400000
  # 新增
  crawler:
    site-cron: "0 0 2 * * ?"       # 每天凌晨2点
    order-cron: "0 0 */6 * * ?"    # 每6小时整点
```

### Task 4: 创建数据库迁移 DDL

**Files:**
- Create: `docs/init_v2_tables.sql`

```sql
-- 任务历史表
CREATE TABLE IF NOT EXISTS task_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         VARCHAR(64) NOT NULL UNIQUE,
    type            VARCHAR(30) NOT NULL COMMENT 'site_crawl / order_crawl / product_crawl / site_index',
    trigger_type    VARCHAR(20) NOT NULL COMMENT 'cron / manual',
    triggered_by    VARCHAR(64) COMMENT 'user ID for manual triggers',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RUNNING / SUCCESS / FAILED',
    cursor_before   VARCHAR(255) COMMENT 'cursor value before task',
    cursor_after    VARCHAR(255) COMMENT 'cursor value after task',
    rows_affected   INT DEFAULT 0,
    error_msg       TEXT,
    duration_ms     BIGINT,
    started_at      DATETIME,
    finished_at     DATETIME,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type (type),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 增量游标表
CREATE TABLE IF NOT EXISTS crawl_cursor (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    cursor_key   VARCHAR(100) NOT NULL UNIQUE COMMENT 'e.g. site_crawler, order_crawler',
    cursor_value VARCHAR(255) NOT NULL COMMENT 'e.g. 2026-06-08T02:00:00, 1234567',
    last_sync_at DATETIME NOT NULL,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 选择器模板库
CREATE TABLE IF NOT EXISTS selector_template (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                      VARCHAR(100) NOT NULL,
    platform                  VARCHAR(20) NOT NULL COMMENT 'woo / shopify / magento / custom',
    title_selector            VARCHAR(500),
    price_selector            VARCHAR(500),
    price_regex               VARCHAR(200),
    description_selector      VARCHAR(500),
    images_selector           VARCHAR(500),
    currency                  VARCHAR(10) DEFAULT 'USD',
    breadcrumb_links_selector VARCHAR(500),
    breadcrumb_last_selector  VARCHAR(500),
    site_map_selector         VARCHAR(500) COMMENT 'only for non-shopify',
    is_system                 TINYINT DEFAULT 0 COMMENT 'pre-built system template',
    created_at                DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 商品爬取站点注册
CREATE TABLE IF NOT EXISTS crawl_site_config (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain     VARCHAR(255) NOT NULL,
    type       VARCHAR(20) NOT NULL COMMENT 'shopify / woo / custom',
    category   VARCHAR(100) DEFAULT '未知分类',
    status     VARCHAR(20) DEFAULT 'active' COMMENT 'active / paused',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_domain (domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 站点↔模板多对多关联
CREATE TABLE IF NOT EXISTS site_template_mapping (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_config_id  BIGINT NOT NULL,
    template_id     BIGINT NOT NULL,
    extra_selectors JSON COMMENT 'per-site extra selectors merged into final xpath',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (site_config_id) REFERENCES crawl_site_config(id) ON DELETE CASCADE,
    FOREIGN KEY (template_id) REFERENCES selector_template(id) ON DELETE CASCADE,
    UNIQUE KEY uk_site_template (site_config_id, template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 预置选择器模板数据
INSERT INTO selector_template (name, platform, title_selector, price_selector, price_regex, description_selector, images_selector, currency, breadcrumb_links_selector, breadcrumb_last_selector, site_map_selector, is_system) VALUES
('WooCommerce Default', 'woo',
 '//h1[@class=''product_title entry-title'']/text() | //h1[contains(@class, ''product-title'')]/text() | //h1[contains(@class, ''product_title'')]/text() | //header//h1/text() | //div[contains(@class, ''summary'')]//h1/text()',
 '//p[@class=''price'']//span[@class=''woocommerce-Price-amount'']/bdi/text() | //p[@class=''price'']//span[@class=''woocommerce-Price-amount amount'']/text() | //ins//span[@class=''woocommerce-Price-amount amount'']/bdi/text() | //meta[@itemprop=''price'']/@content',
 '[\\d.,]+',
 '//div[@class=''woocommerce-product-details__short-description'']//text() | //div[contains(@class, ''woocommerce-tabs'')]//div[@id=''tab-description'']//p//text() | //div[contains(@class, ''woocommerce-tabs'')]//div[@id=''tab-description'']//text() | //div[contains(@class, ''product-short-description'')]//text() | //div[@itemprop=''description'']//text()',
 '//div[@class=''woocommerce-product-gallery__image'']/a/@href | //div[@class=''woocommerce-product-gallery__image'']//img/@src | //figure[contains(@class, ''woocommerce-product-gallery__wrapper'')]//img/@data-large_image | //meta[@property=''og:image'']/@content',
 'USD',
 '//nav[contains(@class, ''woocommerce-breadcrumb'')]//a//text() | //div[contains(@class, ''breadcrumbs'')]//a//text() | //ul[contains(@class, ''breadcrumb'')]//a//text() | //div[contains(@class, ''breadcrumb'')]//a//text()',
 '//nav[contains(@class, ''woocommerce-breadcrumb'')]//span[contains(@class, ''breadcrumb-last'')]//text() | //nav[contains(@class, ''woocommerce-breadcrumb'')]//a[last()]//text()',
 '//*[local-name()=''sitemap'']/*[local-name()=''loc''][contains(text(), ''product-sitemap'')]/text()',
 1
),
('Magnolia Theme', 'woo',
 '//h1/text()',
 '//div[contains(@class,''prices'')]//div//div//span//span/@content',
 '[\\d.,]+',
 '//div[contains(@class, ''card-body collapsible-body pdp-feature-body'')]/text()',
 '//meta[@property=''og:image'']/@content',
 'USD',
 '//ol[contains(@class, ''breadcrumb'')]//a/text()',
 '//ol[contains(@class, ''breadcrumb'')]//span/text()',
 '//*[local-name()=''sitemap'']/*[local-name()=''loc''][contains(text(), ''/sitemap_products_'')]/text()',
 1
),
('Shopify Default', 'shopify', NULL, NULL, NULL, NULL, NULL, 'USD', NULL, NULL, NULL, 1);

-- 初始化游标（从已有数据推断起始值）
INSERT INTO crawl_cursor (cursor_key, cursor_value, last_sync_at) VALUES
('site_crawler', NOW(), NOW()),
('site_index_crawler', NOW(), NOW()),
('order_crawler', '0', NOW())
ON DUPLICATE KEY UPDATE cursor_key=cursor_key;
```

### Task 5: Spring Boot RabbitMQConfig

**Files:**
- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/config/RabbitMQConfig.java`

```java
package com.cyberflow.admin.crawler.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_TASKS = "crawler.tasks";
    public static final String EXCHANGE_DLX = "crawler.tasks.dlx";

    public static final String QUEUE_SITE_CRAWL = "site.crawl";
    public static final String QUEUE_ORDER_CRAWL = "order.crawl";
    public static final String QUEUE_PRODUCT_CRAWL = "product.crawl";
    public static final String QUEUE_TASK_RESULT = "task.result";
    public static final String QUEUE_TASK_DEAD = "task.dead";

    public static final String RK_SITE = "crawler.task.site";
    public static final String RK_ORDER = "crawler.task.order";
    public static final String RK_PRODUCT = "crawler.task.product";
    public static final String RK_RESULT = "crawler.task.result";
    public static final String RK_DEAD = "crawler.task.dead";

    @Bean
    public TopicExchange taskExchange() {
        return new TopicExchange(EXCHANGE_TASKS);
    }

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(EXCHANGE_DLX);
    }

    @Bean
    public Queue siteCrawlQueue() {
        return QueueBuilder.durable(QUEUE_SITE_CRAWL)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(RK_DEAD)
                .build();
    }

    @Bean
    public Queue orderCrawlQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_CRAWL)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(RK_DEAD)
                .build();
    }

    @Bean
    public Queue productCrawlQueue() {
        return QueueBuilder.durable(QUEUE_PRODUCT_CRAWL)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(RK_DEAD)
                .build();
    }

    @Bean
    public Queue taskResultQueue() {
        return new Queue(QUEUE_TASK_RESULT, true);
    }

    @Bean
    public Queue taskDeadQueue() {
        return new Queue(QUEUE_TASK_DEAD, true);
    }

    @Bean
    public Binding siteBinding() {
        return BindingBuilder.bind(siteCrawlQueue()).to(taskExchange()).with(RK_SITE);
    }

    @Bean
    public Binding orderBinding() {
        return BindingBuilder.bind(orderCrawlQueue()).to(taskExchange()).with(RK_ORDER);
    }

    @Bean
    public Binding productBinding() {
        return BindingBuilder.bind(productCrawlQueue()).to(taskExchange()).with(RK_PRODUCT);
    }

    @Bean
    public Binding resultBinding() {
        return BindingBuilder.bind(taskResultQueue()).to(taskExchange()).with(RK_RESULT);
    }

    @Bean
    public Binding deadBinding() {
        return BindingBuilder.bind(taskDeadQueue()).to(dlxExchange()).with(RK_DEAD);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
```

### Task 6: Spring Boot QuartzConfig

**Files:**
- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/config/QuartzConfig.java`

```java
package com.cyberflow.admin.crawler.config;

import com.cyberflow.admin.crawler.scheduler.OrderCrawlJob;
import com.cyberflow.admin.crawler.scheduler.SiteCrawlJob;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    @Value("${cyberflow.crawler.site-cron}")
    private String siteCron;

    @Value("${cyberflow.crawler.order-cron}")
    private String orderCron;

    @Bean
    public JobDetail siteCrawlJobDetail() {
        return JobBuilder.newJob(SiteCrawlJob.class)
                .withIdentity("siteCrawlJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger siteCrawlTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(siteCrawlJobDetail())
                .withIdentity("siteCrawlTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(siteCron))
                .build();
    }

    @Bean
    public JobDetail orderCrawlJobDetail() {
        return JobBuilder.newJob(OrderCrawlJob.class)
                .withIdentity("orderCrawlJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger orderCrawlTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(orderCrawlJobDetail())
                .withIdentity("orderCrawlTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(orderCron))
                .build();
    }
}
```

### Task 7: 创建 TaskHistory entity + mapper

**Files:**
- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/task/entity/TaskHistory.java`

```java
package com.cyberflow.admin.crawler.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("task_history")
public class TaskHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private String type;
    private String triggerType;
    private String triggeredBy;
    private String status;
    private String cursorBefore;
    private String cursorAfter;
    private Integer rowsAffected;
    private String errorMsg;
    private Long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
```

- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/task/entity/CrawlCursor.java`

```java
package com.cyberflow.admin.crawler.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("crawl_cursor")
public class CrawlCursor {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String cursorKey;
    private String cursorValue;
    private LocalDateTime lastSyncAt;
    private LocalDateTime updatedAt;
}
```

- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/task/mapper/TaskHistoryMapper.java`

```java
package com.cyberflow.admin.crawler.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskHistoryMapper extends BaseMapper<TaskHistory> {
}
```

- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/task/mapper/CrawlCursorMapper.java`

```java
package com.cyberflow.admin.crawler.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.crawler.task.entity.CrawlCursor;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CrawlCursorMapper extends BaseMapper<CrawlCursor> {
}
```

### Task 8: 创建 TaskMessagePublisher

**Files:**
- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/messaging/TaskMessagePublisher.java`

```java
package com.cyberflow.admin.crawler.messaging;

import com.cyberflow.admin.crawler.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    public String publishSiteCrawl(String username, String password, String lastUpdatedAt) {
        String taskId = UUID.randomUUID().toString();
        Map<String, Object> message = Map.of(
            "task_id", taskId,
            "type", "site_crawl",
            "trigger", "cron",
            "timestamp", Instant.now().toString(),
            "payload", Map.of(
                "username", username,
                "password", password,
                "cursor", Map.of("last_updated_at", lastUpdatedAt)
            )
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_TASKS, RabbitMQConfig.RK_SITE, message);
        log.info("Published site crawl task: {}", taskId);
        return taskId;
    }

    public String publishSiteIndexCrawl(String username, String password, String lastRecordedAt) {
        String taskId = UUID.randomUUID().toString();
        Map<String, Object> message = Map.of(
            "task_id", taskId,
            "type", "site_index",
            "trigger", "cron",
            "timestamp", Instant.now().toString(),
            "payload", Map.of(
                "username", username,
                "password", password,
                "cursor", Map.of("last_recorded_at", lastRecordedAt)
            )
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_TASKS, RabbitMQConfig.RK_SITE, message);
        log.info("Published site index crawl task: {}", taskId);
        return taskId;
    }

    public String publishOrderCrawl(String maxOrderId) {
        String taskId = UUID.randomUUID().toString();
        Map<String, Object> message = Map.of(
            "task_id", taskId,
            "type", "order_crawl",
            "trigger", "cron",
            "timestamp", Instant.now().toString(),
            "payload", Map.of(
                "cursor", Map.of("max_order_id", maxOrderId)
            )
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_TASKS, RabbitMQConfig.RK_ORDER, message);
        log.info("Published order crawl task: {}", taskId);
        return taskId;
    }

    public String publishProductCrawl(Long siteConfigId, String domain, String type, String category, Long triggeredBy) {
        String taskId = UUID.randomUUID().toString();
        Map<String, Object> message = Map.of(
            "task_id", taskId,
            "type", "product_crawl",
            "trigger", "manual",
            "triggered_by", String.valueOf(triggeredBy),
            "timestamp", Instant.now().toString(),
            "payload", Map.of(
                "site_config_id", siteConfigId,
                "domain", domain,
                "type", type,
                "category", category
            )
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_TASKS, RabbitMQConfig.RK_PRODUCT, message);
        log.info("Published product crawl task: {}", taskId);
        return taskId;
    }
}
```

### Task 9: 创建 TaskResultConsumer (消费 Python 回传的结果)

**Files:**
- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/messaging/TaskResultConsumer.java`

```java
package com.cyberflow.admin.crawler.messaging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyberflow.admin.crawler.config.RabbitMQConfig;
import com.cyberflow.admin.crawler.task.entity.CrawlCursor;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import com.cyberflow.admin.crawler.task.mapper.CrawlCursorMapper;
import com.cyberflow.admin.crawler.task.mapper.TaskHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskResultConsumer {

    private final TaskHistoryMapper taskHistoryMapper;
    private final CrawlCursorMapper cursorMapper;

    @SuppressWarnings("unchecked")
    @RabbitListener(queues = RabbitMQConfig.QUEUE_TASK_RESULT)
    public void handleResult(Map<String, Object> result) {
        String taskId = (String) result.get("task_id");
        String status = (String) result.get("status");
        log.info("Received task result: {} -> {}", taskId, status);

        // 更新 task_history
        TaskHistory history = taskHistoryMapper.selectOne(
            new LambdaQueryWrapper<TaskHistory>().eq(TaskHistory::getTaskId, taskId)
        );
        if (history != null) {
            history.setStatus(status.equals("success") ? "SUCCESS" : "FAILED");
            history.setRowsAffected(result.get("rows_affected") != null
                ? ((Number) result.get("rows_affected")).intValue() : 0);
            history.setDurationMs(result.get("duration_ms") != null
                ? ((Number) result.get("duration_ms")).longValue() : null);
            history.setFinishedAt(LocalDateTime.now());
            if (result.get("error") != null) {
                history.setErrorMsg(result.get("error").toString());
            }
            // 更新游标
            Map<String, Object> newCursor = (Map<String, Object>) result.get("new_cursor");
            if (newCursor != null) {
                history.setCursorAfter(newCursor.toString());
            }
            taskHistoryMapper.updateById(history);
        }

        // 更新 crawl_cursor
        Map<String, Object> newCursor = (Map<String, Object>) result.get("new_cursor");
        if (newCursor != null && history != null) {
            String cursorKey = switch (history.getType()) {
                case "site_crawl" -> "site_crawler";
                case "site_index" -> "site_index_crawler";
                case "order_crawl" -> "order_crawler";
                default -> null;
            };
            if (cursorKey != null) {
                CrawlCursor cursor = cursorMapper.selectOne(
                    new LambdaQueryWrapper<CrawlCursor>().eq(CrawlCursor::getCursorKey, cursorKey)
                );
                if (cursor != null) {
                    String cursorValue = null;
                    if (newCursor.containsKey("max_order_id")) {
                        cursorValue = String.valueOf(newCursor.get("max_order_id"));
                    } else if (newCursor.containsKey("last_updated_at")) {
                        cursorValue = (String) newCursor.get("last_updated_at");
                    } else if (newCursor.containsKey("last_recorded_at")) {
                        cursorValue = (String) newCursor.get("last_recorded_at");
                    }
                    if (cursorValue != null) {
                        cursor.setCursorValue(cursorValue);
                        cursor.setLastSyncAt(LocalDateTime.now());
                        cursorMapper.updateById(cursor);
                    }
                    history.setCursorAfter(cursorValue);
                    taskHistoryMapper.updateById(history);
                }
            }
        }
    }
}
```

### Task 10: 创建 TaskHistoryService + Controller

**Files:**
- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/task/service/TaskHistoryService.java`

```java
package com.cyberflow.admin.crawler.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import com.cyberflow.admin.crawler.task.mapper.TaskHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaskHistoryService {

    private final TaskHistoryMapper taskHistoryMapper;

    public TaskHistory getByTaskId(String taskId) {
        return taskHistoryMapper.selectOne(
            new LambdaQueryWrapper<TaskHistory>().eq(TaskHistory::getTaskId, taskId)
        );
    }

    public Page<TaskHistory> list(int pageNum, int pageSize) {
        Page<TaskHistory> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TaskHistory> wrapper = new LambdaQueryWrapper<TaskHistory>()
            .orderByDesc(TaskHistory::getCreatedAt);
        return taskHistoryMapper.selectPage(page, wrapper);
    }

    public void save(TaskHistory taskHistory) {
        taskHistoryMapper.insert(taskHistory);
    }
}
```

- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/task/controller/TaskHistoryController.java`

```java
package com.cyberflow.admin.crawler.task.controller;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.crawler.task.service.TaskHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/crawler")
@RequiredArgsConstructor
public class TaskHistoryController {

    private final TaskHistoryService taskHistoryService;

    @GetMapping("/status/{taskId}")
    @PreAuthorize("hasAnyAuthority('crawler:site:start', 'crawler:collect:start', 'crawler:order:start')")
    public Result<?> status(@PathVariable String taskId) {
        var task = taskHistoryService.getByTaskId(taskId);
        if (task == null) {
            return Result.fail("Task not found");
        }
        return Result.ok(task);
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAnyAuthority('crawler:site:start', 'crawler:collect:start', 'crawler:order:start')")
    public Result<?> tasks(@RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "20") int size) {
        return Result.ok(taskHistoryService.list(page, size));
    }
}
```

### Task 11: Python 项目脚手架

**Files:**
- Create: `crawler-consumer/requirements.txt`

```
pika==1.3.2
aiohttp==3.10.11
aiomysql==0.2.0
python-dotenv==1.0.1
loguru==0.7.3
```

- Create: `crawler-consumer/config.py`

```python
import os
from dotenv import load_dotenv

load_dotenv()

RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", "5672"))
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "admin")
RABBITMQ_PASS = os.getenv("RABBITMQ_PASS", "admin123")

DATABASE_URL = os.getenv("DATABASE_URL", "mysql+pymysql://root:123456@localhost:3306/cyberflow")
SCRAPED_DB_URL = os.getenv("SCRAPED_DB_URL", "mysql+pymysql://root:123456@localhost:3306/scraped_data")

REDIS_URL = os.getenv("REDIS_URL", "redis://127.0.0.1:6379/0")

# Exchange & Queue names — must match Spring Boot RabbitMQConfig
EXCHANGE_TASKS = "crawler.tasks"
QUEUE_SITE_CRAWL = "site.crawl"
QUEUE_ORDER_CRAWL = "order.crawl"
QUEUE_PRODUCT_CRAWL = "product.crawl"
EXCHANGE_DLX = "crawler.tasks.dlx"
QUEUE_TASK_RESULT = "task.result"
```

- Create: `crawler-consumer/db/__init__.py` (empty)

- Create: `crawler-consumer/consumers/__init__.py` (empty)

- Create: `crawler-consumer/crawlers/__init__.py` (empty)

---

## Phase 2: 站点爬取迁移

### Task 12: Python — base_consumer.py

**Files:**
- Create: `crawler-consumer/consumers/base_consumer.py`

```python
import asyncio
import json
import signal
from abc import ABC, abstractmethod
from loguru import logger
import pika
from pika.adapters.asyncio_connection import AsyncioConnection
from pika.channel import Channel
from pika.spec import Basic, BasicProperties


class BaseConsumer(ABC):
    """Base async consumer using pika AsyncioConnection."""

    def __init__(self, queue_name: str, rabbitmq_url: str):
        self.queue_name = queue_name
        self.rabbitmq_url = rabbitmq_url
        self.connection: AsyncioConnection | None = None
        self.channel: Channel | None = None
        self._closing = False
        self._consuming = False

    def connect(self):
        params = pika.URLParameters(self.rabbitmq_url)
        self.connection = AsyncioConnection(
            parameters=params,
            on_open_callback=self.on_connection_open,
            on_close_callback=self.on_connection_closed,
        )

    def on_connection_open(self, connection):
        logger.info(f"[{self.queue_name}] Connection opened")
        self.connection.channel(on_open_callback=self.on_channel_open)

    def on_connection_closed(self, connection, exception):
        logger.warning(f"[{self.queue_name}] Connection closed: {exception}")
        if not self._closing:
            # Reconnect after delay
            asyncio.get_event_loop().call_later(5, self.connect)

    def on_channel_open(self, channel):
        self.channel = channel
        channel.basic_qos(prefetch_count=1)
        channel.basic_consume(self.queue_name, on_message_callback=self.on_message)
        logger.info(f"[{self.queue_name}] Consuming...")

    def on_message(self, ch, method, properties, body):
        try:
            message = json.loads(body)
            task_id = message.get("task_id", "unknown")
            logger.info(f"[{self.queue_name}] Received task: {task_id}")
            asyncio.ensure_future(self.handle_task(message, ch, method.delivery_tag))
        except Exception as e:
            logger.error(f"[{self.queue_name}] Failed to parse message: {e}")
            ch.basic_nack(method.delivery_tag, requeue=False)

    async def handle_task(self, message: dict, ch, delivery_tag: int):
        try:
            await self.process(message)
            ch.basic_ack(delivery_tag)
        except Exception as e:
            logger.error(f"[{self.queue_name}] Task failed: {e}")
            # Nack without requeue — DLX handles retry
            ch.basic_nack(delivery_tag, requeue=False)

    @abstractmethod
    async def process(self, message: dict):
        """Subclasses implement the actual crawling logic."""
        ...

    def run(self):
        self.connect()
        loop = asyncio.get_event_loop()
        for sig in (signal.SIGINT, signal.SIGTERM):
            loop.add_signal_handler(sig, self.stop)
        logger.info(f"[{self.queue_name}] Consumer started")

    def stop(self):
        logger.info(f"[{self.queue_name}] Stopping...")
        self._closing = True
        if self.channel and self.channel.is_open:
            self.channel.close()
        if self.connection and self.connection.is_open:
            self.connection.close()
```

### Task 13: Python — db/repository.py

**Files:**
- Create: `crawler-consumer/db/repository.py`

```python
import aiomysql
from loguru import logger
from config import DATABASE_URL, SCRAPED_DB_URL


def _parse_mysql_url(url: str):
    """Parse mysql+pymysql://user:pass@host:port/db to kwargs."""
    from urllib.parse import urlparse, unquote
    parsed = urlparse(url)
    return {
        "host": parsed.hostname or "localhost",
        "port": parsed.port or 3306,
        "user": unquote(parsed.username or "root"),
        "password": unquote(parsed.password or ""),
        "db": parsed.path.lstrip("/"),
    }


class CursorRepository:
    """Manage crawl_cursor read/write via aiomysql."""

    def __init__(self):
        self.pool: aiomysql.Pool | None = None

    async def connect(self):
        kwargs = _parse_mysql_url(DATABASE_URL)
        self.pool = await aiomysql.create_pool(
            host=kwargs["host"], port=kwargs["port"],
            user=kwargs["user"], password=kwargs["password"],
            db=kwargs["db"], charset="utf8mb4", autocommit=True,
        )

    async def get_cursor(self, cursor_key: str) -> str | None:
        async with self.pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute(
                    "SELECT cursor_value FROM crawl_cursor WHERE cursor_key = %s",
                    (cursor_key,),
                )
                row = await cur.fetchone()
                return row[0] if row else None

    async def update_cursor(self, cursor_key: str, cursor_value: str):
        async with self.pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute(
                    """INSERT INTO crawl_cursor (cursor_key, cursor_value, last_sync_at)
                       VALUES (%s, %s, NOW())
                       ON DUPLICATE KEY UPDATE cursor_value=%s, last_sync_at=NOW()""",
                    (cursor_key, cursor_value, cursor_value),
                )

    async def update_task_status(self, task_id: str, status: str, **kwargs):
        async with self.pool.acquire() as conn:
            async with conn.cursor() as cur:
                if status == "RUNNING":
                    await cur.execute(
                        "UPDATE task_history SET status='RUNNING', started_at=NOW() WHERE task_id=%s",
                        (task_id,),
                    )
                elif status in ("SUCCESS", "FAILED"):
                    await cur.execute(
                        """UPDATE task_history SET status=%s, rows_affected=%s,
                           duration_ms=%s, error_msg=%s, finished_at=NOW() WHERE task_id=%s""",
                        (status,
                         kwargs.get("rows_affected", 0),
                         kwargs.get("duration_ms", 0),
                         kwargs.get("error_msg", ""),
                         task_id),
                    )

    async def get_site_config(self, site_config_id: int) -> dict | None:
        """Get crawl_site_config + merged selectors."""
        async with self.pool.acquire() as conn:
            async with conn.cursor(aiomysql.DictCursor) as cur:
                await cur.execute(
                    "SELECT * FROM crawl_site_config WHERE id=%s", (site_config_id,)
                )
                config = await cur.fetchone()
                if not config:
                    return None
                # Get all associated templates + extra_selectors
                await cur.execute(
                    """SELECT st.*, stm.extra_selectors
                       FROM site_template_mapping stm
                       JOIN selector_template st ON st.id = stm.template_id
                       WHERE stm.site_config_id = %s""",
                    (site_config_id,),
                )
                templates = await cur.fetchall()
                config["templates"] = templates
                return config

    async def close(self):
        if self.pool:
            self.pool.close()
            await self.pool.wait_closed()


class ProductRepository:
    """Manage scraped_data.ecommerce_products writes (Scrapy Pipeline alternative)."""

    def __init__(self):
        self.pool: aiomysql.Pool | None = None

    async def connect(self):
        kwargs = _parse_mysql_url(SCRAPED_DB_URL)
        self.pool = await aiomysql.create_pool(
            host=kwargs["host"], port=kwargs["port"],
            user=kwargs["user"], password=kwargs["password"],
            db=kwargs["db"], charset="utf8mb4", autocommit=True,
        )

    async def close(self):
        if self.pool:
            self.pool.close()
            await self.pool.wait_closed()
```

### Task 14: Python — site_crawler.py (async 重写)

**Files:**
- Create: `crawler-consumer/crawlers/site_crawler.py`

```python
import asyncio
import aiohttp
from loguru import logger


class AsyncSiteCrawler:
    """Async version of SiteCrawler — fetches site info from management platform."""

    BASE_URL = "http://104.233.194.18"
    PAGE_SIZE = 100

    def __init__(self, username: str, password: str):
        self.username = username
        self.password = password
        self.client: aiohttp.ClientSession | None = None
        self.token: str | None = None

    async def login(self) -> str:
        async with self.client.post(
            f"{self.BASE_URL}/adminapi/login",
            json={"username": self.username, "password": self.password},
        ) as resp:
            data = await resp.json()
            self.token = data.get("data", {}).get("access_token") or data.get("access_token")
            if not self.token:
                raise Exception(f"Login failed: {data}")
            logger.success("🔓 Site crawler logged in")
            return self.token

    def _headers(self):
        return {"Authorization": f"Bearer {self.token}"}

    async def fetch_site_map(self) -> dict:
        """Build domain → {theme_name, product_category} mapping."""
        site_map = {}
        page = 1
        while True:
            url = f"{self.BASE_URL}/adminapi/site/site/list?page={page}&page_size={self.PAGE_SIZE}"
            async with self.client.get(url, headers=self._headers()) as resp:
                data = await resp.json()
                items = data.get("data", {}).get("items", []) or data.get("items", [])
                if not items:
                    break
                for item in items:
                    domain = item.get("site_domain", "").strip()
                    if domain:
                        site_map[domain] = {
                            "theme_name": item.get("theme_name", ""),
                            "product_category": item.get("product_category", ""),
                            "admin_name": item.get("admin_name", ""),
                        }
                if len(items) < self.PAGE_SIZE:
                    break
                page += 1
        logger.info(f"📋 Site map built: {len(site_map)} domains")
        return site_map

    async def fetch_domains(self, site_map: dict, since: str | None = None) -> list[dict]:
        """Fetch domain list, optionally incremental (since = last_updated_at)."""
        results = []
        page = 1
        while True:
            url = f"{self.BASE_URL}/adminapi/domain/domain/list?page={page}&page_size={self.PAGE_SIZE}"
            if since:
                url += f"&updated_since={since}"
            async with self.client.get(url, headers=self._headers()) as resp:
                data = await resp.json()
                items = data.get("data", {}).get("items", []) or data.get("items", [])
                if not items:
                    break
                for item in items:
                    domain = item.get("domain", "").strip()
                    admin_name = item.get("admin_name", "")
                    status = item.get("status", 0)
                    # Apply filters
                    if admin_name == "super" or status == 2:
                        continue
                    record = {
                        "site_domain": domain,
                        "admin_name": admin_name,
                        "username": self.username,
                    }
                    # Enrich from site_map
                    if domain in site_map:
                        record["theme_name"] = site_map[domain]["theme_name"]
                        record["product_category"] = site_map[domain]["product_category"]
                    results.append(record)
                if len(items) < self.PAGE_SIZE:
                    break
                page += 1
                await asyncio.sleep(0.1)  # rate limit
        logger.info(f"📦 Fetched {len(results)} domains (since={since})")
        return results

    async def run(self, since: str | None = None) -> tuple[list[dict], str]:
        """Execute site crawl. Returns (records, new_cursor_value)."""
        self.client = aiohttp.ClientSession(
            connector=aiohttp.TCPConnector(verify_ssl=False),
            timeout=aiohttp.ClientTimeout(total=30),
        )
        try:
            await self.login()
            site_map = await self.fetch_site_map()
            records = await self.fetch_domains(site_map, since)
            new_cursor = None  # Updated by upstream: now()
            return records, new_cursor
        finally:
            await self.client.close()
```

### Task 15: Python — site_consumer.py

**Files:**
- Create: `crawler-consumer/consumers/site_consumer.py`

```python
import time
from datetime import datetime, timezone
from loguru import logger
from consumers.base_consumer import BaseConsumer
from crawlers.site_crawler import AsyncSiteCrawler
from db.repository import CursorRepository
from config import RABBITMQ_USER, RABBITMQ_PASS, RABBITMQ_HOST, RABBITMQ_PORT, QUEUE_SITE_CRAWL, QUEUE_TASK_RESULT, EXCHANGE_TASKS
import pika
import json


class SiteConsumer(BaseConsumer):

    def __init__(self, rabbitmq_url: str):
        super().__init__(QUEUE_SITE_CRAWL, rabbitmq_url)
        self.repo = CursorRepository()

    async def process(self, message: dict):
        task_id = message["task_id"]
        payload = message["payload"]
        since = payload.get("cursor", {}).get("last_updated_at")

        await self.repo.connect()
        start = time.monotonic()

        try:
            await self.repo.update_task_status(task_id, "RUNNING")

            crawler = AsyncSiteCrawler(payload["username"], payload["password"])
            records, _ = await crawler.run(since=since)

            # Upsert records to site_info
            await self._upsert_site_info(records)

            new_cursor = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
            await self.repo.update_cursor("site_crawler", new_cursor)

            duration_ms = int((time.monotonic() - start) * 1000)
            await self.repo.update_task_status(
                task_id, "SUCCESS", rows_affected=len(records), duration_ms=duration_ms
            )

            self._publish_result(task_id, "success", len(records),
                                {"last_updated_at": new_cursor}, duration_ms)
            logger.success(f"✅ Site crawl done: {len(records)} records")

        except Exception as e:
            duration_ms = int((time.monotonic() - start) * 1000)
            await self.repo.update_task_status(
                task_id, "FAILED", error_msg=str(e), duration_ms=duration_ms
            )
            self._publish_result(task_id, "failed", 0, None, duration_ms, str(e))
            logger.error(f"❌ Site crawl failed: {e}")
            raise
        finally:
            await self.repo.close()

    async def _upsert_site_info(self, records: list[dict]):
        async with self.repo.pool.acquire() as conn:
            async with conn.cursor() as cur:
                for r in records:
                    await cur.execute(
                        """INSERT INTO site_info (username, site_domain, admin_name, theme_name, product_category, created_at)
                           VALUES (%s, %s, %s, %s, %s, NOW())
                           ON DUPLICATE KEY UPDATE
                             admin_name=VALUES(admin_name),
                             theme_name=VALUES(theme_name),
                             product_category=VALUES(product_category)""",
                        (r["username"], r["site_domain"], r.get("admin_name"),
                         r.get("theme_name"), r.get("product_category")),
                    )

    def _publish_result(self, task_id, status, rows_affected, new_cursor, duration_ms, error=None):
        # Publish result back — use a synchronous channel briefly
        params = pika.URLParameters(self.rabbitmq_url)
        conn = pika.BlockingConnection(params)
        ch = conn.channel()
        result = {
            "task_id": task_id,
            "status": status,
            "rows_affected": rows_affected,
            "new_cursor": new_cursor,
            "duration_ms": duration_ms,
            "error": error,
            "finished_at": datetime.now(timezone.utc).isoformat(),
        }
        ch.basic_publish(
            exchange=EXCHANGE_TASKS,
            routing_key="crawler.task.result",
            body=json.dumps(result),
            properties=pika.BasicProperties(content_type="application/json"),
        )
        conn.close()
```

### Task 16: Spring Boot — Quartz SiteCrawlJob

**Files:**
- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/scheduler/SiteCrawlJob.java`

```java
package com.cyberflow.admin.crawler.scheduler;

import com.cyberflow.admin.crawler.messaging.TaskMessagePublisher;
import com.cyberflow.admin.crawler.task.entity.CrawlCursor;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import com.cyberflow.admin.crawler.task.mapper.CrawlCursorMapper;
import com.cyberflow.admin.crawler.task.service.TaskHistoryService;
import com.cyberflow.admin.crawler.task.mapper.TaskHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class SiteCrawlJob implements Job {

    private final TaskMessagePublisher publisher;
    private final CrawlCursorMapper cursorMapper;
    private final TaskHistoryService taskHistoryService;

    @Value("${CRAWLER_USERNAME:}")
    private String crawlerUsername;

    @Value("${CRAWLER_PASSWORD:}")
    private String crawlerPassword;

    @Override
    public void execute(JobExecutionContext context) {
        log.info("Quartz triggered: site crawl job");

        // Read cursor
        CrawlCursor cursor = cursorMapper.selectById(
            cursorMapper.selectList(null).stream()
                .filter(c -> "site_crawler".equals(c.getCursorKey()))
                .findFirst().map(CrawlCursor::getId).orElse(null)
        );
        // Fallback: query by key
        if (cursor == null) {
            cursor = cursorMapper.selectList(null).stream()
                .filter(c -> "site_crawler".equals(c.getCursorKey()))
                .findFirst().orElse(null);
        }
        String lastUpdatedAt = cursor != null ? cursor.getCursorValue() : LocalDateTime.now().minusDays(1).toString();

        // Publish task
        String taskId = publisher.publishSiteCrawl(crawlerUsername, crawlerPassword, lastUpdatedAt);

        // Insert task_history
        TaskHistory history = new TaskHistory();
        history.setTaskId(taskId);
        history.setType("site_crawl");
        history.setTriggerType("cron");
        history.setStatus("PENDING");
        history.setCursorBefore(lastUpdatedAt);
        taskHistoryService.save(history);
    }
}
```

### Task 17: 修改 CrawlerService + CrawlerController (MQ 发布 + task_history 查询)

**Files:**
- Modify: `backend-admin/src/main/java/com/cyberflow/admin/crawler/service/CrawlerService.java`

```java
package com.cyberflow.admin.crawler.service;

import com.cyberflow.admin.crawler.messaging.TaskMessagePublisher;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import com.cyberflow.admin.crawler.task.service.TaskHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerService {

    private final TaskMessagePublisher publisher;
    private final TaskHistoryService taskHistoryService;

    public Map<String, Object> triggerSiteCrawler(String username, String password) {
        String lastUpdatedAt = LocalDateTime.now().minusDays(1).toString();
        String taskId = publisher.publishSiteCrawl(username, password, lastUpdatedAt);
        saveTaskHistory(taskId, "site_crawl", "manual", null);
        return Map.of("task_id", taskId, "status", "Task dispatched");
    }

    public Map<String, Object> triggerSiteIndexCrawler(String username, String password) {
        String lastRecordedAt = LocalDateTime.now().minusDays(1).toString();
        String taskId = publisher.publishSiteIndexCrawl(username, password, lastRecordedAt);
        saveTaskHistory(taskId, "site_index", "manual", null);
        return Map.of("task_id", taskId, "status", "Task dispatched");
    }

    public Map<String, Object> triggerOrderCrawler(String startTime, String endTime) {
        String maxOrderId = "0"; // For manual triggers, start from 0 or use a specific range
        String taskId = publisher.publishOrderCrawl(maxOrderId);
        saveTaskHistory(taskId, "order_crawl", "manual", null);
        return Map.of("task_id", taskId, "status", "Task dispatched");
    }

    public Map<String, Object> getTaskStatus(String taskId) {
        TaskHistory task = taskHistoryService.getByTaskId(taskId);
        if (task == null) {
            return Map.of("task_id", taskId, "state", "UNKNOWN");
        }
        return Map.of(
            "task_id", task.getTaskId(),
            "state", task.getStatus(),
            "result", Map.of(
                "rows_affected", task.getRowsAffected() != null ? task.getRowsAffected() : 0,
                "error", task.getErrorMsg() != null ? task.getErrorMsg() : ""
            )
        );
    }

    public Map<String, Object> triggerProductCrawl(Long siteConfigId, String domain, String type, String category, Long triggeredBy) {
        String taskId = publisher.publishProductCrawl(siteConfigId, domain, type, category, triggeredBy);
        saveTaskHistory(taskId, "product_crawl", "manual", String.valueOf(triggeredBy));
        return Map.of("task_id", taskId, "status", "Task dispatched");
    }

    private void saveTaskHistory(String taskId, String type, String triggerType, String triggeredBy) {
        TaskHistory history = new TaskHistory();
        history.setTaskId(taskId);
        history.setType(type);
        history.setTriggerType(triggerType);
        history.setTriggeredBy(triggeredBy);
        history.setStatus("PENDING");
        taskHistoryService.save(history);
    }
}
```

- Modify: `backend-admin/src/main/java/com/cyberflow/admin/crawler/controller/CrawlerController.java`

```java
package com.cyberflow.admin.crawler.controller;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.crawler.service.CrawlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/crawler")
@RequiredArgsConstructor
public class CrawlerController {

    private final CrawlerService crawlerService;

    @PostMapping("/site/start")
    @PreAuthorize("hasAuthority('crawler:site:start')")
    public Result<Map<String, Object>> startSiteCrawler(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        return Result.ok(crawlerService.triggerSiteCrawler(username, password));
    }

    @PostMapping("/site/collect")
    @PreAuthorize("hasAuthority('crawler:collect:start')")
    public Result<Map<String, Object>> collectSite(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        return Result.ok(crawlerService.triggerSiteIndexCrawler(username, password));
    }

    @PostMapping("/order/start")
    @PreAuthorize("hasAuthority('crawler:order:start')")
    public Result<Map<String, Object>> startOrderCrawler(@RequestBody Map<String, String> body) {
        String startTime = body.get("start_time");
        String endTime = body.get("end_time");
        return Result.ok(crawlerService.triggerOrderCrawler(startTime, endTime));
    }

    // Note: status/{taskId} and /tasks endpoints moved to TaskHistoryController
    // Kept here for backward compatibility during migration
    @GetMapping("/status/{taskId}")
    @PreAuthorize("hasAnyAuthority('crawler:site:start', 'crawler:collect:start', 'crawler:order:start')")
    public Result<Map<String, Object>> status(@PathVariable String taskId) {
        return Result.ok(crawlerService.getTaskStatus(taskId));
    }
}
```

---

## Phase 3: 订单爬取迁移

### Task 18: Python — order_crawler.py (async 重写)

**Files:**
- Create: `crawler-consumer/crawlers/order_crawler.py`

```python
import asyncio
import aiohttp
from loguru import logger


class AsyncOrderCrawler:
    """Async version of OrderCrawler — fetches orders from payment platform."""

    BASE_URL = "https://c4partypay.com"
    PAGE_SIZE = 100

    def __init__(self):
        self.client: aiohttp.ClientSession | None = None
        self.token: str | None = None

    async def login(self, username: str, password: str) -> str:
        async with self.client.post(
            f"{self.BASE_URL}/platformapi/login/account",
            json={"username": username, "password": password},
        ) as resp:
            data = await resp.json()
            self.token = data.get("data", {}).get("access_token") or data.get("access_token")
            if not self.token:
                raise Exception(f"Order login failed: {data}")
            logger.success("🔓 Order crawler logged in")
            return self.token

    def _headers(self):
        return {"Authorization": f"Bearer {self.token}"}

    async def fetch_orders(self, since_order_id: str = "0") -> list[dict]:
        """Fetch orders incrementally by order_id cursor."""
        results = []
        page = 1
        current_max_id = int(since_order_id)

        while True:
            url = (
                f"{self.BASE_URL}/platformapi/pay.pay_order/lists"
                f"?tenant_id=95&page_size={self.PAGE_SIZE}&page={page}"
            )
            async with self.client.get(url, headers=self._headers()) as resp:
                data = await resp.json()
                items = data.get("data", {}).get("items", []) or data.get("items", [])
                if not items:
                    break

                for item in items:
                    order_id = item.get("id", 0)
                    # Filter: only orders > cursor
                    if order_id <= int(since_order_id):
                        continue
                    # Filter test orders
                    channel = str(item.get("channel", "")).lower()
                    if any(t in channel for t in ["测试", "ig-3", "test-mutiwp"]):
                        continue
                    # Filter test card numbers
                    card_no = str(item.get("card_no", ""))
                    if card_no in ["400000******0000", "411111******1111"]:
                        continue

                    current_max_id = max(current_max_id, order_id)
                    results.append(item)

                if len(items) < self.PAGE_SIZE:
                    break
                page += 1
                await asyncio.sleep(0.1)

        logger.info(f"📦 Fetched {len(results)} orders (since_id={since_order_id}, new_max={current_max_id})")
        return results, str(current_max_id)

    async def run(self, username: str, password: str, since_order_id: str = "0") -> tuple[list[dict], str]:
        """Execute order crawl. Returns (records, new_max_order_id)."""
        self.client = aiohttp.ClientSession(
            connector=aiohttp.TCPConnector(verify_ssl=False),
            timeout=aiohttp.ClientTimeout(total=30),
        )
        try:
            await self.login(username, password)
            records, new_cursor = await self.fetch_orders(since_order_id)
            return records, new_cursor
        finally:
            await self.client.close()
```

### Task 19: Python — order_consumer.py

**Files:**
- Create: `crawler-consumer/consumers/order_consumer.py`

```python
import time
from datetime import datetime, timezone
from loguru import logger
from consumers.base_consumer import BaseConsumer
from crawlers.order_crawler import AsyncOrderCrawler
from db.repository import CursorRepository
from config import QUEUE_ORDER_CRAWL, RABBITMQ_USER, RABBITMQ_PASS, RABBITMQ_HOST, RABBITMQ_PORT, EXCHANGE_TASKS
import pika
import json


class OrderConsumer(BaseConsumer):

    def __init__(self, rabbitmq_url: str):
        super().__init__(QUEUE_ORDER_CRAWL, rabbitmq_url)
        self.repo = CursorRepository()

    async def process(self, message: dict):
        task_id = message["task_id"]
        payload = message["payload"]
        since_order_id = payload.get("cursor", {}).get("max_order_id", "0")

        await self.repo.connect()
        start = time.monotonic()

        try:
            await self.repo.update_task_status(task_id, "RUNNING")

            crawler = AsyncOrderCrawler()
            # Credentials from env (same as before)
            import os
            username = os.getenv("CRAWLER_USERNAME", "")
            password = os.getenv("CRAWLER_PASSWORD", "")

            records, new_cursor = await crawler.run(username, password, since_order_id)

            # Bulk insert to orders table
            await self._save_orders(records)

            await self.repo.update_cursor("order_crawler", new_cursor)
            duration_ms = int((time.monotonic() - start) * 1000)
            await self.repo.update_task_status(
                task_id, "SUCCESS", rows_affected=len(records), duration_ms=duration_ms
            )

            self._publish_result(task_id, "success", len(records),
                                {"max_order_id": new_cursor}, duration_ms)
            logger.success(f"✅ Order crawl done: {len(records)} records, cursor={new_cursor}")

        except Exception as e:
            duration_ms = int((time.monotonic() - start) * 1000)
            await self.repo.update_task_status(
                task_id, "FAILED", error_msg=str(e), duration_ms=duration_ms
            )
            self._publish_result(task_id, "failed", 0, None, duration_ms, str(e))
            logger.error(f"❌ Order crawl failed: {e}")
            raise
        finally:
            await self.repo.close()

    async def _save_orders(self, records: list[dict]):
        """Bulk insert orders with dedup. Cross-references site_info for admin_name/theme_name/category."""
        async with self.repo.pool.acquire() as conn:
            async with conn.cursor() as cur:
                for r in records:
                    product_host = r.get("product_host", "")
                    # Cross-reference site_info
                    admin_name = ""
                    theme_name = ""
                    product_category = ""
                    if product_host:
                        await cur.execute(
                            "SELECT admin_name, theme_name, product_category FROM site_info WHERE site_domain=%s",
                            (product_host,),
                        )
                        site_row = await cur.fetchone()
                        if site_row:
                            admin_name, theme_name, product_category = site_row[0], site_row[1], site_row[2]

                    await cur.execute(
                        """INSERT INTO orders (id, amount, currency, create_time, product_host,
                           pay_status_text, customer_ip_country, shipping_email,
                           admin_name, theme_name, product_category)
                           VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                           ON DUPLICATE KEY UPDATE
                           amount=VALUES(amount), pay_status_text=VALUES(pay_status_text)""",
                        (r.get("id"), r.get("amount"), r.get("currency"),
                         r.get("create_time"), product_host,
                         r.get("pay_status_text"), r.get("customer_ip_country"),
                         r.get("shipping_email"), admin_name, theme_name, product_category),
                    )

    def _publish_result(self, task_id, status, rows_affected, new_cursor, duration_ms, error=None):
        params = pika.URLParameters(self.rabbitmq_url)
        conn = pika.BlockingConnection(params)
        ch = conn.channel()
        result = {
            "task_id": task_id,
            "status": status,
            "rows_affected": rows_affected,
            "new_cursor": new_cursor,
            "duration_ms": duration_ms,
            "error": error,
            "finished_at": datetime.now(timezone.utc).isoformat(),
        }
        ch.basic_publish(
            exchange=EXCHANGE_TASKS,
            routing_key="crawler.task.result",
            body=json.dumps(result),
            properties=pika.BasicProperties(content_type="application/json"),
        )
        conn.close()
```

### Task 20: Spring Boot — Quartz OrderCrawlJob

**Files:**
- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/scheduler/OrderCrawlJob.java`

```java
package com.cyberflow.admin.crawler.scheduler;

import com.cyberflow.admin.crawler.messaging.TaskMessagePublisher;
import com.cyberflow.admin.crawler.task.entity.CrawlCursor;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import com.cyberflow.admin.crawler.task.mapper.CrawlCursorMapper;
import com.cyberflow.admin.crawler.task.service.TaskHistoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCrawlJob implements Job {

    private final TaskMessagePublisher publisher;
    private final CrawlCursorMapper cursorMapper;
    private final TaskHistoryService taskHistoryService;

    @Override
    public void execute(JobExecutionContext context) {
        log.info("Quartz triggered: order crawl job");

        // Read cursor
        CrawlCursor cursor = cursorMapper.selectOne(
            new LambdaQueryWrapper<CrawlCursor>().eq(CrawlCursor::getCursorKey, "order_crawler")
        );
        String maxOrderId = cursor != null ? cursor.getCursorValue() : "0";

        // Publish task
        String taskId = publisher.publishOrderCrawl(maxOrderId);

        // Insert task_history
        TaskHistory history = new TaskHistory();
        history.setTaskId(taskId);
        history.setType("order_crawl");
        history.setTriggerType("cron");
        history.setStatus("PENDING");
        history.setCursorBefore(maxOrderId);
        taskHistoryService.save(history);
    }
}
```

### Task 21: Python main.py (启动所有 Consumer)

**Files:**
- Create: `crawler-consumer/main.py`

```python
#!/usr/bin/env python3
"""CyberFlow v2.0 — Python Consumer Entry Point"""
import asyncio
import os
import signal
from dotenv import load_dotenv
from loguru import logger
from config import (
    RABBITMQ_USER, RABBITMQ_PASS, RABBITMQ_HOST, RABBITMQ_PORT,
)
from consumers.site_consumer import SiteConsumer
from consumers.order_consumer import OrderConsumer
from consumers.product_consumer import ProductConsumer

load_dotenv()

RABBITMQ_URL = f"amqp://{RABBITMQ_USER}:{RABBITMQ_PASS}@{RABBITMQ_HOST}:{RABBITMQ_PORT}/"


async def main():
    logger.info("🚀 Starting CyberFlow v2.0 Consumers...")

    site = SiteConsumer(RABBITMQ_URL)
    order = OrderConsumer(RABBITMQ_URL)
    product = ProductConsumer(RABBITMQ_URL)

    # Run all consumers in parallel
    tasks = [
        asyncio.ensure_future(_run_consumer(site)),
        asyncio.ensure_future(_run_consumer(order)),
        asyncio.ensure_future(_run_consumer(product)),
    ]

    try:
        await asyncio.gather(*tasks)
    except asyncio.CancelledError:
        logger.info("Consumers cancelled")
    except Exception as e:
        logger.error(f"Consumer error: {e}")


async def _run_consumer(consumer):
    loop = asyncio.get_event_loop()
    consumer.run()
    # Keep alive
    while not consumer._closing:
        await asyncio.sleep(1)


if __name__ == "__main__":
    asyncio.run(main())
```

---

## Phase 4: 商品爬取 + 选择器模板库

### Task 22: Spring Boot — SelectorTemplate entity + mapper + service + controller

**Files:**
- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/selector/entity/SelectorTemplate.java`

```java
package com.cyberflow.admin.crawler.selector.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("selector_template")
public class SelectorTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String platform;
    private String titleSelector;
    private String priceSelector;
    private String priceRegex;
    private String descriptionSelector;
    private String imagesSelector;
    private String currency;
    private String breadcrumbLinksSelector;
    private String breadcrumbLastSelector;
    private String siteMapSelector;
    private Integer isSystem;
    private LocalDateTime createdAt;
}
```

- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/selector/mapper/SelectorTemplateMapper.java`

```java
package com.cyberflow.admin.crawler.selector.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.crawler.selector.entity.SelectorTemplate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SelectorTemplateMapper extends BaseMapper<SelectorTemplate> {
}
```

- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/selector/service/SelectorTemplateService.java`

```java
package com.cyberflow.admin.crawler.selector.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyberflow.admin.crawler.selector.entity.SelectorTemplate;
import com.cyberflow.admin.crawler.selector.mapper.SelectorTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SelectorTemplateService {

    private final SelectorTemplateMapper mapper;

    public List<SelectorTemplate> list(String platform) {
        LambdaQueryWrapper<SelectorTemplate> wrapper = new LambdaQueryWrapper<>();
        if (platform != null && !platform.isEmpty()) {
            wrapper.eq(SelectorTemplate::getPlatform, platform);
        }
        wrapper.orderByAsc(SelectorTemplate::getPlatform).orderByAsc(SelectorTemplate::getName);
        return mapper.selectList(wrapper);
    }

    public SelectorTemplate getById(Long id) {
        return mapper.selectById(id);
    }

    public SelectorTemplate create(SelectorTemplate template) {
        mapper.insert(template);
        return template;
    }

    public SelectorTemplate update(Long id, SelectorTemplate template) {
        template.setId(id);
        mapper.updateById(template);
        return mapper.selectById(id);
    }

    public void delete(Long id) {
        SelectorTemplate template = mapper.selectById(id);
        if (template != null && template.getIsSystem() == 1) {
            throw new RuntimeException("Cannot delete system template");
        }
        mapper.deleteById(id);
    }

    public SelectorTemplate clone(Long id) {
        SelectorTemplate original = mapper.selectById(id);
        if (original == null) throw new RuntimeException("Template not found");
        SelectorTemplate copy = new SelectorTemplate();
        copy.setName(original.getName() + " (copy)");
        copy.setPlatform(original.getPlatform());
        copy.setTitleSelector(original.getTitleSelector());
        copy.setPriceSelector(original.getPriceSelector());
        copy.setPriceRegex(original.getPriceRegex());
        copy.setDescriptionSelector(original.getDescriptionSelector());
        copy.setImagesSelector(original.getImagesSelector());
        copy.setCurrency(original.getCurrency());
        copy.setBreadcrumbLinksSelector(original.getBreadcrumbLinksSelector());
        copy.setBreadcrumbLastSelector(original.getBreadcrumbLastSelector());
        copy.setSiteMapSelector(original.getSiteMapSelector());
        copy.setIsSystem(0);
        mapper.insert(copy);
        return copy;
    }
}
```

- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/selector/controller/SelectorTemplateController.java`

```java
package com.cyberflow.admin.crawler.selector.controller;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.crawler.selector.entity.SelectorTemplate;
import com.cyberflow.admin.crawler.selector.service.SelectorTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/selector/template")
@RequiredArgsConstructor
public class SelectorTemplateController {

    private final SelectorTemplateService service;

    @GetMapping
    @PreAuthorize("hasAuthority('selector:template:list')")
    public Result<?> list(@RequestParam(required = false) String platform) {
        return Result.ok(service.list(platform));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('selector:template:list')")
    public Result<?> get(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('selector:template:create')")
    public Result<?> create(@RequestBody SelectorTemplate template) {
        return Result.ok(service.create(template));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('selector:template:update')")
    public Result<?> update(@PathVariable Long id, @RequestBody SelectorTemplate template) {
        return Result.ok(service.update(id, template));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('selector:template:delete')")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/clone")
    @PreAuthorize("hasAuthority('selector:template:create')")
    public Result<?> clone(@PathVariable Long id) {
        return Result.ok(service.clone(id));
    }
}
```

### Task 23: Spring Boot — CrawlSiteConfig entity + mapper + service + controller

**Files:**
- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/siteconfig/entity/CrawlSiteConfig.java`

```java
package com.cyberflow.admin.crawler.siteconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("crawl_site_config")
public class CrawlSiteConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String domain;
    private String type;
    private String category;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/siteconfig/entity/SiteTemplateMapping.java`

```java
package com.cyberflow.admin.crawler.siteconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("site_template_mapping")
public class SiteTemplateMapping {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long siteConfigId;
    private Long templateId;
    private String extraSelectors;  // JSON string
    private LocalDateTime createdAt;
}
```

- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/siteconfig/mapper/CrawlSiteConfigMapper.java`

```java
package com.cyberflow.admin.crawler.siteconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.crawler.siteconfig.entity.CrawlSiteConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CrawlSiteConfigMapper extends BaseMapper<CrawlSiteConfig> {
}
```

- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/siteconfig/mapper/SiteTemplateMappingMapper.java`

```java
package com.cyberflow.admin.crawler.siteconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.crawler.siteconfig.entity.SiteTemplateMapping;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SiteTemplateMappingMapper extends BaseMapper<SiteTemplateMapping> {
}
```

- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/siteconfig/service/SiteConfigService.java`

```java
package com.cyberflow.admin.crawler.siteconfig.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyberflow.admin.crawler.siteconfig.entity.CrawlSiteConfig;
import com.cyberflow.admin.crawler.siteconfig.entity.SiteTemplateMapping;
import com.cyberflow.admin.crawler.siteconfig.mapper.CrawlSiteConfigMapper;
import com.cyberflow.admin.crawler.siteconfig.mapper.SiteTemplateMappingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SiteConfigService {

    private final CrawlSiteConfigMapper configMapper;
    private final SiteTemplateMappingMapper mappingMapper;

    public List<CrawlSiteConfig> list() {
        return configMapper.selectList(
            new LambdaQueryWrapper<CrawlSiteConfig>().orderByDesc(CrawlSiteConfig::getCreatedAt)
        );
    }

    public CrawlSiteConfig getById(Long id) {
        return configMapper.selectById(id);
    }

    public List<SiteTemplateMapping> getMappings(Long siteConfigId) {
        return mappingMapper.selectList(
            new LambdaQueryWrapper<SiteTemplateMapping>()
                .eq(SiteTemplateMapping::getSiteConfigId, siteConfigId)
        );
    }

    @Transactional
    public CrawlSiteConfig create(CrawlSiteConfig config, List<SiteTemplateMapping> mappings) {
        configMapper.insert(config);
        for (SiteTemplateMapping m : mappings) {
            m.setSiteConfigId(config.getId());
            mappingMapper.insert(m);
        }
        return config;
    }

    @Transactional
    public CrawlSiteConfig update(Long id, CrawlSiteConfig config, List<SiteTemplateMapping> mappings) {
        config.setId(id);
        configMapper.updateById(config);
        // Replace mappings
        mappingMapper.delete(
            new LambdaQueryWrapper<SiteTemplateMapping>()
                .eq(SiteTemplateMapping::getSiteConfigId, id)
        );
        for (SiteTemplateMapping m : mappings) {
            m.setSiteConfigId(id);
            mappingMapper.insert(m);
        }
        return configMapper.selectById(id);
    }

    public void delete(Long id) {
        mappingMapper.delete(
            new LambdaQueryWrapper<SiteTemplateMapping>()
                .eq(SiteTemplateMapping::getSiteConfigId, id)
        );
        configMapper.deleteById(id);
    }
}
```

- Create: `backend-admin/src/main/java/com/cyberflow/admin/crawler/siteconfig/controller/SiteConfigController.java`

```java
package com.cyberflow.admin.crawler.siteconfig.controller;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.crawler.service.CrawlerService;
import com.cyberflow.admin.crawler.siteconfig.entity.CrawlSiteConfig;
import com.cyberflow.admin.crawler.siteconfig.entity.SiteTemplateMapping;
import com.cyberflow.admin.crawler.siteconfig.service.SiteConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/crawler/site-config")
@RequiredArgsConstructor
public class SiteConfigController {

    private final SiteConfigService siteConfigService;
    private final CrawlerService crawlerService;

    @GetMapping
    @PreAuthorize("hasAuthority('crawler:site:config:list')")
    public Result<?> list() {
        return Result.ok(siteConfigService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('crawler:site:config:list')")
    public Result<?> get(@PathVariable Long id) {
        var config = siteConfigService.getById(id);
        var mappings = siteConfigService.getMappings(id);
        return Result.ok(Map.of("config", config, "mappings", mappings));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crawler:site:config:create')")
    public Result<?> create(@RequestBody Map<String, Object> body) {
        // Parse config + mappings from body
        // Simplified: assumes frontend sends structured JSON
        CrawlSiteConfig config = parseConfig(body);
        List<SiteTemplateMapping> mappings = parseMappings(body);
        var created = siteConfigService.create(config, mappings);
        return Result.ok(created);
    }

    @PostMapping("/{id}/crawl")
    @PreAuthorize("hasAuthority('crawler:site:config:crawl')")
    public Result<?> triggerCrawl(@PathVariable Long id, @RequestBody Map<String, String> body) {
        CrawlSiteConfig config = siteConfigService.getById(id);
        if (config == null) return Result.fail("Site config not found");
        Long userId = Long.valueOf(body.getOrDefault("user_id", "0"));
        return Result.ok(crawlerService.triggerProductCrawl(
            config.getId(), config.getDomain(), config.getType(),
            config.getCategory(), userId
        ));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('crawler:site:config:delete')")
    public Result<?> delete(@PathVariable Long id) {
        siteConfigService.delete(id);
        return Result.ok();
    }

    @SuppressWarnings("unchecked")
    private CrawlSiteConfig parseConfig(Map<String, Object> body) {
        CrawlSiteConfig c = new CrawlSiteConfig();
        Map<String, Object> configData = (Map<String, Object>) body.get("config");
        c.setDomain((String) configData.get("domain"));
        c.setType((String) configData.get("type"));
        c.setCategory((String) configData.getOrDefault("category", "未知分类"));
        c.setStatus("active");
        c.setCreatedBy(Long.valueOf(body.getOrDefault("user_id", "0").toString()));
        return c;
    }

    @SuppressWarnings("unchecked")
    private List<SiteTemplateMapping> parseMappings(Map<String, Object> body) {
        List<Map<String, Object>> rawList = (List<Map<String, Object>>) body.get("mappings");
        if (rawList == null) return List.of();
        return rawList.stream().map(m -> {
            SiteTemplateMapping sm = new SiteTemplateMapping();
            sm.setTemplateId(Long.valueOf(m.get("template_id").toString()));
            sm.setExtraSelectors(m.get("extra_selectors") != null
                ? m.get("extra_selectors").toString() : null);
            return sm;
        }).toList();
    }
}
```

### Task 24: Python — product_consumer.py (调度 Scrapy 子进程)

**Files:**
- Create: `crawler-consumer/consumers/product_consumer.py`

```python
import asyncio
import json
import os
import time
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from loguru import logger
from consumers.base_consumer import BaseConsumer
from db.repository import CursorRepository
from config import QUEUE_PRODUCT_CRAWL, RABBITMQ_USER, RABBITMQ_PASS, RABBITMQ_HOST, RABBITMQ_PORT, EXCHANGE_TASKS
import pika


class ProductConsumer(BaseConsumer):

    def __init__(self, rabbitmq_url: str):
        super().__init__(QUEUE_PRODUCT_CRAWL, rabbitmq_url)
        self.repo = CursorRepository()
        self.scrapy_project = Path(__file__).parent.parent / "scrapy_app"

    async def process(self, message: dict):
        task_id = message["task_id"]
        payload = message["payload"]
        site_config_id = payload["site_config_id"]
        domain = payload["domain"]
        site_type = payload["type"]
        category = payload.get("category", "未知分类")

        await self.repo.connect()
        start = time.monotonic()

        try:
            await self.repo.update_task_status(task_id, "RUNNING")

            if site_type == "shopify":
                result = await self._run_shopify(domain, category)
            else:
                result = await self._run_woo(domain, category, site_config_id)

            duration_ms = int((time.monotonic() - start) * 1000)
            await self.repo.update_task_status(
                task_id, "SUCCESS", rows_affected=result, duration_ms=duration_ms
            )
            self._publish_result(task_id, "success", result, None, duration_ms)
            logger.success(f"✅ Product crawl done: {domain} ({result} items)")

        except Exception as e:
            duration_ms = int((time.monotonic() - start) * 1000)
            await self.repo.update_task_status(
                task_id, "FAILED", error_msg=str(e), duration_ms=duration_ms
            )
            self._publish_result(task_id, "failed", 0, None, duration_ms, str(e))
            logger.error(f"❌ Product crawl failed: {e}")
            raise
        finally:
            await self.repo.close()

    async def _run_shopify(self, domain: str, category: str) -> int:
        """Run Shopify spider as subprocess."""
        cmd = [
            "scrapy", "crawl", "shopify_crawl_fast",
            "-a", f"domain={domain}",
            "-a", f"category={category}",
            "-a", "mode=prod",
        ]
        return await self._exec_scrapy(cmd)

    async def _run_woo(self, domain: str, category: str, site_config_id: int) -> int:
        """Run WooCommerce spider with merged selectors from DB."""
        # Get site config with merged selectors
        config = await self.repo.get_site_config(site_config_id)
        if not config:
            raise Exception(f"Site config {site_config_id} not found")

        # Merge selectors from all templates
        merged = self._merge_selectors(config.get("templates", []))
        # Write merged selectors to temp file
        with tempfile.NamedTemporaryFile(
            mode="w", suffix=".json", delete=False, encoding="utf-8"
        ) as f:
            json.dump(merged, f, ensure_ascii=False)
            config_file = f.name

        try:
            cmd = [
                "scrapy", "crawl", "woo_crawl",
                "-a", f"domain={domain}",
                "-a", f"category={category}",
                "-a", f"config_file={config_file}",
                "-a", "mode=prod",
            ]
            return await self._exec_scrapy(cmd)
        finally:
            os.unlink(config_file)

    def _merge_selectors(self, templates: list[dict]) -> dict:
        """Merge all templates' selectors: same key → XPath union with |."""
        field_keys = [
            "title_selector", "price_selector", "description_selector",
            "images_selector", "breadcrumb_links_selector",
            "breadcrumb_last_selector", "site_map_selector",
        ]
        merged = {"price_regex": r"[\d.,]+", "currency": "USD"}

        for key in field_keys:
            parts = []
            for t in templates:
                val = t.get(key)
                if val:
                    parts.append(val.strip())
                # Also check extra_selectors
                extra = t.get("extra_selectors")
                if extra:
                    if isinstance(extra, str):
                        extra = json.loads(extra)
                    if isinstance(extra, dict):
                        # Map JSON keys to DB column names
                        json_key = key.replace("_selector", "")
                        if json_key in extra:
                            parts.append(extra[json_key].strip())

            if parts:
                # Deduplicate while preserving order
                seen = set()
                unique_parts = []
                for p in parts:
                    if p not in seen:
                        seen.add(p)
                        unique_parts.append(p)
                merged[key] = " | ".join(unique_parts)

        return merged

    async def _exec_scrapy(self, cmd: list[str]) -> int:
        """Execute Scrapy command and count items."""
        logger.info(f"🚀 Running: {' '.join(cmd)}")
        proc = await asyncio.create_subprocess_exec(
            *cmd,
            cwd=str(self.scrapy_project),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, stderr = await proc.communicate()

        if proc.returncode != 0:
            error_msg = stderr.decode()[:500] if stderr else "Unknown error"
            raise Exception(f"Scrapy exited {proc.returncode}: {error_msg}")

        # Count yielded items from Scrapy log
        output = stdout.decode()
        item_count = output.count("成功生成商品")
        logger.info(f"📦 Scrapy produced ~{item_count} items")
        return max(item_count, 0)

    def _publish_result(self, task_id, status, rows_affected, new_cursor, duration_ms, error=None):
        params = pika.URLParameters(self.rabbitmq_url)
        conn = pika.BlockingConnection(params)
        ch = conn.channel()
        result = {
            "task_id": task_id,
            "status": status,
            "rows_affected": rows_affected,
            "new_cursor": new_cursor,
            "duration_ms": duration_ms,
            "error": error,
            "finished_at": datetime.now(timezone.utc).isoformat(),
        }
        ch.basic_publish(
            exchange=EXCHANGE_TASKS,
            routing_key="crawler.task.result",
            body=json.dumps(result),
            properties=pika.BasicProperties(content_type="application/json"),
        )
        conn.close()
```

### Task 25: 复制 Scrapy 项目到 crawler-consumer/scrapy_app/

**Files:**
- Copy: `crawler-service/app/crawler/ecommerce_spider/` → `crawler-consumer/scrapy_app/`

```bash
cp -r crawler-service/app/crawler/ecommerce_spider/ecommerce_spider crawler-consumer/scrapy_app/
cp crawler-service/app/crawler/ecommerce_spider/scrapy.cfg crawler-consumer/scrapy_app/
```

Then modify `crawler-consumer/scrapy_app/ecommerce_spider/settings.py` to read DB/Redis config from environment variables.

---

### Task 26: 前端 API 适配

**Files:**
- Modify: `frontend/src/api/crawler.js` — 更新 API 路径，对接新的 SiteConfigController 和 SelectorTemplateController
- Create: `frontend/src/api/selector.js` — 选择器模板库 API 封装
- Create: `frontend/src/views/crawler/SelectorTemplate.vue` — 选择器模板管理页面
- Create: `frontend/src/views/crawler/SiteConfig.vue` — 站点注册页面（域名 + 多选模板 + 分类）
- Modify: `frontend/src/router/index.js` — 添加路由

```javascript
// frontend/src/api/selector.js
import request from '@/utils/request'

export function listTemplates(platform) {
  return request.get('/admin/selector/template', { params: { platform } })
}

export function createTemplate(data) {
  return request.post('/admin/selector/template', data)
}

export function updateTemplate(id, data) {
  return request.put(`/admin/selector/template/${id}`, data)
}

export function deleteTemplate(id) {
  return request.delete(`/admin/selector/template/${id}`)
}

export function cloneTemplate(id) {
  return request.post(`/admin/selector/template/${id}/clone`)
}

// Site Config
export function listSiteConfigs() {
  return request.get('/admin/crawler/site-config')
}

export function createSiteConfig(data) {
  return request.post('/admin/crawler/site-config', data)
}

export function triggerSiteCrawl(id, userId) {
  return request.post(`/admin/crawler/site-config/${id}/crawl`, { user_id: userId })
}
```

Mock 数据更新 (`frontend/src/mock/data/crawler.js`): 添加 selector template 和 site config 的 mock 响应。

---

## Phase 5: 清理

### Task 26: Mark CrawlerApiClient as @Deprecated

**Files:**
- Modify: `backend-admin/src/main/java/com/cyberflow/admin/crawler/client/CrawlerApiClient.java`

Add `@Deprecated` annotation to the class:

```java
@Slf4j
@Service
@Deprecated(since = "2.0", forRemoval = true)
public class CrawlerApiClient {
    // ... existing code unchanged ...
}
```

### Task 27: Update application.yml — enable Quartz scheduling

**Files:**
- Modify: `backend-admin/src/main/resources/application.yml`

Add Quartz properties:

```yaml
spring:
  quartz:
    job-store-type: memory
    properties:
      org:
        quartz:
          scheduler:
            instanceName: CyberFlowScheduler
          threadPool:
            threadCount: 3
```

### Task 28: Update docker-compose.yml (完整服务栈)

**Files:**
- Modify: `docker-compose.yml`

```yaml
version: '3.8'
services:
  rabbitmq:
    image: rabbitmq:3.13-management
    container_name: cyberflow-rabbitmq
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: admin
      RABBITMQ_DEFAULT_PASS: admin123
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "check_port_connectivity"]
      interval: 10s
      timeout: 5s
      retries: 5

  mysql:
    image: mysql:8.0
    container_name: cyberflow-mysql
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: 123456
      MYSQL_DATABASE: cyberflow
    volumes:
      - mysql_data:/var/lib/mysql
      - ./docs/init_v2_tables.sql:/docker-entrypoint-initdb.d/01-init.sql

  redis:
    image: redis:7-alpine
    container_name: cyberflow-redis
    ports:
      - "6379:6379"

  backend-admin:
    build: ./backend-admin
    container_name: cyberflow-admin
    ports:
      - "8080:8080"
    environment:
      RABBITMQ_HOST: rabbitmq
      MYSQL_HOST: mysql
    depends_on:
      rabbitmq:
        condition: service_healthy
      mysql:
        condition: service_started

  crawler-consumer:
    build: ./crawler-consumer
    container_name: cyberflow-consumer
    environment:
      RABBITMQ_HOST: rabbitmq
      MYSQL_HOST: mysql
      REDIS_HOST: redis
    depends_on:
      rabbitmq:
        condition: service_healthy
      mysql:
        condition: service_started

volumes:
  rabbitmq_data:
  mysql_data:
```

---

## 自检清单

- [ ] Phase 1 完成后：RabbitMQ 可访问管理界面，Spring Boot 启动无报错，新表创建成功
- [ ] Phase 2 完成后：Quartz 可自动触发站点爬取，Python SiteConsumer 能消费消息并写入 site_info，结果回传更新 task_history
- [ ] Phase 3 完成后：Quartz 可自动触发订单爬取，OrderConsumer 游标增量生效，多次执行不重复入库
- [ ] Phase 4 完成后：前端可管理选择器模板和站点配置，ProductConsumer 能合并选择器并调度 Scrapy
- [ ] Phase 5 完成后：FastAPI/Celery 下线，系统仅依赖 RabbitMQ + Spring Boot + Python Consumer
