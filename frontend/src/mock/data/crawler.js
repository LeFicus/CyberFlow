/**
 * @fileoverview 爬虫模块 Mock 数据
 * @description 模拟爬虫任务触发、任务状态查询、选择器模板 CRUD、站点配置管理等后端接口。
 *              使用内存数组存储任务记录和配置数据，支持基本的增删改查操作。
 */

/** @type {Array<Object>} 爬虫任务记录存储（内存中，重启后丢失） */
const taskStore = []

/** @type {Array<Object>} 模拟的选择器模板列表 */
const selectorTemplates = [
  { id: 1, name: 'Shopify Default', platform: 'shopify', titleSelector: null, priceSelector: null, priceRegex: null, descriptionSelector: null, imagesSelector: null, currency: 'USD', breadcrumbLinksSelector: null, breadcrumbLastSelector: null, siteMapSelector: null, isSystem: 1, createdAt: '2026-06-01 10:02:00' },
]

/** @type {number} 模板自增 ID 计数器 */
let templateNextId = 2

/** @type {Array<Object>} 模拟的站点配置列表 */
const siteConfigs = [
  { id: 1, domain: 'demo.myshopify.com', type: 'shopify', category: '服饰与配饰', status: 'active', createdBy: 1, createdAt: '2026-06-05 14:00:00', updatedAt: '2026-06-05 14:00:00' },
]

/** @type {Object<number, Array>} 站点配置与模板映射的关联关系（key: siteConfigId） */
const siteTemplateMappings = {}

/** @type {number} 站点配置自增 ID 计数器 */
let configNextId = 2

const crawlerConfig = {
  adminApi: { baseUrl: 'http://216.152.147.6', username: 'yg001', password: '******', verifySsl: true },
  paymentApiA: { baseUrl: '', account: '', password: '', verifySsl: true },
  paymentApiB: { baseUrl: 'https://payment-b.example.com', account: 'group-b', password: '******', verifySsl: true },
  siteStrategy: { skipSiteCheck: true, fetchAdminLoginUrl: false, filterBuiltOnly: false, pageSize: 100 },
  orderStrategy: { filterCardNumberExclude: ['400000******0000', '411111******1111', '411111111111'], pageSize: 100, initialOrderId: '0' },
  revenue: {
    exchangeRate: 6.73,
    rateFactor: 0.42,
    leaderCommissionRate: 0.02,
    commissionTiers: [
      { threshold: 30000, rate: 0.03 },
      { threshold: 80000, rate: 0.05 },
      { threshold: '', rate: 0.08 },
    ],
  },
}

const crawlerSchedules = [
  { taskType: 'site_crawl', cronExpression: '0 0 2 * * ?', enabled: 1, lastTriggeredAt: null },
  { taskType: 'order_crawl', cronExpression: '0 0 3 * * ?', enabled: 1, lastTriggeredAt: null },
]

/**
 * 生成 UUID v4 格式的唯一标识符
 * @returns {string} UUID 字符串
 */
function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16)
  })
}

