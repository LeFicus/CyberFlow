<template>
  <el-container class="layout-shell">
    <el-aside
      :width="isMobile ? '268px' : (appStore.sidebarCollapsed ? '76px' : '248px')"
      class="app-sidebar"
      :class="{ 'is-mobile': isMobile, 'is-open': mobileNavOpen }"
    >
      <div class="brand" @click="router.push('/dashboard/overview')">
        <span class="brand-mark"><span>CF</span></span>
        <span v-if="isMobile || !appStore.sidebarCollapsed" class="brand-copy">
          <strong>CyberFlow</strong>
          <small>DATA OPERATIONS</small>
        </span>
        <button v-if="isMobile" class="sidebar-close" aria-label="关闭导航" @click.stop="mobileNavOpen = false">
          <el-icon><Close /></el-icon>
        </button>
      </div>

      <div v-if="isMobile || !appStore.sidebarCollapsed" class="workspace-label">工作空间</div>
      <el-menu
        :default-active="route.path"
        :default-openeds="openMenus"
        :unique-opened="true"
        :collapse="!isMobile && appStore.sidebarCollapsed"
        :collapse-transition="false"
        class="app-menu"
        @select="handleMenuSelect"
      >
        <template v-for="menu in menuTree" :key="menu.id">
          <el-sub-menu v-if="visibleChildren(menu).length" :index="String(menu.id)">
            <template #title>
              <el-icon><component :is="menu.icon || 'Menu'" /></el-icon>
              <span>{{ menu.menuName }}</span>
            </template>
            <el-menu-item v-for="child in visibleChildren(menu)" :key="child.id" :index="child.path">
              <span>{{ child.menuName }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else-if="canViewPath(menu.path)" :index="menu.path">
            <el-icon><component :is="menu.icon || 'Menu'" /></el-icon>
            <span>{{ menu.menuName }}</span>
          </el-menu-item>
        </template>
      </el-menu>

      <div v-if="isMobile || !appStore.sidebarCollapsed" class="sidebar-footer">
        <div class="status-dot"><span></span>采集服务正常</div>
        <div class="sidebar-version">CyberFlow v2.0 · 企业版</div>
      </div>
    </el-aside>
    <button
      v-if="isMobile && mobileNavOpen"
      class="sidebar-scrim"
      aria-label="关闭导航"
      @click="mobileNavOpen = false"
    ></button>

    <el-container class="content-shell">
      <el-header class="topbar">
        <div class="topbar-left">
          <button class="icon-button nav-toggle" aria-label="切换侧边栏" @click="handleSidebarToggle">
            <el-icon><Menu v-if="isMobile" /><Fold v-else-if="!appStore.sidebarCollapsed" /><Expand v-else /></el-icon>
          </button>
          <div class="breadcrumb-wrap">
            <span class="breadcrumb-root">{{ route.meta.section || 'CyberFlow' }}</span>
            <el-icon><ArrowRight /></el-icon>
            <span class="breadcrumb-current">{{ route.meta.title || '概览' }}</span>
          </div>
        </div>

        <div class="topbar-right">
          <div class="service-pill"><span></span>系统运行中</div>
          <button class="icon-button" aria-label="消息通知"><el-icon><Bell /></el-icon><i class="notification-dot"></i></button>
          <el-dropdown trigger="click" @command="handleUserCommand">
            <button class="profile-button">
              <el-avatar :size="34" class="profile-avatar">{{ avatarText }}</el-avatar>
              <span class="profile-copy"><strong>{{ displayName }}</strong><small>管理员</small></span>
              <el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="page-main">
        <section v-if="route.name !== 'DashboardOverview'" class="route-heading">
          <div>
            <p class="route-eyebrow">{{ route.meta.section || 'CYBERFLOW' }}</p>
            <h1>{{ route.meta.title }}</h1>
            <p class="route-description">{{ route.meta.description }}</p>
          </div>
          <span class="route-index">{{ routeIndex }}</span>
        </section>
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, ArrowRight, Bell, Close, Expand, Fold, Menu } from '@element-plus/icons-vue'
import { useMediaQuery } from '@vueuse/core'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const isMobile = useMediaQuery('(max-width: 860px)')
const mobileNavOpen = ref(false)
const openMenus = computed(() => {
  const parent = menuTree.value.find(menu => menu.children?.some(child => child.path === route.path))
  return parent ? [String(parent.id)] : []
})
const routePermissions = {
  '/dashboard/overview': 'dashboard:overview',
  '/dashboard/sites': 'dashboard:site:view',
  '/dashboard/orders': 'dashboard:order:view',
  '/dashboard/products': 'dashboard:product:view',
  '/indexing/sites': 'dashboard:site:view',
  '/indexing/builders': 'dashboard:site:view',
  '/indexing/servers': 'dashboard:site:view',
  '/categories': 'category:list',
  '/crawler/site': 'crawler:site:start',
  '/crawler/collect': 'crawler:collect:start',
  '/crawler/order': 'crawler:order:view',
  '/crawler/revenue-config': 'crawler:revenue:view',
  '/crawler/history': 'crawler:history:view',
  '/crawler/schedule': 'crawler:schedule:view',
  '/crawler/selector-template': 'selector:template:list',
  '/crawler/site-config': 'crawler:site:config:list',
  '/new-site': 'newsite:list',
  '/system/user': 'system:user:list',
  '/system/role': 'system:role:list',
  '/system/menu': 'system:menu:list',
  '/system/log': 'system:log:view',
}
const serverMenuPaths = computed(() => {
  const paths = new Set()
  const collect = nodes => (nodes || []).forEach(node => {
    if (node.path) paths.add(node.path)
    collect(node.children)
  })
  collect(userStore.userInfo?.menus)
  return paths
})
const canViewPath = path => {
  if (!path) return true
  if (serverMenuPaths.value.has(path)) return true
  const requiredPermission = routePermissions[path]
  return requiredPermission ? userStore.hasPermission(requiredPermission) : !userStore.userInfo?.menus?.length
}
const visibleChildren = menu => (menu.children || []).filter(child => child && canViewPath(child.path))

