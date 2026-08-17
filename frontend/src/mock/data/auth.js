/**
 * @fileoverview 认证模块 Mock 数据
 * @description 提供登录和获取用户信息的模拟响应数据。
 *              登录接口仅允许 admin/admin123 账号；
 *              返回的 userInfo 包含角色、权限列表和侧边栏菜单树结构。
 */
export default {
  /**
   * 登录 Mock 接口
   * 返回 JWT Token 及包含角色、权限、菜单的用户信息
   * @returns {Object} { code, msg, data: { token, userInfo } }
   */
  login: () => ({
    code: 200,
    msg: 'success',
    data: {
      token: 'eyJhbGciOiJIUzI1NiJ9.mock-jwt-token-for-admin',
      userInfo: {
        id: 1,
        username: 'admin',
        nickname: '系统管理员',
        roles: ['ROLE_ADMIN', 'ROLE_OPERATOR'],
        permissions: [
          'dashboard:overview', 'dashboard:site:view', 'dashboard:order:view', 'dashboard:product:view',
          'crawler:site:start', 'crawler:collect:start', 'crawler:order:start',
          'crawler:schedule:view', 'crawler:schedule:update', 'crawler:schedule:trigger',
          'crawler:site:config:list', 'crawler:site:config:create', 'crawler:site:config:crawl', 'crawler:site:config:delete',
          'selector:template:list', 'selector:template:create', 'selector:template:update', 'selector:template:delete',
          'system:user:list', 'system:user:create', 'system:user:update', 'system:user:delete', 'system:user:assign',
          'system:role:list', 'system:role:create', 'system:role:update', 'system:role:delete', 'system:role:assign',
          'system:menu:list', 'system:menu:create', 'system:menu:update', 'system:menu:delete',
          'system:log:view',
        ],
        menus: [
          {
            id: 1, parentId: 0, menuName: '数据看板', menuType: 0, icon: 'DataBoard',
            children: [
              { id: 11, parentId: 1, menuName: '概览', menuType: 1, path: '/dashboard/overview', component: 'dashboard/Overview' },
              { id: 12, parentId: 1, menuName: '站点列表', menuType: 1, path: '/dashboard/sites', component: 'dashboard/SiteList' },
              { id: 13, parentId: 1, menuName: '订单列表', menuType: 1, path: '/dashboard/orders', component: 'dashboard/OrderList' },
              // { id: 14, parentId: 1, menuName: '商品列表', menuType: 1, path: '/dashboard/products', component: 'dashboard/ProductList' },
            ],
          },
          {
            id: 2, parentId: 0, menuName: '数据同步', menuType: 0, icon: 'RefreshRight',
            children: [
              { id: 21, parentId: 2, menuName: '站点爬虫', menuType: 1, path: '/crawler/site', component: 'crawler/SiteCrawler' },
              { id: 22, parentId: 2, menuName: '收录统计', menuType: 1, path: '/crawler/collect', component: 'crawler/CollectCrawler' },
              { id: 23, parentId: 2, menuName: '订单爬虫', menuType: 1, path: '/crawler/order', component: 'crawler/OrderCrawler' },
              { id: 24, parentId: 2, menuName: '任务历史', menuType: 1, path: '/crawler/history', component: 'crawler/TaskHistory' },
              { id: 28, parentId: 2, menuName: '计划任务', menuType: 1, path: '/crawler/schedule', component: 'crawler/ScheduleTask', perms: 'crawler:schedule:view' },
            ],
          },
          {
            id: 4, parentId: 0, menuName: '商品采集', menuType: 0, icon: 'Goods',
            children: [
              { id: 41, parentId: 4, menuName: '数据源站点', menuType: 1, path: '/crawler/site-config', component: 'crawler/SiteConfig' },
              { id: 42, parentId: 4, menuName: '选择器模板', menuType: 1, path: '/crawler/selector-template', component: 'crawler/SelectorTemplate' },
            ],
          },
          {
            id: 3, parentId: 0, menuName: '系统管理', menuType: 0, icon: 'Setting',
            children: [
              { id: 31, parentId: 3, menuName: '用户管理', menuType: 1, path: '/system/user', component: 'system/UserList' },
              { id: 32, parentId: 3, menuName: '角色管理', menuType: 1, path: '/system/role', component: 'system/RoleList' },
              { id: 33, parentId: 3, menuName: '菜单管理', menuType: 1, path: '/system/menu', component: 'system/MenuTree' },
              { id: 34, parentId: 3, menuName: '操作日志', menuType: 1, path: '/system/log', component: 'system/OperationLog' },
            ],
          },
        ],
      },
    },
  }),
  /**
   * 获取用户信息 Mock 接口
   * 返回当前用户的基本信息、角色和权限（不含菜单树）
   * @returns {Object} { code, msg, data: { id, username, nickname, roles, permissions, menus } }
   */
  userinfo: () => ({
    code: 200,
    msg: 'success',
    data: {
      id: 1,
      username: 'admin',
      nickname: '系统管理员',
      roles: ['ROLE_ADMIN'],
      permissions: ['dashboard:overview', 'dashboard:site:view', 'dashboard:order:view', 'dashboard:product:view', 'crawler:site:start', 'crawler:collect:start', 'crawler:order:start', 'crawler:schedule:view', 'crawler:schedule:update', 'crawler:schedule:trigger', 'crawler:site:config:list', 'crawler:site:config:create', 'crawler:site:config:crawl', 'crawler:site:config:delete', 'selector:template:list', 'selector:template:create', 'selector:template:update', 'selector:template:delete', 'system:user:list', 'system:user:create', 'system:user:update', 'system:user:delete', 'system:user:assign', 'system:role:list', 'system:role:create', 'system:role:update', 'system:role:delete', 'system:role:assign', 'system:menu:list', 'system:menu:create', 'system:menu:update', 'system:menu:delete', 'system:log:view'],
      menus: [],
    },
  }),
}
