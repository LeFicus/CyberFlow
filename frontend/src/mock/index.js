/**
 * @fileoverview Mock 服务入口模块
 * @description 开发环境使用 Mock.js 拦截 AJAX 请求并返回模拟数据。
 *              涵盖认证、仪表盘数据、爬虫任务、选择器模板、站点配置、
 *              用户管理、角色管理、菜单管理、操作日志等全部后端 API。
 *              通过正则匹配 URL 模式来拦截对应接口，模拟延迟 200-400ms。
 */
import Mock from 'mockjs'
import authData from './data/auth'
import dashboardData from './data/dashboard'
import crawlerData from './data/crawler'
import systemData from './data/system'

// 设置 Mock 请求延迟，模拟真实网络环境（200-400ms）
Mock.setup({ timeout: '200-400' })

/**
 * 将 URL 查询字符串解析为键值对对象
 * @param {string} url - 完整 URL（含 query string）
 * @returns {Object} 解析后的参数对象，如 { page: '1', size: '10' }
 */
function paramParser(url) {
  const q = url.indexOf('?')
  if (q < 0) return {}
  const obj = {}
  url.substring(q + 1).split('&').forEach(p => {
    const [k, v] = p.split('=')
    obj[decodeURIComponent(k)] = decodeURIComponent(v || '')
  })
  return obj
}

// ========== Auth 认证模块 ==========
/** 拦截 POST /admin/auth/login - 登录认证，仅允许 admin/admin123 */
Mock.mock(/\/admin\/auth\/login$/, 'post', (options) => {
  const body = JSON.parse(options.body || '{}')
  if (body.username === 'admin' && body.password === 'admin123') {
    return authData.login()
  }
  return { code: 401, msg: '用户名或密码错误', data: null }
})
/** 拦截 GET /admin/auth/userinfo - 获取当前用户信息 */
Mock.mock(/\/admin\/auth\/userinfo/, 'get', authData.userinfo)

// ========== Dashboard 仪表盘模块 ==========
/** 拦截 GET /admin/dashboard/overview - 概览统计数据 */
Mock.mock(/\/admin\/dashboard\/overview/, 'get', dashboardData.overview)
/** 拦截 GET /admin/dashboard/charts - 趋势图表数据 */
Mock.mock(/\/admin\/dashboard\/charts/, 'get', dashboardData.charts)
/** 拦截 GET /admin/dashboard/sites - 站点分页列表 */
Mock.mock(/\/admin\/dashboard\/sites(\?|$)/, 'get', (options) => dashboardData.sites(paramParser(options.url)))
/** 拦截 GET /admin/dashboard/site-index-history - 站点收录历史 */
Mock.mock(/\/admin\/dashboard\/site-index-history(\?|$)/, 'get', (options) => dashboardData.siteIndexHistory(paramParser(options.url)))
/** 拦截 GET /admin/dashboard/orders-by-domain - 按域名查询订单 */
Mock.mock(/\/admin\/dashboard\/orders-by-domain(\?|$)/, 'get', (options) => dashboardData.ordersByDomain(paramParser(options.url)))
/** 拦截 GET /admin/dashboard/orders - 订单分页列表 */
Mock.mock(/\/admin\/dashboard\/orders(\?|$)/, 'get', (options) => dashboardData.orders(paramParser(options.url)))
/** 拦截 GET /admin/dashboard/products - 商品分页列表 */
Mock.mock(/\/admin\/dashboard\/products(\?|$)/, 'get', (options) => dashboardData.products(paramParser(options.url)))

