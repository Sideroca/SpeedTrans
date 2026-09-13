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
import android.widget.FrameLayout
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

    private var root: View? = null
    private var topBar: LinearLayout? = null

    // ---- 流式滚动"钉住"状态（修复：偶发与手指抢屏 / 不断下滚） ----
    private var userTouching = false
    private var lastTouchUpAt = 0L
    private var streaming = false
    private var catcher: View? = null
    private var tvStatus: TextView? = null
    private var tvOut: TextView? = null
    private var wm: WindowManager? = null

    val visible: Boolean get() = root?.isAttachedToWindow == true

    /** 面板窗口是否持有按键焦点（有焦点时按键直达面板自身，全局键过滤无需插手） */
    val hasKeyFocus: Boolean get() = root?.hasWindowFocus() == true

    private val orphanViews = ArrayList<View>()
    private val sweepHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var sweepTries = 0

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density + 0.5f).toInt()

    fun ensure() {
        sweepOrphans()
        if (root != null) return
        val ctx = context
        val st = SettingsStore(ctx)
        val pal = ThemeEngine.current(ctx)
        val bs = st.overlayButtonScale
        // 撞色条独立选色：用户覆盖优先，未设置跟随主题；条上文字按明度自动黑/白
        val barBg = st.barColorOverride() ?: pal.barBg
        val barTextC = if (Color.luminance(barBg) > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
        wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 视觉面板（底部浮出）：窗口根已改为"全屏"，面板只是它的一个底部子视图
        val panel = LinearLayout(ctx).apply {
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
            // 只记录"手指是否在摸"（append 时的位置锁用）；不再做任何自动拉回——
            // 修复：流式滚动与手指"抢屏"。新原则：内容只追加，视图永不自动滚，整页滚动完全归用户
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
        }
        val out = TextView(ctx).apply {
            setTextColor(pal.panelText)
            textSize = st.overlayTextSize.toFloat()
            setLineSpacing(0f, 1.3f)
            // 长按进入系统文本选择（浮动工具条复制指定内容）；「复制」按钮仍复制全文
            setTextIsSelectable(true)
        }
        scroll.addView(out)

        panel.addView(top)
        topBar = top
        panel.addView(scroll)

        val screenH = ctx.resources.displayMetrics.heightPixels
        val panelH = (screenH * st.overlayHeightPct / 100f).toInt()
        val panelTopY = screenH - panelH

        // 诊断标记：只要"窗外区域"收到触摸，就在触点闪一个圆点——
        // 一次性区分"触摸没到"还是"逻辑没关"（也证明新方案真的接住了触摸）
        val markDot = View(ctx).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0x99FF6A00.toInt())
            }
            visibility = View.GONE
        }

        // 全屏窗口根：面板之外的一切都由它自己接住——"点外面关闭"不再依赖任何隐形捕捉层
        val rootBox = FrameLayout(ctx).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            addView(markDot, FrameLayout.LayoutParams(dp(30), dp(30)))
        }
        panel.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, panelH
        ).apply { gravity = Gravity.BOTTOM }
        rootBox.addView(panel)

        // 迷你返回键（右下角·灰白·不引人注目）：返回键失灵时的保底关闭通道。
        // 点击 = 取消翻译 + 关闭面板（与返回键/✕ 等效）；跟随"显示关闭按钮"开关。
        if (st.showClose) {
            val backIcon = TextView(ctx).apply {
                text = "←"
                textSize = 14f
                // 浅色面板 → 柔灰；深色面板 → 灰白。半透明、无背景，极简
                setTextColor(
                    if (Color.luminance(pal.panelBg) > 0.5f) 0xB08A8F98.toInt()
                    else 0xB0C9CED6.toInt()
                )
                setPadding(dp(6), dp(2), dp(6), dp(2))
                contentDescription = "关闭"
                setOnClickListener {
                    TranslateCoordinator.cancelActive()
                    close()
                }
            }
            rootBox.addView(backIcon, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                rightMargin = dp(10)
                bottomMargin = dp(6)
            })
        }

        // 顶部 8%（状态栏带）不接管：下拉状态栏/截屏走系统；其余区域：点=关（滑动不关）
        val topGuardPx = (screenH * 0.08f).toInt()
        val moveSlopPx = dp(24).toFloat()
        var downX = 0f
        var downY = 0f
        var moved = false
        var armed = false       // 本次手势是否为"窗外点击=关闭"候选
        var onBallDown = false
        rootBox.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (e.y < topGuardPx) {
                        false   // 状态栏带：放行给系统
                    } else {
                        downX = e.x
                        downY = e.y
                        moved = false
                        val b = com.speedtrans.app.service.BallService.instance?.ballBoundsOnScreen()
                        onBallDown = b != null && b.contains(e.rawX.toInt(), e.rawY.toInt())
                        armed = !onBallDown && e.y < panelTopY
                        if (armed) {
                            (markDot.layoutParams as? FrameLayout.LayoutParams)?.let { mlp ->
                                mlp.leftMargin = (e.x - dp(15)).toInt()
                                mlp.topMargin = (e.y - dp(15)).toInt()
                                markDot.layoutParams = mlp
                            }
                            markDot.visibility = View.VISIBLE
                            markDot.postDelayed({ markDot.visibility = View.GONE }, 900)
                        }
                        true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.x - downX
                    val dy = e.y - downY
                    if (dx * dx + dy * dy > moveSlopPx * moveSlopPx) moved = true
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.performClick()   // 无障碍
                    when {
                        onBallDown && !moved ->
                            com.speedtrans.app.service.BallService.instance?.tapFromPanel()
                        armed && !moved ->
                            TranslateCoordinator.onOutsideTouch()   // 点=关；大滑动不关
                    }
                    true
                }
                else -> true
            }
        }

        rootBox.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK &&
                event.action == android.view.KeyEvent.ACTION_DOWN
            ) {
                if (root === rootBox) {
                    TranslateCoordinator.cancelActive()
                    close()
                    // 摘除若被魔改 ROM 卡住：立刻把窗口降级为"不抢焦点、不吞键"，保住返回键活路（防僵尸窗）
                    if (rootBox.isAttachedToWindow) neutralizeZombie(rootBox)
                    true
                } else false
            } else false
        }

        // 全屏窗口（可聚焦收返回键）：触摸投递不依赖透明度、不依赖系统"放行"
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            0,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 0
        lp.y = 0
        // 横屏：允许铺进挖孔区域
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (android.os.Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        wm?.addView(rootBox, lp)
        rootBox.post { if (rootBox.isAttachedToWindow) rootBox.requestFocus() }
        rootBox.requestFocus()
        root = rootBox
        tvStatus = status
        tvOut = out
    }

    fun begin(reset: Boolean, status: String) {
        streaming = true
        ensure()
        if (reset) tvOut?.text = ""
        tvStatus?.text = status
        if (reset) scrollOutTop()
        // 用户再次与球/面板互动（新一轮翻译）：顺手把窗口焦点要回来——返回键可直接生效
        val b = root
        b?.post { if (!b.hasWindowFocus()) b.requestFocus() }
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
        if (!userTouching && android.os.SystemClock.elapsedRealtime() - lastTouchUpAt > 1200L) {
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

    /** 立刻视觉隐藏（任何关闭路径先走这一步：手感即时；摘窗慢一拍也不影响观感） */
    fun hideNow() {
        try {
            root?.animate()?.cancel()
            root?.alpha = 0f
            root?.visibility = View.GONE
            catcher?.visibility = View.GONE
        } catch (_: Exception) {
        }
    }

    fun close() {
        hideNow()
        val r = root
        val c = catcher
        // 先"立即清状态"：返回键/点外面的判定马上不再把本面板算作"开着"——
        // 修复"关闭在途 / 摘除受阻时，后续返回键被反复误吞"的竞态（返回键失灵的残留根因）
        root = null
        tvOut = null
        tvStatus = null
        topBar = null
        catcher = null
        if (r != null) orphanViews.add(r)
        if (c != null) orphanViews.add(c)
        sweepTries = 0
        sweepOrphans()
    }

    /** 摘除遗留窗口：能摘就摘；摘不掉先"降级"（撤焦点/触控）再继续排队——绝不阻塞返回键 */
    private fun sweepOrphans() {
        val it = orphanViews.iterator()
        while (it.hasNext()) {
            val v = it.next()
            removeHard(v)
            if (!v.isAttachedToWindow) it.remove()
        }
        if (orphanViews.isEmpty()) {
            sweepTries = 0
            return
        }
        sweepTries++
        if (sweepTries == 6) {
            orphanViews.forEach { v -> neutralizeZombie(v, schedule = false) }
        }
        if (sweepTries <= 12) {
            sweepHandler.postDelayed({ sweepOrphans() }, 300)
        } else {
            orphanViews.clear()
            sweepTries = 0
        }
    }

    /** 僵尸窗口降级：撤销焦点与触摸能力（保住返回键/触摸的活路），并继续尝试摘除 */
    private fun neutralizeZombie(v: View, schedule: Boolean = true) {
        try {
            v.isFocusable = false
            v.isFocusableInTouchMode = false
            v.clearFocus()
            v.visibility = View.GONE
            (v.layoutParams as? WindowManager.LayoutParams)?.let { lp ->
                lp.flags = lp.flags or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                wm?.updateViewLayout(v, lp)
            }
        } catch (_: Exception) {
        }
        if (schedule && v.isAttachedToWindow) sweepHandler.postDelayed({ removeHard(v) }, 600)
    }

    /** 立即摘窗（removeViewImmediate 为主：removeView 异步排程，主线程忙时会延迟数秒才消失）；失败不静默，补一发 */
    private fun removeHard(v: View?) {
        if (v == null) return
        try {
            wm?.removeViewImmediate(v)
            return
        } catch (_: Exception) {
        }
        try {
            wm?.removeView(v)
        } catch (_: Exception) {
        }
        if (!v.isAttachedToWindow) return
        v.post {
            try {
                wm?.removeViewImmediate(v)
            } catch (_: Exception) {
            }
        }
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