export default {
  // ==================== 爬虫任务触发 ====================

  /**
   * 触发站点信息爬取任务
   * 生成任务 ID 并存入任务列表，返回下发成功响应
   * @returns {Object} { code, msg, data: { task_id, crawler_type, status } }
   */
  triggerSiteCrawler: () => {
    const taskId = uuid()
    taskStore.unshift({ task_id: taskId, type: 'site', state: 'SUCCESS', result: '站点信息同步完成', created_at: Date.now() })
    return { code: 200, msg: 'success', data: { task_id: taskId, crawler_type: 'site', status: 'Task dispatched' } }
  },

  /**
   * 触发站点收录统计任务
   * @returns {Object} { code, msg, data: { task_id, crawler_type, status } }
   */
  triggerCollectCrawler: () => {
    const taskId = uuid()
    taskStore.unshift({ task_id: taskId, type: 'site_index', state: 'SUCCESS', result: '收录统计完成', created_at: Date.now() })
    return { code: 200, msg: 'success', data: { task_id: taskId, crawler_type: 'site_index', status: 'Task dispatched' } }
  },

  /**
   * 触发订单爬取任务
   * @returns {Object} { code, msg, data: { task_id, crawler_type, status } }
   */
  triggerOrderCrawler: () => {
    const taskId = uuid()
    taskStore.unshift({ task_id: taskId, type: 'order', state: 'SUCCESS', result: '订单同步完成：新增 23 条', created_at: Date.now() })
    return { code: 200, msg: 'success', data: { task_id: taskId, crawler_type: 'order', status: 'Task dispatched' } }
  },

  /**
   * 查询任务状态
   * @returns {Object} { code, msg, data: { task_id, state, result } }
   */
  status: () => ({
    code: 200, msg: 'success',
    data: { task_id: '', state: 'SUCCESS', result: null },
  }),

  /**
   * 获取最近 20 条任务记录
   * @returns {Object} { code, msg, data: Array }
   */
  tasks: () => ({
    code: 200, msg: 'success',
    data: taskStore.slice(0, 20),
  }),

  configGet: () => ({
    code: 200, msg: 'success',
    data: crawlerConfig,
  }),

  configUpdate: (body) => {
    Object.keys(body || {}).forEach(group => {
      crawlerConfig[group] = { ...(crawlerConfig[group] || {}), ...body[group] }
      if (crawlerConfig[group].password) crawlerConfig[group].password = '******'
    })
    return { code: 200, msg: 'success', data: crawlerConfig }
  },

  scheduleList: () => ({
    code: 200, msg: 'success',
    data: crawlerSchedules,
  }),

  scheduleUpdate: (options) => {
    const taskType = options.url.match(/\/admin\/crawler\/config\/schedules\/([^/]+)/)?.[1]
    const body = JSON.parse(options.body || '{}')
    const schedule = crawlerSchedules.find(item => item.taskType === taskType)
    if (!schedule) return { code: 404, msg: 'not found', data: null }
    schedule.enabled = body.enabled ? 1 : 0
    schedule.cronExpression = body.cronExpression || schedule.cronExpression
    return { code: 200, msg: 'success', data: schedule }
  },

  // ==================== 选择器模板 CRUD ====================

  /**
   * 获取模板列表（支持按 platform 过滤）
   * @param {Object} [options] - 查询参数 { platform }
   * @returns {Object} { code, msg, data: Array }
   */
  selectorTemplateList: (options) => {
    const platform = options?.platform
    let list = selectorTemplates
    if (platform) list = list.filter(t => t.platform === platform)
    return { code: 200, msg: 'success', data: list }
  },

  /**
   * 获取单个模板详情
   * @param {Object} options - Mock 拦截器传入的请求上下文 { url }
   * @returns {Object} { code, msg, data: Object|null }
   */
  selectorTemplateGet: (options) => {
    const id = parseInt(options.url.match(/\/admin\/selector\/template\/(\d+)/)[1])
    const tmpl = selectorTemplates.find(t => t.id === id)
    return tmpl ? { code: 200, msg: 'success', data: tmpl } : { code: 404, msg: 'not found', data: null }
  },

  /**
   * 创建选择器模板
   * @param {Object} body - 模板数据对象
   * @returns {Object} { code, msg, data: Object }
   */
  selectorTemplateCreate: (body) => {
    const tmpl = { id: templateNextId++, isSystem: 0, createdAt: new Date().toISOString(), ...body }
    selectorTemplates.unshift(tmpl)
    return { code: 200, msg: 'success', data: tmpl }
  },

  /**
   * 更新选择器模板
   * @param {Object} options - Mock 请求上下文 { url, body }
   * @returns {Object} { code, msg, data: Object|null }
   */
  selectorTemplateUpdate: (options) => {
    const id = parseInt(options.url.match(/\/admin\/selector\/template\/(\d+)/)[1])
    const idx = selectorTemplates.findIndex(t => t.id === id)
    if (idx >= 0) {
      const body = JSON.parse(options.body || '{}')
      selectorTemplates[idx] = { ...selectorTemplates[idx], ...body }
      return { code: 200, msg: 'success', data: selectorTemplates[idx] }
    }
    return { code: 404, msg: 'not found', data: null }
  },

  /**
   * 删除选择器模板（系统模板不可删除）
   * @param {Object} options - Mock 请求上下文 { url }
   * @returns {Object} { code, msg, data: null }
   */
  selectorTemplateDelete: (options) => {
    const id = parseInt(options.url.match(/\/admin\/selector\/template\/(\d+)/)[1])
    const idx = selectorTemplates.findIndex(t => t.id === id)
    if (idx >= 0 && selectorTemplates[idx].isSystem !== 1) {
      selectorTemplates.splice(idx, 1)
      return { code: 200, msg: 'success', data: null }
    }
    return { code: 400, msg: 'Cannot delete system template', data: null }
  },

  /**
   * 克隆选择器模板
   * 复制原模板数据，名称为「原名 (copy)」，标记为非系统模板
   * @param {Object} options - Mock 请求上下文 { url }
   * @returns {Object} { code, msg, data: Object|null }
   */
  selectorTemplateClone: (options) => {
    const id = parseInt(options.url.match(/\/admin\/selector\/template\/(\d+)\/clone/)[1])
    const original = selectorTemplates.find(t => t.id === id)
    if (original) {
      const copy = { ...original, id: templateNextId++, name: original.name + ' (copy)', isSystem: 0, createdAt: new Date().toISOString() }
      selectorTemplates.unshift(copy)
      return { code: 200, msg: 'success', data: copy }
    }
    return { code: 404, msg: 'not found', data: null }
  },

  // ==================== 站点配置 CRUD ====================

  /**
   * 获取所有站点配置列表
   * @returns {Object} { code, msg, data: Array }
   */
  siteConfigList: () => ({ code: 200, msg: 'success', data: siteConfigs }),

  /**
   * 获取站点配置详情（含模板映射）
   * @param {Object} options - Mock 请求上下文 { url }
   * @returns {Object} { code, msg, data: { config, mappings }|null }
   */
  siteConfigGet: (options) => {
    const id = parseInt(options.url.match(/\/admin\/crawler\/site-config\/(\d+)/)[1])
    const config = siteConfigs.find(c => c.id === id)
    const mappings = siteTemplateMappings[id] || []
    return config ? { code: 200, msg: 'success', data: { config, mappings } } : { code: 404, msg: 'not found', data: null }
  },

  /**
   * 创建站点配置（含模板映射关系）
   * @param {Object} body - { config: {...}, mappings: [...] }
   * @returns {Object} { code, msg, data: Object }
   */
  siteConfigCreate: (body) => {
    const config = { id: configNextId++, status: 'active', createdBy: 1, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(), ...body.config }
    siteConfigs.unshift(config)
    if (body.mappings?.length > 0) {
      siteTemplateMappings[config.id] = body.mappings.map((m, i) => ({ id: i + 1, siteConfigId: config.id, templateId: m.template_id, extraSelectors: m.extra_selectors || null, createdAt: new Date().toISOString() }))
    }
    return { code: 200, msg: 'success', data: config }
  },

  /**
   * 删除站点配置及其关联的模板映射
   * @param {Object} options - Mock 请求上下文 { url }
   * @returns {Object} { code, msg, data: null }
   */
  siteConfigDelete: (options) => {
    const id = parseInt(options.url.match(/\/admin\/crawler\/site-config\/(\d+)/)[1])
    const idx = siteConfigs.findIndex(c => c.id === id)
    if (idx >= 0) {
      siteConfigs.splice(idx, 1)
      delete siteTemplateMappings[id]
      return { code: 200, msg: 'success', data: null }
    }
    return { code: 404, msg: 'not found', data: null }
  },

  /**
   * 触发站点的商品爬取任务
   * @param {Object} options - Mock 请求上下文 { url }
   * @returns {Object} { code, msg, data: { task_id, status }|null }
   */
  siteConfigCrawl: (options) => {
    const id = parseInt(options.url.match(/\/admin\/crawler\/site-config\/(\d+)\/crawl/)[1])
    const config = siteConfigs.find(c => c.id === id)
    if (config) {
      const taskId = uuid()
      taskStore.unshift({ task_id: taskId, type: 'product_crawl', state: 'SUCCESS', result: `爬取完成: ${config.domain}`, created_at: Date.now() })
      return { code: 200, msg: 'success', data: { task_id: taskId, status: 'Task dispatched' } }
    }
    return { code: 404, msg: 'not found', data: null }
  },
}
