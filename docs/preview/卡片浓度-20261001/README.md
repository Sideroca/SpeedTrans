# 设置页「卡片浓度」对照预览 · 2026-10-01

**问题**：设置页的「卡片浓度」无法影响主题标签下的三个分类按钮（现代经典 / 🏮中国传统色 / 属性设置）。

## 数值证据（同一次真机截屏，浓度 = 51%）

| 元素 | 实测填充 | 判决 |
|---|---|---|
| 现代经典按钮 | `#FEFEFE`（通道波动 0.1） | = 源码 `card #FFFFFF` → **100% 不透明** |
| 属性设置按钮 | `#789261`（通道波动 0.2） | = 源码 `accent #789262` → **100% 不透明** |
| 同页大卡片空隙 | `#AFAFAE` / `#CFCFC9` | 被壁纸稀释 → **51% 已生效** |

按钮纯色零波动、卡片已被稀释 → 浓度参数确实没流到这三个按钮。

## 病根

`SettingsActivity.kt` · `selectThemeCategory()` 内的局部函数：

```kotlin
fun style(chip: TextView, on: Boolean) {
    chip.background = ThemeEngine.cardDrawable(
        if (on) pal.accent else pal.card, 18f, d, pal.cardStroke)   // ← 未过 fadedCard()
```

同页其他元素（属性设置大盒、面板按钮、皮肤胶囊）全部包了 `fadedCard()`，唯此处漏了；
且浓度滑条回调只调 `applySkin()` → `restylePropsFills()`，从不重刷分类按钮。

## 图（按段落拆开，便于查看/下载）

| 文件 | 内容 |
|---|---|
| `p1-now.png` | 现状：真机截图裁切（浓度 51%，按钮纹丝不动） |
| `p2-planA.png` | 方案 A｜三个按钮完全跟随浓度（100 / 50 / 10%） |
| `p7-FINAL.png` | ★ **定稿**：B-1 保底 30% + 文字半跟 |
| `p3-planB1-floor30.png` | 方案 B-1｜选中态**保底 30%**：`max(浓度, 30%)` |
| `p4-planB2-drop30.png` | 方案 B-2｜选中态**只掉 30% 降幅**：`1 − 0.30×(1−浓度)` |
| `p5-themerows.png` | 附：主题色卡行同口径预览（本次不改） |
| `p6-evidence.png` | 结论与数值证据 |
| `overview.jpg` | 全图缩略（1000px 宽） |
| `preview-full.png` | 全图原尺寸（1520×5632，体积大，查看器可能吃力） |

同名 `.jpg` 为轻量版（≤210KB），优先看 `.jpg`。

## 定稿参数（轻响 2026-10-01 确认）

| 元素 | 规则 |
|---|---|
| 未选中 填充 | `pal.card` × 浓度 |
| **选中 填充** | `pal.accent` × **`max(浓度, 30%)`**（保底 30%，守住"当前分类"辨识度） |
| 描边 | `pal.cardStroke` × 浓度（与同页 `styleSecondary` 口径一致） |
| **文字** | × **`(100+浓度)/2`**（只跟一半）→ 100% / 75% / 55% |

## 代码改动（已写、未推、未编译）

`SettingsActivity.kt` 单文件，+37 / −18：

1. 新增 `CAT_CHIP_FLOOR = 30`（companion）
2. `applySkin()` 里补一次 `restyleCatChips()` → 拖动浓度滑条实时生效
3. `selectThemeCategory()` 删掉漏了浓度的局部 `style()`，改调 `restyleCatChips()`
4. 新增 `restyleCatChips()` / `styleCatChip()`（按定稿参数上色）
5. `fadedCard(color)` → `fadedCard(color, pct = 当前浓度)`，可指定百分比

## 源文件

| 文件 | 说明 |
|---|---|
| `index.html` | 参数化复刻页 |
| `gen.py` | 生成 HTML（配色取自 palettes.json，几何为真机实测） |
| `render.py` | Playwright 渲染 → 主图 + 分段图 |
| `wp.png` | 从真机截图裁切的真实壁纸底（950×340） |
| `now.png` | 真机截图裁切（1440×210） |

## 方法

复刻式预览（**非真机截图**，合成渲染）：

- **配色**：`夕汀前端规范/palettes.json` 中 `tea`（茶文化·竹青）实测值，源 `ThemeEngine.kt @ c4f8ea5`
- **几何**：真机 1440×3200 截图像素实测 → dp×3.5 / sp×3.77；按钮高 112px、圆角钳制 56px、内距 14dp/7dp、字号 13sp
- **底色**：真机截图裁切的真实壁纸（保证「透出去看到什么」是真的）
- **合成**：CSS `rgba()` 与 Android `src-over` 数学等价，故 alpha 表现可直接外推
- 为什么不走「真机像素合成」：按钮当前 100% 不透明，其下方壁纸**不可观测**（无法反解），故以真机壁纸作底复刻更可靠

## 复核

`python3 gen.py && python3 render.py`（需 Playwright chromium）。
