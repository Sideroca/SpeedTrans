package com.speedtrans.app.theme

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import com.speedtrans.app.R

/**
 * 设置页壳层皮肤系统：骨架固定（Cuff Links 底弧 + 中央面板 + 底部保存），皮可换。
 * 皮肤 = 参数化描述（配色槽 + 形状 + 氛围开关），为「橡皮泥」远景预留扩展位。
 */
data class ShellSkin(
    val id: String,
    val name: String,
    val bg: Int,
    val panelBg: Int,      // 输入框 / 卡片底
    val stroke: Int,       // 边框
    val accent: Int,       // 交互色（焦点 / 选中 / 导航）
    val accentStrong: Int, // 强调色（保存按钮 / 选中指示点）
    val text: Int,
    val subText: Int,
    val cornerDp: Float,   // 切角尺寸（dp）
    val glow: Boolean,     // 输入聚焦发光
    val scanline: Boolean,
    val beam: Boolean
)

object ShellSkins {

    private const val KEY = "shell_skin"

    /** 炫酷黑：纯黑域 + 青蓝交互 + 琥珀金强调 + 切角 + 扫描线 */
    private val dsHolo = ShellSkin(
        "ds_holo", "炫酷黑",
        bg = 0xFF05070A.toInt(),
        panelBg = 0xFF0E1116.toInt(),
        stroke = 0xFF242933.toInt(),
        accent = 0xFF1EA5C7.toInt(),
        accentStrong = 0xFFFBC02D.toInt(),
        text = 0xFFE8ECF1.toInt(),
        subText = 0xFF6E7176.toInt(),
        cornerDp = 8f,
        glow = true,
        scanline = true,
        beam = true
    )

