/**
 * @fileoverview 系统管理模块 Mock 数据
 * @description 为系统管理页面提供模拟数据，包括用户 CRUD、角色列表、
 *              菜单树结构和操作日志分页查询。使用内存数组存储可变数据。
 */

/** @type {string} 基准时间字符串 */
const now = '2026-04-29 12:00:00'

/** @type {Array<Object>} 用户列表（可增删改） */
let users = [
  { id: 1, username: 'admin', password: '', nickname: '系统管理员', email: 'admin@cyberflow.com', status: 1, created_at: '2026-01-01 00:00:00' },
  { id: 2, username: 'operator', password: '', nickname: '运营人员', email: 'op@cyberflow.com', status: 1, created_at: '2026-03-15 10:30:00' },
]

/** @type {Array<Object>} 角色列表（只读） */
const roles = [
  { id: 1, role_name: '超级管理员', role_code: 'ROLE_ADMIN', description: '拥有所有权限', status: 1, created_at: now },
  { id: 2, role_name: '运营人员', role_code: 'ROLE_OPERATOR', description: '可查看数据看板、触发爬虫', status: 1, created_at: now },
]

/** @type {Array<Object>} 一级菜单节点（目录类型，children 动态构建） */
const menus = [
  { id: 1, parent_id: 0, menu_name: '数据看板', menu_type: 0, perms: null, path: '/dashboard', icon: 'DataBoard', sort_order: 1, status: 1, children: [] },
  { id: 2, parent_id: 0, menu_name: '爬虫管理', menu_type: 0, perms: null, path: '/crawler', icon: 'Cpu', sort_order: 2, status: 1, children: [] },
  { id: 3, parent_id: 0, menu_name: '系统管理', menu_type: 0, perms: null, path: '/system', icon: 'Setting', sort_order: 3, status: 1, children: [] },
]

/** @type {Array<Object>} 二级及以下菜单节点（菜单类型 + 按钮类型） */
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

/**
 * 构建树形菜单结构
 * 将一级菜单的 children 字段填充为对应的子节点，并按 sort_order 排序
 * @returns {Array<Object>} 完整的树形菜单结构
 */
function buildMenuTree() {
  menus.forEach(m => {
    m.children = childMenus
      .filter(c => c.parent_id === m.id)
      .sort((a, b) => a.sort_order - b.sort_order)
  })
  return menus
}

/** @type {Array<Object>} 所有菜单节点的扁平数组（用于整体查询） */
const allMenus = [...menus, ...childMenus]

/** @type {Array<Object>} 模拟操作日志列表（共 25 条） */
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
  // ==================== 用户管理 ====================

  /**
   * 获取用户列表（过滤密码字段）
   * @returns {Object} { code, msg, data: { records, total, size, current, pages } }
   */
  userList: () => ({ code: 200, msg: 'success', data: { records: users.map(u => ({ ...u, password: '' })), total: users.length, size: 10, current: 1, pages: 1 } }),

  /**
   * 创建用户
   * @param {Object} body - { username, nickname, email, password, status }
   * @returns {Object} { code, msg, data: null }
   */
  userCreate: (body) => {
    const newUser = { id: users.length + 1, ...body, created_at: now, status: 1 }
    users.push(newUser)
    return { code: 200, msg: 'success', data: null }
  },

  /**
   * 更新用户信息
   * @param {number|string} id - 用户 ID
   * @param {Object} body - 更新的用户字段
   * @returns {Object} { code, msg, data: null }
   */
  userUpdate: (id, body) => {
    const idx = users.findIndex(u => u.id === parseInt(id))
    if (idx >= 0) Object.assign(users[idx], body)
    return { code: 200, msg: 'success', data: null }
  },

  /**
   * 删除用户
   * @param {number|string} id - 用户 ID
   * @returns {Object} { code, msg, data: null }
   */
  userDelete: (id) => {
    users = users.filter(u => u.id !== parseInt(id))
    return { code: 200, msg: 'success', data: null }
  },

  // ==================== 角色管理 ====================

  /**
   * 获取角色分页列表
   * @returns {Object} { code, msg, data: { records, total } }
   */
  roleList: () => ({ code: 200, msg: 'success', data: { records: roles, total: roles.length } }),

  /**
   * 获取所有角色（不分页）
   * @returns {Object} { code, msg, data: Array }
   */
  roleAll: () => ({ code: 200, msg: 'success', data: roles }),

  // ==================== 菜单管理 ====================

  /**
   * 获取树形菜单结构数据
   * @returns {Object} { code, msg, data: Array }
   */
  menuTree: () => ({ code: 200, msg: 'success', data: buildMenuTree() }),

  /**
   * 获取所有菜单节点（扁平列表）
   * @returns {Object} { code, msg, data: Array }
   */
  menuAll: () => ({ code: 200, msg: 'success', data: allMenus }),

  // ==================== 操作日志 ====================

  /**
   * 获取操作日志分页列表
   * @param {Object} params - { page, size, username, module }
   * @returns {Object} { code, msg, data: { records, total, size, current, pages } }
   */
  logList: (params) => {
    const page = parseInt(params.page) || 1
    const size = parseInt(params.size) || 10
    const start = (page - 1) * size
    return { code: 200, msg: 'success', data: { records: logs.slice(start, start + size), total: logs.length, size, current: page, pages: Math.ceil(logs.length / size) } }
  },
}
