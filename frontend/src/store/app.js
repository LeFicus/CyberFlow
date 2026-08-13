/**
 * @fileoverview 应用全局状态 Store
 * @description 使用 Pinia 管理应用级别的 UI 状态，包括侧边栏折叠状态和面包屑导航数据。
 *              通过 ref 定义响应式数据，通过 function 暴露状态修改方法。
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 应用全局 Store
 * @namespace useAppStore
 * @description 管理布局相关状态：侧边栏折叠/展开、页面面包屑
 */
export const useAppStore = defineStore('app', () => {
  /** @type {import('vue').Ref<boolean>} 侧边栏是否折叠 */
  const sidebarCollapsed = ref(false)
  /** @type {import('vue').Ref<Array>} 面包屑导航项列表 */
  const breadcrumbs = ref([])

  /**
   * 切换侧边栏折叠状态
   */
  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  /**
   * 设置面包屑导航项
   * @param {Array<{name: string, path?: string}>} items - 面包屑项数组
   */
  function setBreadcrumbs(items) {
    breadcrumbs.value = items
  }

  return { sidebarCollapsed, breadcrumbs, toggleSidebar, setBreadcrumbs }
})
