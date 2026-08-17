/**
 * @fileoverview Vue Router 路由配置
 * @description 定义应用全部路由规则，包括：
 *              - /login：登录页面（无需认证）
 *              -  / ：主布局容器，包含所有业务子路由（仪表盘、爬虫管理、系统管理）
 *              - 未匹配路径（404）重定向到概览页
 *              使用 createWebHistory 模式实现无 # 的 URL。
 *              通过 beforeEach 导航守卫进行登录态校验。
 */

import { createRouter, createWebHistory } from 'vue-router'

/**
 * 路由表配置
 * 所有需要登录的业务路由均作为主路由 '/' 的子路由，配合 layout 组件渲染
 */
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/',
    component: () => import('@/views/layout/index.vue'),
    redirect: '/dashboard/overview',
    children: [
      { path: 'dashboard/overview', name: 'DashboardOverview', component: () => import('@/views/dashboard/Overview.vue'), meta: { title: '概览', section: '数据看板', description: '站点、订单与采集任务的实时运营视图', perm: 'dashboard:overview' } },
      { path: 'dashboard/sites', name: 'DashboardSites', component: () => import('@/views/dashboard/SiteList.vue'), meta: { title: '站点列表', section: '数据看板', description: '查看站点信息、关联订单与收录趋势', perm: 'dashboard:site:view' } },
      { path: 'dashboard/orders', name: 'DashboardOrders', component: () => import('@/views/dashboard/OrderList.vue'), meta: { title: '订单列表', section: '数据看板', description: '筛选并追踪多站点订单数据', perm: 'dashboard:order:view' } },
      { path: 'dashboard/products', name: 'DashboardProducts', component: () => import('@/views/dashboard/ProductList.vue'), meta: { title: '商品列表', section: '数据看板', description: '集中管理采集商品与跨平台导出', perm: 'dashboard:product:view' } },
      { path: 'crawler/site', name: 'CrawlerSite', component: () => import('@/views/crawler/SiteCrawler.vue'), meta: { title: '站点爬虫', section: '数据同步', description: '配置站点基础信息同步策略与执行计划', perm: 'crawler:site:start' } },
      { path: 'crawler/collect', name: 'CrawlerCollect', component: () => import('@/views/crawler/CollectCrawler.vue'), meta: { title: '收录统计', section: '数据同步', description: '同步站点搜索引擎收录数据', perm: 'crawler:collect:start' } },
      { path: 'crawler/order', name: 'CrawlerOrder', component: () => import('@/views/crawler/OrderCrawler.vue'), meta: { title: '订单爬虫', section: '数据同步', description: '配置订单同步来源与增量规则', perm: 'crawler:order:view' } },
      { path: 'crawler/history', name: 'CrawlerHistory', component: () => import('@/views/crawler/TaskHistory.vue'), meta: { title: '任务历史', section: '数据同步', description: '统一追踪同步与商品采集任务的执行结果', perm: 'crawler:history:view' } },
      { path: 'crawler/schedule', name: 'CrawlerSchedule', component: () => import('@/views/crawler/ScheduleTask.vue'), meta: { title: '计划任务', section: '数据同步', description: '统一管理爬虫任务的自动执行计划', perm: 'crawler:schedule:view' } },
      { path: 'crawler/selector-template', name: 'SelectorTemplate', component: () => import('@/views/crawler/SelectorTemplate.vue'), meta: { title: '选择器模板', section: '商品采集', description: '维护不同电商平台的商品字段提取规则', perm: 'selector:template:list' } },
      { path: 'crawler/site-config', name: 'CrawlerSiteConfig', component: () => import('@/views/crawler/SiteConfig.vue'), meta: { title: '数据源站点', section: '商品采集', description: '添加商品来源数据源并绑定选择器模板', perm: 'crawler:site:config:list' } },
      { path: 'system/user', name: 'SystemUser', component: () => import('@/views/system/UserList.vue'), meta: { title: '用户管理', section: '系统管理', description: '管理成员账号、状态与角色', perm: 'system:user:list' } },
      { path: 'system/role', name: 'SystemRole', component: () => import('@/views/system/RoleList.vue'), meta: { title: '角色管理', section: '系统管理', description: '配置角色及对应功能权限', perm: 'system:role:list' } },
      { path: 'system/menu', name: 'SystemMenu', component: () => import('@/views/system/MenuTree.vue'), meta: { title: '菜单管理', section: '系统管理', description: '维护后台导航结构与权限标识', perm: 'system:menu:list' } },
      { path: 'system/log', name: 'SystemLog', component: () => import('@/views/system/OperationLog.vue'), meta: { title: '操作日志', section: '系统管理', description: '审计用户操作与系统访问记录', perm: 'system:log:view' } },
    ],
  },
  // 所有未匹配的路径重定向到概览页
  { path: '/:pathMatch(.*)*', redirect: '/dashboard/overview' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.onError((error, to) => {
  // Lazy chunks can be stale after a deployment. Retry once; otherwise a
  // permanent reload loop would hide the real error and leave router-view blank.
  console.error(`[router] failed to load ${to?.fullPath || ''}`, error)
  if (to?.fullPath) {
    const retryKey = `router-chunk-retry:${to.fullPath}`
    if (!sessionStorage.getItem(retryKey)) {
      sessionStorage.setItem(retryKey, '1')
      window.location.reload()
    } else {
      sessionStorage.removeItem(retryKey)
      router.replace('/dashboard/overview')
    }
  }
})

/**
 * 全局导航守卫 — 登录态校验
 * 在每次路由切换前检查 token 是否存在；
 * 未登录时除 /login 外均强制跳转至登录页
 * @param {Object} to - 目标路由对象
 * @param {Object} from - 来源路由对象
 * @param {Function} next - 放行/重定向函数
 */
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else if (to.meta?.perm) {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    const permissions = userInfo.permissions || []
    if (permissions.length && !permissions.includes(to.meta.perm)) {
      next('/dashboard/overview')
      return
    }
    next()
  } else {
    next()
  }
})

export default router