// ========== Crawler 爬虫模块 ==========
/** 拦截 POST /admin/crawler/site/start - 触发站点信息爬取 */
Mock.mock(/\/admin\/crawler\/site\/start/, 'post', crawlerData.triggerSiteCrawler)
/** 拦截 POST /admin/crawler/site/collect - 触发站点收录统计 */
Mock.mock(/\/admin\/crawler\/site\/collect/, 'post', crawlerData.triggerCollectCrawler)
/** 拦截 POST /admin/crawler/order/start - 触发订单爬取 */
Mock.mock(/\/admin\/crawler\/order\/start/, 'post', crawlerData.triggerOrderCrawler)
/** 拦截 GET /admin/crawler/status/:taskId - 查询任务状态 */
Mock.mock(/\/admin\/crawler\/status\//, 'get', crawlerData.status)
/** 拦截 GET /admin/crawler/tasks - 获取最近任务列表 */
Mock.mock(/\/admin\/crawler\/tasks/, 'get', crawlerData.tasks)
/** 拦截 GET /admin/crawler/config - 获取爬虫运行配置 */
Mock.mock(/\/admin\/crawler\/config$/, 'get', crawlerData.configGet)
/** 拦截 PUT /admin/crawler/config - 更新爬虫运行配置 */
Mock.mock(/\/admin\/crawler\/config$/, 'put', (options) => crawlerData.configUpdate(JSON.parse(options.body || '{}')))
/** 拦截 GET /admin/crawler/config/schedules - 获取定时任务配置 */
Mock.mock(/\/admin\/crawler\/config\/schedules$/, 'get', crawlerData.scheduleList)
/** 拦截 PUT /admin/crawler/config/schedules/:taskType - 更新定时任务配置 */
Mock.mock(/\/admin\/crawler\/config\/schedules\/[^/]+$/, 'put', crawlerData.scheduleUpdate)

// ========== Selector Template 选择器模板 ==========
/** 拦截 GET /admin/selector/template - 模板列表（支持 platform 参数过滤） */
Mock.mock(/\/admin\/selector\/template(\?|$)/, 'get', crawlerData.selectorTemplateList)
/** 拦截 GET /admin/selector/template/:id - 获取单个模板 */
Mock.mock(/\/admin\/selector\/template\/\d+$/, 'get', crawlerData.selectorTemplateGet)
/** 拦截 POST /admin/selector/template - 创建模板 */
Mock.mock(/\/admin\/selector\/template$/, 'post', crawlerData.selectorTemplateCreate)
/** 拦截 PUT /admin/selector/template/:id - 更新模板 */
Mock.mock(/\/admin\/selector\/template\/\d+$/, 'put', crawlerData.selectorTemplateUpdate)
/** 拦截 DELETE /admin/selector/template/:id - 删除模板 */
Mock.mock(/\/admin\/selector\/template\/\d+$/, 'delete', crawlerData.selectorTemplateDelete)
/** 拦截 POST /admin/selector/template/:id/clone - 克隆模板 */
Mock.mock(/\/admin\/selector\/template\/\d+\/clone/, 'post', crawlerData.selectorTemplateClone)

// ========== Site Config 站点配置 ==========
/** 拦截 GET /admin/crawler/site-config - 站点配置列表 */
Mock.mock(/\/admin\/crawler\/site-config(\?|$)/, 'get', crawlerData.siteConfigList)
/** 拦截 GET /admin/crawler/site-config/:id - 获取站点配置详情 */
Mock.mock(/\/admin\/crawler\/site-config\/\d+$/, 'get', crawlerData.siteConfigGet)
/** 拦截 POST /admin/crawler/site-config - 创建站点配置 */
Mock.mock(/\/admin\/crawler\/site-config$/, 'post', crawlerData.siteConfigCreate)
/** 拦截 DELETE /admin/crawler/site-config/:id - 删除站点配置 */
Mock.mock(/\/admin\/crawler\/site-config\/\d+$/, 'delete', crawlerData.siteConfigDelete)
/** 拦截 POST /admin/crawler/site-config/:id/crawl - 触发商品爬取 */
Mock.mock(/\/admin\/crawler\/site-config\/\d+\/crawl/, 'post', crawlerData.siteConfigCrawl)

// ========== System: User 用户管理 ==========
/** 拦截 GET /admin/system/user - 用户列表 */
Mock.mock(/\/admin\/system\/user(\?|$)/, 'get', systemData.userList)
/** 拦截 POST /admin/system/user - 创建用户 */
Mock.mock(/\/admin\/system\/user$/, 'post', (options) => systemData.userCreate(JSON.parse(options.body || '{}')))
/** 拦截 PUT /admin/system/user/:id - 更新用户 */
Mock.mock(/\/admin\/system\/user\/\d+$/, 'put', (options) => {
  const id = options.url.match(/\/admin\/system\/user\/(\d+)/)[1]
  return systemData.userUpdate(id, JSON.parse(options.body || '{}'))
})
/** 拦截 DELETE /admin/system/user/:id - 删除用户 */
Mock.mock(/\/admin\/system\/user\/\d+$/, 'delete', (options) => {
  const id = options.url.match(/\/admin\/system\/user\/(\d+)/)[1]
  return systemData.userDelete(id)
})
/** 拦截 PUT /admin/system/user/:id/roles - 分配角色 */
Mock.mock(/\/admin\/system\/user\/\d+\/roles/, 'put', { code: 200, msg: 'success', data: null })

// ========== System: Role 角色管理 ==========
/** 拦截 GET /admin/system/role - 角色分页列表 */
Mock.mock(/\/admin\/system\/role(\?|$)/, 'get', systemData.roleList)
/** 拦截 GET /admin/system/role/all - 所有角色（不分页） */
Mock.mock(/\/admin\/system\/role\/all/, 'get', systemData.roleAll)
/** 拦截 PUT /admin/system/role/:id/menus - 为角色分配菜单 */
Mock.mock(/\/admin\/system\/role\/\d+\/menus/, 'put', { code: 200, msg: 'success', data: null })

// ========== System: Menu 菜单管理 ==========
/** 拦截 GET /admin/system/menu/tree - 获取菜单树 */
Mock.mock(/\/admin\/system\/menu\/tree/, 'get', systemData.menuTree)

// ========== System: Log 操作日志 ==========
/** 拦截 GET /admin/system/log - 操作日志分页列表 */
Mock.mock(/\/admin\/system\/log(\?|$)/, 'get', (options) => systemData.logList(paramParser(options.url)))
