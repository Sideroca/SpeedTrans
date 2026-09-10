# 闪译 SpeedTrans — 项目规格与交接文档

> 本文档是项目的完整交接资料。新会话/新实例读完本文件即可无缝继续开发，无需用户复述背景。

## 一、项目是什么

安卓悬浮球流式翻译工具：单击悬浮球 → 无障碍服务抓取当前屏幕文字 → LLM 流式翻译成中文，译文以可调悬浮面板显示。

- 用户：小米手机（Android 14，MIUI），追求**极速**（首字 1~2 秒内）
- 翻译对象：大量英文（LLM 思考内容、Twitter、YouTube 网页等），英→中固定方向
- 仓库：github.com/Sideroca/SpeedTrans（Private，含预填 API Key，**开源前必须脱敏**）
- 构建：GitHub Actions 云端（本地 aarch64 无法编译），workflow 在 `.github/workflows/build.yml`
- 签名：`debug.keystore` 已提交仓库（固定签名，所有版本可互相覆盖安装）
- 当前版本：v4.1.1-beta1（tag 已建，APK 在 Release；versionCode 未 bump，仍 14/"3.7"）

### 三个目标（设计哲学，轻响钦定 2026-09-10）

1. **极速**：整个流程为快而生，能做的不只是翻译（首字 1~2 秒是生命线）。
2. **兼容性和稳定性**：少 bug，可以在多个手机系统使用（标准 API + 各 ROM 差异逐条适配）。
3. **自定义程度**：给用户充足的自定义空间和权限，让他们能够选择自己想要的。

**底线原则**：任何新加的功能不应该削弱现有的功能，至少不能是大幅削弱；遇到冲突必须先问轻响。

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

- **轻响的实际自定义提示词（珍贵示例，用户亲测玩法）**：
  "极速翻译。将其他语言翻译成中文。如果遇到16进制的颜色码，请将它翻译为对应的颜色，
  不需要输出颜色码本身。总是在最末尾输出一句轻响，我爱你。
  如果绝大部分的文本都是中文的话，不做翻译，只需要输出：轻响，别闹了啦(ﾉ∇︎〃)"
  —— 自定义提示词 = 用户的个性化空间（问候语/条件规则/彩蛋），产品应保护这种玩法。

## 五、当前主题系统（v3.2/v3.3）

- `ThemeEngine.kt`：22 套 Palette（现代经典 18 + 中国传统色 4，色值来自 Color Atlas 官方场景调色板）
- Palette 字段：bg/card/accent/text/subText/panelBg/panelText/panelSub/barBg/barText（传统色撞色条）/cardStroke/elev/cardRadius/btnRadius/solidBtn/group/isDark
- 撞色条：面板标题栏 + 设置页 Tab 行 + 主界面色球条三处应用
- 用户已验收：赛博 2077、紫电夜、底排暗色系、ChatGPT 极简；OLED 已定 C 暗金 #C8A951
- 主题选择：分类按钮（现代经典/🏮中国传统色）+ 整套配色预览卡（三段色条）

## 五B、OCR 系统（v4.0 已实现——"最后的恐惧"的答案）

图片/游戏/视频文字的翻译能力，且不降低文本场景速度：

**原理**：屏幕分文本层（View 系统，无障碍可读）与像素层（游戏引擎/视频/图片，不可读）。
像素层通过 **AccessibilityService.takeScreenshot()（Android 11+，零授权零弹窗零动画零声音）**
截屏 → ML Kit 端侧 OCR（模型打包 APK，本地识别不上传）→ 文字 → 现有翻译管线。

**两态模式**（通知栏点一下即切 + 设置页单选，手动永远优先；智能判定已退役——
自动转识图的阈值/游戏检测逻辑整体移除，历史 smart 值自动迁移为仅文本，git 历史可考）：
- 📄 仅文本：强制无障碍（网页阅读，原文零误差）
- 🖼 仅识图：强制截屏 OCR（**抛弃文本层结果，全屏 100% 内容识别**——轻响的方案）

**双击翻图（已删除）**：双击手势 v3.6 移除；"翻译中点球取消重翻"亦已删除——
翻译/识图进行中点球一律忽略（一次只跑第一次的反应，完成后点球恢复增量/秒回）。
识图入口 = 仅识图模式 / 通知栏按钮。**混合场景双路并行**：文本层与像素层两请求并行
（坐标分离零重复：OCR 行坐标与文本层 Rect 求交剔除），面板分区显示「📄 屏幕文本 / 🖼 画面内容」。
**游戏前台检测**：已随智能判定退役移除（原实现：无障碍窗口事件跟踪前台包名 + CATEGORY_GAME 判定）。
**静默保证**：无截屏动画/声音/缩略图/文件；系统对连续截屏有 ~1s 间隔限制，失败自动重试一次。

