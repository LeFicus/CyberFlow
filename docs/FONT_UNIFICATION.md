# 前端字体统一方案（已落地）

> 目标：macOS / Windows / Linux 上使用同一套字形与字宽，避免金额、SKU、日期和中文标题因系统字体不同而漂移。
> 结论：采用自托管的 **Inter + Noto Sans SC（GB2312 子集）+ JetBrains Mono**。

---

## 1. 字体组成

| 用途 | 字体 | 文件 | 体积 |
| --- | --- | --- | --- |
| 英文、数字、标点 | Inter Variable | `InterVariable.woff2` | 47 KB |
| 中文 | Noto Sans SC（GB2312 子集） | `NotoSansSC-GB2312.woff2` | 1.8 MB |
| 日志、任务 ID | JetBrains Mono | `JetBrainsMono.woff2` | 39 KB |

三个文件都在 `frontend/src/assets/fonts/`，均为 SIL Open Font License 1.1，许可证已随仓库和构建产物（`dist/fonts/`）提供。

---

## 2. 字体栈

```css
--cf-font-sans: "CyberFlow Sans", Inter, "PingFang SC", "HarmonyOS Sans SC",
  "Microsoft YaHei", system-ui, -apple-system, "Segoe UI", sans-serif;
--cf-font-mono: "CyberFlow Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
```

`"CyberFlow Sans"` 由两个 `@font-face` 组成，使用 **互不重叠的 `unicode-range`**：

- Inter：拉丁字母、数字、常用标点
- Noto Sans SC：CJK 汉字与全角标点

浏览器按字符自动选择字体，英文/数字来自 Inter，中文来自 Noto，不会出现“整段中文被英文字体渲染”的问题。

`"CyberFlow Mono"` 用于任务 ID 和日志，替换原来的系统等宽栈。

---

## 3. 覆盖范围

中文子集包含：

- **6,763 个 GB2312 汉字**
- ASCII、Latin-1、常用标点、CJK 标点、全角字符
- `frontend/src` 中实际出现的全部非 ASCII 字符

动态商品名/分类中如果出现 GB2312 之外的生僻字，会回退到系统中文字体。这是为了把字体控制在 2 MB 以内的取舍；如需 100% 覆盖，可改用全量 Noto Sans SC（约 10 MB）或扩展子集。

---

## 4. 加载策略

- 全部字体 `font-display: swap`：先用系统字体渲染，字体就绪后替换，避免白屏。
- 中文子集不使用 `preload`：1.8 MB 预加载会拖慢首屏，交给 CSS 发现后按需加载。
- 字体文件带 hash，可长期缓存。
- 首次访问会增加约 **1.9 MB** 字体流量；之后走浏览器缓存。

**代价**：首次访问可能出现一次字形切换（FOUT），中文段落可能有轻微重排。若线上更在意 CLS，可改 `font-display: optional`，但首次访问可能停留在系统字体，直到下一次导航。

---

## 5. 图表与 Element Plus

- Element Plus：`--el-font-family: var(--cf-font-sans)`。
- ECharts 是 Canvas 渲染，不继承 CSS，已通过 `CHART_FONT_FAMILY` 写入图表 `textStyle`（收入分类饼图、收录趋势折线图）。
- 表格数字继续使用 `font-variant-numeric: tabular-nums`。

---

## 6. 验证结果

| 项目 | 结果 |
| --- | --- |
| `document.fonts.check` 中文 | `true` |
| `document.fonts.check` 英文/数字 | `true` |
| 等宽字体（任务历史） | `true` |
| 实际加载字体 | CyberFlow Sans ×2 + CyberFlow Mono |
| 19 页 × 3 宽度布局 | 无横向溢出 |
| 构建产物 | 三个 woff2 + 许可证已输出 |
| axe 可访问性 | 字体替换后未新增违规 |

---

## 7. 维护方式

新增或替换字体时：

1. 更新 `frontend/src/assets/fonts/` 与许可证。
2. 重新生成中文子集（命令见 `frontend/src/assets/fonts/README.md`）。
3. 同步 `frontend/src/styles/fonts.css` 的 `unicode-range`。
4. 更新 `CHART_FONT_FAMILY`（`frontend/src/utils/chartFont.js`）。
5. 重新跑布局指标与 axe。

> 说明：同一字体文件在不同操作系统的抗锯齿/字重渲染仍有细微差异，字形与字宽可以统一，但无法做到像素级完全一致。
