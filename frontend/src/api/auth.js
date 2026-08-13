/**
 * @fileoverview 认证相关 API 接口
 * @description 封装用户登录、获取当前用户信息等认证接口。
 *              所有接口均通过 @/utils/request 封装的 axios 实例发送请求。
 */

import request from '@/utils/request'

/**
 * 用户登录
 * @param {string} username - 登录用户名
 * @param {string} password - 登录密码
 * @returns {Promise<Object>} 返回 Promise，包含 token 和 userInfo 等登录凭证
 */
export function login(username, password) {
  return request.post('/admin/auth/login', { username, password })
}

/**
 * 获取当前登录用户信息
 * @returns {Promise<Object>} 返回 Promise，包含用户角色、权限、菜单树等信息
 */
export function getUserInfo() {
  return request.get('/admin/auth/userinfo')
}
