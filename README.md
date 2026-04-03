CyberFlow - 多语言跨境电商爬虫管理系统

## 技术栈
- **后端管理**: Spring Boot 3.x
- **爬虫服务**: FastAPI + Scrapy + Celery
- **任务队列**: Redis
- **数据存储**: MySQL 8.0

## 项目结构
- `backend-admin`: Java 后台管理系统
- `crawler-service`: Python 异步爬虫微服务
- `docker-compose.yml`: 一键部署配置

## 快速开始
1. 配置 `.env` 环境参数
2. 运行 `docker-compose up -d`
3. 访问 `http://localhost:8080` (Admin) 或 `http://localhost:8000/docs` (API)