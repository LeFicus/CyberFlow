/**
 * @fileoverview 仪表盘模块 Mock 数据
 * @description 为数据看板页面提供模拟数据，包括概览统计、订单/收录趋势图表、
 *              站点/订单/商品分页列表、站点收录历史、按域名查询订单等。
 *              所有数据在首次加载时通过随机算法生成并缓存在内存中。
 */

/**
 * 生成 [min, max] 范围内的随机整数
 * @param {number} min - 最小值（包含）
 * @param {number} max - 最大值（包含）
 * @returns {number} 随机整数
 */
function random(min, max) { return Math.floor(Math.random() * (max - min + 1)) + min }

/**
 * 生成 daysAgo 天前的日期时间字符串（格式: YYYY-MM-DD HH:mm:ss）
 * @param {number} daysAgo - 距今天数
 * @returns {string} 日期时间字符串
 */
function datetime(daysAgo) { const d = new Date(); d.setDate(d.getDate() - daysAgo); return d.toISOString().slice(0, 19).replace('T', ' ') }

/** @type {string[]} 管理员姓名列表 */
const adminNames = ['张三', '李四', '王五', '赵六', '钱七']
/** @type {string[]} 站点主题名称列表 */
const themeNames = ['Default', 'Electro', 'Fashion', 'Minimal', 'Organic']
/** @type {string[]} 商品分类列表 */
const categories = ['服装', '电子产品', '家居', '美妆', '运动', '食品']
/** @type {string[]} 模拟域名列表 */
const domains = ['shop1.com', 'beauty-store.com', 'tech-gadgets.com', 'home-life.com', 'fashion-hub.com', 'sports-gear.com']
/** @type {string[]} 货币代码列表 */
const currencies = ['USD', 'EUR', 'GBP', 'JPY']

/** @type {Array<Object>} 模拟站点列表（共 53 条） */
const sites = Array.from({ length: 53 }, (_, i) => ({
  id: i + 1,
  username: 'admin',
  site_domain: domains[i % domains.length] + (i > 5 ? `/${i}` : ''),
  admin_name: adminNames[i % adminNames.length],
  theme_name: themeNames[i % themeNames.length],
  product_category: categories[i % categories.length],
  created_at: datetime(random(1, 90)),
}))

/** @type {Array<Object>} 模拟订单列表（共 127 条） */
const orders = Array.from({ length: 127 }, (_, i) => ({
  id: 10000 + i,
  amount: parseFloat((Math.random() * 500 + 20).toFixed(2)),
  currency: currencies[i % currencies.length],
  create_time: datetime(random(0, 60)),
  product_host: domains[i % domains.length],
  pay_status_text: i % 10 === 0 ? '退款' : '已支付',
  customer_ip_country: ['US', 'CN', 'UK', 'DE', 'FR'][i % 5],
  shipping_email: `user${i}@example.com`,
  admin_name: adminNames[i % adminNames.length],
  theme_name: themeNames[i % themeNames.length],
  product_category: categories[i % categories.length],
}))

/** @type {Array<Object>} 模拟商品列表（共 215 条） */
const products = Array.from({ length: 215 }, (_, i) => ({
  id: i + 1,
  SKU: `SKU-${String(i + 1).padStart(5, '0')}`,
  Name: `商品 ${i + 1} - ${categories[i % categories.length]}`,
  Description: `这是商品 ${i + 1} 的详细描述`,
  'Regular price': parseFloat((Math.random() * 200 + 5).toFixed(2)),
  Categories: categories[i % categories.length],
  Images: JSON.stringify([`https://picsum.photos/seed/${i}/400/400`]),
  cf_opingts: '',
  '自定义分类': categories[i % categories.length],
  '原站域名': domains[i % domains.length],
  '语言': ['zh', 'en', 'ja'][i % 3],
  created_at: datetime(random(1, 120)),
}))

