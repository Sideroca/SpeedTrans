package com.speedtrans.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.speedtrans.app.R
import com.speedtrans.app.store.SettingsStore
import com.speedtrans.app.theme.ThemeEngine
import kotlin.math.hypot

/**
 * 沉浸式 · 一个显示单元（= 无障碍树里的一个文本节点）。
 * `rect` 现在是**文字字形包围盒**（来自逐字矩形），不是节点元素框；
 * 拿不到逐字矩形时才回退节点框。
 */
data class ImmersiveItem(
    val index: Int,
    val original: String,
    val rect: Rect,
    val bg: Int,
    val textColor: Int,
    val textSizeSp: Float,
    /** true = 无痕替换：用采样页面底色盖住原文，写中文；false = 深色卡片兜底 */
    val seamless: Boolean = false
)

/**
 * 沉浸式翻译叠层（2026-10-17 · 字形框定位）。
 *
 * 只保留覆盖式（2026-10-15 轻响定稿）：
 *  - 覆盖 100% = 纯译文，用深色卡擦掉原文，只留译文；
 *  - 覆盖 <100% = 半透明，原文淡淡透出，译文叠在上面。
 * 卡片矩形优先用 `EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY` 的字形并集；
 * 拿不到才回退节点框。叠层窗口原点会实测补偿。
 *
 * **两套窗口**：① 整屏 `NOT_TOUCHABLE` 叠层（被穿透，自身不接触摸）；
 * ② 独立返回小窗（可拖 · 记忆位置 · 长按复位）——唯一屏幕退出口。
 *
 * 与 [ResultOverlay] 不共用窗口，也不动 `overlayHeightPct` —— 现有两模式零回归。
 */
class ImmersiveOverlay(private val context: Context) {

    private var wm: WindowManager? = null
    private var root: FrameLayout? = null
    private var exit: View? = null

    private var screenW = 0
    private var screenH = 0

    /**
     * 2026-10-17 「小贴」：叠层窗口的 (0,0) 不一定在屏幕左上角（TYPE_APPLICATION_OVERLAY
     * 常被系统放在状态栏下方）。无障碍 rect 是整屏坐标，画卡时必须减掉这个原点。
     */
    private var originX = 0
    private var originY = 0

    private val items = HashMap<Int, ImmersiveItem>()
    private val blocks = HashMap<Int, View>()       // index → 块容器
    private val texts = HashMap<Int, TextView>()    // index → 译文 TextView
    private val order = ArrayList<Pair<Int, Long>>() // index → 面积（大在下、小在上）
    private val handler = Handler(Looper.getMainLooper())

    private var coverAlphaPct = 100
    private var seamlessPct = 100
    private var accent = 0

    /** 关闭请求（点返回小窗） */
    var onClose: (() -> Unit)? = null

