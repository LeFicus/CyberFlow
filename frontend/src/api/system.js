/**
 * @fileoverview 系统管理 API 接口
 * @description 封装用户管理、角色管理、菜单管理和操作日志等后端接口。
 *              所有接口均通过 @/utils/request 封装的 axios 实例发送请求。
 */

import request from '@/utils/request'

// ==================== 用户管理 ====================

/** @param {Object} params - 分页参数 { page, size } */
export function getUsers(params) { return request.get('/admin/system/user', { params }) }
/** @param {Object} data - 用户数据 { username, password, nickname, email, status } */
export function createUser(data) { return request.post('/admin/system/user', data) }
/** @param {number} id - 用户 ID @param {Object} data - 更新的用户字段 */
export function updateUser(id, data) { return request.put(`/admin/system/user/${id}`, data) }
/** @param {number} id - 用户 ID */
export function deleteUser(id) { return request.delete(`/admin/system/user/${id}`) }
/** @param {number} id - 用户 ID @param {number[]} roleIds - 角色 ID 数组 */
export function assignUserRoles(id, roleIds) { return request.put(`/admin/system/user/${id}/roles`, roleIds) }
export function getUserRoleIds(id) { return request.get(`/admin/system/user/${id}/roles`) }

// ==================== 角色管理 ====================

/** @param {Object} params - 分页参数 { page, size } */
export function getRoles(params) { return request.get('/admin/system/role', { params }) }
/** @returns {Promise<Object>} 返回所有角色列表（不分页） */
export function getAllRoles() { return request.get('/admin/system/role/all') }
export function createRole(data) { return request.post('/admin/system/role', data) }
export function updateRole(id, data) { return request.put(`/admin/system/role/${id}`, data) }
export function deleteRole(id) { return request.delete(`/admin/system/role/${id}`) }
export function getRoleMenuIds(id) { return request.get(`/admin/system/role/${id}/menus`) }
export function assignRoleMenus(id, menuIds) { return request.put(`/admin/system/role/${id}/menus`, menuIds) }

// ==================== 菜单管理 ====================

/** @returns {Promise<Object>} 返回树形菜单结构数据 */
export function getMenuTree() { return request.get('/admin/system/menu/tree') }
export function createMenu(data) { return request.post('/admin/system/menu', data) }
export function updateMenu(id, data) { return request.put(`/admin/system/menu/${id}`, data) }
export function deleteMenu(id) { return request.delete(`/admin/system/menu/${id}`) }

// ==================== 操作日志 ====================

/** @param {Object} params - 查询参数 { page, size, username, module } */
export function getLogs(params) { return request.get('/admin/system/log', { params }) }
