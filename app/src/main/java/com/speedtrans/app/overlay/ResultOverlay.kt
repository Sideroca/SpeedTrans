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
import android.widget.TextView
import com.speedtrans.app.service.BallService

/**
 * 译文悬浮面板：常驻屏幕底部，不随原文滚动而消失。
 * 标题行 = 状态文字 | 复制 | 关闭。
 */
class ResultOverlay(private val service: BallService) {

    private var root: LinearLayout? = null
    private var tvStatus: TextView? = null
    private var tvOut: TextView? = null
    private var wm: WindowManager? = null

    val visible: Boolean get() = root != null

    private fun dp(v: Int): Int = (v * service.resources.displayMetrics.density + 0.5f).toInt()

    fun ensure() {
        if (root != null) return
        val ctx = service
        wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xF2FFFFFF.toInt())
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(14), dp(8), dp(14), dp(10))
        }

        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val status = TextView(ctx).apply {
            setTextColor(0xFF999999.toInt())
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnCopy = TextView(ctx).apply {
            text = "复制"
            textSize = 13f
            setTextColor(0xFF1E88E5.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(6), dp(6), dp(6))
            setOnClickListener {
                val t = tvOut?.text?.toString() ?: ""
                if (t.isNotEmpty()) {
                    service.copyToClipboard(t)
                    tvStatus?.text = "已复制到剪贴板"
                }
            }
        }
        val btnClose = TextView(ctx).apply {
            text = "✕"
            textSize = 15f
            setTextColor(0xFF666666.toInt())
            setPadding(dp(12), dp(6), 0, dp(6))
            setOnClickListener { close() }
        }
        top.addView(status)
        top.addView(btnCopy)
        top.addView(btnClose)

        val out = TextView(ctx).apply {
            setTextColor(0xFF222222.toInt())
            textSize = 15f
            setLineSpacing(0f, 1.15f)
            movementMethod = ScrollingMovementMethod()
            maxLines = 14
        }
        box.addView(top)
        box.addView(out)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.START
        // gravity 为 BOTTOM|START 时：x = 距左边缘偏移，y = 距底边缘偏移
        lp.x = dp(10)
        lp.y = dp(16)

        wm?.addView(box, lp)
        root = box
        tvStatus = status
        tvOut = out
    }

    /** 开始一次翻译。reset=true 清空旧译文（全新内容）；false 则在旧译文后继续追加（增量）。 */
    fun begin(reset: Boolean, status: String) {
        ensure()
        if (reset) {
            tvOut?.text = ""
            scrollOutBottom()
        }
        tvStatus?.text = status
    }

    fun showStatus(msg: String) {
        ensure()
        tvStatus?.text = msg
    }

    fun showFinished(srcLen: Int, translated: String) {
        ensure()
        tvOut?.text = translated
        tvStatus?.text = "⚡ 原文 $srcLen 字 · 秒回（内容未变）"
        scrollOutBottom()
    }

    fun append(delta: String) {
        tvOut?.append(delta)
        scrollOutBottom()
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

    private fun scrollOutBottom() {
        val tv = tvOut ?: return
        val layout = tv.layout ?: return
        tv.scrollTo(0, (layout.height - tv.height).coerceAtLeast(0))
    }
}
