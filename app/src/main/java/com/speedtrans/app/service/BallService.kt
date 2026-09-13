package com.speedtrans.app.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
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

        /** 服务单例：供通知栏/设置页触发；onDestroy 里已置空。lint 静态持有告警在此为误报 */
        @SuppressLint("StaticFieldLeak")
        @Volatile
        var instance: BallService? = null
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var ball: View? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var lastRects: List<android.graphics.Rect> = emptyList()

    override fun onCreate() {
        super.onCreate()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        mainHandler.post { showBall() }
        // 后台预热 OCR 模型（消除首次识图的冷启动）
        mainHandler.postDelayed({ warmUpOcr() }, 800)
        // 桌面图标自愈（旧伪装别名残留 → 复位为默认图标；不必打开主界面）
        try {
            com.speedtrans.app.ui.LauncherAliases.repair(this)
        } catch (_: Exception) {
        }
        // 后台预热翻译连接（DNS/TCP/TLS，首字更快）
        mainHandler.postDelayed({
            try {
                com.speedtrans.app.translate.TranslateEngine(SettingsStore(this@BallService)).warmUp()
            } catch (_: Exception) {
            }
        }, 600)
    }

    override fun onDestroy() {
        instance = null
        mainHandler.post { hideBall() }
        mainHandler.post { TranslateCoordinator.closeOverlay() }   // 服务没了，面板也不该留在屏幕上
        super.onDestroy()
    }

    override fun onInterrupt() {}

    /** 窗口事件：智能判定退役后暂无用途，保留空实现（无障碍服务必须覆写） */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 回桌面（launcher 到前台）→ 关闭译文面板，与返回键行为一致。
        // HOME 键不允许被无障碍过滤键拦截（系统限制），所以走"窗口变化 + launcher 当前活跃"这条路。
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!TranslateCoordinator.overlayVisible) return
        if (!isLauncher(event.packageName?.toString())) return
        val launcherActive = try {
            windows.any { w -> w.isActive && isLauncher(w.root?.packageName?.toString()) }
        } catch (_: Exception) {
            false
        }
        if (launcherActive) mainHandler.post { TranslateCoordinator.closeOverlay() }
    }

    /** 主屏 launcher 包名集合（缓存；各 ROM 桌面都覆盖） */
    private var launcherPkgs: Set<String>? = null

    private fun isLauncher(pkg: String?): Boolean {
        if (pkg.isNullOrEmpty()) return false
        var cached = launcherPkgs
        if (cached == null) {
            val set = HashSet<String>()
            try {
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                packageManager.queryIntentActivities(home, 0).forEach { set.add(it.activityInfo.packageName) }
            } catch (_: Exception) {
            }
            cached = set
            launcherPkgs = set
        }
        return cached.contains(pkg)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 通知栏「🖼 识图翻译」入口：全屏游戏场景下拉通知即可触发
        if (intent?.action == ACTION_OCR) {
            mainHandler.post { captureAndOcr(force = true) }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** 是否刚吞掉过一枚返回键 DOWN：只吞紧随其后的那一个 UP（配对吞，时限 1200ms） */
    private var backDownConsumed = false
    private var backDownConsumedAt = 0L

    /**
     * 全局返回键过滤：只做"焦点不在面板上"的兜底。
     * ① 面板持有焦点 → 不插手（按键直达面板自身 keyListener，最稳路径）；
     * ② 面板开着但焦点丢失 → 吞掉 DOWN 并关面板（配对 UP 一并吞）；
     * ③ 面板已关（含"关闭在途"）→ 绝不吞，返回键直达下层 App。
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val ov = TranslateCoordinator.overlay(this)
                if (ov?.visible == true && !ov.hasKeyFocus) {
                    ov.hideNow()   // 即时视觉反馈：按键一按，面板立即消失（哪怕摘窗慢一拍）
                    mainHandler.post { TranslateCoordinator.closeOverlay() }
                    backDownConsumed = true
                    backDownConsumedAt = android.os.SystemClock.elapsedRealtime()
                    return true
                }
                backDownConsumed = false
            } else if (event.action == KeyEvent.ACTION_UP && backDownConsumed) {
                backDownConsumed = false
                // 只吞与 DOWN 配对的那一枚 UP（时限内有效，防"丢 UP"后长期误吞）
                if (android.os.SystemClock.elapsedRealtime() - backDownConsumedAt < 1200L) return true
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
        val shape = st.ballShape
        // 竖长方形：宽收窄、高不变（长边从上到下）
        val widthPx = if (shape == "rect") (sizePx * 0.62f).toInt() else sizePx

        val lp = WindowManager.LayoutParams(
            widthPx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(12)
        lp.y = dp(180)

        val imgPath = st.ballImagePath
        val view: View = if (imgPath.startsWith("res:")) {
            // 内置图片球（如"吐魂"）：从 drawable 资源解码，完整显示（不裁切）
            val resId = resources.getIdentifier(imgPath.removePrefix("res:"), "drawable", packageName)
            ImageView(this).apply {
                if (resId != 0) setImageBitmap(BitmapFactory.decodeResource(resources, resId))
                scaleType = ImageView.ScaleType.FIT_CENTER
                // 白底垫片：随形状轮廓裁剪——圆形时被图形完全盖住；长方形/三角形等形状下轮廓可见
                setBackgroundColor(0xFFFFFFFF.toInt())
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, o: Outline) {
                        ballOutline(o, shape, v.width, v.height, st.ballSizeDp / 4)
                    }
                }
            }
        } else if (imgPath.isNotEmpty() && File(imgPath).exists()) {
            ImageView(this).apply {
                setImageBitmap(decodeScaled(imgPath, sizePx * 2))
                // 中心裁剪填满球面：横图竖图都不留空边（业界头像裁剪标准做法）
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, o: Outline) {
                        ballOutline(o, shape, v.width, v.height, st.ballSizeDp / 4)
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
                background = ballBackground(shape, st.ballColorInt, st.ballSizeDp / 4)
                // 三角形尖朝上：文字下移让出尖角
                if (shape == "triangle") setPadding(0, dp((st.ballSizeDp * 0.30f).toInt()), 0, 0)
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
                    if (!dragging) {
                        v.performClick()   // 无障碍：让 TalkBack 等能识别为一次点击
                        onBallTap()
                    }
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

    /** 供译文面板捕捉层查询：球在屏幕上的位置（点落在球上要放行给球，而不是关面板） */
    fun ballBoundsOnScreen(): android.graphics.Rect? {
        val v = ball ?: return null
        if (v.width <= 0 || v.height <= 0) return null
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return android.graphics.Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
    }

    /** 面板开着时点球：由捕捉层转交（走完整守卫：翻译中/刚关面板都忽略） */
    fun tapFromPanel() = onBallTap()

    // ---------------- 抓取 + 三态判定 + OCR ----------------

    private fun collectScreen(): TextCollector.Collected {
        var root: AccessibilityNodeInfo? = rootInActiveWindow
        // 译文面板可聚焦，可能成为活动窗口：此时改取其下方最新的第三方应用窗口
        if (root?.packageName?.toString() == packageName) {
            // 找最顶层的第三方应用窗口（按 layer 降序；避免撞上旧的残留窗口）
            root = windows.sortedByDescending { it.layer }.firstOrNull { w ->
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
        // 两态：仅文本 / 仅识图（智能判定已退役）
        when (SettingsStore(this).translateMode) {
            "ocr" -> captureAndOcr(force = true)
            else -> translateText()
        }
    }

    private fun translateText() {
        val collected = collectScreen()
        TranslateCoordinator.startTranslate(this, collected.text)
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
                // 缩放到宽 ≤900：像素量约 -30%，识别更快；识别为空时自动原尺寸重试兜底
                val bmp = if (scaled && raw.width > 900) {
                    val r = 900f / raw.width
                    Bitmap.createScaledBitmap(raw, 1080, (raw.height * r).toInt(), true)
                } else raw
                val tPrep = android.os.SystemClock.elapsedRealtime() - t0
                mainHandler.post { ov.showStatus("🔍 识别中…（截屏 ${tShot}ms · 预处理 ${tPrep - tShot}ms）") }

                // force（仅识图模式）= 抛弃文本层、全屏 100% 内容；自动回退 = 剔除文本层坐标
                // 状态栏/导航栏永远排除（系统栏不是翻译对象）
                // 排除清单统一换算到位图坐标系——修复高分辨率机型缩放后文本层排除失效的存量 bug
                val r = if (scaled && raw.width > 900) 900f / raw.width else 1f
                fun toBmp(rc: android.graphics.Rect) = if (r == 1f) rc else android.graphics.Rect(
                    (rc.left * r).toInt(), (rc.top * r).toInt(), (rc.right * r).toInt(), (rc.bottom * r).toInt()
                )
                val sysRects = buildList {
                    val sbH = systemDimenPx("status_bar_height")
                    val nbH = systemDimenPx("navigation_bar_height")
                    if (sbH > 0) add(android.graphics.Rect(0, 0, raw.width, sbH))
                    if (nbH > 0 && nbH < raw.height) add(android.graphics.Rect(0, raw.height - nbH, raw.width, raw.height))
                }
                val exclude = (if (force) emptyList() else lastRects.map { toBmp(it) }) + sysRects.map { toBmp(it) }
                OcrEngine.recognize(
                    this@BallService, bmp, exclude,
                    onResult = { t ->
                        val tAll = android.os.SystemClock.elapsedRealtime() - t0
                        ocrBusy = false
                        mainHandler.post {
                            if (t.isNotEmpty()) {
                                // 识图文字净化：去开头空白 + 折叠 3+ 连续空行（避免悬浮页顶部出现大片空白）
                                val clean = t.trim().replace(Regex("\\n{3,}"), "\\n\\n")
                                ov.showStatus("✓ 识别 ${clean.length} 字 · 耗时 ${tAll}ms")
                                TranslateCoordinator.startTranslate(this@BallService, clean)
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

    /** 系统尺寸资源（状态栏/导航栏高度）。getIdentifier 反射是唯一可行解（这些 dimen 未公开） */
    @SuppressLint("DiscouragedApi")
    private fun systemDimenPx(name: String): Int = try {
        val res = android.content.res.Resources.getSystem()
        val id = res.getIdentifier(name, "dimen", "android")
        if (id != 0) res.getDimensionPixelSize(id) else 0
    } catch (_: Exception) {
        0
    }

    /** 悬浮球底色/形状（文字球）。参数名避开 shapeName，防止遮蔽 GradientDrawable.shape */
    private fun ballBackground(shapeName: String, color: Int, cornerDp: Int): Drawable = when (shapeName) {
        "roundrect" -> GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(cornerDp).toFloat()
            setColor(color)
        }
        "cut" -> com.speedtrans.app.theme.ShellSkins.CutCornerDrawable(color, dp(8).toFloat(), 0, 0f)
        "rect" -> GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 0f
            setColor(color)
        }
        "triangle" -> triangleDrawable(color)
        else -> GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    }

    /** 图片球轮廓（四种形状全为凸多边形，setConvexPath 可用） */
    private fun ballOutline(o: Outline, shape: String, w: Int, h: Int, cornerDp: Int) {
        when (shape) {
            "roundrect" -> o.setRoundRect(0, 0, w, h, dp(cornerDp).toFloat())
            "rect" -> o.setRect(0, 0, w, h)
            "cut" -> {
                val c = dp(8).toFloat()
                val p = Path()
                p.moveTo(c, 0f); p.lineTo(w - c, 0f)
                p.lineTo(w.toFloat(), c); p.lineTo(w.toFloat(), h - c)
                p.lineTo(w - c, h.toFloat()); p.lineTo(c, h.toFloat())
                p.lineTo(0f, h - c); p.lineTo(0f, c)
                p.close()
                o.setConvexPath(p)
            }
            "triangle" -> {
                val p = Path()
                p.moveTo(w / 2f, 0f)
                p.lineTo(w.toFloat(), h * 0.92f)
                p.lineTo(0f, h * 0.92f)
                p.close()
                o.setConvexPath(p)
            }
            else -> o.setOval(0, 0, w, h)
        }
    }

    /** 三角形球底（文字球用，尖朝上） */
    private fun triangleDrawable(color: Int): Drawable = object : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            path.reset()
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            path.moveTo(w / 2f, 0f)
            path.lineTo(w, h * 0.92f)
            path.lineTo(0f, h * 0.92f)
            path.close()
            p.color = color
            p.style = Paint.Style.FILL
            canvas.drawPath(path, p)
        }
        override fun getOutline(outline: Outline) {
            val b = bounds
            if (b.isEmpty) return
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            path.reset()
            path.moveTo(w / 2f, 0f)
            path.lineTo(w, h * 0.92f)
            path.lineTo(0f, h * 0.92f)
            path.close()
            outline.setConvexPath(path)
        }
        override fun setAlpha(alpha: Int) { p.alpha = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { p.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun decodeScaled(path: String, target: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        // 按长边采样：竖图不再整张解码进内存（3200 高的图曾直解 18MB）
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, opts)
    }

    fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("translation", text))
    }
}
