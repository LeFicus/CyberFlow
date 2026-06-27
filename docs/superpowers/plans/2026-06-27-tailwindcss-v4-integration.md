# Tailwind CSS v4 集成 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 CyberFlow 前端渐进式引入 Tailwind CSS v4，现有样式保留，新代码可用 Tailwind 工具类。

**Architecture:** 通过 `@tailwindcss/vite` 插件在 Vite 构建管线中注入 Tailwind，CSS-first 配置（`@import "tailwindcss"`），无需 PostCSS 或 JS 配置文件。

**Tech Stack:** Vite 6, Vue 3, Tailwind CSS v4, @tailwindcss/vite

---

### Task 1: 安装依赖

**Files:**
- Modify: `frontend/package.json`

- [ ] **Step 1: 安装 tailwindcss 和 @tailwindcss/vite，移除未使用的 sass**

```bash
cd frontend && npm install tailwindcss @tailwindcss/vite && npm uninstall sass
```

- [ ] **Step 2: 验证 package.json 变更**

确认 `dependencies` 或 `devDependencies` 中包含 `tailwindcss` 和 `@tailwindcss/vite`，且 `sass` 已移除。

- [ ] **Step 3: 提交**

```bash
git add frontend/package.json frontend/package-lock.json
git commit -m "chore: add tailwindcss v4 + @tailwindcss/vite, remove unused sass"
```

---

### Task 2: 配置 Vite 插件

**Files:**
- Modify: `frontend/vite.config.js`

- [ ] **Step 1: 在 vite.config.js 中添加 tailwindcss 插件**

将 `frontend/vite.config.js` 从：

```js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/admin': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 正式环境去除 /admin 前缀
        // rewrite: (path)=>path.replace(/^\/admin/, '') 
      },
    },
  },
})
```

改为：

```js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/admin': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 正式环境去除 /admin 前缀
        // rewrite: (path)=>path.replace(/^\/admin/, '') 
      },
    },
  },
})
```

- [ ] **Step 2: 提交**

```bash
git add frontend/vite.config.js
git commit -m "feat: add @tailwindcss/vite plugin to vite config"
```

---

### Task 3: 创建 Tailwind CSS 入口文件

**Files:**
- Create: `frontend/src/index.css`

- [ ] **Step 1: 创建 src/index.css**

```css
@import "tailwindcss";
```

- [ ] **Step 2: 提交**

```bash
git add frontend/src/index.css
git commit -m "feat: add tailwindcss entry CSS file"
```

---

### Task 4: 在 main.js 中引入 Tailwind CSS

**Files:**
- Modify: `frontend/src/main.js`

- [ ] **Step 1: 在 Element Plus CSS 之后引入 index.css**

将 `frontend/src/main.js` 第 4 行后添加一行：

```js
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './index.css'          // Tailwind CSS v4
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './mock' // Mock 数据拦截

const app = createApp(App)

// 注册所有 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
```

- [ ] **Step 2: 提交**

```bash
git add frontend/src/main.js
git commit -m "feat: import tailwindcss in main.js"
```

---

### Task 5: 验证

- [ ] **Step 1: 启动开发服务器**

```bash
cd frontend && npm run dev
```

- [ ] **Step 2: 验证无启动错误**

控制台输出应类似：
```
VITE v6.0.5  ready in xxx ms
➜  Local:   http://localhost:5173/
```

确认无 CSS 相关报错。

- [ ] **Step 3: 在浏览器 DevTools 中验证 Tailwind 生效**

打开 `http://localhost:5173/`，在任意元素的 class 中临时添加 `text-red-500`，确认文字变红。
