const now = '2026-04-29 12:00:00'

let users = [
  { id: 1, username: 'admin', password: '', nickname: '系统管理员', email: 'admin@cyberflow.com', status: 1, created_at: '2026-01-01 00:00:00' },
  { id: 2, username: 'operator', password: '', nickname: '运营人员', email: 'op@cyberflow.com', status: 1, created_at: '2026-03-15 10:30:00' },
]

const roles = [
  { id: 1, role_name: '超级管理员', role_code: 'ROLE_ADMIN', description: '拥有所有权限', status: 1, created_at: now },
  { id: 2, role_name: '运营人员', role_code: 'ROLE_OPERATOR', description: '可查看数据看板、触发爬虫', status: 1, created_at: now },
]

const menus = [
  { id: 1, parent_id: 0, menu_name: '数据看板', menu_type: 0, perms: null, path: '/dashboard', icon: 'DataBoard', sort_order: 1, status: 1, children: [] },
  { id: 2, parent_id: 0, menu_name: '爬虫管理', menu_type: 0, perms: null, path: '/crawler', icon: 'Cpu', sort_order: 2, status: 1, children: [] },
  { id: 3, parent_id: 0, menu_name: '系统管理', menu_type: 0, perms: null, path: '/system', icon: 'Setting', sort_order: 3, status: 1, children: [] },
]

// 确保 children 已填充
const childMenus = [
  { id: 11, parent_id: 1, menu_name: '概览', menu_type: 1, perms: 'dashboard:overview', path: '/dashboard/overview', icon: null, sort_order: 1, status: 1 },
  { id: 12, parent_id: 1, menu_name: '站点列表', menu_type: 1, perms: 'dashboard:site:view', path: '/dashboard/sites', icon: null, sort_order: 2, status: 1 },
  { id: 13, parent_id: 1, menu_name: '订单列表', menu_type: 1, perms: 'dashboard:order:view', path: '/dashboard/orders', icon: null, sort_order: 3, status: 1 },
  // { id: 14, parent_id: 1, menu_name: '商品列表', menu_type: 1, perms: 'dashboard:product:view', path: '/dashboard/products', icon: null, sort_order: 4, status: 1 },
  { id: 21, parent_id: 2, menu_name: '站点爬虫', menu_type: 1, perms: 'crawler:site:start', path: '/crawler/site', icon: null, sort_order: 1, status: 1 },
  { id: 22, parent_id: 2, menu_name: '收录统计', menu_type: 1, perms: 'crawler:collect:start', path: '/crawler/collect', icon: null, sort_order: 2, status: 1 },
  { id: 23, parent_id: 2, menu_name: '订单爬虫', menu_type: 1, perms: 'crawler:order:start', path: '/crawler/order', icon: null, sort_order: 3, status: 1 },
  { id: 24, parent_id: 2, menu_name: '任务历史', menu_type: 1, perms: null, path: '/crawler/history', icon: null, sort_order: 4, status: 1 },
  { id: 31, parent_id: 3, menu_name: '用户管理', menu_type: 1, perms: 'system:user:list', path: '/system/user', icon: null, sort_order: 1, status: 1 },
  { id: 32, parent_id: 3, menu_name: '角色管理', menu_type: 1, perms: 'system:role:list', path: '/system/role', icon: null, sort_order: 2, status: 1 },
  { id: 33, parent_id: 3, menu_name: '菜单管理', menu_type: 1, perms: 'system:menu:list', path: '/system/menu', icon: null, sort_order: 3, status: 1 },
  { id: 34, parent_id: 3, menu_name: '操作日志', menu_type: 1, perms: 'system:log:view', path: '/system/log', icon: null, sort_order: 4, status: 1 },
]

function buildMenuTree() {
  menus.forEach(m => {
    m.children = childMenus
      .filter(c => c.parent_id === m.id)
      .sort((a, b) => a.sort_order - b.sort_order)
  })
  return menus
}

const allMenus = [...menus, ...childMenus]

const logs = Array.from({ length: 25 }, (_, i) => ({
  id: i + 1,
  username: i % 3 === 0 ? 'operator' : 'admin',
  operation: ['QUERY', 'CREATE', 'UPDATE', 'DELETE', 'TRIGGER_CRAWLER'][i % 5],
  module: ['SYSTEM', 'CRAWLER', 'DASHBOARD'][i % 3],
  target: ['用户管理', '站点爬虫', '数据看板'][i % 3],
  request_method: 'POST',
  request_url: `/admin/${['system/user', 'crawler/site/start', 'dashboard/overview'][i % 3]}`,
  ip: '127.0.0.1',
  status: i % 10 === 0 ? 0 : 1,
  error_msg: i % 10 === 0 ? '服务器内部错误' : null,
  cost_time: Math.floor(Math.random() * 500) + 10,
  created_at: `2026-04-${String(29 - i % 29).padStart(2, '0')} 1${String(i % 10).padStart(2, '0')}:00:00`,
}))

export default {
  // User CRUD
  userList: () => ({ code: 200, msg: 'success', data: { records: users.map(u => ({ ...u, password: '' })), total: users.length, size: 10, current: 1, pages: 1 } }),
  userCreate: (body) => {
    const newUser = { id: users.length + 1, ...body, created_at: now, status: 1 }
    users.push(newUser)
    return { code: 200, msg: 'success', data: null }
  },
  userUpdate: (id, body) => {
    const idx = users.findIndex(u => u.id === parseInt(id))
    if (idx >= 0) Object.assign(users[idx], body)
    return { code: 200, msg: 'success', data: null }
  },
  userDelete: (id) => {
    users = users.filter(u => u.id !== parseInt(id))
    return { code: 200, msg: 'success', data: null }
  },

  // Role CRUD
  roleList: () => ({ code: 200, msg: 'success', data: { records: roles, total: roles.length } }),
  roleAll: () => ({ code: 200, msg: 'success', data: roles }),

  // Menu
  menuTree: () => ({ code: 200, msg: 'success', data: buildMenuTree() }),
  menuAll: () => ({ code: 200, msg: 'success', data: allMenus }),

  // Log
  logList: (params) => {
    const page = parseInt(params.page) || 1
    const size = parseInt(params.size) || 10
    const start = (page - 1) * size
    return { code: 200, msg: 'success', data: { records: logs.slice(start, start + size), total: logs.length, size, current: page, pages: Math.ceil(logs.length / size) } }
  },
}
