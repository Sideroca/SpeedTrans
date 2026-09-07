package com.speedtrans.app.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/**
 * 主题引擎 v3.2：24 套配色图书馆。
 * 两大分类：现代经典（各知名软件设计语言）/ 中国传统色（Chinese Color Atlas 官方调色板）。
 * 每套 = 语义色 + 形状语言（圆角/实心描边）+ 质感（描边/投影）+ 撞色条（barBg/barText，传统色对撞）。
 */
data class Palette(
    val id: String,
    val name: String,
    val group: String,    // "modern" = 现代经典 | "chinese" = 中国传统色
    val isDark: Boolean,
    val bg: Int,
    val card: Int,
    val accent: Int,
    val text: Int,
    val subText: Int,
    val panelBg: Int,
    val panelText: Int,
    val panelSub: Int,
    val barBg: Int,       // 撞色条背景（传统色对撞）
    val barText: Int,     // 撞色条文字
    val cardStroke: Int = 0,
    val elev: Int = 0,
    val cardRadius: Int = 12,
    val btnRadius: Int = 12,
    val solidBtn: Boolean = true
)

object ThemeEngine {

    private fun c(v: Long) = v.toInt()

    // ============ 现代经典 · 浅色（9） ============
    val modernLight = listOf(
        Palette("deepseek", "深海蓝", "modern", false,
            c(0xFFF7F8FA), c(0xFFFFFFFF), c(0xFF4D6BFE), c(0xFF1F2329), c(0xFF828A9B),
            c(0xFFFFFFFF), c(0xFF1F2329), c(0xFF828A9B),
            barBg = c(0xFFF0C239), barText = c(0xFF1F2329),
            cardStroke = c(0xFFE3E7EF), elev = 4),
        Palette("ios", "iOS 经典", "modern", false,
            c(0xFFF2F2F7), c(0xFFFFFFFF), c(0xFF007AFF), c(0xFF000000), c(0xFF8E8E93),
            c(0xFFFFFFFF), c(0xFF000000), c(0xFF8E8E93),
            barBg = c(0xFFED5736), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFC6C6C8), elev = 0, cardRadius = 10, btnRadius = 10),
        Palette("kimi", "月白紫", "modern", false,
            c(0xFFFFFFFF), c(0xFFF7F9FC), c(0xFF007CFF), c(0xFF002F5B), c(0xFF8A99AC),
            c(0xFFFFFFFF), c(0xFF002F5B), c(0xFF8A99AC),
            barBg = c(0xFF9D2933), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFE3E9F4), elev = 4),
        Palette("qq", "QQ 经典蓝", "modern", false,
            c(0xFFF0F4F8), c(0xFFFFFFFF), c(0xFF12B7F5), c(0xFF1A1A1A), c(0xFF86909C),
            c(0xFFFFFFFF), c(0xFF1A1A1A), c(0xFF86909C),
            barBg = c(0xFFF0C239), barText = c(0xFF1A1A1A),
            cardStroke = c(0xFFE1E8F0), elev = 4),
        Palette("notion", "Notion 极简", "modern", false,
            c(0xFFFFFFFF), c(0xFFF7F6F3), c(0xFF2EAADC), c(0xFF37352F), c(0xFF9B9A97),
            c(0xFFFFFFFF), c(0xFF37352F), c(0xFF9B9A97),
            barBg = c(0xFF789262), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFE9E7E1), elev = 0, cardRadius = 6, btnRadius = 6),
        Palette("chatgpt", "ChatGPT 极简", "modern", false,
            c(0xFFFFFFFF), c(0xFFF7F7F8), c(0xFF10A37F), c(0xFF0D0D0D), c(0xFF8E8EA0),
            c(0xFFFFFFFF), c(0xFF0D0D0D), c(0xFF8E8EA0),
            barBg = c(0xFFD6ECF0), barText = c(0xFF0D0D0D),
            cardStroke = c(0xFFECECF1), elev = 0, cardRadius = 16, btnRadius = 24),
        Palette("claude", "Claude 米橙", "modern", false,
            c(0xFFFAF9F5), c(0xFFF0EEE6), c(0xFFD97757), c(0xFF3D3929), c(0xFF919086),
            c(0xFFFAF9F5), c(0xFF3D3929), c(0xFF919086),
            barBg = c(0xFF1A2847), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFE5E1D6), elev = 0, solidBtn = false),
        Palette("perplexity", "Perplexity 青", "modern", false,
            c(0xFFF3F3EE), c(0xFFFCFCF9), c(0xFF20808D), c(0xFF13343B), c(0xFF7A8B8F),
            c(0xFFFCFCF9), c(0xFF13343B), c(0xFF7A8B8F),
            barBg = c(0xFFD6ECF0), barText = c(0xFF13343B),
            cardStroke = c(0xFFE4E4DC), elev = 4),
        Palette("cat_latte", "Catppuccin 拿铁", "modern", false,
            c(0xFFEFF1F5), c(0xFFE6E9EF), c(0xFF1E66F5), c(0xFF4C4F69), c(0xFF7C7F93),
            c(0xFFFFFFFF), c(0xFF4C4F69), c(0xFF8C8FA3),
            barBg = c(0xFF789262), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFFBCC0CC), elev = 0)
    )

    // ============ 现代经典 · 暗色（9） ============
    val modernDark = listOf(
        Palette("github", "GitHub 夜", "modern", true,
            c(0xFF0D1117), c(0xFF161B22), c(0xFF2F81F7), c(0xFFE6EDF3), c(0xFF8B949E),
            c(0xFF161B22), c(0xFFE6EDF3), c(0xFF8B949E),
            barBg = c(0xFFD6ECF0), barText = c(0xFF0D1117),
            cardStroke = c(0xFF30363D), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("discord", "Discord 夜", "modern", true,
            c(0xFF313338), c(0xFF2B2D31), c(0xFF5865F2), c(0xFFDBDEE1), c(0xFF949BA4),
            c(0xFF2B2D31), c(0xFFDBDEE1), c(0xFF949BA4),
            barBg = c(0xFFF0C239), barText = c(0xFF1A1B26),
            cardStroke = c(0xFF3F4147), elev = 0),
        Palette("cyber", "赛博 2077", "modern", true,
            c(0xFF0D0D10), c(0xFF16161A), c(0xFFFCEE0A), c(0xFFF2F2EE), c(0xFF9A9A8F),
            c(0xFF101014), c(0xFFF2F2EE), c(0xFF8F8F86),
            barBg = c(0xFF1A2847), barText = c(0xFFF0C239),
            cardStroke = c(0xFF33321E), elev = 0, cardRadius = 6, btnRadius = 4),
        Palette("violet", "紫电夜", "modern", true,
            c(0xFF170F2B), c(0xFF221742), c(0xFF9C5CFF), c(0xFFF1EAFE), c(0xFF9E90C4),
            c(0xFF221742), c(0xFFF1EAFE), c(0xFF9E90C4),
            barBg = c(0xFFF0C239), barText = c(0xFF221742),
            cardStroke = c(0xFF33265A), elev = 0),
        Palette("cat_mocha", "Catppuccin 摩卡", "modern", true,
            c(0xFF1E1E2E), c(0xFF313244), c(0xFF89B4FA), c(0xFFCDD6F4), c(0xFFA6ADC8),
            c(0xFF181825), c(0xFFCDD6F4), c(0xFFA6ADC8),
            barBg = c(0xFF789262), barText = c(0xFFFFFFFF),
            cardStroke = c(0xFF45475A), elev = 0),
        Palette("nord", "Nord 北欧", "modern", true,
            c(0xFF2E3440), c(0xFF3B4252), c(0xFF88C0D0), c(0xFFECEFF4), c(0xFFD8DEE9),
            c(0xFF2E3440), c(0xFFECEFF4), c(0xFF93A1B5),
            barBg = c(0xFFD6ECF0), barText = c(0xFF2E3440),
            cardStroke = c(0xFF4C566A), elev = 0, cardRadius = 8, btnRadius = 8),
        Palette("dracula", "Dracula 霓虹", "modern", true,
            c(0xFF282A36), c(0xFF44475A), c(0xFFBD93F9), c(0xFFF8F8F2), c(0xFF9AA0C0),
            c(0xFF21222C), c(0xFFF8F8F2), c(0xFF9AA0C0),
            barBg = c(0xFFF0C239), barText = c(0xFF282A36),
            cardStroke = c(0xFF6272A4), elev = 0),
        Palette("tokyo", "Tokyo Night", "modern", true,
            c(0xFF1A1B26), c(0xFF24283B), c(0xFF7AA2F7), c(0xFFC0CAF5), c(0xFF7982A9),
            c(0xFF16161E), c(0xFFC0CAF5), c(0xFF7982A9),
            barBg = c(0xFFF0C239), barText = c(0xFF1A1B26),
            cardStroke = c(0xFF292E42), elev = 0),
        Palette("ds", "死亡搁浅 · 冷蓝", "modern", true,
            c(0xFF1E262E), c(0xFF262F38), c(0xFF7FB4D9), c(0xFFC8D4DC), c(0xFF7A8A96),
            c(0xFF222B33), c(0xFFC8D4DC), c(0xFF7A8A96),
            barBg = c(0xFF9D2933), barText = c(0xFFF2F3F7),
            cardStroke = c(0xFF3A465E), elev = 0,
            cardRadius = 6, btnRadius = 6, solidBtn = false)
    )

    // ============ 中国传统色（4，Chinese Color Atlas 官方调色板） ============
    val chineseLight = listOf(
        // 宋代美学：月白留白，天水碧为魂，缃色点缀
        Palette("song", "宋代美学 · 天水碧", "chinese", false,
            c(0xFFD6ECF0), c(0xFFFFFFFF), c(0xFF5AA4AE), c(0xFF33454F), c(0xFF758A99),
            c(0xFFFFFFFF), c(0xFF33454F), c(0xFF758A99),
            barBg = c(0xFFF0C239), barText = c(0xFF33454F),
            cardStroke = c(0xFF9FBFBF), elev = 0, cardRadius = 8, btnRadius = 8),
        // 茶文化：竹青为底意，月白留白，茶褐点缀，禅意
        Palette("tea", "茶文化 · 竹青", "chinese", false,
            c(0xFFEFF3EA), c(0xFFFFFFFF), c(0xFF789262), c(0xFF3B4634), c(0xFF9E8368),
            c(0xFFFFFFFF), c(0xFF3B4634), c(0xFF9E8368),
            barBg = c(0xFFF0C239), barText = c(0xFF3B4634),
            cardStroke = c(0xFFC5D1BC), elev = 0, cardRadius = 8, btnRadius = 8)
    )

    val chineseDark = listOf(
        // 水墨网页：墨色打底，月白为字，朱砂为 CTA
        Palette("ink", "水墨 · 朱砂", "chinese", true,
            c(0xFF1A1210), c(0xFF2E211B), c(0xFFFF4C00), c(0xFFD6ECF0), c(0xFF758A99),
            c(0xFF2A1E17), c(0xFFD6ECF0), c(0xFF758A99),
            barBg = c(0xFFD6ECF0), barText = c(0xFF1A1210),
            cardStroke = c(0xFF4A352A), elev = 0),
        // 国潮品牌：玄红为底，描金为点缀
        Palette("guochao", "国潮 · 描金", "chinese", true,
            c(0xFF26120C), c(0xFF331913), c(0xFFFF4C00), c(0xFFD6ECF0), c(0xFF758A99),
            c(0xFF331913), c(0xFFD6ECF0), c(0xFF758A99),
            barBg = c(0xFFEACD76), barText = c(0xFF26120C),
            cardStroke = c(0xFF5A2C1F), elev = 0)
    )

    val palettes: List<Palette> = modernLight + modernDark + chineseLight + chineseDark

    private const val KEY = "theme_id"

    fun current(context: Context): Palette {
        val id = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString(KEY, "deepseek")!!
        return palettes.firstOrNull { it.id == id } ?: palettes[0]
    }

    fun save(context: Context, id: String) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString(KEY, id).apply()
    }

    fun cardDrawable(
        color: Int,
        radiusDp: Float,
        density: Float,
        strokeColor: Int = 0
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusDp * density
        if (strokeColor != 0) setStroke((1 * density).toInt(), strokeColor)
    }

    /**
     * 递归应用主题到视图树（语义色 + 形状语言 + 撞色条）。
     */
    fun applyTo(view: View, pal: Palette, cardIds: Set<Int> = emptySet(), subIds: Set<Int> = emptySet()) {
        val density = view.resources.displayMetrics.density
        when (view) {
            is ViewGroup -> for (i in 0 until view.childCount) applyTo(view.getChildAt(i), pal, cardIds, subIds)
            is Button -> {
                if (view is android.widget.CompoundButton) {
                    view.buttonTintList = ColorStateList.valueOf(pal.accent)
                    view.setTextColor(pal.text)
                } else if (pal.solidBtn) {
                    view.backgroundTintList = ColorStateList.valueOf(pal.accent)
                    view.setTextColor(if (Color.luminance(pal.accent) > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt())
                } else {
                    view.background = cardDrawable(pal.card, pal.btnRadius.toFloat(), density, pal.accent)
                    view.setTextColor(pal.accent)
                }
            }
            is EditText -> {
                view.background = cardDrawable(pal.card, 10f, density, pal.cardStroke)
                view.setTextColor(pal.text)
                view.setHintTextColor(pal.subText)
            }
            is TextView -> {
                // 白名单制：只有明确标记的才上卡片背景，说明文字保持纯文字
                if (view.id in cardIds) {
                    view.background = cardDrawable(pal.card, pal.cardRadius.toFloat(), density, pal.cardStroke)
                }
                view.setTextColor(when (view.id) {
                    in cardIds -> pal.text
                    in subIds -> pal.subText
                    else -> pal.text
                })
            }
        }
    }
}
