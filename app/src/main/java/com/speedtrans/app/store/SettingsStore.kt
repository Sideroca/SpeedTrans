package com.speedtrans.app.store

import android.content.Context

/**
 * 全局配置存取。翻译接口为任意 OpenAI 兼容端点。
 */
class SettingsStore(context: Context) {

    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 完整接口地址，例如 https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions */
    var baseUrl: String
        get() = sp.getString(KEY_URL, DEFAULT_BASE_URL)!!.trim()
        set(v) = sp.edit().putString(KEY_URL, v.trim()).apply()

    var apiKey: String
        get() = sp.getString(KEY_API, DEFAULT_API_KEY)!!.trim()
        set(v) = sp.edit().putString(KEY_API, v.trim()).apply()

    var model: String
        get() = sp.getString(KEY_MODEL, DEFAULT_MODEL)!!.trim()
        set(v) = sp.edit().putString(KEY_MODEL, v.trim()).apply()

    val isConfigured: Boolean get() = apiKey.isNotEmpty() && baseUrl.startsWith("http")

    companion object {
        private const val KEY_URL = "base_url"
        private const val KEY_API = "api_key"
        private const val KEY_MODEL = "model"

        const val DEFAULT_BASE_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"

        /**
         * 默认模型：千问翻译特化模型（官方通用首选档，支持流式与增量输出）。
         * 追求极限延迟可改 qwen-mt-lite；要更高质量可改 qwen-mt-plus；
         * 也可填任意通用对话模型（qwen3.8-flash / deepseek-chat 等），
         * 引擎按模型名前缀 qwen-mt 自动切换翻译协议。
         */
        const val DEFAULT_MODEL = "qwen-mt-flash"

        /** 预置 key（装完即用）。⚠️ 仓库须保持 Private，公开前必须清空此值 */
        const val DEFAULT_API_KEY =
            "sk-ws-H.EXMXRPY.Berr.MEUCIGTI4-WF-XMlgG8Cxczsp9u3LATJ4CE8Al2nO93jDTupAiEAzlI3vBB4Fo0nqpvwoUDvFfZxmcuBj83ri-CgFVkHj2Q"
    }
}