// The API can omit menus while a session is being refreshed. Keep navigation
// usable until the server returns the user's actual menu tree.
const fallbackMenus = [
  { id: 1, menuName: '数据看板', icon: 'DataBoard', children: [
    { id: 11, menuName: '概览', path: '/dashboard/overview' },
    { id: 12, menuName: '站点列表', path: '/dashboard/sites' },
    { id: 13, menuName: '订单列表', path: '/dashboard/orders' },
    { id: 14, menuName: '商品列表', path: '/dashboard/products' },
  ] },
  { id: 6, menuName: '收录数据', icon:'DataLine', children:[
    {id:16, menuName:'站点明细',path:'/indexing/sites'},
    {id:68, menuName:'建站者汇总',path:'/indexing/builders'},
    {id:69, menuName:'服务器汇总',path:'/indexing/servers'},
  ] },
  { id:70, menuName:'自定义分类',path:'/categories',icon:'CollectionTag',children:[] },
  { id: 2, menuName: '数据同步', icon: 'RefreshRight', children: [
    { id: 21, menuName: '站点爬虫', path: '/crawler/site' },
    { id: 22, menuName: '收录统计', path: '/crawler/collect' },
    { id: 23, menuName: '订单爬虫', path: '/crawler/order' },
    { id: 35, menuName: '收入参数', path: '/crawler/revenue-config' },
    { id: 24, menuName: '任务历史', path: '/crawler/history' },
    { id: 28, menuName: '计划任务', path: '/crawler/schedule' },
  ] },
  { id: 4, menuName: '商品采集', icon: 'Goods', children: [
    { id: 41, menuName: '数据源站点', path: '/crawler/site-config' },
    { id: 42, menuName: '选择器模板', path: '/crawler/selector-template' },
  ] },
  { id: 5, menuName: '站点建设', icon: 'Shop', children: [
    { id: 63, menuName: '新站点管理', path: '/new-site' },
  ] },
  { id: 3, menuName: '系统管理', icon: 'Setting', children: [
    { id: 31, menuName: '用户管理', path: '/system/user' },
    { id: 32, menuName: '角色管理', path: '/system/role' },
    { id: 33, menuName: '菜单管理', path: '/system/menu' },
    { id: 34, menuName: '操作日志', path: '/system/log' },
  ] },
]

