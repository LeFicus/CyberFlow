import Mock from 'mockjs'
import authData from './data/auth'
import dashboardData from './data/dashboard'
import crawlerData from './data/crawler'
import systemData from './data/system'

Mock.setup({ timeout: '200-400' })

function paramParser(url) {
  // 将 URL 的 query 解析为对象
  const q = url.indexOf('?')
  if (q < 0) return {}
  const obj = {}
  url.substring(q + 1).split('&').forEach(p => {
    const [k, v] = p.split('=')
    obj[decodeURIComponent(k)] = decodeURIComponent(v || '')
  })
  return obj
}

// ========== Auth ==========
Mock.mock(/\/admin\/auth\/login$/, 'post', (options) => {
  const body = JSON.parse(options.body || '{}')
  if (body.username === 'admin' && body.password === 'admin123') {
    return authData.login()
  }
  return { code: 401, msg: '用户名或密码错误', data: null }
})
Mock.mock(/\/admin\/auth\/userinfo/, 'get', authData.userinfo)

// ========== Dashboard ==========
Mock.mock(/\/admin\/dashboard\/overview/, 'get', dashboardData.overview)
Mock.mock(/\/admin\/dashboard\/charts/, 'get', dashboardData.charts)
Mock.mock(/\/admin\/dashboard\/sites(\?|$)/, 'get', (options) => dashboardData.sites(paramParser(options.url)))
Mock.mock(/\/admin\/dashboard\/site-index-history(\?|$)/, 'get', (options) => dashboardData.siteIndexHistory(paramParser(options.url)))
Mock.mock(/\/admin\/dashboard\/orders-by-domain(\?|$)/, 'get', (options) => dashboardData.ordersByDomain(paramParser(options.url)))
Mock.mock(/\/admin\/dashboard\/orders(\?|$)/, 'get', (options) => dashboardData.orders(paramParser(options.url)))
Mock.mock(/\/admin\/dashboard\/products(\?|$)/, 'get', (options) => dashboardData.products(paramParser(options.url)))

// ========== Crawler ==========
Mock.mock(/\/admin\/crawler\/site\/start/, 'post', crawlerData.triggerSiteCrawler)
Mock.mock(/\/admin\/crawler\/site\/collect/, 'post', crawlerData.triggerCollectCrawler)
Mock.mock(/\/admin\/crawler\/order\/start/, 'post', crawlerData.triggerOrderCrawler)
Mock.mock(/\/admin\/crawler\/status\//, 'get', crawlerData.status)
Mock.mock(/\/admin\/crawler\/tasks/, 'get', crawlerData.tasks)

// ========== System: User ==========
Mock.mock(/\/admin\/system\/user(\?|$)/, 'get', systemData.userList)
Mock.mock(/\/admin\/system\/user$/, 'post', (options) => systemData.userCreate(JSON.parse(options.body || '{}')))
Mock.mock(/\/admin\/system\/user\/\d+$/, 'put', (options) => {
  const id = options.url.match(/\/admin\/system\/user\/(\d+)/)[1]
  return systemData.userUpdate(id, JSON.parse(options.body || '{}'))
})
Mock.mock(/\/admin\/system\/user\/\d+$/, 'delete', (options) => {
  const id = options.url.match(/\/admin\/system\/user\/(\d+)/)[1]
  return systemData.userDelete(id)
})
Mock.mock(/\/admin\/system\/user\/\d+\/roles/, 'put', { code: 200, msg: 'success', data: null })

// ========== System: Role ==========
Mock.mock(/\/admin\/system\/role(\?|$)/, 'get', systemData.roleList)
Mock.mock(/\/admin\/system\/role\/all/, 'get', systemData.roleAll)
Mock.mock(/\/admin\/system\/role\/\d+\/menus/, 'put', { code: 200, msg: 'success', data: null })

// ========== System: Menu ==========
Mock.mock(/\/admin\/system\/menu\/tree/, 'get', systemData.menuTree)

// ========== System: Log ==========
Mock.mock(/\/admin\/system\/log(\?|$)/, 'get', (options) => systemData.logList(paramParser(options.url)))