/** @type {Array<Object>} 近 30 天 Google 收录趋势数据 */
const indexTrend = Array.from({ length: 30 }, (_, i) => ({
  date: datetime(29 - i).slice(0, 10),
  total_index: random(500, 2000) + i * 10,
  total_products: random(100, 500) + i * 5,
  site_count: 53,
}))

/** @type {Array<Object>} 近 30 天订单趋势数据 */
const orderTrend = Array.from({ length: 30 }, (_, i) => ({
  date: datetime(29 - i).slice(0, 10),
  count: random(3, 15),
  amount: parseFloat((Math.random() * 3000 + 500).toFixed(2)),
}))

/** @type {Object<string, Array>} 各站点收录历史缓存（按域名索引，90 天数据） */
const siteIndexCache = {}

/**
 * 获取指定站点的 Google 收录指数历史（90 天），首次调用时生成并缓存
 * @param {string} domain - 站点域名
 * @returns {Array<Object>} 90 天的收录数据数组 [{ date, index_count, product_count, site_domain }]
 */
function getSiteIndexHistory(domain) {
  if (siteIndexCache[domain]) return siteIndexCache[domain]
  const baseIndex = random(200, 3000)
  const baseProducts = random(20, 300)
  siteIndexCache[domain] = Array.from({ length: 90 }, (_, i) => ({
    date: datetime(89 - i).slice(0, 10),
    index_count: baseIndex + random(-50, 50) + Math.floor(i * random(1, 8)),
    product_count: baseProducts + random(-10, 10) + Math.floor(i * random(1, 3)),
    site_domain: domain,
  }))
  return siteIndexCache[domain]
}

/**
 * 根据域名查询关联的订单数据（先按域名过滤，再按时间范围过滤，最后分页）
 * @param {string} domain - 站点域名
 * @param {Object} [params={}] - 查询参数 { startDate, endDate, page, size }
 * @returns {Object} { total, list: Array }
 */
function getOrdersByDomain(domain, params = {}) {
  let matched = orders.filter(o => o.product_host === domain)
  if (params.startDate && params.endDate) {
    matched = matched.filter(o => {
      const d = o.create_time.slice(0, 10)
      return d >= params.startDate && d <= params.endDate
    })
  }
  const page = parseInt(params.page) || 1
  const size = parseInt(params.size) || 10
  const start = (page - 1) * size
  return {
    total: matched.length,
    list: matched.slice(start, start + size),
  }
}