const menuTree = computed(() => {
  const rawSource = userStore.userInfo?.menus?.length ? userStore.userInfo.menus : fallbackMenus
  const sanitizeMenu = menu => ({
    ...menu,
    children: menu.path
      ? []
      : (menu.children || []).filter(child => child.menuType !== 2 && child.menu_type !== 2 && canViewPath(child.path)).map(sanitizeMenu),
  })
  const dynamicSource = rawSource.map(sanitizeMenu)
  const source = fallbackMenus.map(fallbackRoot => {
    const dynamicRoot = dynamicSource.find(menu => menu.id === fallbackRoot.id || menu.menuName === fallbackRoot.menuName)
    if (!dynamicRoot) return sanitizeMenu(fallbackRoot)

    const dynamicChildren = dynamicRoot.children || []
    const mergedChildren = fallbackRoot.children.map(fallbackChild => {
      const dynamicChild = dynamicChildren.find(child => child.id === fallbackChild.id || child.path === fallbackChild.path)
      return dynamicChild ? { ...fallbackChild, ...dynamicChild, children: [] } : { ...fallbackChild, children: [] }
    })
    dynamicChildren.forEach(child => {
      if (child.path && !mergedChildren.some(item => item.path === child.path)) mergedChildren.push(child)
    })
    return { ...fallbackRoot, ...dynamicRoot, children: mergedChildren }
  })
  dynamicSource.forEach(dynamicRoot => {
    if (!source.some(menu => menu.id === dynamicRoot.id || menu.menuName === dynamicRoot.menuName)) source.push(dynamicRoot)
  })

  const crawlerMenu = source.find(menu => menu.id === 2 || menu.menuName === '爬虫管理')
  if (!crawlerMenu || crawlerMenu.menuName === '数据同步') {
    return source.filter(menu => menu.children?.length || ![1, 2, 3, 4, 6].includes(Number(menu.id)))
  }

  const crawlerChildren = crawlerMenu.children || []
  const findCrawlerItem = (path, fallback) => crawlerChildren.find(item => item.path === path) || fallback
  const fallbackSync = fallbackMenus.find(menu => menu.id === 2)
  const fallbackProduct = fallbackMenus.find(menu => menu.id === 4)
  const syncPaths = ['/crawler/site', '/crawler/collect', '/crawler/order', '/crawler/history', '/crawler/schedule', '/crawler/revenue-config']
  const productPaths = ['/crawler/site-config', '/crawler/selector-template']
  const groupedMenus = source.filter(menu => menu !== crawlerMenu && menu.id !== 4 && menu.menuName !== '商品采集')
  const crawlerIndex = Math.max(0, source.indexOf(crawlerMenu))

  groupedMenus.splice(crawlerIndex, 0,
    {
      id: 'data-sync', menuName: '数据同步', icon: 'RefreshRight',
      children: syncPaths
        .filter(path => canViewPath(path))
        .map(path => findCrawlerItem(path, fallbackSync.children.find(item => item.path === path))),
    },
    {
      id: 'product-crawl', menuName: '商品采集', icon: 'Goods',
      children: productPaths
        .filter(path => canViewPath(path))
        .map(path => findCrawlerItem(path, fallbackProduct.children.find(item => item.path === path))),
    },
  )
  return groupedMenus
})
const displayName = computed(() => userStore.userInfo?.nickname || userStore.userInfo?.username || '管理员')
const avatarText = computed(() => displayName.value.slice(0, 1))
const routeIndex = computed(() => {
  const routes = router.getRoutes().filter(item => item.meta?.title && item.name !== 'Login')
  const index = routes.findIndex(item => item.name === route.name)
  return String(Math.max(0, index) + 1).padStart(2, '0')
})

