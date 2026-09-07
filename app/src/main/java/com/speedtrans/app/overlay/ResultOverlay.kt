package com.speedtrans.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.speedtrans.app.store.SettingsStore
import com.speedtrans.app.translate.TranslateCoordinator

/**
 * 译文悬浮面板：
 * - 高度可调（30%~100% 屏高），底部停靠
 * - 译文从顶部开始显示，绝不自动滚动
 * - 增量翻译在旧译文后继续追加
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
        val bs = st.overlayButtonScale
        wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xF7FFFFFF.toInt())
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(16), dp(10), dp(16), dp(12))
        }

        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val status = TextView(ctx).apply {
            setTextColor(0xFF999999.toInt())
            textSize = 12f * bs
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnCopy = TextView(ctx).apply {
            text = "复制"
            textSize = 14f * bs
            setTextColor(0xFF1E88E5.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setPadding(
                dp((14 * bs).toInt()), dp((8 * bs).toInt()),
                dp((6 * bs).toInt()), dp((8 * bs).toInt())
            )
            setOnClickListener {
                val t = tvOut?.text?.toString() ?: ""
                if (t.isNotEmpty()) {
                    TranslateCoordinator.copyToClipboard(ctx, t)
                    tvStatus?.text = "已复制到剪贴板"
                }
            }
        }
        val btnClose = TextView(ctx).apply {
            text = "✕"
            textSize = 16f * bs
            setTextColor(0xFF666666.toInt())
            setPadding(
                dp((14 * bs).toInt()), dp((8 * bs).toInt()),
                dp((4 * bs).toInt()), dp((8 * bs).toInt())
            )
            setOnClickListener { close() }
        }
        top.addView(status)
        top.addView(btnCopy)
        top.addView(btnClose)

        val scroll = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        val out = TextView(ctx).apply {
            setTextColor(0xFF222222.toInt())
            textSize = 16f
            setLineSpacing(0f, 1.3f)
        }
        scroll.addView(out)

        box.addView(top)
        box.addView(scroll)

        val screenH = ctx.resources.displayMetrics.heightPixels
        val panelH = (screenH * st.overlayHeightPct / 100f).toInt()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            panelH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.START
        lp.x = 0
        lp.y = 0

        wm?.addView(box, lp)
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
        tvStatus?.text = if (err == null) "✓ 完成 · 点球继续，✕ 关闭"
        else "✗ ${err.message?.take(120) ?: "翻译失败"}"
    }

    fun currentText(): String = tvOut?.text?.toString() ?: ""

    fun close() {
        root?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        root = null
        tvOut = null
        tvStatus = null
    }

    private fun scrollOutTop() {
        tvOut?.scrollTo(0, 0)
    }
}
