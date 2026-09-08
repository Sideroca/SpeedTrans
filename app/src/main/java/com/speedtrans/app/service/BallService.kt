package com.speedtrans.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.ImageView
import android.widget.TextView
import com.speedtrans.app.ocr.OcrEngine
import com.speedtrans.app.store.SettingsStore
import com.speedtrans.app.translate.TextCollector
import com.speedtrans.app.translate.TranslateCoordinator
import java.io.File
import kotlin.math.hypot

/**
 * 无障碍服务：绘制悬浮球 + 取词/截屏 + 触发翻译。
 *
 * 取词三态（设置页/通知栏可切）：
 * - 📄 仅文本：无障碍节点取词（原文零误差）
 * - 🖼 仅识图：静默截屏 + 端侧 OCR（游戏/图片/视频字幕，100% 画面内容）
 * - 🤖 智能（默认）：先取词，字数低于阈值或前台是游戏 → 自动转识图
 *
 * 双击悬浮球（时间窗可调）= 取消当前 + 强制识图翻当前画面。
 * 识图截屏完全静默（无动画无声音不留文件），图像仅在本地识别。
 */
class BallService : AccessibilityService() {

    companion object {
        private const val TAG = "SpeedTrans"
            const val ACTION_OCR = "com.speedtrans.app.action.OCR"

        @Volatile
        var instance: BallService? = null
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var ball: View? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var lastRects: List<android.graphics.Rect> = emptyList()

    /** 上一次成功提交翻译的完整原文 —— 用于增量翻译判断 */
    private var lastSource: String = ""

    /** 上一次的完整译文 —— 用于相同内容秒回 */
    private var lastTranslation: String = ""

    private var currentCall: okhttp3.Call? = null

    /** 前台应用包名（窗口切换事件跟踪，用于游戏检测） */
    private var fgPackage: String = ""

    override fun onCreate() {
        super.onCreate()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        mainHandler.post { showBall() }
        // 后台预热 OCR 模型（消除首次识图的冷启动）
        mainHandler.postDelayed({ warmUpOcr() }, 800)
    }

    override fun onDestroy() {
        instance = null
        mainHandler.post { hideBall() }
        super.onDestroy()
    }

    override fun onInterrupt() {}

    /** 跟踪前台应用包名（游戏检测用） */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrEmpty() && pkg != packageName) fgPackage = pkg
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 通知栏「🖼 识图翻译」入口：全屏游戏场景下拉通知即可触发
        if (intent?.action == ACTION_OCR) {
            mainHandler.post { captureAndOcr(force = true) }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** 全局返回键过滤：译文面板打开时，按返回 = 关闭面板 */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_BACK
        ) {
            val ov = TranslateCoordinator.overlay(this)
            if (ov?.visible == true) {
                mainHandler.post { ov.close() }
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    private fun wm(): WindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    // ---------------- 悬浮球 ----------------

    private fun showBall() {
        if (!Settings.canDrawOverlays(this) || ball != null) return
        val st = SettingsStore(this)
        val sizePx = dp(st.ballSizeDp)

        val lp = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(12)
        lp.y = dp(180)

        val imgPath = st.ballImagePath
        val view: View = if (imgPath.isNotEmpty() && File(imgPath).exists()) {
            ImageView(this).apply {
                setImageBitmap(decodeScaled(imgPath, sizePx * 2))
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, o: Outline) {
                        if (st.ballCircle) {
                            o.setOval(0, 0, v.width, v.height)
                        } else {
                            o.setRoundRect(
                                0, 0, v.width, v.height,
                                dp(st.ballSizeDp / 4).toFloat()
                            )
                        }
                    }
                }
            }
        } else {
            TextView(this).apply {
                text = st.ballText
                textSize = st.ballSizeDp * 0.34f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    if (st.ballCircle) {
                        shape = GradientDrawable.OVAL
                    } else {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(st.ballSizeDp / 4).toFloat()
                    }
                    setColor(st.ballColorInt)
                }
            }
        }