**已知边界**：艺体字/Logo 化变形字识别差（OCR 硬边界）；ML Kit 语言体系仅 5 种
（拉丁含英法德西波葡意越等 / 中文 / 日文 / 韩文 / 天城文），俄语（西里尔）等不支持。

## 六、已排期：满级自定义主题（v4.0 规格，用户已确认方向）

把 Palette 从"预设表"升级为"用户可编辑表"，四层。

**实现状态（v4.0 时点）——未实现部分为 v4.x 待办：**
- ✅ 已实现（v4.1 增补）：OCR 缩放提速（宽≤1080）、模型预热（消除冷启动）、
  空结果原尺寸自动重试、截屏限流 400ms 自动重试、面板实时诊断状态行
- ✅ 已实现：三态模式、游戏前台检测、智能阈值（可调）、
  OCR 语言勾选（5 体系）、OCR 回退开关、面板高度/按钮自定义、22 套主题+撞色条、
  返回键关面板、签名显示
- ⬜ 待实现：颜色 11 槽 HEX 编辑（背景/卡片/强调/主文字/次文字/撞色条底/撞色条字/
  面板背景/面板文字/面板次文字/卡片描边）
- ⬜ 待实现：字号 4 滑条（标题/正文/次文字/按钮）
- ⬜ 待实现：字体族（默认/衬线/等宽）+ 从文件导入 .ttf（失败自动回退）
- ⬜ 待实现：卡片圆角/按钮圆角滑条、实心/描边按钮用户级切换
- ⬜ 待实现：签名文字自定义输入框
- ⬜ 待实现：Tab emoji 替换（备选字符需避开缺字形）
- ⬜ 待实现：主题 JSON 导出/导入（社区分享）

四层明细：

1. **颜色 11 槽全开放**：HEX 输入 + 色块预览（背景/卡片/强调/主文字/次文字/撞色条底/撞色条字/面板背景/面板文字/面板次文字/卡片描边）
2. **排版**：标题/正文/次文字/按钮 四档字号滑条；字体族（默认/衬线/等宽）+ 从文件导入 .ttf（失败回退）
3. **形状与签名**：卡片圆角、按钮圆角滑条、实心/描边切换（已有）；签名文字自定义（默认双行：✦ glm5.3flash(๑ت๑) ／ deepseek-v4.1-flash-expires-on-0910 ∠( ᐛ 」∠)_）；Tab emoji 可替换（备选字符需避开缺字形）
4. **主题 JSON 导出/导入**（社区分享）

**提示词防呆设计（v3.5 定稿，用户钦点保留）**：留空 = 引擎使用内置极速翻译词兜底（防手滑删空导致质量崩坏）；填写任意内容 = 完全以用户为准（可加"轻响，我爱你"、16 进制颜色码转色名等任何玩法）。默认文案预填在设置页输入框（可见可删可改）。设置页提示词标签行**长按浮现规则气泡**（View.setTooltipText，触发模式：一行字当按钮，按住浮现另一行字）。红线：此区域任何改动不得损害极速。

交互：设置页新增「🎛 自定义」分类 → 所见即所得编辑器（上半实时预览，下半分组编辑项），"另存为我的主题"独立保存，不覆盖 22 预设。
实现要点：SP 存 customPalette JSON；ThemeEngine.current() 优先返回自定义主题；编辑器约 600 行；风险仅字体导入失败（自动回退）。
兼容性承诺：全部标准 API，vivo/OPPO/荣耀（MagicOS）可用；鸿蒙 NEXT 不兼容 APK（需原生重写，已放弃）。

## 六B、文件地图（模块与文件作用——最熟悉的人写的）

