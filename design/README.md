# design/ —— UI/UX 设计台

一个**可交互**的设计稿：一个页面里看完整 App 的所有页面、所有组件和桌面小组件。
直接用浏览器打开 `index.html` 即可（**不需要起服务器**，普通 `<script>`，`file://` 就能跑）。

```bash
open design/index.html          # macOS
# 想直接进深色：index.html?dark=1
```

## 只有一份样式

**没有多套主题 / 多个版本**，就是这个 App 的样子。要调观感，按下面这张表找地方：

| 想改什么 | 改哪个文件 |
|---|---|
| **颜色、圆角、描边、间距节奏、字号、点阵强度** | `css/tokens.css` ← 优先改这里 |
| **某一个页面**的样式 | `css/pages/<页面>.css` 顶部的「本页可调参数」 |
| 卡片 / tab / FAB 等跨页面组件 | `css/components.css`（谨慎，全站都会变） |
| 深色模式 | `css/dark.css`（只做 token 替换，页面样式不用动） |
| 图标 | `js/icons.js` |
| 数据规则（排序 / 搜索 / 新增 / 删除） | `js/store.js` |
| 某个页面的结构与交互 | `js/pages/<页面>.js` |
| 页面清单 | `js/app.js` |

页面与它们的 `.css` / `.js` 一一对应：
`list`（笔记 + 待办列表、多选）、`editor`、`search`、`settings`、`widget`、`homescreen`（演示用的真实桌面外壳，不属于 App）。

### 「本页可调参数」怎么用

每个 `css/pages/*.css` 顶部都留了这样一段，改它只影响这一页：

```css
/* ---------- 本页可调参数（只影响列表页，覆盖 tokens.css） ---------- */
.list-page{
  --list-pad-x: var(--pad-page);   /* 左右留白，默认 18px */
  --list-gap: var(--gap-list);     /* 条目间距 */
  --card-pad-y: 14px;              /* 卡片上下内边距 */
  --card-pad-x: 16px;
}
```

## 约定（务必遵守）

1. **页面样式里不许写死颜色**，只能引用 `var(--c-*)`。写死了，切深色就会漏。
2. **卡片只有一处实现**：`js/ui.js` 的 `BitSay.ui.card()`。首页和搜索页都调它 ——
   和 Android 侧共用 `ui/components/ItemList.kt` 是同一个理由：两套实现一定会漂移。
3. **颜色只走 token**：`css/tokens.css` 定义，`css/dark.css` 替换，页面 CSS 一行都不用改。
4. 演示台外壳（`.shell` / `.panel` / `.knob`）**不吃 App 的 token**，用的是 `--sh-*`，
   否则切深色连演示台一起变，就看不清 App 本身了。

## 加一个新页面

1. `css/pages/<name>.css`（顶部留「本页可调参数」区块）；
2. `js/pages/<name>.js`，导出 `mount(host, opts)`；
3. `index.html` 里加 `<link>` 和 `<script>`；
4. `js/app.js` 里挂到演示台路由和总览网格。

## 真机实测尺寸（设计台的画布基准）

设计台按 **1 CSS px = 1 dp** 画，屏幕容器固定 **360 × 800**，与真机一致：

```
vivo V2309A：wm size 1260x2800，wm density 560  →  1260/3.5 = 360dp，2800/3.5 = 800dp
（refs/screenshots 里那张 1080×2400 是同一比例，3 px/dp）
```

下列数值是用 `uiautomator dump` 在真机上量出来的（`px ÷ 3.5`），已写进对应 CSS：

| 元素 | 实测 dp | 写在 |
|---|---|---|
| 状态栏（safeDrawing 顶） | 高 36 | `base.css --safe-top` |
| 列表页标题 | x22 起，行高 36（28sp/36sp） | `components.css .topbar__title` |
| 列表页副标题 | 行高 16（12sp/16sp） | `.topbar__sub` |
| 图标按钮 | 视觉 40×40，字形 22 | `.icon-btn` |
| 分段 tab 轨道 | x 18..342，高 56（内边距 4） | `.seg` |
| 分段 tab 按钮 | 高 48，宽 156 | `.seg__item` |
| 条目卡片 | 高 68.9 = 14 + 18.9 + 6 + 16 + 14 | `.card` |
| 卡片文字 / 时间 | 行高 18.9 / 16 | `.card__text` / `.card__meta` |
| FAB | 58×58，右 22、下 26 | `.fab` |
| 列表内容内边距 | 左右 18、上 12、下 104 | `pages/list.css` |
| 编辑器纸面 | x 18..342，y 100..740 | `pages/editor.css` |
| 搜索框 | 高 38，x 58..348 | `pages/search.css` |
| 设置分组标题 | x 24 | `pages/settings.css .sect` |
| 设置行 | 高 70（单行说明）/ 86（两行） | `.srow` |
| **小组件** | **206 × 535**，位于桌面 (36, 75) | `pages/widget.css` |
| 小组件顶栏 / 图标按钮 | 47 / 43.7 | `--w-head-h` / `--w-btn` |
| 小组件行 | 卡片 43，节距 47 | `--w-row-h` / `--w-row-gap` |
| 桌面图标列 | x 265..322，首个 y 87，节距 ≈ 97 | `pages/homescreen.css` |
| Dock | x 24..336，y 729..779 | 同上 |

> 宿主（OriginOS 桌面）会把小组件按约 **0.91 倍**渲染，所以实测比声明的 48dp 略小。
> 改小组件尺寸时，声明的 dp 和实测的 dp 都要想一下。

### 自己复测

```bash
adb shell uiautomator dump /sdcard/d.xml && adb pull /sdcard/d.xml
# 里面 bounds="[x1,y1][x2,y2]" 是 px，除以 3.5 就是 dp
```

## 和 Android 的对应关系

见 `PROGERSS.md` §13 的映射表。设计定稿后需要回填的是：
`ui/theme/Color.kt`（颜色）、`ui/theme/Theme.kt`（形状 / 字体）、
`res/drawable/*`（描边等装饰）、以及各 `ui/screen/*.kt` 的间距微调。