    val visible: Boolean get() = root?.isAttachedToWindow == true

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun withAlpha(color: Int, pct: Int): Int {
        val a = (255 * pct / 100f).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    /** 无痕程度：100% 取页面色，0% 取深色卡色 */
    private fun blendColor(pageColor: Int, darkColor: Int, pct: Int): Int {
        val p = pct.coerceIn(0, 100)
        fun ch(shift: Int): Int {
            val a = (pageColor shr shift) and 0xFF
            val b = (darkColor shr shift) and 0xFF
            return (a * p + b * (100 - p)) / 100
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** 铺窗口（幂等）。**返回是否真的挂上**——挂不上要让编排层别置 active（否则球永久失效、无出口） */
    fun ensure(): Boolean {
        if (root != null) return true
        val m = context.resources.displayMetrics
        screenW = m.widthPixels
        screenH = m.heightPixels
        wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val st = SettingsStore(context)
        coverAlphaPct = st.immersiveCoverAlphaPct
        accent = ThemeEngine.current(context).accent

        // ① 穿透叠层（整屏，不接收触摸）
        val box = FrameLayout(context)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 0
        lp.y = 0
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (android.os.Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val w = wm
        if (w == null) {
            root = null
            return false          // WindowManager 拿不到 → 绝不让 root 假置真
        }
        try {
            w.addView(box, lp)
            root = box
        } catch (e: Exception) {
            root = null
            android.util.Log.e("SpeedTrans", "immersive addView 失败", e)
            return false
        }

        // 窗口真正布局后才拿得到屏幕原点：布局变化时重算，并立刻补一次 post
        box.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int
            ) {
                // 布局过程中改子 View 的 LayoutParams 容易触发二次布局，放到下一帧
                v.post { updateOrigin(v) }
            }
        })
        box.post { updateOrigin(box) }

        // ② 返回小窗（独立窗口，唯一退出口；半透明、微不可查）
        val size = dp(44)
        val exitBox = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                // 正方形圆角：透明填充 + 很浅的灰色描边
                cornerRadius = dp(10).toFloat()
                setColor(0x00000000)
                setStroke(dp(1), 0x66808080)   // 比上一版略深一点
            }
        }
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_back_arrow)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0.6f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = "退出沉浸式翻译"
        }
        exitBox.addView(
            icon,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val elp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        elp.gravity = Gravity.TOP or Gravity.START
        placeExit(elp, size)
        attachExitTouch(exitBox, elp, size)
        try {
            wm?.addView(exitBox, elp)
            exit = exitBox
        } catch (e: Exception) {
            // 退出小窗挂不上 → **视为整体失败**：宁可不要叠层，也不能"有叠层没出口"（球会永久惰性）
            android.util.Log.e("SpeedTrans", "immersive 退出小窗 addView 失败", e)
            try {
                wm?.removeViewImmediate(box)
            } catch (_: Exception) {
            }
            root = null
            return false
        }
        return true
    }

    /** 实测叠层窗口在屏幕上的原点；变了就把已画的卡全部平移回去 */
    private fun updateOrigin(v: View) {
        if (!v.isAttachedToWindow) return
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        if (loc[0] != originX || loc[1] != originY) {
            originX = loc[0]
            originY = loc[1]
            com.speedtrans.app.util.DiagLog.log("imm", "叠层原点=($originX,$originY)")
            repositionBlocks()
        }
    }

    private fun positionFor(item: ImmersiveItem): Triple<Int, Int, Int> {
        val padX = if (item.seamless) dp(1) else dp(6)
        val padY = if (item.seamless) dp(1) else dp(5)
        val availW = (screenW - originX).coerceAtLeast(dp(40))
        val left = (item.rect.left - padX - originX)
            .coerceIn(0, (availW - dp(24)).coerceAtLeast(0))
        val maxW = (availW - left - dp(2)).coerceAtLeast(dp(40))
        val top = (item.rect.top - padY - originY).coerceAtLeast(0)
        return Triple(left, top, maxW)
    }

    /** 原点/布局变化后，把已有卡的 margin 重新算一遍（不重建 View） */
    private fun repositionBlocks() {
        blocks.forEach { (idx, v) ->
            val item = items[idx] ?: return@forEach
            val (left, top, maxW) = positionFor(item)
            val lp = v.layoutParams as? FrameLayout.LayoutParams ?: return@forEach
            lp.leftMargin = left
            lp.topMargin = top
            if (item.seamless) {
                lp.width = maxOf(
                    (item.rect.width() + dp(2)).coerceIn(dp(8), maxW),
                    dp(44).coerceAtMost(maxW)
                )
                lp.height = FrameLayout.LayoutParams.WRAP_CONTENT
                val tv = v as? TextView
                if (tv != null) {
                    val density = context.resources.displayMetrics.density
                    tv.minimumHeight = maxOf(
                        item.rect.height() + dp(2),
                        (item.textSizeSp * density * 1.3f).toInt()
                    ).coerceAtLeast(dp(8))
                }
            } else {
                val tv = v as? TextView
                if (tv != null) tv.maxWidth = maxW
            }
            v.layoutParams = lp
        }
    }

    /** 定位返回小窗：读归一化设置，未设置走默认右下角 */
    private fun placeExit(lp: WindowManager.LayoutParams, size: Int) {
        val st = SettingsStore(context)
        val xn = st.immersiveBtnXNorm
        val yn = st.immersiveBtnYNorm
        val cx: Float
        val cy: Float
        if (xn in 0f..1f && yn in 0f..1f) {
            cx = xn * screenW
            cy = yn * screenH
        } else {
            cx = screenW - dp(12) - size / 2f
            cy = screenH - dp(72) - size / 2f
        }
        lp.x = (cx - size / 2f).toInt().coerceIn(0, (screenW - size).coerceAtLeast(0))
        lp.y = (cy - size / 2f).toInt().coerceIn(0, (screenH - size).coerceAtLeast(0))
    }

    private fun saveExitPos(lp: WindowManager.LayoutParams, size: Int) {
        val st = SettingsStore(context)
        st.immersiveBtnXNorm = ((lp.x + size / 2f) / screenW).coerceIn(0f, 1f)
        st.immersiveBtnYNorm = ((lp.y + size / 2f) / screenH).coerceIn(0f, 1f)
    }

    private fun defaultExitNorm(size: Int): Pair<Float, Float> {
        val cx = screenW - dp(12) - size / 2f
        val cy = screenH - dp(72) - size / 2f
        return (cx / screenW).coerceIn(0f, 1f) to (cy / screenH).coerceIn(0f, 1f)
    }

    /** 返回小窗：拖拽（移动）· 点按（退出）· 长按（复位右下角） */
    private fun attachExitTouch(v: View, lp: WindowManager.LayoutParams, size: Int) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var longFired = false
        val longRun = Runnable {
            longFired = true
            val (xn, yn) = defaultExitNorm(size)
            lp.x = (xn * screenW - size / 2f).toInt().coerceIn(0, (screenW - size).coerceAtLeast(0))
            lp.y = (yn * screenH - size / 2f).toInt().coerceIn(0, (screenH - size).coerceAtLeast(0))
            try {
                wm?.updateViewLayout(v, lp)
            } catch (_: Exception) {
            }
            saveExitPos(lp, size)
        }
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    longFired = false
                    handler.postDelayed(longRun, 600)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > 12f) {
                        dragging = true
                        handler.removeCallbacks(longRun)
                    }
                    if (dragging) {
                        lp.x = (startX + dx).toInt().coerceIn(0, (screenW - size).coerceAtLeast(0))
                        lp.y = (startY + dy).toInt().coerceIn(0, (screenH - size).coerceAtLeast(0))
                        try {
                            wm?.updateViewLayout(view, lp)
                        } catch (_: Exception) {
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longRun)
                    when {
                        dragging -> saveExitPos(lp, size)
                        !longFired -> {
                            view.performClick()
                            onClose?.invoke()
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longRun)
            }
            true
        }
    }

    /** 清空已画的块视图（run 与 run 之间必须清，否则旧视图残留 → 同一译文出现多份） */
    fun clearViews() {
        root?.removeAllViews()
        blocks.clear()
        texts.clear()
        order.clear()
    }

    /** 记录本屏要翻译的块（几何 + 采样色） */
    fun setItems(list: List<ImmersiveItem>) {
        // 每轮都重读样式（ensure() 遇到旧窗口会提前返回，缓存会变成"改了没反应"）
        val st = SettingsStore(context)
        coverAlphaPct = st.immersiveCoverAlphaPct
        seamlessPct = st.immersiveSeamlessPct
        com.speedtrans.app.util.DiagLog.log(
            "imm", "本轮覆盖不透明度=$coverAlphaPct% 无痕程度=$seamlessPct%"
        )
        items.clear()
        list.forEach { items[it.index] = it }
    }

    /** 增量渲染：按编号归位；首次出现才建块视图 */
    fun render(map: Map<Int, String>) {
        if (root == null) return
        map.forEach { (idx, text) ->
            if (text.isBlank()) return@forEach
            val item = items[idx] ?: return@forEach
            val tv = texts[idx] ?: makeBlock(item)?.let { texts[idx] } ?: return@forEach
            if (tv.text?.toString() != text) tv.text = text
        }
    }

    private fun makeBlock(item: ImmersiveItem): View? {
        val box = root ?: return null
        // 2026-10-17 「小贴」：无痕替换优先；采样不可靠/无痕程度为 0 时走深色卡兜底。
        return if (item.seamless && seamlessPct > 0) makeSeamlessBlock(box, item)
        else makeCoverBlock(box, item)
    }

    /**
     * 无痕替换：用截屏采样的页面底色盖住这一行原文，再写中文。
     * 卡片大小=这一行的大小；文字垂直居中；字号自动缩到一行内。
     */
    private fun makeSeamlessBlock(box: FrameLayout, item: ImmersiveItem): View? {
        if (item.bg == 0 || item.textColor == 0) return makeCoverBlock(box, item)
        val dark = 0xFF15171A.toInt()
        val bg = blendColor(item.bg, dark, seamlessPct)
        val ink = blendColor(item.textColor, 0xFFFFFFFF.toInt(), seamlessPct)
        val padX = dp(1)
        val padY = dp(1)
        val (left, top, maxW) = positionFor(item)
        // 短标签（登录 / OK / Docs）原文框可能只有几十 px；给一个最小宽度，
        // 否则中文会被挤成一字一行（“登”上“录”下）。
        val w = maxOf(
            (item.rect.width() + padX * 2).coerceIn(dp(8), maxW),
            dp(44).coerceAtMost(maxW)
        )
        val density = context.resources.displayMetrics.density
        // 行框可能只有 11px（边缘/被裁的行）；不能把文字硬塞进这个高度。
        // 最小高度 = 行框高度，或 1.3 倍字号（保证字不全被切）。
        val minH = maxOf(
            item.rect.height() + padY * 2,
            (item.textSizeSp * density * 1.3f).toInt()
        ).coerceAtLeast(dp(8))
        val tv = TextView(context).apply {
            setTextColor(ink)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, item.textSizeSp)
            typeface = android.graphics.Typeface.DEFAULT
            setLineSpacing(0f, 1f)
            setIncludeFontPadding(false)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(1), 0, dp(1), 0)
            // 译文长就换行、让卡向下长，不再 ellipsize 截断。
            // 不设 12 行上限：BBC 这类长段落/合并块仍可能超过 12 行，超出的部分会直接消失。
            maxLines = 100
            ellipsize = null
            minimumHeight = minH
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(1).toFloat()
                // 无痕替换必须不透明；无痕程度只混合页面底色/深色卡
                setColor(bg)
            }
            // 仍然尽量把字号缩到一行宽；缩不下去就换行
            setAutoSizeTextTypeUniformWithConfiguration(
                8, item.textSizeSp.toInt().coerceAtLeast(9), 1, TypedValue.COMPLEX_UNIT_SP
            )
        }
        val lp = FrameLayout.LayoutParams(w, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = left
        lp.topMargin = top
        if (blocks.size < 3) {
            com.speedtrans.app.util.DiagLog.log(
                "ui",
                "无痕块${item.index} 行框=(${item.rect.left},${item.rect.top}) " +
                        "${item.rect.width()}x${item.rect.height()} bg=#${Integer.toHexString(item.bg)} " +
                        "ink=#${Integer.toHexString(item.textColor)} 字号=${"%.1f".format(item.textSizeSp)}"
            )
        }
        addBlock(box, tv, item, lp)
        blocks[item.index] = tv
        texts[item.index] = tv
        return tv
    }

    /** 纯译文 / 丙：底色铺满整块，译文写在上面（不透明度由滑条决定） */
    private fun makeCoverBlock(box: FrameLayout, item: ImmersiveItem): View? {
        // 深色卡兜底：位置由 positionFor() 统一加 6dp/5dp 外扩
        val (left, top, maxW) = positionFor(item)

        // 2026-10-15 轻响定稿：**不用"采样页面底色"**（白底页=白块，等于没底色）。
        // 改为**深色卡片**：不透明度滑条直接控制这块深色的浓淡（100%=实心黑；调低=原文透出）。
        val card = 0xFF15171A.toInt()
        val tv = TextView(context).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, item.textSizeSp * 1.22f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setLineSpacing(0f, 1.15f)
            setIncludeFontPadding(false)
            setPadding(dp(6), dp(2), dp(6), dp(2))
            // 取词已改为"无障碍节点"（不再截屏）→ 没有采样底色；只用**单层深色卡**：
            // 100% = 完全不透（原文被盖住）；调低 = 原文透出（这是你的滑条语义）
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(withAlpha(card, coverAlphaPct))
                setStroke(dp(1), 0x1AFFFFFF)   // 细描边，防深色页卡片边界糊成一片
            }
        }
        // 2026-10-17 轻响定稿：**黑框只框译文**，不再强制等于原文块大小。
        // 大段英文译成一行中文时，卡片就只占这一行的高度/宽度；不拿原文框撑大。
        tv.maxWidth = maxW
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        )
        lp.leftMargin = left
        lp.topMargin = top
        if (blocks.size < 3) {
            com.speedtrans.app.util.DiagLog.log(
                "ui",
                "块${item.index} 字形框=(${item.rect.left},${item.rect.top}) ${item.rect.width()}x${item.rect.height()}" +
                        " → 落点=(${lp.leftMargin},${lp.topMargin}) 原点=($originX,$originY) " +
                        "最大宽=$maxW 字号=${"%.1f".format(item.textSizeSp * 1.22f)}"
            )
        }
        addBlock(box, tv, item, lp)
        blocks[item.index] = tv
        texts[item.index] = tv
        return tv
    }

    /**
     * 2026-10-17 「小贴」：按面积插入——大卡先加（在下），小卡后加（在上）。
     * 这样叠放顺序与流式返回先后无关，又不用把所有卡预建出来（保速度）。
     */
    private fun addBlock(box: FrameLayout, tv: TextView, item: ImmersiveItem, lp: FrameLayout.LayoutParams) {
        val area = item.rect.width().toLong() * item.rect.height()
        var at = 0
        while (at < order.size && order[at].second >= area) at++
        box.addView(tv, at, lp)
        order.add(at, item.index to area)
    }

    fun close() {
        blocks.clear()
        texts.clear()
        items.clear()
        order.clear()
        try {
            root?.let { wm?.removeViewImmediate(it) }
        } catch (_: Exception) {
        }
        try {
            exit?.let { wm?.removeViewImmediate(it) }
        } catch (_: Exception) {
        }
        root = null
        exit = null
        originX = 0
        originY = 0
        handler.removeCallbacksAndMessages(null)
    }
}
