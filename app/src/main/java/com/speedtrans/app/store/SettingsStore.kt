package com.speedtrans.app.store

import android.content.Context
import android.graphics.Color

/**
 * 全局配置存取：翻译接口 + 悬浮球外观 + 译文面板外观。
 */
class SettingsStore(context: Context) {

    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ---------- 翻译接口 ----------

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

    // ---------- 取词引擎 ----------

    /** "ocr" = 屏幕投影+OCR（默认，权限温和）；"a11y" = 无障碍（可抓折叠全文） */
    var engine: String
        get() = sp.getString("engine", "ocr")!!
        set(v) = sp.edit().putString("engine", v).apply()

    /** 自定义翻译提示词（留空用默认） */
    var customPrompt: String
        get() = sp.getString("custom_prompt", "")!!.trim()
        set(v) = sp.edit().putString("custom_prompt", v.trim()).apply()

    // ---------- 悬浮球外观 ----------

    /** 球上文字（无自定义图片时显示） */
    var ballText: String
        get() = sp.getString("ball_text", "译")!!.ifBlank { "译" }
        set(v) = sp.edit().putString("ball_text", v.trim()).apply()

    /** 球颜色（#AARRGGBB） */
    var ballColorHex: String
        get() = sp.getString("ball_color", "#E6FF4757")!!
        set(v) = sp.edit().putString("ball_color", v.trim()).apply()

    val ballColorInt: Int
        get() = try {
            Color.parseColor(ballColorHex)
        } catch (_: Exception) {
            0xE6FF4757.toInt()
        }

    /** true=圆形 false=圆角方形 */
    var ballCircle: Boolean
        get() = sp.getBoolean("ball_circle", true)
        set(v) = sp.edit().putBoolean("ball_circle", v).apply()

    /** 球尺寸 dp（44/52/60） */
    var ballSizeDp: Int
        get() = sp.getInt("ball_size", 52)
        set(v) = sp.edit().putInt("ball_size", v).apply()

    /** 自定义图片路径（空 = 使用文字球）。图片存于应用私有目录。 */
    var ballImagePath: String
        get() = sp.getString("ball_image", "")!!
        set(v) = sp.edit().putString("ball_image", v).apply()

    // ---------- 译文面板外观 ----------

    /** 面板高度占屏幕百分比（30~100） */
    var overlayHeightPct: Int
        get() = sp.getInt("overlay_height", 80)
        set(v) = sp.edit().putInt("overlay_height", v).apply()

    /** 标题栏按钮缩放百分比（85/100/125） */
    var overlayButtonScalePct: Int
        get() = sp.getInt("overlay_btn_scale", 100)
        set(v) = sp.edit().putInt("overlay_btn_scale", v).apply()

    val overlayButtonScale: Float get() = overlayButtonScalePct / 100f

    companion object {
        private const val KEY_URL = "base_url"
        private const val KEY_API = "api_key"
        private const val KEY_MODEL = "model"

        const val DEFAULT_BASE_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"

        /** 默认模型：qwen3.7-flash（百炼免费额度）。qwen-mt-* 自动走翻译特化协议 */
        const val DEFAULT_MODEL = "qwen3.7-flash"

        /** 预置 key（装完即用）。⚠️ 开源前必须移除并改为构建注入 */
        const val DEFAULT_API_KEY =
            "sk-ws-H.EXMXRPY.Berr.MEUCIGTI4-WF-XMlgG8Cxczsp9u3LATJ4CE8Al2nO93jDTupAiEAzlI3vBB4Fo0nqpvwoUDvFfZxmcuBj83ri-CgFVkHj2Q"
    }
}
