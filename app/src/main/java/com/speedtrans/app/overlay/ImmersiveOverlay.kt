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
 * 沉浸式 · 一个显示单元（= 一个 OCR 文本块）。
 * `bg` / `textColor` 来自**截屏采样**（跟随页面，而非 App 主题）。
 */
data class ImmersiveItem(
    val index: Int,
    val original: String,
    val rect: Rect,
    val bg: Int,
    val textColor: Int,
    val textSizeSp: Float
)

/**
 * 沉浸式翻译叠层（2026-10-14 · v3 三态）。
 *
 * **三种显示方式（同一个"覆盖不透明度"滑条 + 一个"中英对照"开关）**：
 *  - **纯译文**：覆盖 100% → 用页面底色擦掉原文，只留译文（泡泡派）。
 *  - **丙 · 半透明**：覆盖 <100% → 原文淡淡透出，译文叠在上面。
 *  - **甲 · 中英对照**：不覆盖 → 原文原样不动，译文紧贴其**正下方**（沉浸式翻译派）。
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

    private val items = HashMap<Int, ImmersiveItem>()
    private val blocks = HashMap<Int, View>()       // index → 块容器
    private val texts = HashMap<Int, TextView>()    // index → 译文 TextView
    private val handler = Handler(Looper.getMainLooper())

    private var coverAlphaPct = 100
    private var accent = 0

    /** 关闭请求（点返回小窗） */
    var onClose: (() -> Unit)? = null

    val visible: Boolean get() = root?.isAttachedToWindow == true

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun withAlpha(color: Int, pct: Int): Int {
        val a = (255 * pct / 100f).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
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

        // ② 返回小窗（独立窗口，唯一退出口；半透明、微不可查）
        val size = dp(44)
        val exitBox = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(withAlpha(0xFFFFFFFF.toInt(), 30))
                setStroke(dp(1), 0x55FFFFFF)
            }
        }
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_back_arrow)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0.5f
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
        val r = root ?: return
        r.removeAllViews()
        blocks.clear()
        texts.clear()
    }

    /** 记录本屏要翻译的块（几何 + 采样色） */
    fun setItems(list: List<ImmersiveItem>) {
        // 每轮都重读样式（ensure() 遇到旧窗口会提前返回，缓存会变成"改了没反应"）
        val st = SettingsStore(context)
        coverAlphaPct = st.immersiveCoverAlphaPct
        com.speedtrans.app.util.DiagLog.log("imm", "本轮覆盖不透明度=$coverAlphaPct%")
        items.clear()
        list.forEach { items[it.index] = it }
    }

    /** 增量渲染：按编号归位；首次出现才建块视图 */
    fun render(map: Map<Int, String>) {
        if (root == null) return
        map.forEach { (idx, text) ->
            val item = items[idx] ?: return@forEach
            val tv = texts[idx] ?: makeBlock(item)?.let { texts[idx] } ?: return@forEach
            if (tv.text?.toString() != text) tv.text = text
        }
    }

    private fun makeBlock(item: ImmersiveItem): View? {
        val box = root ?: return null
        // 2026-10-15 轻响：**彻底删除"中英对照"**——只保留覆盖式（层次分明，且滑条真正生效）
        return makeCoverBlock(box, item)
    }

    /** 纯译文 / 丙：底色铺满整块，译文写在上面（不透明度由滑条决定） */
    private fun makeCoverBlock(box: FrameLayout, item: ImmersiveItem): View? {
        val d = context.resources.displayMetrics.density
        // 2026-10-16 真机图证：只按 OCR 框贴 → 原文尾巴从卡片右边露出来（"中英混排"的真相）。
        // 覆盖框**四边各外扩 6dp**，完整吞掉原文（宁可多盖一点，也不要露字）
        val padX = dp(6)
        val padY = dp(5)
        val left = (item.rect.left - padX).coerceIn(0, (screenW - dp(24)).coerceAtLeast(0))
        val maxW = (screenW - left - dp(2)).coerceAtLeast(dp(40))
        val w = (item.rect.width() + padX * 2).coerceAtLeast(dp(48)).coerceAtMost(maxW)

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
                setStroke(dp(2), 0xFFFF0000.toInt())   // 诊断描边（定位后删）
            }
        }
        // 关键：覆盖高度**至少=原文块高度**。否则译文比原文短时只盖住上半截，
        // 原文下半截全露出来 → "长文像是没翻"（真机实测的根因）
        tv.minimumHeight = item.rect.height() + padY * 2
        val lp = FrameLayout.LayoutParams(w, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = left
        lp.topMargin = (item.rect.top - padY).coerceAtLeast(0)
        if (blocks.size < 3) {
            com.speedtrans.app.util.DiagLog.log(
                "ui",
                "块${item.index} 原文框=(${item.rect.left},${item.rect.top}) ${item.rect.width()}x${item.rect.height()}" +
                        " → 落点=(${lp.leftMargin},${lp.topMargin}) 宽=$w 字号=${"%.1f".format(item.textSizeSp * 1.22f)}"
            )
        }
        box.addView(tv, lp)
        blocks[item.index] = tv
        texts[item.index] = tv
        return tv
    }

    fun close() {
        blocks.clear()
        texts.clear()
        items.clear()
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
        handler.removeCallbacksAndMessages(null)
    }
}