onMounted(async () => {
  if (userStore.token) await userStore.refreshUserInfo().catch(() => {})
})

function handleUserCommand(command) {
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}

function handleSidebarToggle() {
  if (isMobile.value) {
    mobileNavOpen.value = !mobileNavOpen.value
    return
  }
  appStore.toggleSidebar()
}

async function handleMenuSelect(path) {
  // Do not rely on Element Plus' built-in router integration here. The menu
  // is rendered from a server-side tree while the actual pages are static
  // nested routes; explicit navigation keeps both in sync after refreshes.
  if (path && path !== route.path) {
    await router.push(path)
  }
  if (isMobile.value) mobileNavOpen.value = false
}
</script>

<style scoped>
.layout-shell { min-height: 100vh; background: var(--cf-canvas); }
.app-sidebar {
  position: relative; display: flex; flex-direction: column; overflow: hidden;
  z-index: 40; background: #10192d; color: #aab7cf; transition: width .25s ease, transform .25s ease; flex-shrink: 0;
}
.app-sidebar::before { position: absolute; top: -130px; left: 32px; width: 280px; height: 280px; border-radius: 50%; pointer-events: none; background: radial-gradient(circle, #556ef72b 0, transparent 70%); content: ''; }
.brand { position: relative; height: 78px; display: flex; align-items: center; gap: 12px; padding: 0 22px; cursor: pointer; }
.brand-mark { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 10px; color: #fff; font-size: 11px; font-weight: 800; letter-spacing: -.04em; background: linear-gradient(135deg, #5b7cfa, #7657ef); box-shadow: 0 8px 20px #586af055; }
.brand-copy { display: grid; gap: 3px; white-space: nowrap; }
.brand-copy strong { color: #fff; font-size: 17px; letter-spacing: -.02em; }
.brand-copy small { color: #7182a3; font-size: 8px; font-weight: 700; letter-spacing: .14em; }
.workspace-label { padding: 12px 24px 8px; color: #627292; font-size: 10px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.app-menu { flex: 1; border: 0; padding: 4px 12px; background: transparent; }
.app-menu :deep(.el-menu-item), .app-menu :deep(.el-sub-menu__title) { height: 46px; margin: 4px 0; border-radius: 10px; color: #9aaaca; font-size: 13px; transition: all .2s ease; }
.app-menu :deep(.el-menu-item .el-icon), .app-menu :deep(.el-sub-menu__title .el-icon) { margin-right: 11px; color: #7182a3; font-size: 17px; }
.app-menu :deep(.el-menu-item:hover), .app-menu :deep(.el-sub-menu__title:hover) { color: #fff; background: #1b2946; }
.app-menu :deep(.el-menu-item.is-active) { color: #fff; background: linear-gradient(90deg, #3d62e9, #536ff1); box-shadow: 0 7px 18px #395fe94d; }
.app-menu :deep(.el-menu-item.is-active .el-icon) { color: #fff; }
.app-menu :deep(.el-sub-menu .el-menu) { background: transparent; }
.app-menu :deep(.el-sub-menu .el-menu-item) { min-width: auto; padding-left: 54px !important; font-size: 12px; }
.sidebar-footer { padding: 18px 22px 24px; color: #687996; font-size: 11px; }
.status-dot { display: flex; align-items: center; gap: 7px; color: #8fa1c1; }
.status-dot span, .service-pill span { width: 6px; height: 6px; border-radius: 50%; background: #36d399; box-shadow: 0 0 0 4px #36d3991f; }
.sidebar-version { margin-top: 10px; color: #52627e; font-size: 10px; }
.sidebar-close { display: grid; width: 34px; height: 34px; margin-left: auto; place-items: center; border: 0; border-radius: 9px; color: #8fa0bd; background: #ffffff0a; cursor: pointer; }
.sidebar-scrim { position: fixed; z-index: 35; inset: 0; width: 100%; height: 100%; border: 0; background: #10192d80; backdrop-filter: blur(3px); cursor: pointer; }
.content-shell { min-width: 0; }
.topbar { position: sticky; z-index: 20; top: 0; display: flex; align-items: center; justify-content: space-between; height: 78px; padding: 0 34px; border-bottom: 1px solid #e9ecf2; background: #ffffffeb; backdrop-filter: blur(16px); }
.topbar-left, .topbar-right, .breadcrumb-wrap, .profile-button { display: flex; align-items: center; }
.topbar-left { gap: 20px; }
.breadcrumb-wrap { gap: 9px; color: #a1aec1; font-size: 12px; }
.breadcrumb-wrap .el-icon { font-size: 12px; }
.breadcrumb-root { color: #98a5b8; }
.breadcrumb-current { color: #25344f; font-weight: 700; }
.icon-button { position: relative; display: grid; width: 36px; height: 36px; place-items: center; border: 0; border-radius: 9px; color: #75839a; background: transparent; cursor: pointer; transition: background .2s, color .2s; }
.icon-button:hover { color: #3563e9; background: #f0f4ff; }
.icon-button .notification-dot { position: absolute; top: 7px; right: 8px; width: 5px; height: 5px; border: 1px solid #fff; border-radius: 50%; background: #f05d72; }
.topbar-right { gap: 12px; }
.service-pill { display: flex; align-items: center; gap: 8px; margin-right: 8px; padding: 7px 11px; border: 1px solid #e2f4ec; border-radius: 999px; color: #5d8b75; background: #f4fcf8; font-size: 11px; }
.profile-button { gap: 9px; padding: 3px 0 3px 8px; border: 0; background: transparent; cursor: pointer; }
.profile-avatar { color: #fff; background: linear-gradient(135deg, #536ff1, #7c63e9); font-size: 13px; font-weight: 700; }
.profile-copy { display: grid; gap: 1px; text-align: left; }
.profile-copy strong { color: #25344f; font-size: 12px; }
.profile-copy small { color: #98a5b8; font-size: 10px; }
.profile-button > .el-icon { color: #9ca8b9; font-size: 12px; }
.page-main { min-width: 0; padding: 30px 34px 48px; overflow-y: auto; }
.route-heading { display: flex; max-width: 1440px; align-items: flex-end; justify-content: space-between; margin: 0 auto 22px; }
.route-heading p, .route-heading h1 { margin: 0; }
.route-eyebrow { margin-bottom: 7px !important; color: var(--cf-blue); font-size: 9px; font-weight: 800; letter-spacing: .18em; text-transform: uppercase; }
.route-heading h1 { color: var(--cf-ink); font-size: 27px; letter-spacing: -.045em; }
.route-description { margin-top: 7px !important; color: var(--cf-muted); font-size: 12px; }
.route-index { color: #e1e5ed; font-size: 44px; font-weight: 760; letter-spacing: -.06em; line-height: .9; }
.page-fade-enter-active, .page-fade-leave-active { transition: opacity .18s ease, transform .18s ease; }
.page-fade-enter-from { opacity: 0; transform: translateY(5px); }
.page-fade-leave-to { opacity: 0; transform: translateY(-3px); }
@media (max-width: 860px) {
  .app-sidebar.is-mobile { position: fixed; top: 0; bottom: 0; left: 0; width: 268px !important; box-shadow: 20px 0 50px #0d15294d; transform: translateX(-102%); }
  .app-sidebar.is-mobile.is-open { transform: translateX(0); }
  .topbar { height: 68px; padding: 0 16px; }
  .service-pill, .profile-copy { display: none; }
  .page-main { padding: 23px 18px 36px; }
}
@media (max-width: 560px) {
  .breadcrumb-root, .breadcrumb-wrap > .el-icon { display: none; }
  .topbar-right { gap: 4px; }
  .route-heading { align-items: flex-start; }
  .route-heading h1 { font-size: 24px; }
  .route-description { max-width: 270px; line-height: 1.6; }
  .route-index { display: none; }
}
</style>