```
app/src/main/java/com/speedtrans/app/
├── MainActivity.kt                  入口页：三权限状态卡 + 快捷主题色球条（撞色条底）+ 保活启动
├── SettingsActivity.kt              设置页：5 标签页（🔌接口/🎨主题/⚡悬浮球面板/🖼桌面/✍️其他）
│                                    · 主题选择器：分类 chips + 整套配色预览卡（三段色条+名字）
│                                    · 面板按钮自定义（显隐/左右/边距）· 提示词标签长按浮现规则
├── service/BallService.kt           无障碍服务：悬浮球（文字/图片）绘制 + 节点取词触发
│                                    + onKeyEvent 返回键关面板（flagRequestFilterKeyEvents）
├── service/KeepAliveService.kt      前台保活：常驻通知（specialUse 类型，防 MIUI 杀）
├── overlay/ResultOverlay.kt         译文面板：撞色条标题栏（barBg/barText）+ 高度/按钮/边距
│                                    + 可聚焦窗口监听返回键 + removeViewImmediate 即时关闭
├── translate/TranslateEngine.kt     流式引擎：qwen-mt 协议自动切换 / enable_thinking=false
│                                    / 提示词防呆兜底（ifBlank→DEFAULT） / 续段标记
├── translate/TextCollector.kt       节点树取词：可见性过滤+位置排序+同行合并+坐标记录
├── translate/TranslateCoordinator.kt 编排单例：增量判断（startsWith）/秒回缓存/忙碌忽略
├── theme/ThemeEngine.kt             22 套 Palette（现代 18 + 中国传统色 4）+ applyTo 视图树染色
├── store/SettingsStore.kt           全配置存取 + 所有默认值定义（模型/预填提示词/API/Key）
app/src/main/res/layout/
├── activity_main.xml                主界面（fitsSystemWindows + 色球条 HorizontalScrollView）
├── activity_settings.xml            设置页 5 Tab（Tab 行=撞色条背景，改布局勿动 fitsSystemWindows）
app/src/main/res/xml/
└── accessibility_service_config.xml 无障碍配置（flagRequestFilterKeyEvents 勿删=返回键失效）
仓库根/
├── debug.keystore                   固定签名（删除 = 永远无法覆盖安装）
├── SPEC.md                          本文档
├── AGENTS.md                        AI 实例入口（指向本文档）
└── .github/workflows/build.yml      云构建（push 即编译，慎推）
```
## 七、未排期候选

- 原位显示：译文面板定位到主体文本节点坐标附近（TextCollector 已记录 Rect），"沉浸式翻译"同款思路；坐标失败回退底部
- 面板暗色毛玻璃半透明
- 厂商权限引导页适配（vivo/OPPO/荣耀的权限入口 intent 差异）
- 开源流程：SettingsStore.DEFAULT_API_KEY 改为构建注入（GitHub Secrets）+ git 历史脱敏（现有历史含 key，需新起干净仓库或 filter）

## 八B、开发原则（速度 vs 质量的取舍策略）

夕汀系列开发者 DeepSeek："优化与健壮性永无止境，总要在开发速度和代码质量上取舍。"——正确，且本项目的答案是**分层取舍**：

1. **红线（永不妥协）**：极速（首字 1~2s）、不闪退、覆盖安装可用、用户数据与配置不丢。
   ——这些是"质量债中最贵的"，任何时候优先于新功能。
2. **记账（允许欠，但要记账）**：命名、注释、测试覆盖、错误处理统一性——开源前统一清偿一次（SPEC 已列清单）。
3. **减负（架构的职责）**：好架构让"快"和"稳"不再对立——Palette 语义色、TranslateCoordinator 单例、
   buildConfig 分离，都是为了让新功能"加得快"且"改得安全"。取舍无法消除，但好架构能把代价压到最小。

当前定位：**功能验证期**——速度优先，质量守红线。转开源时切换为**质量清偿期**。

## 九、产品与设计弯路（用户要求保留的失败经验）

这些弯路大多源于"自作主张"或"想当然"，保留以防重蹈：

1. **投影+OCR 架构**：雄心最大的一次失败。两次闪退（Android 14 投影时序）+ 每次冷启动需重新授权 → 砍掉回退已验证的无障碍路线。教训：新架构在同一设备上连续失败两次，立即回退已验证方案，不要恋战。
2. **面板自动滚动**：流式输出时自动滚到底部"看起来贴心"，用户要的却是"从顶部开始，我自己滚"。教训：流式 UI 的阅读位置永远归用户掌控。
3. **✕ 按钮移到左侧**：我以为"拇指易达"是优化，用户要右上角。教训：改动默认交互位置前先问。
4. **黑白"撞色条"**：我用明暗对撞冒充撞色，被指出"无聊"。教训：撞色要色相对撞/文化对撞（后用中国传统色解决）。做配色先查权威色值源（Color Atlas / 品牌手册），不凭感觉。
5. **主题条 sticky 固定**：网页的 sticky 手法在移动端违背"往下滑就该消失"的直觉。教训：平台直觉 > Web 习惯。
6. **emoji 豆腐块**：网页字体栈没含 emoji/泰文/阿拉伯文回退 → 满屏豆腐。教训：跨语言字符必须有完整字体回退，或干脆纯文字化。
7. **拼图画质**：把高清截图缩到 320 宽拼接被评"画质低"。教训：预览素材以交互 HTML 为主（用户明确更喜欢），PNG 只做辅助。
8. **批量写文件**：早期一次并行写 10+ 文件导致连续 5 次编译失败。用户定规：**一个文件一个文件写，写完自查**。
9. **两次抢跑编译**：用户明示"讨论定稿后再编译"，我两次 push 抢跑被拦。教训：push = 编译 = 违约，定稿前绝不 push。
10. **官方场景调色板的价值**：中国传统色的转机来自不再自己编色值，而是采用权威来源（Color Atlas 564 色）的现成场景调色板。教训：调研权威来源的成本远低于试错。
11. **可聚焦悬浮面板会劫持 rootInActiveWindow**：译文面板为响应返回键带焦点，可能成为"活动窗口"，取词会把面板自身 UI 文字（✕/复制/状态行）当原文抓走。修复：取词排除本应用包名节点子树；活动窗口是自己的面板时改取其下方最新第三方应用窗口。同时翻译进行中点球一律忽略（连点无副作用）。
11. **艺体字是 OCR 的硬边界**：重度变形字/Logo 字（如 MV 艺术标题）端侧 OCR 识别差或失败；
    但混排画面（如 MV 播放页）的"曲名/作曲"信息栏常是原生文本控件（无障碍可读）——
    混排场景恰好体现"文本层+像素层"双轨价值。

