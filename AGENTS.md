# AGENTS.md — AI 实例入口

**闪译 SpeedTrans**：安卓悬浮球流式翻译（无障碍/识图取词 → LLM 流式中译 → 悬浮面板）。
夕汀系列第二款作品。用户：**轻响**（直接称呼，不要用"用户"这种冷称呼）。

## 接手前三步
1. 读 `SPEC.md`（完整交接：架构/踩坑/模型配置/主题系统/用户偏好）
2. `git log --oneline -20` + `git status` 核对远端同步状态
3. 本文件只做入口，细节全在 SPEC

## 铁律（用户定，逐条血泪）
1. **UI 先讨论定稿**，用户点头后才动第一行代码（push = 编译 = 违约）
2. **一个文件一个文件写**，写完自查（批量并行写 = 连续编译失败的前科）
3. **任何改动不得降低极速**（首字 1~2 秒是生命线）
4. 重新生成分支前先 `git log` 核对，git 副作用会持久化
5. **写代码与推送/构建永远分离**：代码交付消息不得包含 push；push 仅在用户单独明确指令时执行，且该消息只做推送这一件事
6. **文档命名**：色板/主题禁用皇帝年号人名（康熙/雍正✗）；朝代与窑口学名可用（清/明/郎窑红✓）

## 架构现状速查（v4.1.x）
```
BallService(无障碍) ─┬─ TextCollector(节点树取词,排除自身包名) ├─ OcrEngine(ML Kit五语言,跨识别器去重)
                     └─ KeepAliveService(前台保活+通知控制中心)
TranslateCoordinator(单例总线) ─┬─ TranslateEngine(SSE; 服务商预设+思考档位映射)
                                └─ ResultOverlay(译文面板+捕捉层)
ThemeEngine(22套主配色) + ShellSkins(设置页皮肤) + SettingsStore(单一事实源)
IconStudio(快捷图标工坊) + HistoryStore(本地历史200条)
```
- **两态模式**：📄 仅文本 / 🖼 仅识图（智能判定已退役，smart 值自动迁移为仅文本）
- **智能接口**：六家服务商预设（千问/DeepSeek/豆包/GLM/Kimi/ChatGPT）+ ContainsAdapter 包含式联想 + 钥匙按服务商归档 + 思考档位映射
- **设置页皮肤**：炫酷黑 / 跟随主界面主题（22 套穿控制台骨架）

## 踩坑速查（新例，详见 SPEC 三/九）
- `TextView.append` 会请求把新增区域滚到可见 → 流式 append 前后锁存/恢复两层 scrollY
- 可聚焦悬浮窗劫持 `rootInActiveWindow` → 取词排除本应用包名；活动窗口是自己的面板时改取第三方窗口
- 下拉候选过滤后位置漂移 → 按候选文本解析，绝不按 pos 索引全量表
- ACTV 默认前缀过滤 → ContainsAdapter 包含式（浏览器式联想）
- 服务商候选点击后钥匙不换 → 401 疑云；钥匙按服务商归档（`pk_<id>`）
- 通知小图标必须纯白单色矢量（ic_notif 点阵双环）；启动器图标用自适应（前景透明+照片底）

## 发布流程
push → Actions 自动构建（3~5 分钟）→ Artifacts 下载 `SpeedTrans-debug-apk` → 覆盖安装。
Release 直链流程：建 tag → POST release → 上传 APK 资产（脚本已跑通）。
⚠️ versionCode/versionName 尚未随功能 bump（仍 3.7/14），发版前需统一。

## 红线仓库
- 仓库保持 Private：`SettingsStore.DEFAULT_API_KEY` 预填 + git 历史含 Key，开源前必须脱敏
- `debug.keystore` 勿删（删 = 永远无法覆盖安装）
- `accessibility_service_config.xml` 的 `flagRequestFilterKeyEvents` 勿删（返回键关面板失效）
