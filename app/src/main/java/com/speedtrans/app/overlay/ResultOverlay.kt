package com.speedtrans.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.speedtrans.app.store.SettingsStore
import com.speedtrans.app.theme.ThemeEngine
import com.speedtrans.app.translate.TranslateCoordinator

/**
 * 译文悬浮面板：高度可调、译文从顶部开始不自动滚、配色随全局主题。
 * 标题栏：✕（左，拇指易达）| 状态 | 复制（右）。
 */
class ResultOverlay(private val context: Context) {

    private var root: LinearLayout? = null
    private var tvStatus: TextView? = null
    private var tvOut: TextView? = null
    private var wm: WindowManager? = null

    val visible: Boolean get() = root != null

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density + 0.5f).toInt()

    fun ensure() {
        if (root != null) return
        val ctx = context
        val st = SettingsStore(ctx)
        val pal = ThemeEngine.current(ctx)
        val bs = st.overlayButtonScale
        // 撞色条独立选色：用户覆盖优先，未设置跟随主题；条上文字按明度自动黑/白
        val barBg = st.barColorOverride() ?: pal.barBg
        val barTextC = if (Color.luminance(barBg) > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
        wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ThemeEngine.cardDrawable(
                pal.panelBg, pal.cardRadius.toFloat(),
                ctx.resources.displayMetrics.density
            )
            elevation = dp(8).toFloat() // 空气感投影（iOS/线框主题为 0）
            setPadding(dp(16), dp(8), dp(16), dp(10))
        }

        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // 传统撞色条：每套主题配一个对撞的传统色
            background = ThemeEngine.cardDrawable(
                barBg, pal.cardRadius.toFloat(),
                ctx.resources.displayMetrics.density, pal.cardStroke
            )
        }
        val btnClose = TextView(ctx).apply {
            text = "✕"
            textSize = 16f * bs
            setTextColor(barTextC)
            setPadding(0, dp((8 * bs).toInt()), dp((10 * bs).toInt()), dp((8 * bs).toInt()))
            setOnClickListener {
                TranslateCoordinator.cancelActive()
                close()
            }
        }
        val status = TextView(ctx).apply {
            setTextColor(barTextC)
            textSize = 12f * bs
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(6), 0, dp(6), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnCopy = TextView(ctx).apply {
            text = "复制"
            textSize = 14f * bs
            setTextColor(barTextC)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp((6 * bs).toInt()), dp((8 * bs).toInt()), 0, dp((8 * bs).toInt()))
            setOnClickListener {
                val tv = tvOut ?: return@setOnClickListener
                // 有系统选区 → 只复制所选；无选区 → 复制全文
                val sel = if (tv.hasSelection()) tv.text.substring(tv.selectionStart, tv.selectionEnd) else ""
                val t = sel.ifEmpty { tv.text?.toString() ?: "" }
                if (t.isNotEmpty()) {
                    TranslateCoordinator.copyToClipboard(ctx, t)
                    tvStatus?.text = if (sel.isEmpty()) "已复制全文到剪贴板" else "已复制所选（${sel.length} 字）"
                }
            }
        }
        // 按钮配置：显示开关 + 左右位置 + 距边缘距离
        val btns = mutableListOf<TextView>()
        if (st.showCopy) btns.add(btnCopy)
        if (st.showClose) btns.add(btnClose)
        top.setPadding(dp(st.btnPaddingDp), 0, dp(st.btnPaddingDp), 0)
        if (st.btnCloseLeft) {
            btns.forEach { top.addView(it) }
            top.addView(status)
        } else {
            top.addView(status)
            btns.forEach { top.addView(it) }
        }

        val scroll = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        val out = TextView(ctx).apply {
            setTextColor(pal.panelText)
            textSize = 16f
            setLineSpacing(0f, 1.3f)
            // 长按进入系统文本选择（浮动工具条复制指定内容）；「复制」按钮仍复制全文
            setTextIsSelectable(true)
        }
        scroll.addView(out)

        box.addView(top)
        box.addView(scroll)

        val screenH = ctx.resources.displayMetrics.heightPixels
        val panelH = (screenH * st.overlayHeightPct / 100f).toInt()
        // 可聚焦窗口：直接监听返回键（不依赖无障碍的按键过滤，ROM 兼容性最好）
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            panelH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, // 点窗外（原文区）= 关面板
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.START
        lp.x = 0
        lp.y = 0

        box.isFocusable = true
        box.isFocusableInTouchMode = true
        box.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK &&
                event.action == android.view.KeyEvent.ACTION_DOWN
            ) {
                TranslateCoordinator.cancelActive()
                close()
                true
            } else false
        }

        // 窗外触摸（原文区）= 关面板；触摸继续传给底层应用（非模态）
        box.setOnTouchListener { _, e ->
            if (e.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                TranslateCoordinator.onOutsideTouch()
                true
            } else false
        }

        wm?.addView(box, lp)
        box.requestFocus()
        root = box
        tvStatus = status
        tvOut = out
    }

    fun begin(reset: Boolean, status: String) {
        ensure()
        if (reset) tvOut?.text = ""
        tvStatus?.text = status
        if (reset) scrollOutTop()
    }

    fun showStatus(msg: String) {
        ensure()
        tvStatus?.text = msg
    }

    fun showFinished(srcLen: Int, translated: String) {
        ensure()
        tvOut?.text = translated
        tvStatus?.text = "⚡ 原文 $srcLen 字 · 秒回（内容未变）"
        scrollOutTop()
    }

    fun append(delta: String) {
        tvOut?.append(delta)
    }

    fun finish(err: Throwable?) {
        tvStatus?.text = if (err == null) "✓ 完成 · 点球继续，✕ 或返回键关闭"
        else "✗ ${err.message?.take(120) ?: "翻译失败"}"
    }

    fun currentText(): String = tvOut?.text?.toString() ?: ""

    fun close() {
        val r = root
        root = null
        tvOut = null
        tvStatus = null
        // 同步立即移除：removeView 是异步排程，主线程忙时会延迟数秒才消失
        r?.let { try { wm?.removeViewImmediate(it) } catch (_: Exception) {} }
    }

    private fun scrollOutTop() {
        tvOut?.scrollTo(0, 0)
    }
}
