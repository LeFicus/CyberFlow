/**
 * @fileoverview 用户状态管理 Store
 * @description 使用 Pinia 管理用户认证相关的状态：JWT Token、用户信息，
 *              并提供登录态持久化（localStorage 读写）、退出登录、权限判断等方法。
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getUserInfo } from '@/api/auth'

/**
 * 用户 Store
 * @namespace useUserStore
 * @description 管理用户 Token、用户基本信息、权限信息，支持本地持久化与退出登录
 */
export const useUserStore = defineStore('user', () => {
  /** @type {import('vue').Ref<string>} JWT 认证令牌（从 localStorage 恢复） */
  const token = ref(localStorage.getItem('token') || '')
  /** @type {import('vue').Ref<Object>} 当前登录用户信息（角色、权限、菜单等） */
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || '{}'))

  /**
   * 设置 Token 并持久化到 localStorage
   * @param {string} t - JWT 令牌字符串
   */
  function setToken(t) {
    token.value = t
    localStorage.setItem('token', t)
  }

  /**
   * 设置用户信息并持久化到 localStorage
   * @param {Object} info - 用户信息对象（id, username, nickname, roles, permissions, menus 等）
   */
  function setUserInfo(info) {
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  /**
   * 从后端刷新完整用户信息，包括动态菜单树。
   * 登录接口只返回基础信息，菜单由 /admin/auth/userinfo 提供。
   */
  async function refreshUserInfo() {
    const res = await getUserInfo()
    setUserInfo(res.data)
    return res.data
  }

  /**
   * 退出登录：清空 Token 和用户信息，移除 localStorage 持久化数据
   */
  function logout() {
    token.value = ''
    userInfo.value = {}
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  /**
   * 判断当前用户是否拥有指定权限
   * @param {string} perm - 权限标识符（如 'system:user:list'）
   * @returns {boolean} 拥有权限返回 true，否则返回 false
   */
  function hasPermission(perm) {
    return userInfo.value?.permissions?.includes(perm) ?? false
  }

  return { token, userInfo, setToken, setUserInfo, refreshUserInfo, logout, hasPermission }
})
