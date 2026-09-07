package com.speedtrans.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import com.speedtrans.app.overlay.ResultOverlay
import com.speedtrans.app.store.SettingsStore
import com.speedtrans.app.translate.TextCollector
import com.speedtrans.app.translate.TranslateEngine
import okhttp3.Call
import kotlin.math.hypot

/**
 * 核心：无障碍服务。
 * 职责：绘制悬浮球 + 抓取屏幕文字 + 编排流式翻译。
 */
class BallService : AccessibilityService() {

    companion object {
        private const val TAG = "SpeedTrans"
        /** 单次翻译原文上限（字符），防止极端长文拖垮输出时间 */
        private const val MAX_CHARS = 12000

        @Volatile
        var instance: BallService? = null
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var ball: View? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var overlay: ResultOverlay? = null
    private lateinit var engine: TranslateEngine
    private var currentCall: Call? = null

    /** 上一次成功提交翻译的完整原文 —— 用于增量翻译判断 */
    private var lastSource: String = ""

    /** 上一次的完整译文（窗内全部内容）—— 用于相同内容秒回 */
    private var lastTranslation: String = ""

    override fun onCreate() {
        super.onCreate()
        engine = TranslateEngine(SettingsStore(this))
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        mainHandler.post { showBall() }
    }

    override fun onDestroy() {
        instance = null
        mainHandler.post {
            hideBall()
            overlay?.close()
        }
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    private fun wm(): WindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    // ---------------- 悬浮球 ----------------

    private fun showBall() {
        if (!Settings.canDrawOverlays(this) || ball != null) return
        val tv = TextView(this).apply {
            text = "译"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE6FF4757.toInt())
            }
        }
        val lp = WindowManager.LayoutParams(
            dp(52), dp(52),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(12)
        lp.y = dp(180)
        tv.setOnTouchListener(BallTouchListener(lp))
        try {
            wm().addView(tv, lp)
            ball = tv
            ballParams = lp
        } catch (e: Exception) {
            Log.e(TAG, "showBall", e)
        }
    }

    private fun hideBall() {
        ball?.let { try { wm().removeView(it) } catch (_: Exception) {} }
        ball = null
        ballParams = null
    }

    private inner class BallTouchListener(private val lp: WindowManager.LayoutParams) :
        View.OnTouchListener {

        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (dragging || hypot(dx, dy) > 14f) {
                        dragging = true
                        lp.x = (startX + dx).toInt()
                        lp.y = (startY + dy).toInt()
                        try {
                            wm().updateViewLayout(v, lp)
                        } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) onBallTap()
                }
            }
            return true
        }
    }

    private fun onBallTap() = startTranslate()

    // ---------------- 抓取与翻译 ----------------

    private fun collectScreenText(): String {
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return ""
        var text = TextCollector.collect(root)
        if (text.length > MAX_CHARS) {
            text = text.take(MAX_CHARS) + "\n…[内容过长已截断]"
        }
        return text
    }

    fun startTranslate() {
        val ov = overlay ?: ResultOverlay(this).also { overlay = it }
        ov.ensure()

        val text = collectScreenText()
        if (text.length < 2) {
            ov.showStatus("⚠️ 没有抓到屏幕文字")
            return
        }

        // 1) 内容完全没变：0 请求，直接回显
        if (text == lastSource && lastTranslation.isNotEmpty()) {
            ov.showFinished(text.length, lastTranslation)
            return
        }

        // 2) 增量翻译：内容在增长（典型：模型思考持续输出），
        //    新文本 = 旧文本 + 新增尾巴，只把尾巴发去翻译
        val incremental = lastSource.isNotEmpty() &&
                text.length > lastSource.length &&
                text.startsWith(lastSource)
        val segment = if (incremental) text.substring(lastSource.length) else text

        // 3) 取消进行中的请求，立刻发新的
        currentCall?.cancel()
        ov.begin(
            reset = !incremental,
            status = if (incremental) "⚡ 增量 ${segment.length} 字 · 翻译中…"
            else "⚡ 原文 ${text.length} 字 · 翻译中…"
        )

        currentCall = engine.translate(
            segment,
            onDelta = { d -> mainHandler.post { ov.append(d) } },
            onDone = { err ->
                mainHandler.post {
                    if (err == null) {
                        lastSource = text
                        lastTranslation = ov.currentText()
                        ov.finish(null)
                    } else {
                        ov.finish(err)
                    }
                }
            }
        )
    }

    fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("translation", text))
    }
}
