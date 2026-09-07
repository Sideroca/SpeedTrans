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
import com.speedtrans.app.R

/**
 * 撞色主题引擎：内置 6 套预设（5 暗 1 浅），
 * 遵循"没人知道最好的 UI，让用户自己选"。
 */
data class Palette(
    val id: String,
    val name: String,
    val bg: Int,        // 页面背景
    val card: Int,      // 卡片背景
    val accent: Int,    // 强调色（按钮/链接）
    val text: Int,      // 主文字
    val subText: Int,   // 次文字
    val panelBg: Int,   // 译文面板背景
    val panelText: Int, // 面板主文字
    val panelSub: Int   // 面板次文字
)

object ThemeEngine {

    private fun c(v: Long) = v.toInt()

    val palettes = listOf(
        Palette("light", "经典浅色",
            c(0xFFFFFFFF), c(0xFFF4F5F7), c(0xFF1E88E5), c(0xFF222222), c(0xFF777777),
            c(0xF7FFFFFF), c(0xFF222222), c(0xFF999999)),
        Palette("neon", "暗夜霓虹",
            c(0xFF0B1020), c(0xFF151B2E), c(0xFF35D0A0), c(0xFFEAEFF7), c(0xFF8B93A7),
            c(0xF00F1526), c(0xFFEAEFF7), c(0xFF8B93A7)),
        Palette("rose", "玫红夜",
            c(0xFF120B10), c(0xFF1E1218), c(0xFFFF2E88), c(0xFFF7EAF1), c(0xFFA98CA0),
            c(0xF01E1218), c(0xFFF7EAF1), c(0xFFA98CA0)),
        Palette("azure", "蔚蓝夜",
            c(0xFF0A0F1A), c(0xFF101A2C), c(0xFF2E9BFF), c(0xFFE8F1FB), c(0xFF7E93AD),
            c(0xF0101A2C), c(0xFFE8F1FB), c(0xFF7E93AD)),
        Palette("lemon", "明黄夜",
            c(0xFF101008), c(0xFF1D1C0F), c(0xFFFFD60A), c(0xFFFBF6E3), c(0xFFA79F86),
            c(0xF01D1C0F), c(0xFFFBF6E3), c(0xFFA79F86)),
        Palette("violet", "紫电夜",
            c(0xFF0D0A18), c(0xFF161129), c(0xFF9C5CFF), c(0xFFEFE9FB), c(0xFF9A8FB8),
            c(0xF0161129), c(0xFFEFE9FB), c(0xFF9A8FB8))
    )

    private const val KEY = "theme_id"

    fun current(context: Context): Palette {
        val id = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString(KEY, "light")!!
        return palettes.firstOrNull { it.id == id } ?: palettes[0]
    }

    fun save(context: Context, id: String) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString(KEY, id).apply()
    }

    fun cardDrawable(color: Int, radiusDp: Float, density: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radiusDp * density
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
                view.backgroundTintList = ColorStateList.valueOf(pal.accent)
                view.setTextColor(if (Color.luminance(pal.accent) > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt())
            }
            is EditText -> {
                view.background = cardDrawable(pal.card, 10f, density)
                view.setTextColor(pal.text)
                view.setHintTextColor(pal.subText)
            }
            is TextView -> {
                view.background = cardDrawable(pal.card, 12f, density)
                view.setTextColor(when (view.id) {
                    in cardIds -> pal.text
                    in subIds -> pal.subText
                    else -> pal.text
                })
            }
        }
    }
}
