# Tailwind CSS v4 集成设计

**日期:** 2026-06-27
**范围:** 前端项目渐进式引入 Tailwind CSS v4

## 目标

为 CyberFlow 前端引入 Tailwind CSS v4，采用渐进式策略——现有样式全部保留，新代码可使用 Tailwind 工具类。

## 技术选型

选择 Tailwind CSS v4 + `@tailwindcss/vite` 插件，理由：
- Vite 6 原生集成，无需 PostCSS 配置
- CSS-first 配置（`@theme`），无需 `tailwind.config.js`
- 按需构建，性能优于 v3
- 项目当前无 PostCSS 配置，直接上新方案无迁移负担

## 改动清单

### 1. 安装依赖

```bash
npm install tailwindcss @tailwindcss/vite
```

移除未使用的 `sass` 依赖（项目中无 `.scss` 文件）。

### 2. vite.config.js — 添加插件

```js
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  // ... 其余不变
})
```

### 3. src/index.css — 新建 Tailwind 入口

```css
@import "tailwindcss";
```

### 4. src/main.js — 引入 CSS

在 Element Plus CSS 之后 import：

```js
import 'element-plus/dist/index.css'
import './index.css'
```

### 5. 可选：自定义主题（按需）

如需匹配 Element Plus 主题色，后续可在 `src/index.css` 中添加：

```css
@theme {
  --color-primary: #409eff;
}
```

## 不改的

- 所有现有 Vue 组件的 `<style>` 块和内联样式
- Element Plus 组件样式
- 路由、Store、API 层

## 验证

1. `npm run dev` 启动无报错
2. 在任意组件中添加 Tailwind class（如 `class="text-red-500"`）验证生效
