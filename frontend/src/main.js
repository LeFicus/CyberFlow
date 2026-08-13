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

const app = createApp(App)

// 注册所有 Element Plus 图标组件为全局组件，便于在模板中直接使用
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
// 使用 Element Plus 并设置中文语言包
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
