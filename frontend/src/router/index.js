import { createRouter, createWebHistory } from 'vue-router'

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
      { path: 'dashboard/overview', name: 'DashboardOverview', component: () => import('@/views/dashboard/Overview.vue'), meta: { title: '概览', perm: 'dashboard:overview' } },
      { path: 'dashboard/sites', name: 'DashboardSites', component: () => import('@/views/dashboard/SiteList.vue'), meta: { title: '站点列表', perm: 'dashboard:site:view' } },
      { path: 'dashboard/orders', name: 'DashboardOrders', component: () => import('@/views/dashboard/OrderList.vue'), meta: { title: '订单列表', perm: 'dashboard:order:view' } },
      // { path: 'dashboard/products', name: 'DashboardProducts', component: () => import('@/views/dashboard/ProductList.vue'), meta: { title: '商品列表', perm: 'dashboard:product:view' } },
      { path: 'crawler/site', name: 'CrawlerSite', component: () => import('@/views/crawler/SiteCrawler.vue'), meta: { title: '站点爬虫', perm: 'crawler:site:start' } },
      { path: 'crawler/collect', name: 'CrawlerCollect', component: () => import('@/views/crawler/CollectCrawler.vue'), meta: { title: '收录统计', perm: 'crawler:collect:start' } },
      { path: 'crawler/order', name: 'CrawlerOrder', component: () => import('@/views/crawler/OrderCrawler.vue'), meta: { title: '订单爬虫', perm: 'crawler:order:start' } },
      { path: 'crawler/history', name: 'CrawlerHistory', component: () => import('@/views/crawler/TaskHistory.vue'), meta: { title: '任务历史' } },
      { path: 'crawler/selector-template', name: 'SelectorTemplate', component: () => import('@/views/crawler/SelectorTemplate.vue'), meta: { title: '选择器模板' } },
      { path: 'crawler/site-config', name: 'CrawlerSiteConfig', component: () => import('@/views/crawler/SiteConfig.vue'), meta: { title: '站点注册' } },
      { path: 'system/user', name: 'SystemUser', component: () => import('@/views/system/UserList.vue'), meta: { title: '用户管理', perm: 'system:user:list' } },
      { path: 'system/role', name: 'SystemRole', component: () => import('@/views/system/RoleList.vue'), meta: { title: '角色管理', perm: 'system:role:list' } },
      { path: 'system/menu', name: 'SystemMenu', component: () => import('@/views/system/MenuTree.vue'), meta: { title: '菜单管理', perm: 'system:menu:list' } },
      { path: 'system/log', name: 'SystemLog', component: () => import('@/views/system/OperationLog.vue'), meta: { title: '操作日志', perm: 'system:log:view' } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard/overview' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
