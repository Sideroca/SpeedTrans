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

    /** 最大输出 tokens；0 = 不发送此参数（交给服务端默认）。默认给大一些：长文翻译不被截断 */
    var maxTokens: Int
        get() = sp.getInt("max_tokens", 8192).let { if (it >= 65536) 8192 else it }   // 老的 100000 默认值自动降回 8192
        set(v) = sp.edit().putInt("max_tokens", v).apply()

    /** 采样温度；负数 = 不发送（默认不发送，尊重各家模型默认值，用户想调才调） */
    var temperature: Float
        get() = sp.getFloat("temperature", -1f)
        set(v) = sp.edit().putFloat("temperature", v).apply()

    /** 自定义翻译提示词（留空用默认） */
    var customPrompt: String
        // 未保存过时预填默认文案（可见可删可改）；用户保存空 = 真正为空，不发 system
        get() = sp.getString("custom_prompt", "")!!.trim()   // 空 = 用内置（不再把内置词预填进输入框）
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

    /** 悬浮球形状：circle / roundrect / cut / triangle（老 ballCircle 自动迁移） */
    var ballShape: String
        get() {
            val v = sp.getString("ball_shape", null)
            if (v != null) return v
            return if (sp.getBoolean("ball_circle", true)) "circle" else "roundrect"
        }
        set(v) = sp.edit().putString("ball_shape", v).apply()

    /** 球尺寸 dp（44/52/60） */
    var ballSizeDp: Int
        get() = sp.getInt("ball_size", 52)
        set(v) = sp.edit().putInt("ball_size", v).apply()

    /** 自定义图片路径（空 = 使用文字球）。图片存于应用私有目录。 */
    var ballImagePath: String
        get() = sp.getString("ball_image", "")!!
        set(v) = sp.edit().putString("ball_image", v).apply()

    // ---------- 译文面板外观 ----------

    /** ✕ 关闭按钮位置：true=标题栏左侧 false=右侧（默认右上） */
    var btnCloseLeft: Boolean
        get() = sp.getBoolean("btn_close_left", false)
        set(v) = sp.edit().putBoolean("btn_close_left", v).apply()

    /** 标题栏按钮距面板边缘的距离（dp，0~24） */
    var btnPaddingDp: Int
        get() = sp.getInt("btn_padding", 8)
        set(v) = sp.edit().putInt("btn_padding", v).apply()

    /** 单次翻译原文长度上限（字符，4000~50000，超出截断） */
    var maxChars: Int
        get() = sp.getInt("max_chars", 12000)
        set(v) = sp.edit().putInt("max_chars", v).apply()

    /** 屏幕文字少时自动截屏识别图片/游戏内容 */
    /** 📄仅文本 / 🖼仅识图（两态；历史 smart 值自动迁移为仅文本） */
    var translateMode: String
        get() = sp.getString("translate_mode", "text")!!.let { if (it == "smart") "text" else it }
        set(v) = sp.edit().putString("translate_mode", if (v == "ocr") "ocr" else "text").apply()

    /** 译文文字大小（sp，12~24） */
    var overlayTextSize: Int
        get() = sp.getInt("overlay_text_size", 16)
        set(v) = sp.edit().putInt("overlay_text_size", v.coerceIn(12, 24)).apply()

    /** 思考档位（off/auto/on/low/high/min/mid/max），由服务商映射为各家参数；默认最快档 */
    var thinkingLevel: String
        get() = sp.getString("thinking_level", "off")!!
        set(v) = sp.edit().putString("thinking_level", v).apply()

    /** OCR 语言勾选（文字体系：latin/chinese/japanese/korean/devanagari） */
    var ocrLanguages: Set<String>
        get() = sp.getStringSet("ocr_langs", setOf("latin", "chinese", "japanese"))!!
        set(v) = sp.edit().putStringSet("ocr_langs", v).apply()

    /** 标题栏显示「复制」按钮 */
    /** 译文累积：面板未关闭时，新译文追加在下方（不清空重开） */
    var panelAccumulate: Boolean
        get() = sp.getBoolean("panel_accumulate", true)
        set(v) = sp.edit().putBoolean("panel_accumulate", v).apply()

    var showCopy: Boolean
        get() = sp.getBoolean("show_copy", true)
        set(v) = sp.edit().putBoolean("show_copy", v).apply()

    /** 标题栏显示「✕」关闭按钮 */
    var showClose: Boolean
        get() = sp.getBoolean("show_close", true)
        set(v) = sp.edit().putBoolean("show_close", v).apply()

    /** 面板高度占屏幕百分比（30~100） */
    var overlayHeightPct: Int
        get() = sp.getInt("overlay_height", 80)
        set(v) = sp.edit().putInt("overlay_height", v).apply()

    /** 标题栏按钮缩放百分比（85/100/125） */
    var overlayButtonScalePct: Int
        get() = sp.getInt("overlay_btn_scale", 100)
        set(v) = sp.edit().putInt("overlay_btn_scale", v).apply()

    val overlayButtonScale: Float get() = overlayButtonScalePct / 100f

    // ---------- 撞色条独立选色 ----------

    /** 译文面板标题条（撞色条）独立颜色；空 = 跟随主题 Palette */
    var barColorHex: String
        get() = sp.getString("bar_color", "#E0F0E8")!!.trim()   // 默认 = 影青（用户勾选的第 4 种），直到用户改动
        set(v) = sp.edit().putString("bar_color", v).apply()

    /** 解析撞色条覆盖色；未设置返回 null */
    fun barColorOverride(): Int? =
        try {
            if (barColorHex.isEmpty()) null else Color.parseColor(barColorHex)
        } catch (_: Exception) {
            null
        }

    // ---------- 页面壁纸 ----------

    /** 服务商独立钥匙记忆：切服务商自动换钥匙（千问/DeepSeek/GLM 各存各的） */
    fun providerKeyOf(id: String): String? =
        if (id == "custom") null else sp.getString("pk_$id", null)

    fun setProviderKey(id: String, key: String) {
        if (id == "custom") return
        sp.edit().putString("pk_$id", key).apply()
    }

    // ---------- 页面壁纸 v2：两处独立（page = 设置页 / main = 主界面） ----------
    // 每槽：orig=原图、crop=已取景图（显示用）、en=启用、dim=遮罩浓度、nx/ny/nz=取景参数（归一化，与分辨率无关）

    fun wpOrig(slot: String): String = sp.getString("wp_${slot}_orig", "")!!.trim()
    fun setWpOrig(slot: String, v: String) = sp.edit().putString("wp_${slot}_orig", v.trim()).apply()

    fun wpCrop(slot: String): String = sp.getString("wp_${slot}_crop", "")!!.trim()
    fun setWpCrop(slot: String, v: String) = sp.edit().putString("wp_${slot}_crop", v.trim()).apply()

    fun wpEnabled(slot: String, def: Boolean = true): Boolean = sp.getBoolean("wp_${slot}_en", def)
    fun setWpEnabled(slot: String, v: Boolean) = sp.edit().putBoolean("wp_${slot}_en", v).apply()

    fun wpDim(slot: String, def: Int = 50): Int = sp.getInt("wp_${slot}_dim", def)
    fun setWpDim(slot: String, v: Int) = sp.edit().putInt("wp_${slot}_dim", v.coerceIn(0, 80)).apply()

    fun wpFrame(slot: String): FloatArray = floatArrayOf(
        sp.getFloat("wp_${slot}_nx", 0f),
        sp.getFloat("wp_${slot}_ny", 0f),
        sp.getFloat("wp_${slot}_nz", 1f)
    )

    fun setWpFrame(slot: String, nx: Float, ny: Float, nz: Float) = sp.edit()
        .putFloat("wp_${slot}_nx", nx)
        .putFloat("wp_${slot}_ny", ny)
        .putFloat("wp_${slot}_nz", nz)
        .apply()

    // ---------- 首页外观 ----------

    /** 首页卡片不透明度（30~100，默认 82；越低越透、越显壁纸） */
    var cardAlphaPct: Int
        get() = sp.getInt("card_alpha", 82).coerceIn(30, 100)
        set(v) = sp.edit().putInt("card_alpha", v.coerceIn(30, 100)).apply()

    /** 首页字号缩放（80~140，默认 100；作用到首页文字，大标题除外；与系统字体缩放叠加） */
    var homeFontPct: Int
        get() = sp.getInt("home_font_pct", 100).coerceIn(80, 140)
        set(v) = sp.edit().putInt("home_font_pct", v.coerceIn(80, 140)).apply()

    // 旧版单张壁纸字段（仅迁移逻辑读取；新代码勿用）
    var wallpaperPath: String
        get() = sp.getString("wallpaper_path", "")!!
        set(v) = sp.edit().putString("wallpaper_path", v).apply()

    var wallpaperDim: Int
        get() = sp.getInt("wallpaper_dim", 50)
        set(v) = sp.edit().putInt("wallpaper_dim", v.coerceIn(0, 80)).apply()

    var wallpaperOnSettings: Boolean
        get() = sp.getBoolean("wallpaper_settings", true)
        set(v) = sp.edit().putBoolean("wallpaper_settings", v).apply()

    var wallpaperOnMain: Boolean
        get() = sp.getBoolean("wallpaper_main", true)
        set(v) = sp.edit().putBoolean("wallpaper_main", v).apply()

    companion object {
        private const val KEY_URL = "base_url"
        private const val KEY_API = "api_key"
        private const val KEY_MODEL = "model"

        /** 最终版：不预置地址（全新安装 = 未配置，引导用户自行填写） */
        const val DEFAULT_BASE_URL = ""

        /** 最终版：不预置模型 */
        const val DEFAULT_MODEL = ""

        /** 提示词预填文案（用户可全删或自定义；空 = 不发送 system 消息） */
        const val DEFAULT_SYS_PROMPT =
            "You are a fast translation engine. Translate the user's text into Simplified Chinese. " +
                    "Output ONLY the Chinese translation. Preserve line breaks. " +
                    "Keep code, URLs and proper nouns unchanged. No notes, no explanations."

        /** 最终版：绝不预置任何密钥（用户自行填写，本地保存） */
        const val DEFAULT_API_KEY = ""
    }
}
