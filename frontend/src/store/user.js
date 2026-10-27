/**
 * @fileoverview 用户状态管理 Store
 * @description 使用 Pinia 管理用户认证相关的状态：JWT Token、用户信息，
 *              并提供登录态持久化（localStorage 读写）、退出登录、权限判断等方法。
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getUserInfo } from '@/api/auth'
import { clearAuth, readToken, readUserInfo, writeAuth } from '@/utils/authStorage'

/**
 * 用户 Store
 * @namespace useUserStore
 * @description 管理用户 Token、用户基本信息、权限信息，支持本地持久化与退出登录
 */
export const useUserStore = defineStore('user', () => {
  /** @type {import('vue').Ref<string>} JWT 认证令牌（localStorage 或 sessionStorage） */
  const token = ref(readToken())
  /** @type {import('vue').Ref<Object>} 当前登录用户信息（角色、权限、菜单等） */
  const userInfo = ref(JSON.parse(readUserInfo() || '{}'))
  /** 当前会话是否跨浏览器重启保留 */
  const persistent = ref(Boolean(localStorage.getItem('token')))

  /**
   * 设置 Token 并按“记住登录状态”选择存储位置
   * @param {string} t - JWT 令牌字符串
   * @param {boolean} remember - true 写入 localStorage，false 只写入 sessionStorage
   */
  function setToken(t, remember = true) {
    token.value = t
    persistent.value = remember
    writeAuth({ token: t, persist: remember })
  }

  /**
   * 设置用户信息，并写入当前会话所属的存储位置
   * @param {Object} info - 用户信息对象（id, username, nickname, roles, permissions, menus 等）
   */
  function setUserInfo(info) {
    userInfo.value = info
    writeAuth({ token: token.value, userInfo: info, persist: persistent.value })
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
    persistent.value = false
    clearAuth()
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
