# 闪译 SpeedTrans — 项目规格与交接文档

> 本文档是项目的完整交接资料。新会话/新实例读完本文件即可无缝继续开发，无需用户复述背景。

## 一、项目是什么

安卓悬浮球流式翻译工具：单击悬浮球 → 无障碍服务抓取当前屏幕文字 → LLM 流式翻译成中文，译文以可调悬浮面板显示。

- 用户：小米手机（Android 14，MIUI），追求**极速**（首字 1~2 秒内）
- 翻译对象：大量英文（LLM 思考内容、Twitter、YouTube 网页等），英→中固定方向
- 仓库：github.com/Sideroca/SpeedTrans（Private，含预填 API Key，**开源前必须脱敏**）
- 构建：GitHub Actions 云端（本地 aarch64 无法编译），workflow 在 `.github/workflows/build.yml`
- 签名：`debug.keystore` 已提交仓库（固定签名，所有版本可互相覆盖安装）
- 当前版本：v3.3（versionCode 10），Release 上有 v1.0~v3.0、v3.3 各包

## 二、技术架构（全部为标准 Android API，无厂商/谷歌依赖）

```
无障碍服务 BallService（取词引擎，唯一已实现引擎）
  ├─ 绘制悬浮球（文字球/自定义图片球，外观可配置）
  ├─ 单击 → TextCollector 遍历节点树取词（含节点屏幕坐标，可做"原位显示"）
  └─ TranslateCoordinator（翻译编排单例）
       ├─ 增量翻译：新文本 startsWith 旧文本 → 只翻新增尾巴（带【续段】标记+规则重申）
       ├─ 相同内容 → 0 请求秒回
       └─ TranslateEngine（OpenAI 兼容流式，SSE）
            ├─ qwen-mt-* 模型自动切翻译特化协议（单 user 消息 + translation_options）
            ├─ DashScope 域名自动 enable_thinking=false（关闭 qwen3 思考，首字提速关键）
            └─ 自定义提示词：通用模型走 system，MT 模型走 domains
ResultOverlay（译文面板：高度/按钮显隐/左右/边距可调，不自动滚动，返回键关闭）
KeepAliveService（前台保活，划掉最近任务不掉球）
KeepAliveService + adjustResize + fitsSystemWindows（状态栏/键盘适配）
```

## 三、关键经验（别踩重复的坑）

1. **Outline.setRoundRect 用 int 坐标**（只有圆角是 float）
2. **ImageReader 在 android.media 包**，Image.Plane 的属性是 `buffer` 不是 `rowBuffer`
3. **WindowManager.LayoutParams 没有 setMargins**，用 x/y + gravity
4. **layout XML 文本里的英文双引号会破坏 AAPT 解析**，用「」或 &quot;
5. **Android 14 mediaProjection FGS 必须先授权后启动**（时序错误 = SecurityException 闪退）
6. **FGS 必须声明 foregroundServiceType**，否则 MissingForegroundServiceTypeException 闪退
7. **removeView 是异步的**，要立即消失用 removeViewImmediate + 先取消翻译请求
8. **CompoundButton（Radio/Check/Switch）不能套用实心按钮染色**（浅色主题白字消失），单独处理
9. **github push 偶发 GnuTLS 网络抖动**，重试 2-3 次即可
10. **用户规矩**：UI 先讨论定稿 → 用户点头 → 才 push（push 会自动触发构建）

## 四、模型与 API（已实测有效）

- DashScope OpenAI 兼容：`https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`
- Key：sk-ws-H.EXMXRPY.Berr.MEUCIGTI4-WF-XMlgG8Cxczsp9u3LATJ4CE8Al2nO93jDTupAiEAzlI3vBB4Fo0nqpvwoUDvFfZxmcuBj83ri-CgFVkHj2Q（用户所有，预填于 SettingsStore.DEFAULT_API_KEY）
- 模型：qwen3.7-flash（用户付费档，比 3.6 便宜 80%）/ qwen-mt-flash（翻译特化，无思考）
- 翻译模型官方文档：chinesecoloratlas.com 不相关；模型文档在 help.aliyun.com/zh/model-studio/machine-translation

## 五、当前主题系统（v3.2/v3.3）

- `ThemeEngine.kt`：22 套 Palette（现代经典 18 + 中国传统色 4，色值来自 Color Atlas 官方场景调色板）
- Palette 字段：bg/card/accent/text/subText/panelBg/panelText/panelSub/barBg/barText（传统色撞色条）/cardStroke/elev/cardRadius/btnRadius/solidBtn/group/isDark
- 撞色条：面板标题栏 + 设置页 Tab 行 + 主界面色球条三处应用
- 用户已验收：赛博 2077、紫电夜、底排暗色系、ChatGPT 极简；OLED 已定 C 暗金 #C8A951
- 主题选择：分类按钮（现代经典/🏮中国传统色）+ 整套配色预览卡（三段色条）

## 六、已排期：满级自定义主题（v4.0 规格，用户已确认方向）

把 Palette 从"预设表"升级为"用户可编辑表"，四层：

1. **颜色 11 槽全开放**：HEX 输入 + 色块预览（背景/卡片/强调/主文字/次文字/撞色条底/撞色条字/面板背景/面板文字/面板次文字/卡片描边）
2. **排版**：标题/正文/次文字/按钮 四档字号滑条；字体族（默认/衬线/等宽）+ 从文件导入 .ttf（失败回退）
3. **形状与签名**：卡片圆角、按钮圆角滑条、实心/描边切换（已有）；签名文字自定义（默认 ✦ glm5.3flash(๑ت๑)）；Tab emoji 可替换（备选字符需避开缺字形）
4. **主题 JSON 导出/导入**（社区分享）

交互：设置页新增「🎛 自定义」分类 → 所见即所得编辑器（上半实时预览，下半分组编辑项），"另存为我的主题"独立保存，不覆盖 22 预设。
实现要点：SP 存 customPalette JSON；ThemeEngine.current() 优先返回自定义主题；编辑器约 600 行；风险仅字体导入失败（自动回退）。
兼容性承诺：全部标准 API，vivo/OPPO/荣耀（MagicOS）可用；鸿蒙 NEXT 不兼容 APK（需原生重写，已放弃）。

## 七、未排期候选

- 原位显示：译文面板定位到主体文本节点坐标附近（TextCollector 已记录 Rect），"沉浸式翻译"同款思路；坐标失败回退底部
- 面板暗色毛玻璃半透明
- 厂商权限引导页适配（vivo/OPPO/荣耀的权限入口 intent 差异）
- 开源流程：SettingsStore.DEFAULT_API_KEY 改为构建注入（GitHub Secrets）+ git 历史脱敏（现有历史含 key，需新起干净仓库或 filter）

## 八、给接手实例的话

用户审美偏好：撞色（明暗对撞太弱，要传统色/互补色级别的对撞）、分页机制（忌瀑布流长页）、可自定义程度拉满、讨厌敷衍感。沟通风格：直接、要方案对比表、讨厌未讨论就执行。中文交流。用户称呼助手为 GLM/智谱清言，签名颜文字 (๑ت๑) 是用户钦点的身份标识。