export default {
  /**
   * 概览统计数据
   * @returns {Object} { code, msg, data: { total_sites, total_orders, total_products, today_orders, today_amount } }
   */
  overview: () => ({
    code: 200, msg: 'success',
    data: {
      total_sites: 53,
      total_orders: 127,
      deduplicated_orders: 127,
      successful_orders: orders.filter(item => item.pay_status_text === '已支付').length,
      successful_amount: orders.filter(item => item.pay_status_text === '已支付').reduce((sum, item) => sum + item.amount, 0),
      total_products: 215,
      today_orders: random(3, 12),
      today_amount: parseFloat((Math.random() * 2000 + 200).toFixed(2)),
    },
  }),

  /**
   * 站点分页列表
   * @param {Object} params - { page, size }
   * @returns {Object} { code, msg, data: { total, list } }
   */
  sites: (params) => {
    const page = parseInt(params.page) || 1
    const size = parseInt(params.size) || 10
    const start = (page - 1) * size
    let matched = sites
    if (params.adminName) matched = matched.filter(item => item.admin_name?.includes(params.adminName))
    if (params.domain) matched = matched.filter(item => item.site_domain?.toLowerCase().includes(params.domain.toLowerCase()))
    if (params.startDate) matched = matched.filter(item => item.created_at?.slice(0, 10) >= params.startDate)
    if (params.endDate) matched = matched.filter(item => item.created_at?.slice(0, 10) <= params.endDate)
    return { code: 200, msg: 'success', data: { total: matched.length, list: matched.slice(start, start + size) } }
  },

  /**
   * 订单分页列表
   * @param {Object} params - { page, size }
   * @returns {Object} { code, msg, data: { total, list } }
   */
  orders: (params) => {
    const page = parseInt(params.page) || 1
    const size = parseInt(params.size) || 10
    const start = (page - 1) * size
    let matched = orders
    if (params.orderId) matched = matched.filter(item => String(item.id).includes(params.orderId))
    if (params.adminName) matched = matched.filter(item => item.admin_name?.includes(params.adminName))
    if (params.domain) matched = matched.filter(item => item.product_host?.toLowerCase().includes(params.domain.toLowerCase()))
    if (params.payStatus) matched = matched.filter(item => item.pay_status_text === params.payStatus)
    if (params.currency) matched = matched.filter(item => item.currency === params.currency)
    if (params.country) matched = matched.filter(item => item.customer_ip_country?.toLowerCase().includes(params.country.toLowerCase()))
    if (params.startDate) matched = matched.filter(item => item.create_time?.slice(0, 10) >= params.startDate)
    if (params.endDate) matched = matched.filter(item => item.create_time?.slice(0, 10) <= params.endDate)
    const paid = matched.filter(item => item.pay_status_text === '已支付')
    const summary = {
      total_count: matched.length,
      total_amount: matched.reduce((sum, item) => sum + Number(item.amount || 0), 0),
      paid_count: paid.length,
      paid_amount: paid.reduce((sum, item) => sum + Number(item.amount || 0), 0),
    }
    return { code: 200, msg: 'success', data: { total: matched.length, list: matched.slice(start, start + size), summary } }
  },

  /**
   * 商品分页列表
   * @param {Object} params - { page, size }
   * @returns {Object} { code, msg, data: { total, list } }
   */
  products: (params) => {
    const page = parseInt(params.page) || 1
    const size = parseInt(params.size) || 10
    const start = (page - 1) * size
    const filtered = products.slice(start, start + size)
    return { code: 200, msg: 'success', data: { total: products.length, list: filtered } }
  },

  /**
   * 图表趋势数据（订单趋势、收录趋势、各维度统计）
   * @returns {Object} { code, msg, data: { order_trend, index_trend, ... } }
   */
  charts: () => ({
    code: 200, msg: 'success',
    data: {
      order_trend: orderTrend,
      index_trend: indexTrend,
      orders_by_admin: adminNames.map(a => ({ admin_name: a, count: random(10, 40), amount: parseFloat((Math.random() * 5000 + 1000).toFixed(2)) })),
      sites_by_admin: adminNames.map(a => ({ admin_name: a, count: random(8, 15) })),
      sites_by_category: categories.map(c => ({ product_category: c, count: random(5, 15) })),
      products_by_category: categories.map(c => ({ custom_category: c, count: random(20, 50) })),
      products_by_domain: domains.map(d => ({ source_domain: d, count: random(20, 50) })),
      orders_by_currency: currencies.map(c => ({ currency: c, count: random(15, 40) })),
      order_summary: { 'COUNT(*)': 127, total_amount: 38542.75 },
    },
  }),

  /**
   * 站点 Google 收录指数历史
   * @param {Object} params - { domain }
   * @returns {Object} { code, msg, data: Array }
   */
  siteIndexHistory: (params) => {
    const domain = params.domain
    if (!domain) return { code: 400, msg: 'domain required', data: null }
    return { code: 200, msg: 'success', data: getSiteIndexHistory(domain) }
  },

  /**
   * 按域名查询关联订单
   * @param {Object} params - { domain, page, size, startDate, endDate }
   * @returns {Object} { code, msg, data: { total, list } }
   */
  ordersByDomain: (params) => {
    const domain = params.domain
    if (!domain) return { code: 400, msg: 'domain required', data: null }
    return { code: 200, msg: 'success', data: getOrdersByDomain(domain, params) }
  },
}