    /** 跟随主界面主题：22 套旧配色穿 DS 骨架（切角保留，氛围关闭） */
    private fun follow(pal: Palette): ShellSkin {
        val d = pal.isDark
        fun blend(base: Int, fg: Int, alpha: Float): Int {
            val a = (alpha * 255).toInt()
            fun ch(b: Int, f: Int) = (b * (255 - a) + f * a) / 255
            return Color.argb(255, ch(Color.red(base), Color.red(fg)),
                ch(Color.green(base), Color.green(fg)), ch(Color.blue(base), Color.blue(fg)))
        }
        val strokeFallback = if (pal.cardStroke != 0) pal.cardStroke
        else blend(pal.bg, if (d) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), 0.12f)
        return ShellSkin(
            "follow_theme", "跟随主界面主题",
            bg = ThemeEngine.backdrop(pal),
            panelBg = pal.card,
            stroke = strokeFallback,
            accent = pal.accent,
            accentStrong = if (pal.barBg != 0) pal.barBg else pal.accent,
            text = pal.text,
            subText = pal.subText,
            cornerDp = 8f,
            glow = false,
            scanline = false,
            beam = false
        )
    }

    fun list(context: Context): List<ShellSkin> = listOf(
        dsHolo,
        follow(ThemeEngine.current(context))
    )

    fun current(context: Context): ShellSkin {
        val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return when (sp.getString(KEY, "follow_theme")) {
            "follow_theme" -> follow(ThemeEngine.current(context))
            else -> dsHolo
        }
    }

    fun save(context: Context, id: String) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString(KEY, id).apply()
    }

    /** 切角八边形 Drawable（DS 骨架通用形状语言） */
    class CutCornerDrawable(
        private var fill: Int,
        private val cornerPx: Float,
        private var stroke: Int = 0,
        private val strokePx: Float = 0f
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            buildPath(b)
            paint.style = Paint.Style.FILL
            paint.color = fill
            canvas.drawPath(path, paint)
            if (stroke != 0 && strokePx > 0) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = strokePx
                paint.color = stroke
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.FILL
            }
        }

        private fun buildPath(b: Rect) {
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val c = minOf(cornerPx, w / 2f, h / 2f)
            path.reset()
            path.moveTo(b.left + c, b.top.toFloat())
            path.lineTo(b.right - c, b.top.toFloat())
            path.lineTo(b.right.toFloat(), b.top + c)
            path.lineTo(b.right.toFloat(), b.bottom - c)
            path.lineTo(b.right - c, b.bottom.toFloat())
            path.lineTo(b.left + c, b.bottom.toFloat())
            path.lineTo(b.left.toFloat(), b.bottom - c)
            path.lineTo(b.left.toFloat(), b.top + c)
            path.close()
        }

        override fun getOutline(outline: Outline) {
            buildPath(bounds)
            outline.setConvexPath(path)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    fun cut(skin: ShellSkin, d: Float): CutCornerDrawable =
        CutCornerDrawable(skin.panelBg, skin.cornerDp * d * 0.75f, skin.stroke, d)

    fun chipBg(skin: ShellSkin, selected: Boolean, d: Float): Drawable =
        if (selected) CutCornerDrawable(skin.accent, skin.cornerDp * d * 0.75f, skin.accent, d)
        else CutCornerDrawable(skin.panelBg, skin.cornerDp * d * 0.75f, skin.stroke, d)

    fun chipText(skin: ShellSkin, selected: Boolean): Int =
        if (selected) {
            if (Color.luminance(skin.accent) > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
        } else skin.subText

    fun cuffIconBg(skin: ShellSkin, active: Boolean, d: Float): Drawable =
        if (active) CutCornerDrawable(blend(skin.bg, skin.accent, 0.20f), skin.cornerDp * d, skin.accent, d)
        else CutCornerDrawable(skin.panelBg, skin.cornerDp * d, skin.stroke, d)

    private fun blend(base: Int, fg: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt()
        fun ch(b: Int, f: Int) = (b * (255 - a) + f * a) / 255
        return Color.argb(255, ch(Color.red(base), Color.red(fg)),
            ch(Color.green(base), Color.green(fg)), ch(Color.blue(base), Color.blue(fg)))
    }

    /**
     * 壳层染色：递归视图树，切角化 + 皮肤配色。
     * CompoundButton 单独走 tint（踩坑 #8）；btnSave 特判为琥珀金描边次级按钮。
     */
    fun applyShell(root: View, skin: ShellSkin, cardIds: Set<Int> = emptySet(), subIds: Set<Int> = emptySet()) {
        val d = root.resources.displayMetrics.density
        when (root) {
            is ViewGroup -> for (i in 0 until root.childCount) applyShell(root.getChildAt(i), skin, cardIds, subIds)
            is Button -> {
                if (root is CompoundButton) {
                    root.buttonTintList = android.content.res.ColorStateList.valueOf(skin.accent)
                    root.setTextColor(skin.text)
                } else if (root.id == R.id.btnSave) {
                    root.background = CutCornerDrawable(Color.TRANSPARENT, skin.cornerDp * d, skin.accentStrong, d)
                    root.setTextColor(skin.accentStrong)
                } else if (root.id == R.id.btnIconRestore || root.id == R.id.btnClearImage) {
                    // 次级按钮（还原成默认图标）：页面底浅染主色 + 完整软描边
                    // 配方：底 = blend(页面底, accent, 深22%/浅16%)；描边 = blend(底, accent, 深34%/浅26%)
                    //       字 = accent 加深30%（面色偏深时反转为提亮25%）
                    val dark = Color.luminance(skin.bg) < 0.4f
                    val fill = blend(skin.bg, skin.accent, if (dark) 0.22f else 0.16f)
                    val stroke = blend(fill, skin.accent, if (dark) 0.34f else 0.26f)
                    root.background = CutCornerDrawable(fill, skin.cornerDp * d * 0.75f, stroke, 1.2f * d)
                    root.setTextColor(
                        if (Color.luminance(fill) > 0.5f) blend(skin.accent, 0xFF000000.toInt(), 0.30f)
                        else blend(skin.accent, 0xFFFFFFFF.toInt(), 0.25f)
                    )
                } else {
                    root.background = CutCornerDrawable(skin.accent, skin.cornerDp * d * 0.75f, skin.accent, d)
                    root.setTextColor(if (Color.luminance(skin.accent) > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            is EditText -> {
                root.background = CutCornerDrawable(skin.panelBg, skin.cornerDp * d * 0.75f, skin.stroke, d)
                root.setTextColor(skin.text)
                root.setHintTextColor(skin.subText)
            }
            is CompoundButton -> {
                root.buttonTintList = android.content.res.ColorStateList.valueOf(skin.accent)
                root.setTextColor(skin.text)
            }
            is SeekBar -> {
                root.progressTintList = android.content.res.ColorStateList.valueOf(skin.accent)
                root.thumbTintList = android.content.res.ColorStateList.valueOf(skin.accent)
            }
            is TextView -> {
                if (root.id in cardIds) {
                    root.background = CutCornerDrawable(skin.panelBg, skin.cornerDp * d, skin.stroke, d)
                }
                root.setTextColor(when {
                    root.id in cardIds -> skin.text
                    root.id in subIds -> skin.subText
                    else -> skin.text
                })
            }
        }
    }

    /** 输入框聚焦发光：描边变交互色（皮肤 glow 关闭时仅换色不发光） */
    fun bindFocusGlow(activity: android.app.Activity, skinProvider: () -> ShellSkin, vararg ids: Int) {
        ids.forEach { id ->
            val et = activity.findViewById<EditText>(id)
            et.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                val s = skinProvider()
                val d = v.resources.displayMetrics.density
                v.background = CutCornerDrawable(
                    s.panelBg, s.cornerDp * d * 0.75f,
                    if (hasFocus) s.accent else s.stroke, d
                )
            }
        }
    }
}
