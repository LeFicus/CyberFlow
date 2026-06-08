import request from '@/utils/request'

// User
export function getUsers(params) { return request.get('/admin/system/user', { params }) }
export function createUser(data) { return request.post('/admin/system/user', data) }
export function updateUser(id, data) { return request.put(`/admin/system/user/${id}`, data) }
export function deleteUser(id) { return request.delete(`/admin/system/user/${id}`) }
export function assignUserRoles(id, roleIds) { return request.put(`/admin/system/user/${id}/roles`, roleIds) }

// Role
export function getRoles(params) { return request.get('/admin/system/role', { params }) }
export function getAllRoles() { return request.get('/admin/system/role/all') }

// Menu
export function getMenuTree() { return request.get('/admin/system/menu/tree') }

// Log
export function getLogs(params) { return request.get('/admin/system/log', { params }) }
