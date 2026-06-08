function random(min, max) { return Math.floor(Math.random() * (max - min + 1)) + min }
function datetime(daysAgo) { const d = new Date(); d.setDate(d.getDate() - daysAgo); return d.toISOString().slice(0, 19).replace('T', ' ') }

const adminNames = ['张三', '李四', '王五', '赵六', '钱七']
const themeNames = ['Default', 'Electro', 'Fashion', 'Minimal', 'Organic']
const categories = ['服装', '电子产品', '家居', '美妆', '运动', '食品']
const domains = ['shop1.com', 'beauty-store.com', 'tech-gadgets.com', 'home-life.com', 'fashion-hub.com', 'sports-gear.com']
const currencies = ['USD', 'EUR', 'GBP', 'JPY']

// 生成站点列表
const sites = Array.from({ length: 53 }, (_, i) => ({
  id: i + 1,
  username: 'admin',
  site_domain: domains[i % domains.length] + (i > 5 ? `/${i}` : ''),
  admin_name: adminNames[i % adminNames.length],
  theme_name: themeNames[i % themeNames.length],
  product_category: categories[i % categories.length],
  created_at: datetime(random(1, 90)),
}))

// 生成订单列表
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

// 生成商品列表
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

// 生成收录趋势
const indexTrend = Array.from({ length: 30 }, (_, i) => ({
  date: datetime(29 - i).slice(0, 10),
  total_index: random(500, 2000) + i * 10,
  total_products: random(100, 500) + i * 5,
  site_count: 53,
}))

// 生成订单趋势
const orderTrend = Array.from({ length: 30 }, (_, i) => ({
  date: datetime(29 - i).slice(0, 10),
  count: random(3, 15),
  amount: parseFloat((Math.random() * 3000 + 500).toFixed(2)),
}))

// 生成每个站点的收录历史（90 天）
const siteIndexCache = {}
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

// 按域名获取订单
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
  overview: () => ({
    code: 200, msg: 'success',
    data: {
      total_sites: 53,
      total_orders: 127,
      total_products: 215,
      today_orders: random(3, 12),
      today_amount: parseFloat((Math.random() * 2000 + 200).toFixed(2)),
    },
  }),
  sites: (params) => {
    const page = parseInt(params.page) || 1
    const size = parseInt(params.size) || 10
    const start = (page - 1) * size
    const filtered = sites.slice(start, start + size)
    return { code: 200, msg: 'success', data: { total: sites.length, list: filtered } }
  },
  orders: (params) => {
    const page = parseInt(params.page) || 1
    const size = parseInt(params.size) || 10
    const start = (page - 1) * size
    const filtered = orders.slice(start, start + size)
    return { code: 200, msg: 'success', data: { total: orders.length, list: filtered } }
  },
  products: (params) => {
    const page = parseInt(params.page) || 1
    const size = parseInt(params.size) || 10
    const start = (page - 1) * size
    const filtered = products.slice(start, start + size)
    return { code: 200, msg: 'success', data: { total: products.length, list: filtered } }
  },
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
  // 站点收录历史
  siteIndexHistory: (params) => {
    const domain = params.domain
    if (!domain) return { code: 400, msg: 'domain required', data: null }
    return { code: 200, msg: 'success', data: getSiteIndexHistory(domain) }
  },
  // 按域名获取订单
  ordersByDomain: (params) => {
    const domain = params.domain
    if (!domain) return { code: 400, msg: 'domain required', data: null }
    return { code: 200, msg: 'success', data: getOrdersByDomain(domain, params) }
  },
}
