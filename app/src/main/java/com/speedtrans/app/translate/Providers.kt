package com.speedtrans.app.translate

/**
 * 服务商预设档案（2026-09 按各家官方文档核对）。
 * 全部为 OpenAI 兼容 /chat/completions + Bearer 认证，差异只在「思考档位」参数形状：
 * - DashScope：enable_thinking
 * - 豆包/GLM/Kimi/DeepSeek：thinking {type}（DeepSeek 另有 reasoning_effort）
 * - OpenAI：reasoning_effort
 * levels 的 value 由 TranslateEngine 按服务商映射为真实参数；档位必须真实有效（无假档位）。
 */
object Providers {

    data class Preset(
        val id: String,
        val label: String,                    // 候选显示名
        val url: String,                      // 预填接口地址（自定义为空）
        val models: List<String>,             // 型号候选（不限于清单，可自由输入）
        val keys: List<String>,               // 域名识别片段（baseUrl contains，忽略大小写）
        val levels: List<Pair<String, String>>, // (档位名, 值)；空 = 无思考概念/自定义
        val note: String = ""                 // 副标题提示
    )

    val all = listOf(
        Preset(
            "qwen", "千问 · 阿里云百炼",
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            listOf("qwen3.8-flash", "qwen3.8-max", "qwen3-vl-plus", "qwen3.7-flash", "qwen-mt-flash", "qwen-mt-plus"),
            listOf("dashscope", "aliyun"),
            listOf("关" to "off", "开" to "on"),
            "qwen-mt 系为翻译特化模型；qwen3.8-flash 为最新主力"
        ),
        Preset(
            "deepseek", "DeepSeek · 深度求索",
            "https://api.deepseek.com/chat/completions",
            listOf("deepseek-v4.1-flash-expires-on-0910", "deepseek-flash", "deepseek-v4-flash-vision-exp", "deepseek-v4-pro"),
            listOf("deepseek"),
            listOf("关" to "off", "低" to "low", "高" to "high", "极高" to "max"),
            "deepseek-flash = V4.1 稳定名；deepseek-v4.1-flash-expires-on-0910 为体验 ID（0910 到期）"
        ),
        Preset(
            "doubao", "豆包 · 火山方舟",
            "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
            listOf("doubao-seed-2-1-turbo", "doubao-seed-2-1-pro", "doubao-seed-2-1-pro-260628", "doubao-seed-character"),
            listOf("volces.com"),
            listOf("关" to "off", "自动" to "auto", "开" to "on"),
            "部分账号需使用接入点号 ep-xxx 代替模型名"
        ),
        Preset(
            "glm", "GLM · 智谱",
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            listOf("glm-5.3-flash", "glm-5.3", "glm-5.2", "glm-5", "glm-4.5-flash"),
            listOf("bigmodel.cn"),
            listOf("关" to "off", "低" to "low", "高" to "high", "极高" to "max"),
            "glm-5.3-flash 为多模态速档；glm-5 系强制思考（选「关」自动降为最低强度）"
        ),
        Preset(
            "kimi", "Kimi · 月之暗面",
            "https://api.moonshot.cn/v1/chat/completions",
            listOf("kimi-k3", "kimi-k2.7-code", "kimi-k2.7-code-highspeed", "kimi-k2.6"),
            listOf("moonshot"),
            listOf("关" to "off", "开" to "on"),
            "kimi-k2 / moonshot-v1 已于 2026-08-31 下线"
        ),
        Preset(
            "openai", "ChatGPT · OpenAI",
            "https://api.openai.com/v1/chat/completions",
            listOf("gpt-5.2", "gpt-5.2-chat-latest", "gpt-5.2-pro", "gpt-5", "gpt-5-mini"),
            listOf("openai.com"),
            listOf("低" to "min", "中" to "mid", "高" to "max"),
            "国内需代理/VPN"
        ),
        Preset(
            "custom", "自定义", "", emptyList(), emptyList(), emptyList(),
            "任意 OpenAI 兼容服务"
        )
    )

    /** 按 baseUrl 匹配预设；匹配不到 = 自定义（返回 null） */
    fun match(url: String): Preset? =
        all.firstOrNull { p -> p.keys.any { url.contains(it, ignoreCase = true) } }
}
