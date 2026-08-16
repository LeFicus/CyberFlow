/**
 * @fileoverview Axios 请求封装模块
 * @description 基于 axios 创建统一的 HTTP 请求实例，封装请求/响应拦截器：
 *              - 请求拦截器：自动从 localStorage 获取 token 并附加到 Authorization 请求头
 *              - 响应拦截器：统一处理业务错误码（非200）和 HTTP 状态码（401 未授权）
 *              所有后端 API 调用均应通过此模块导出的实例进行。
 */

import axios from 'axios'
import { ElMessage } from 'element-plus'

let redirectingToLogin = false

function handleSessionExpired() {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  if (window.location.pathname === '/login' || redirectingToLogin) return
  redirectingToLogin = true
  ElMessage.error('登录已过期，请重新登录')
  window.location.replace('/login')
}

/**
 * HTTP 请求服务实例
 * 配置了默认超时时间 15 秒，无预设 baseURL（使用相对路径同源请求）
 */
const service = axios.create({
  baseURL: '',
  timeout: 15000,
  // Serialize arrays as repeated keys (`category=a&category=b`) so Spring's
  // List<String> request parameters receive multi-select filters directly.
  paramsSerializer: { indexes: null },
})

/**
 * 请求拦截器 — 自动附加 Token
 * 在每次请求发送前，从 localStorage 读取 JWT token 并设置为 Bearer 鉴权头
 * @param {Object} config - axios 请求配置对象
 * @returns {Object} 更新后的请求配置
 */
service.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

/**
 * 响应拦截器 — 统一错误处理
 * - 成功响应（code=200）：直接返回响应数据
 * - 业务错误（code≠200）：弹出错误提示并拒绝 Promise
 * - HTTP 401 错误：清除本地 token，提示登录过期并重定向到 /login
 * @param {Object} response - axios 响应对象
 * @param {Object} error - 网络错误或 HTTP 错误对象
 */
service.interceptors.response.use(
  response => {
    // File downloads are not wrapped in the application's { code, data } JSON envelope.
    if (response.config.responseType === 'blob') return response
    const res = response.data
    if (res.code === 401) {
      handleSessionExpired()
      return Promise.reject(new Error(res.msg || '登录已过期'))
    }
    if (res.code !== 200) {
      ElMessage.error(res.msg || '请求失败')
      return Promise.reject(new Error(res.msg || '请求失败'))
    }
    return res
  },
  error => {
    const isLoginRequest = error.config?.url?.includes('/admin/auth/login')
    if (error.response?.status === 401 && !isLoginRequest) {
      handleSessionExpired()
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  },
)

export default service
