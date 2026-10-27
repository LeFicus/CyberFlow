/**
 * @fileoverview CyberFlow 前端应用入口文件
 * @description 负责初始化 Vue 应用实例，注册 Pinia 状态管理、Vue Router 路由、
 *              Element Plus UI 组件库（中文本地化）及所有 Element Plus 图标组件，
 *              并挂载到 DOM 根节点 #app。
 */

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import FilterBar from '@/components/FilterBar.vue'
import DataPanel from '@/components/DataPanel.vue'
import EmptyState from '@/components/EmptyState.vue'

const app = createApp(App)

// 注册所有 Element Plus 图标组件为全局组件，便于在模板中直接使用
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 共享布局组件全局注册：页面模板可直接使用，业务脚本无需重复 import。
for (const [name, component] of Object.entries({ PageHeader, StatCard, FilterBar, DataPanel, EmptyState })) {
  app.component(name, component)
}

app.use(createPinia())
app.use(router)
// 使用 Element Plus 并设置中文语言包
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')

/**
 * Accessibility bridge for Element Plus internals.
 * - pagination page-size selects get a name
 * - selects/switches inside el-form-item inherit the visible label
 * - table scroll containers become keyboard focusable
 * Element Plus renders these nodes dynamically, so patch after DOM updates.
 */
function patchAccessibility() {
  document.querySelectorAll('.el-pagination .el-select__input:not([aria-label])').forEach(input => {
    input.setAttribute('aria-label', '每页条数')
  })

  document.querySelectorAll('.el-select__input:not([aria-label]), .el-switch__input:not([aria-label])').forEach(input => {
    const item = input.closest('.el-form-item')
    const label = item?.querySelector('.el-form-item__label')?.textContent?.trim()
    if (label) input.setAttribute('aria-label', label)
  })

  document.querySelectorAll('.el-table__body-wrapper .el-scrollbar__wrap:not([tabindex])').forEach(scroller => {
    scroller.setAttribute('tabindex', '0')
  })
}

let a11yPatchScheduled = false
const scheduleA11yPatch = () => {
  if (a11yPatchScheduled) return
  a11yPatchScheduled = true
  requestAnimationFrame(() => {
    a11yPatchScheduled = false
    patchAccessibility()
  })
}

new MutationObserver(scheduleA11yPatch).observe(document.body, { childList: true, subtree: true })
scheduleA11yPatch()
