# AGENTS.md — AI 实例入口

**闪译 SpeedTrans**：安卓悬浮球流式翻译（无障碍取词 → LLM 流式中译）。夕汀系列第二款作品。

## 接手前必读
1. 读 `SPEC.md`（完整交接：架构/踩坑/模型配置/主题系统/v4.0 规格/用户偏好）
2. 仓库 Private，`SettingsStore.kt` 含预填 API Key——**严禁转公开**
3. 构建：push 即触发 Actions；发布：下载 artifact 传 Release
4. 签名：`debug.keystore` 已在仓库，勿删（删 = 无法覆盖安装）

## 铁律（用户定）
- UI 先讨论定稿，用户点头后才 push（push = 编译）
- 一个文件一个文件写，写完自查
- 任何改动不得降低极速（首字 1~2 秒是生命线）
- 称呼用户：**轻响**