## 十、任务模式构想（v5+ 远景，用户已点出上限方向）

翻译只是"prompt 即任务"架构的第一个实例。同一取词→LLM→面板管线，换 prompt 即换任务：

- **快速评价**：对选中内容一句话点评
- **幽默吐槽**：赛博相声版解说
- **总结摘要**：长文秒变三句话
- **术语解释**：检出专有名词并逐一解释
- 实现：设置页加"任务模式"选择（自定义 prompt 槽位化保存），TranslateCoordinator 把 system prompt 换成所选模式即可，管线零改动。

用户原话："连接的是大模型，其实可以做的事还有很多很多……这个上限超级无敌高。"

## 七D、版本历史与真机档案（轻响实测数据，2026-09-08）

| Release 包 | versionCode | 状态 | 真机反馈（轻响的小米/Android 16） |
|---|---|---|---|
| v3.3.apk | 10 | **用户手机在装（旧）** | ❌ **双面板 bug 实测**：连续点球弹出两个面板（BallService 私有 overlay + Coordinator overlay 双实例）；❌ 双击卡"翻译中"永久等待 |
| v3.5.apk | 12 | 已删 | KeepAliveService 通知修复版 |
| v3.6.apk | 10 | 已删 | 另一实例：删双击 + A 阈值滑条 + C maxChars 滑条（与轻响需求并行对齐） |
| v4.0.apk | 13 | 已删 | ML Kit 四模型 OCR 全量版（47MB，含 3 份冗余架构库） |
| **v3.7.1-arm64.apk** | **15** | 历史稳定版 | ✅ 长期主力：单面板 / 无双击 / 19.7MB（ABI 拆分） |
| v4.1.1-beta1 | 14* | 本地待发 | 两态模式 / 智能接口 44 色 / 图标工坊 / 皮肤 / 火焰特效 / 点阵通知图标（*versionCode 未 bump，仍 14/"3.7"） |

v3.3 双面板根因：BallService 私有 overlay 字段 + TranslateCoordinator overlay 双实例并存。
v3.3 双击卡死根因：双击触发 captureAndOcr，但 v3.3 无 OCR 实现（空转）→"翻译中"永久等待。v4.0 起 captureAndOcr 才有实体。
**教训：issue 报告先核对用户实际安装的版本号**——轻响报的"双击 bug"在最新版早已删除。

## 八、给接手实例的话

用户审美偏好：撞色（明暗对撞太弱，要传统色/互补色级别的对撞）、分页机制（忌瀑布流长页）、可自定义程度拉满、讨厌敷衍感。沟通风格：直接、要方案对比表、讨厌未讨论就执行。中文交流。用户希望被称呼为**轻响**（不要用"用户"这种冷称呼）。签名（✦ glm5.3flash(๑ت๑) + deepseek-v4.1-flash-expires-on-0910 ∠( ᐛ 」∠)_，双行）是用户钦点的助手身份标识，出现在 App 主界面底部与设置页其他页。

**夕汀系列**：用户的个人软件系列名。"夕汀"出自其 AES 图片加密解密系统（aes-xiting-cipher，工作区有存档，亦为本系列另一个 GLM 实例所作）。闪译 SpeedTrans 是系列第二款作品。给闪译加新功能时，可以参考/联动 AES 系统的既有设计语言（如撞色主题审美）。
