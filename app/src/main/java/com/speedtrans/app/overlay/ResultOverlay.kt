package com.speedtrans.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
    private var topBar: LinearLayout? = null

    // ---- 流式滚动"钉住"状态（修复：偶发与手指抢屏 / 不断下滚） ----
    private var userTouching = false
    private var lastTouchUpAt = 0L
    private var pinY = 0
    private var selfScroll = false
    private var streaming = false
    private var catcher: View? = null
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
            // 撞色条：独立选色 = 水浸渐变（主色→原色→提亮）；跟随主题 = 平色
            background = if (st.barColorOverride() != null)
                ThemeEngine.barGradient(
                    barBg, pal.cardRadius * ctx.resources.displayMetrics.density,
                    pal.cardStroke, if (pal.cardStroke != 0) ctx.resources.displayMetrics.density else 0f
                )
            else
                ThemeEngine.cardDrawable(
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
            // 右侧留出与「✕」的间距（跟着按钮大小缩放）——原来 0 间距贴太近
            setPadding(dp((6 * bs).toInt()), dp((8 * bs).toInt()), dp((10 * bs).toInt()), dp((8 * bs).toInt()))
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
            // 流式期间"钉住"滚动位置：用户没摸屏幕时，任何自动滚动都会被拉回原位
            // （修复：偶发与手指抢屏 / 内容不断自动下滚）
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> userTouching = true
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        userTouching = false
                        lastTouchUpAt = android.os.SystemClock.elapsedRealtime()
                    }
                }
                false
            }
            setOnScrollChangeListener { _, _, _, _, _ ->
                val now = android.os.SystemClock.elapsedRealtime()
                if (!streaming) {
                    pinY = scrollY
                } else if (userTouching || now - lastTouchUpAt < 350L) {
                    pinY = scrollY          // 用户自己滑的：跟随
                } else if (!selfScroll && scrollY != pinY) {
                    selfScroll = true
                    scrollTo(0, pinY)       // 非用户滚动：立刻钉回去
                    selfScroll = false
                }
            }
        }
        val out = TextView(ctx).apply {
            setTextColor(pal.panelText)
            textSize = st.overlayTextSize.toFloat()
            setLineSpacing(0f, 1.3f)
            // 长按进入系统文本选择（浮动工具条复制指定内容）；「复制」按钮仍复制全文
            setTextIsSelectable(true)
        }
        scroll.addView(out)

        box.addView(top)
        topBar = top
        box.addView(scroll)

        val screenH = ctx.resources.displayMetrics.heightPixels
        val panelH = (screenH * st.overlayHeightPct / 100f).toInt()
        // 可聚焦窗口：直接监听返回键（不依赖无障碍的按键过滤，ROM 兼容性最好）
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            panelH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            0, // 面板可聚焦收返回键；窗外触摸由全屏捕捉层统一处理（确定性，ROM 无关）
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

        // 全屏捕捉层：垫在面板之下，接住窗外触摸——点原文区一次 = 关面板 + 取消翻译（确定性，ROM 无关）
        val catchView = View(ctx).apply {
            setBackgroundColor(Color.TRANSPARENT)
            // 点球 = 放行给球（继续翻译/累积追加）；点其他空白 = 照旧关闭面板；
            // 在球上拖动 = 不误触（视为未发生，球本身也不会动）
            var downX = 0f; var downY = 0f; var moved = false
            setOnTouchListener { v, e ->
                val b = com.speedtrans.app.service.BallService.instance?.ballBoundsOnScreen()
                val onBall = b != null && b.contains(e.rawX.toInt(), e.rawY.toInt())
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; moved = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downX; val dy = e.rawY - downY
                        if (dx * dx + dy * dy > 60f * 60f) moved = true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.performClick()  // 无障碍
                        if (onBall && !moved) com.speedtrans.app.service.BallService.instance?.tapFromPanel()
                        else if (!onBall) TranslateCoordinator.onOutsideTouch()
                    }
                }
                true
            }
        }
        val clp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        wm?.addView(catchView, clp)
        catcher = catchView

        wm?.addView(box, lp)
        box.requestFocus()
        root = box
        tvStatus = status
        tvOut = out
    }

    fun begin(reset: Boolean, status: String) {
        streaming = true
        if (reset) pinY = 0
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
        val tv = tvOut ?: return
        // 锁存内外两层滚动位置再 append——TextView.append 会请求把新增区域滚到可见处，
        // 长文本流式时会强制把 ScrollView 拉到底部（偶发“页面强制下滑”的根因）
        val sv = tv.parent as? ScrollView
        val keepSv = sv?.scrollY ?: 0
        val keepTv = tv.scrollY
        tv.append(delta)
        // 同步锁一次；用户正在拖动时不抢（非用户滚动由 ScrollView 的"钉住"监听兜底）
        if (!userTouching) {
            if (sv != null && sv.scrollY != keepSv) sv.scrollTo(0, keepSv)
            if (tv.scrollY != keepTv) tv.scrollTo(0, keepTv)
        }
    }

    fun finish(err: Throwable?) {
        streaming = false
        tvStatus?.text = if (err == null) "✓ 完成 · 点球继续"
        else "✗ ${err.message?.take(120) ?: "翻译失败"}"
    }

    fun currentText(): String = tvOut?.text?.toString() ?: ""

    /** 译文文字大小实时生效 */
    fun applyTextSize(spSize: Int) {
        tvOut?.textSize = spSize.toFloat()
    }

    fun close() {
        val r = root
        root = null
        tvOut = null
        tvStatus = null
        topBar = null
        val c = catcher
        catcher = null
        c?.let { try { wm?.removeViewImmediate(it) } catch (_: Exception) {} }
        // 同步立即移除：removeView 是异步排程，主线程忙时会延迟数秒才消失
        r?.let { try { wm?.removeViewImmediate(it) } catch (_: Exception) {} }
    }

    /** 距面板边缘实时生效（设置页滑条拖动时调用） */
    fun applyEdgePadding(padDp: Int) {
        topBar?.setPadding(dp(padDp), 0, dp(padDp), 0)
        topBar?.requestLayout()
    }

    private fun scrollOutTop() {
        tvOut?.scrollTo(0, 0)
    }
}