        view.setOnTouchListener(BallTouchListener(lp))
        try {
            wm().addView(view, lp)
            ball = view
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

    fun refreshBall() {
        mainHandler.post { hideBall(); showBall() }
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

    // ---------------- 点击判定：单击 = 智能，双击 = 强制识图 ----------------

    private fun onBallTap() {
        // 翻译/识图进行中忽略点球：连点视为未发生，一次只跑第一次的反应
        // 面板刚被窗外触摸关闭（400ms 内）同样忽略——否则球上 UP 会变相复活"取消重翻"
        if (ocrBusy || TranslateCoordinator.busy || TranslateCoordinator.recentlyAutoClosed()) return
        startTranslate()
    }

    // ---------------- 抓取 + 三态判定 + OCR ----------------

    private fun collectScreen(): TextCollector.Collected {
        var root: AccessibilityNodeInfo? = rootInActiveWindow
        // 译文面板可聚焦，可能成为活动窗口：此时改取其下方最新的第三方应用窗口
        if (root?.packageName?.toString() == packageName) {
            root = windows.firstOrNull { w ->
                w.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                        w.root?.packageName?.toString() != packageName
            }?.root ?: root
        }
        val c = if (root != null) TextCollector.collectWithRects(root, packageName)
                else TextCollector.Collected("", emptyList())
        val maxChars = SettingsStore(this).maxChars
        var text = c.text
        if (text.length > maxChars) text = text.take(maxChars) + "\n…[内容过长已截断]"
        lastRects = c.rects
        return TextCollector.Collected(text, c.rects)
    }

    /** 单击入口：按当前模式分发 */
    fun startTranslate() {
        when (SettingsStore(this).translateMode) {
            "ocr" -> captureAndOcr(force = true)
            "text" -> smartTranslate(forceText = true)
            else -> smartTranslate()
        }
    }

    private fun smartTranslate(forceText: Boolean = false) {
        val st = SettingsStore(this)
        val ov = TranslateCoordinator.overlay(this)
        ov.ensure()

        val collected = collectScreen()

        // 智能判定：文字少于阈值 或 前台是游戏 → 自动转识图
        val fewText = collected.text.trim().length < st.smartThresholdChars
        val autoOcr = !forceText && st.ocrFallback && (
                fewText || (st.gameAutoDetect && isForegroundGame())
                )

        if (autoOcr && Build.VERSION.SDK_INT >= 30) {
            ov.showStatus("📷 屏幕文字较少，正在识别画面内容…")
            captureAndOcr(force = false)
            return
        }

        TranslateCoordinator.startTranslate(this, collected.text)
    }

    /** 前台应用是否被系统标记为游戏 */
    private fun isForegroundGame(): Boolean {
        if (fgPackage.isEmpty()) return false
        return try {
            packageManager.getApplicationInfo(fgPackage, 0).category ==
                    ApplicationInfo.CATEGORY_GAME
        } catch (_: Exception) {
            false
        }
    }

    // ---------------- 静默截屏 + 端侧 OCR ----------------

    private var ocrBusy = false

    fun captureAndOcr(force: Boolean, scaled: Boolean = true, allowRetry: Boolean = true) {
        if (ocrBusy || TranslateCoordinator.busy) return
        val ov = TranslateCoordinator.overlay(this)
        ov.ensure()

        if (Build.VERSION.SDK_INT < 30) {
            ov.showStatus("⚠️ 图像识别需要 Android 11 及以上")
            return
        }
        ocrBusy = true
        val t0 = android.os.SystemClock.elapsedRealtime()
        ov.showStatus("📷 正在静默截屏…")

        takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val tShot = android.os.SystemClock.elapsedRealtime() - t0
                val hw = result.hardwareBuffer
                val raw = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                    ?.copy(Bitmap.Config.ARGB_8888, false)
                hw.close()
                if (raw == null) {
                    ocrBusy = false
                    mainHandler.post { ov.showStatus("⚠️ 截屏转换失败") }
                    return
                }
                // 缩放到宽 ≤1080：识别速度提升数倍，常规文字精度足够
                val bmp = if (scaled && raw.width > 1080) {
                    val r = 1080f / raw.width
                    Bitmap.createScaledBitmap(raw, 1080, (raw.height * r).toInt(), true)
                } else raw
                val tPrep = android.os.SystemClock.elapsedRealtime() - t0
                mainHandler.post { ov.showStatus("🔍 识别中…（截屏 ${tShot}ms · 预处理 ${tPrep - tShot}ms）") }

                // force（手动/双击入口已移除，现为仅识图模式）= 全屏 100% 内容；
                // 自动回退 = 剔除文本层坐标，与文本路零重复
                val exclude = if (force) emptyList() else lastRects
                OcrEngine.recognize(
                    this@BallService, bmp, exclude,
                    onResult = { t ->
                        val tAll = android.os.SystemClock.elapsedRealtime() - t0
                        ocrBusy = false
                        mainHandler.post {
                            if (t.isNotEmpty()) {
                                ov.showStatus("✓ 识别 ${t.length} 字 · 耗时 ${tAll}ms")
                                TranslateCoordinator.startTranslate(this@BallService, t)
                            } else if (scaled && allowRetry) {
                                // 缩放版识别为空 → 原尺寸重试一次（防小字丢失）
                                ov.showStatus("🔍 缩放识别为空，原尺寸重试…")
                                mainHandler.postDelayed({
                                    captureAndOcr(force, scaled = false, allowRetry = false)
                                }, 150)
                            } else {
                                ov.showStatus("⚠️ 画面中没有识别到文字")
                            }
                        }
                    },
                    onFail = { e ->
                        ocrBusy = false
                        mainHandler.post { ov.showStatus("⚠️ 识别失败：${e?.message ?: "请重试"}") }
                    }
                )
            }

            override fun onFailure(errorCode: Int) {
                // 系统对连续截屏有最短间隔限制：400ms 后自动重试一次
                if (allowRetry) {
                    mainHandler.postDelayed({
                        ocrBusy = false
                        captureAndOcr(force, scaled = false, allowRetry = false)
                    }, 400)
                } else {
                    ocrBusy = false
                    mainHandler.post { ov.showStatus("⚠️ 截屏失败（code $errorCode）") }
                }
            }
        })
    }

    /** 预热：服务连接后后台空跑一遍启用语言的模型，消除首次识别冷启动 */
    private fun warmUpOcr() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                OcrEngine.warmUp(this)
            } catch (_: Exception) {
            }
        }
    }

    private fun decodeScaled(path: String, target: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, opts)
    }

    fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("translation", text))
    }
}
