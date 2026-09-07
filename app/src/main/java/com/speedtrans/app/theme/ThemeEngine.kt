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
 * 主题引擎 v3：十套经典配色图书馆。
 * 每套 = 一条 Palette（语义色 + 质感：描边/投影），来源于各经典软件的公开设计语言。
 */
data class Palette(
    val id: String,
    val name: String,
    val bg: Int,        // 页面背景
    val card: Int,      // 卡片背景
    val accent: Int,    // 强调色
    val text: Int,      // 主文字
    val subText: Int,   // 次文字
    val panelBg: Int,   // 译文面板背景
    val panelText: Int, // 面板主文字
    val panelSub: Int,  // 面板次文字
    val cardStroke: Int, // 卡片描边（0 = 无）
    val elev: Int        // 卡片投影 dp（0 = 无，iOS 分组式为 0）
)

object ThemeEngine {

    private fun c(v: Long) = v.toInt()

    // 分组：浅色系
    val lightPalettes = listOf(
        Palette("ios", "iOS 经典",
            c(0xFFF2F2F7), c(0xFFFFFFFF), c(0xFF007AFF), c(0xFF000000), c(0xFF8E8E93),
            c(0xFFFFFFFF), c(0xFF000000), c(0xFF8E8E93),
            cardStroke = c(0xFFC6C6C8), elev = 0),
        Palette("deepseek", "深海蓝",
            c(0xFFF7F8FA), c(0xFFFFFFFF), c(0xFF4D6BFE), c(0xFF1F2329), c(0xFF828A9B),
            c(0xFFFFFFFF), c(0xFF1F2329), c(0xFF828A9B),
            cardStroke = c(0xFFE3E7EF), elev = 4),
        Palette("kimi", "月白紫",
            c(0xFFFFFFFF), c(0xFFF7F9FC), c(0xFF007CFF), c(0xFF002F5B), c(0xFF8A99AC),
            c(0xFFFFFFFF), c(0xFF002F5B), c(0xFF8A99AC),
            cardStroke = c(0xFFE3E9F4), elev = 4),
        Palette("qq", "QQ 经典蓝",
            c(0xFFF0F4F8), c(0xFFFFFFFF), c(0xFF12B7F5), c(0xFF1A1A1A), c(0xFF86909C),
            c(0xFFFFFFFF), c(0xFF1A1A1A), c(0xFF86909C),
            cardStroke = c(0xFFE1E8F0), elev = 4),
        Palette("notion", "Notion 极简",
            c(0xFFFFFFFF), c(0xFFF7F6F3), c(0xFF2EAADC), c(0xFF37352F), c(0xFF9B9A97),
            c(0xFFFFFFFF), c(0xFF37352F), c(0xFF9B9A97),
            cardStroke = c(0xFFE9E7E1), elev = 0)
    )

    // 分组：暗色系
    val darkPalettes = listOf(
        Palette("github", "GitHub 夜",
            c(0xFF0D1117), c(0xFF161B22), c(0xFF2F81F7), c(0xFFE6EDF3), c(0xFF8B949E),
            c(0xFF161B22), c(0xFFE6EDF3), c(0xFF8B949E),
            cardStroke = c(0xFF30363D), elev = 0),
        Palette("discord", "Discord 夜",
            c(0xFF313338), c(0xFF2B2D31), c(0xFF5865F2), c(0xFFDBDEE1), c(0xFF949BA4),
            c(0xFF2B2D31), c(0xFFDBDEE1), c(0xFF949BA4),
            cardStroke = c(0xFF3F4147), elev = 0),
        Palette("cyber", "赛博 2077",
            c(0xFF0D0D10), c(0xFF16161A), c(0xFFFCEE0A), c(0xFFF2F2EE), c(0xFF9A9A8F),
            c(0xFF101014), c(0xFFF2F2EE), c(0xFF8F8F86),
            cardStroke = c(0xFF33321E), elev = 0),
        Palette("violet", "紫电夜",
            c(0xFF170F2B), c(0xFF221742), c(0xFF9C5CFF), c(0xFFF1EAFE), c(0xFF9E90C4),
            c(0xFF221742), c(0xFFF1EAFE), c(0xFF9E90C4),
            cardStroke = c(0xFF33265A), elev = 0)
    )

    val palettes: List<Palette> = lightPalettes + darkPalettes

    private const val KEY = "theme_id"

    fun current(context: Context): Palette {
        val id = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString(KEY, "deepseek")!!
        return palettes.firstOrNull { it.id == id } ?: palettes[1] // 默认深海蓝
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
     * 递归应用主题到视图树。
     * @param cardIds 使用卡片背景的 TextView 集合（其文字色由业务代码控制）
     * @param subIds 使用次文字色的 TextView 集合
     */
    fun applyTo(view: View, pal: Palette, cardIds: Set<Int> = emptySet(), subIds: Set<Int> = emptySet()) {
        val density = view.resources.displayMetrics.density
        when (view) {
            is ViewGroup -> for (i in 0 until view.childCount) applyTo(view.getChildAt(i), pal, cardIds, subIds)
            is Button -> {
                if (view is android.widget.CompoundButton) {
                    view.buttonTintList = ColorStateList.valueOf(pal.accent)
                    view.setTextColor(pal.text)
                } else {
                    view.backgroundTintList = ColorStateList.valueOf(pal.accent)
                    view.setTextColor(if (Color.luminance(pal.accent) > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            is EditText -> {
                view.background = cardDrawable(pal.card, 10f, density, pal.cardStroke)
                view.setTextColor(pal.text)
                view.setHintTextColor(pal.subText)
            }
            is TextView -> {
                view.background = cardDrawable(pal.card, 12f, density, pal.cardStroke)
                view.setTextColor(when (view.id) {
                    in cardIds -> pal.text
                    in subIds -> pal.subText
                    else -> pal.text
                })
            }
        }
    }
}
