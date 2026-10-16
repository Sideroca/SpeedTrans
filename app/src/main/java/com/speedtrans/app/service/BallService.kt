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
import com.speedtrans.app.overlay.ImmersiveItem
import com.speedtrans.app.store.SettingsStore
import com.speedtrans.app.translate.ImmersiveCoordinator
import com.speedtrans.app.util.DiagLog
import com.speedtrans.app.translate.TextCollector
import com.speedtrans.app.translate.TranslateCoordinator
import java.io.File
import kotlin.math.hypot

/**
 * 无障碍服务：绘制悬浮球 + 取词/截屏 + 触发翻译。
 *
 * 取词两态（设置页/通知栏可切）：
 * - 📄 仅文本：无障碍节点取词（原文零误差）
 * - 🖼 仅识图：静默截屏 + 端侧 OCR（游戏/图片/视频字幕，100% 画面内容）
 * （旧的「🤖 智能」判定与游戏前台检测已退役，历史 smart 值自动迁移为仅文本）
 *
 * 单击悬浮球 = 按当前模式取词/识图。（v3.6 已移除双击手势，见 SPEC §九「双击翻图（已删除）」）
 * 识图截屏完全静默（无动画无声音不留文件），图像仅在本地识别。
 */
class BallService : AccessibilityService() {

    companion object {
        private const val TAG = "SpeedTrans"
            const val ACTION_OCR = "com.speedtrans.app.action.OCR"

        /** 通知栏「🧩 沉浸式翻译」入口（2026-10-14） */
            const val ACTION_IMMERSIVE = "com.speedtrans.app.action.IMMERSIVE"

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
        // 服务回来了：把通知栏从"告警态"恢复成常态
        KeepAliveService.refreshBallState(this)
        mainHandler.post { showBall() }
        // 关键：运行时**再设一次**事件类型——只改 XML 在部分 ROM（含 MIUI）上不重读，
        // 会导致"用户触摸屏幕"事件收不到 → 译文不会随触摸消失（真机实测）
        try {
            val si = serviceInfo
            si.eventTypes = si.eventTypes or AccessibilityEvent.TYPE_TOUCH_INTERACTION_START or AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_VIEW_SCROLLED
            setServiceInfo(si)
            DiagLog.log("a11y", "已启用触摸事件类型 eventTypes=${si.eventTypes}")
        } catch (e: Throwable) {
            DiagLog.log("a11y", "启用触摸事件失败：${e.message}")
        }
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
        // 先掐掉所有待执行的延时任务（OCR 预热 800ms、连接预热 600ms 等），
        // 否则服务已销毁它们还会跑一轮，拿着废 Context 去碰 ML Kit / OkHttp
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post { hideBall() }
        mainHandler.post { TranslateCoordinator.closeOverlay() }   // 服务没了，面板也不该留在屏幕上
        try { ImmersiveCoordinator.close() } catch (_: Exception) {}   // 服务没了，沉浸式叠层也一起收
        super.onDestroy()
    }

    /**
     * 系统解绑（重启 / 被清理 / 应用更新）——悬浮球会就此消失。
     * 必须**在这里**先把 instance 置空：通知内容是照 instance 判"在不在线"的，
     * 而 onDestroy 晚于 onUnbind，等它再置空就晚了。
     */
    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        try { ImmersiveCoordinator.close() } catch (_: Exception) {}
        KeepAliveService.refreshBallState(this)
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        // 系统抢占/静默解绑前也把沉浸式收掉（否则退出小窗会残留）
        try { ImmersiveCoordinator.close() } catch (_: Exception) {}
    }

    /** 窗口事件：智能判定退役后暂无用途，保留空实现（无障碍服务必须覆写） */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 回桌面（launcher 到前台）→ 关闭译文面板，与返回键行为一致。
        // HOME 键不允许被无障碍过滤键拦截（系统限制），所以走"窗口变化 + launcher 当前活跃"这条路。
        // 轻响 2026-10-15 钦定：**只有用户自己碰屏幕才关**（页面自己放动画绝不动它）
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
        ) {
            if (ImmersiveCoordinator.active) {
                DiagLog.log("imm", "用户操作（type=${event.eventType}）→ 关闭沉浸式")
                ImmersiveCoordinator.close()
            }
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        // 沉浸式：页面变化（跨 App / 窗口切换）→ 自动关（"只翻当前屏"的边界保险）
        if (ImmersiveCoordinator.active) {
            // 只认"应用窗口"（Activity）——IME / 系统弹窗 / 通知栏 / 我们自己的叠层 等异包事件不算换页
            val win = try { windows.firstOrNull { it.isActive } } catch (_: Exception) { null }
            if (win != null && win.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                ImmersiveCoordinator.onWindowChanged(win.root?.packageName?.toString(), win.id)
            }
        }
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
        // 通知栏「🧩 沉浸式翻译」入口（2026-10-14）
        if (intent?.action == ACTION_IMMERSIVE) {
            mainHandler.post { startImmersive() }
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

    /**
     * 运行时开关「**全局按键过滤**」。
     *
     * **平时关**：开着的话，系统会把**每一个物理按键**（含音量键）先送到本进程等回话
     * —— 用户 2026-10-10 实测：这正是"音量键按下去卡顿一下"的根因（关掉无障碍立刻顺滑）。
     * **只在译文面板显示期间开**：那时才需要拦"返回键"来关面板。
     *
     * 注意：`accessibility_service_config.xml` 里的 `canRequestFilterKeyEvents="true"`（**能力声明**）
     * 必须保留，否则运行时开不起来；而 `accessibilityFlags` 里的 `flagRequestFilterKeyEvents`
     * （**当前是否接收**）已移除 —— 由本方法动态控制。
     */
    fun setKeyFilter(on: Boolean) {
        runCatching {
            val si = serviceInfo ?: return
            val F = android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            @Suppress("DEPRECATION")
            val has = (si.flags and F) != 0
            if (has == on) return
            @Suppress("DEPRECATION")
            si.flags = if (on) si.flags or F else si.flags and F.inv()
            setServiceInfo(si)
            Log.i(TAG, "key filter -> $on")
        }
    }

    /** 按持久化的开关同步悬浮球可见性（通知条的「隐藏/召唤」按钮调它） */
    fun syncBallVisibility() {
        mainHandler.post {
            if (SettingsStore(this).ballHidden) hideBall() else showBall()
        }
    }

    private fun showBall() {
        if (!Settings.canDrawOverlays(this) || ball != null) return
        // 用户把球藏起来了 → 不重建窗口。**服务照常连着**（不影响自启动/无障碍状态），
        // 只是屏幕上没有球。"隐藏"= 摘窗，没有窗口就没有触摸目标，零误触。
        if (SettingsStore(this).ballHidden) return
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

    // ---------------- 点击判定：单击 = 按当前模式取词/识图（无双击手势） ----------------

    private fun onBallTap() {
        // 沉浸式叠层**真的在屏幕上**时才惰性；叠层没挂上（active 假阳性）时球照常可用
        if (ImmersiveCoordinator.overlayVisible) return
        // 翻译/识图进行中忽略点球：连点视为未发生，一次只跑第一次的反应
        // 面板刚被窗外触摸关闭（400ms 内）同样忽略——否则球上 UP 会变相复活"取消重翻"
        if (ocrBusy || TranslateCoordinator.busy || TranslateCoordinator.recentlyAutoClosed()) return
        DiagLog.log("ball", "点球 → 模式=${SettingsStore(this).translateMode}")
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

    // ---------------- 抓取 + 按当前模式分发 + OCR ----------------

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

    /** 单击入口：按当前**模式**分发（📄仅文本 / 🖼仅识图 / 🧩沉浸式；模式由通知条切换） */
    fun startTranslate() {
        when (SettingsStore(this).translateMode) {
            "immersive" -> startImmersive()
            "ocr" -> captureAndOcr(force = true)
            else -> translateText()
        }
    }

    private fun translateText() {
        val collected = collectScreen()
        TranslateCoordinator.startTranslate(this, collected.text)
    }

    // ---------------- 静默截屏 + 端侧 OCR ----------------

    @Volatile
    private var ocrBusy = false

    fun captureAndOcr(force: Boolean, scaled: Boolean = true, allowRetry: Boolean = true) {
        if (ocrBusy || TranslateCoordinator.busy) return
        val ov = TranslateCoordinator.overlay(this)
        ov.ensure()

        if (Build.VERSION.SDK_INT < 30) {
            ov.showStatus("⚠️ 图像识别需要 Android 11 及以上")
            return
        }
        // 提前拦：OCR 引擎在"一个语言都没勾"时只会回一句笼统的"识别失败：请重试"，
        // 用户会以为是坏了，其实是没勾语言
        if (SettingsStore(this).ocrLanguages.isEmpty()) {
            ov.showStatus("⚠️ 请先在设置里勾选至少一种识别语言")
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
                // 缩放到宽 900：像素量约 -30%，识别更快；识别为空时自动原尺寸重试兜底
                // 宽高必须共用同一个比例 r：此前宽度写死 1080、高度却按 900 的比例算，
                // 图像被横向拉伸 1.2×，且下方按 900 换算的排除矩形只盖住左侧 83% 宽
                //（状态栏右半边的时钟/电量因此漏网，会被认成正文混进译文）
                // 先把原图尺寸存下来：recycle() 之后就不再依赖位图对象本身了
                val rawW = raw.width
                val rawH = raw.height
                val r = if (scaled && rawW > 900) 900f / rawW else 1f
                val bmp = if (r != 1f)
                    Bitmap.createScaledBitmap(raw, 900, (rawH * r).toInt(), true)
                else raw
                if (bmp !== raw) raw.recycle()   // 全屏 ARGB_8888（≈18MB）用完即放，不再每次识图留 18MB 垃圾
                val tPrep = android.os.SystemClock.elapsedRealtime() - t0
                mainHandler.post { ov.showStatus("🔍 识别中…（截屏 ${tShot}ms · 预处理 ${tPrep - tShot}ms）") }

                // force（仅识图模式）= 抛弃文本层、全屏 100% 内容；自动回退 = 剔除文本层坐标
                // 状态栏/导航栏永远排除（系统栏不是翻译对象）
                // 排除清单换算到位图坐标系——比例与上面缩放用的 r 严格一致
                fun toBmp(rc: android.graphics.Rect) = if (r == 1f) rc else android.graphics.Rect(
                    (rc.left * r).toInt(), (rc.top * r).toInt(), (rc.right * r).toInt(), (rc.bottom * r).toInt()
                )
                val sysRects = buildList {
                    val sbH = systemDimenPx("status_bar_height")
                    val nbH = systemDimenPx("navigation_bar_height")
                    if (sbH > 0) add(android.graphics.Rect(0, 0, rawW, sbH))
                    if (nbH > 0 && nbH < rawH) add(android.graphics.Rect(0, rawH - nbH, rawW, rawH))
                }
                val exclude = (if (force) emptyList() else lastRects.map { toBmp(it) }) + sysRects.map { toBmp(it) }
                OcrEngine.recognize(
                    this@BallService, bmp, exclude,
                    onResult = { t ->
                        val tAll = android.os.SystemClock.elapsedRealtime() - t0
                        bmp.recycle()   // 识别已全部结束，位图可以放了
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
                        bmp.recycle()
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

    // ---------------- 沉浸式翻译（2026-10-14） ----------------

    /** 系统栏真实 inset（按当前方向；API30+ 走 WindowMetrics，旧版回退反射 dimen） */
    private data class InsetsPx(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun systemBarInsets(): InsetsPx {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val ins = wm.currentWindowMetrics.windowInsets
                    .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars())
                if (ins.left + ins.top + ins.right + ins.bottom > 0)
                    return InsetsPx(ins.left, ins.top, ins.right, ins.bottom)
            } catch (_: Exception) {
            }
        }
        return InsetsPx(0, systemDimenPx("status_bar_height"), 0, systemDimenPx("navigation_bar_height"))
    }

    private fun toast(msg: String) {
        try {
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    /**
     * 沉浸式入口：静默截屏 → 逐行 OCR → 结构化流式翻译 → 译文原位贴合。
     * 进行中 / 翻译中一律忽略（退出只靠返回小窗 / 页面变化自动关）。
     */
    fun startImmersive() {
        DiagLog.log("imm", "startImmersive() 进入")
        try {
            startImmersiveInner()
        } catch (e: Throwable) {
            // 任何异常都不许把无障碍服务打挂（打挂 → 通知条回退成"未运行" → 按钮消失）
            DiagLog.log("imm", "异常: ${e.javaClass.simpleName}: ${e.message}")
            DiagLog.log("imm", e.stackTrace.take(6).joinToString(" | ") { "${it.fileName}:${it.lineNumber}" })
            toast("沉浸式异常：${e.message?.take(80) ?: e.javaClass.simpleName}")
        }
    }

    private fun startImmersiveInner() {
        if (ImmersiveCoordinator.active) {
            DiagLog.log("imm", "跳过：已在沉浸式中")
            return
        }
        if (ocrBusy || TranslateCoordinator.busy) {
            DiagLog.log("imm", "跳过：忙碌 ocrBusy=$ocrBusy busy=${TranslateCoordinator.busy}")
            return
        }
        val st = SettingsStore(this)
        if (Build.VERSION.SDK_INT < 30) {
            DiagLog.log("imm", "跳过：SDK=${Build.VERSION.SDK_INT} < 30")
            toast("沉浸式需要 Android 11 及以上")
            return
        }
        if (st.ocrLanguages.isEmpty()) {
            DiagLog.log("imm", "跳过：未勾选识别语言")
            toast("请先在设置里勾选至少一种识别语言")
            return
        }
        if (!st.isConfigured) {
            DiagLog.log("imm", "跳过：接口未配置")
            toast("接口未配置：设置 → 🔌 接口")
            return
        }
        // qwen-mt 不再拦：编排层对 MT 模型直接走"逐块请求"（单条 user 协议），对位不依赖模型

        DiagLog.log("imm", "开始截屏…（langs=${st.ocrLanguages}）")
        // 注意：这里**不弹提示**（轻响反馈"弹窗反复弹、很焦虑"）——只在失败时提示

        // 进来前先收掉普通译文面板（避免两套叠层同时挂屏）
        TranslateCoordinator.closeOverlay()

        // 记下当前前台**应用**窗口（包名 + 窗口 id）：之后换 App 或同 App 换窗（网页跳转）就自动关
        val baseWin = windows.firstOrNull {
            it.isActive && it.type == AccessibilityWindowInfo.TYPE_APPLICATION
        }
        val basePkg = baseWin?.root?.packageName?.toString()
            ?: rootInActiveWindow?.packageName?.toString()
        val baseWinId = baseWin?.id ?: -1
        if (basePkg == null) DiagLog.log("imm", "警告：basePkg=null（本次不自动关）")

        ocrBusy = true
        takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                  try {
                    val hw = result.hardwareBuffer
                    val raw = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                    hw.close()
                    if (raw == null) {
                        DiagLog.log("imm", "截屏位图转换失败")
                        ocrBusy = false
                        toast("截屏转换失败")
                        return
                    }
                    DiagLog.log("imm", "截屏成功 ${raw.width}x${raw.height}，开始 OCR")
                    val rawW = raw.width
                    val rawH = raw.height
                    val r = if (rawW > 900) 900f / rawW else 1f
                    val bmp = if (r != 1f)
                        Bitmap.createScaledBitmap(raw, 900, (rawH * r).toInt(), true)
                    else raw
                    if (bmp !== raw) raw.recycle()
                    val back = if (r != 1f) rawW.toFloat() / 900f else 1f

                    // 排除系统栏（状态栏 + 导航栏；横屏导航栏在侧边也一并排除）
                    val inPx = systemBarInsets()
                    fun toBmp(rc: android.graphics.Rect) = if (r == 1f) rc else android.graphics.Rect(
                        (rc.left * r).toInt(), (rc.top * r).toInt(),
                        (rc.right * r).toInt(), (rc.bottom * r).toInt()
                    )
                    val exclude = buildList {
                        if (inPx.top > 0) add(toBmp(android.graphics.Rect(0, 0, rawW, inPx.top)))
                        if (inPx.bottom > 0 && inPx.bottom < rawH)
                            add(toBmp(android.graphics.Rect(0, rawH - inPx.bottom, rawW, rawH)))
                        if (inPx.left > 0) add(toBmp(android.graphics.Rect(0, 0, inPx.left, rawH)))
                        if (inPx.right > 0) add(toBmp(android.graphics.Rect(rawW - inPx.right, 0, rawW, rawH)))
                    }

                    OcrEngine.recognizeBlocks(
                        this@BallService, bmp, exclude,
                        onResult = { blocks ->
                            // ML Kit 回调默认在**主线程**；采样最多约 14 万像素 + 组装 → 挪到后台线程，别卡界面
                            Thread {
                                var items: List<ImmersiveItem> = emptyList()
                                try {
                                    items = buildImmersiveItems(blocks, back, bmp)
                                } catch (t: Throwable) {
                                    DiagLog.log("imm", "组装条目异常：${t.message}")
                                } finally {
                                    bmp.recycle()
                                }
                                DiagLog.log("imm", "OCR 块=${blocks.size} → 过滤后条目=${items.size}")
                                val sample = items.take(3).joinToString("; ") {
                                    "${it.index}:${it.rect.left},${it.rect.top},${it.rect.width()}x${it.rect.height()}"
                                }
                                DiagLog.log("imm", "前几处坐标 $sample")
                                val n = items.size
                                val total = blocks.size
                                mainHandler.post {
                                    // 复位与"启动编排"同一帧 → 消灭空隙重入（否则那段空隙里点球会开第二层）
                                    ocrBusy = false
                                    if (n == 0) toast("识别到 $total 处，但都不像文字（已过滤）")
                                    else ImmersiveCoordinator.start(this@BallService, basePkg, items, baseWinId)
                                }
                            }.start()
                        },
                        onFail = { e ->
                            bmp.recycle()
                            ocrBusy = false
                            DiagLog.log("imm", "OCR 失败: ${e?.message}")
                            mainHandler.post { toast("识别失败：${e?.message ?: "请重试"}") }
                        }
                    )
                  } catch (e: Throwable) {
                    ocrBusy = false
                    DiagLog.log("imm", "截屏回调异常：${e.javaClass.simpleName}: ${e.message}")
                  }
                }

                override fun onFailure(errorCode: Int) {
                    ocrBusy = false
                    DiagLog.log("imm", "截屏失败 code=$errorCode")
                    mainHandler.post { toast("截屏失败（code $errorCode）") }
                }
            }
        )
    }

    /** OCR 块 → 沉浸式条目（换算屏幕坐标 + 采样页面底色/文字色 + 推断字号；限量保首字快） */
    private fun buildImmersiveItems(
        blocks: List<OcrEngine.Block>, back: Float, bmp: Bitmap
    ): List<ImmersiveItem> {
        val st = SettingsStore(this)
        val maxChars = st.maxChars
        val density = resources.displayMetrics.density
        val minH = dp(4)   // 2026-10-15：原 dp(9) 把"文件夹/实用工具"这类正常小标签也误杀；现由置信度/几何把关，尺寸只作兜底
        val out = ArrayList<ImmersiveItem>()
        var total = 0
        val rej = ArrayList<String>()
        for (b in blocks) {
            val br = b.rect ?: continue
            val t = b.text.trim()
            if (t.isEmpty()) continue
            if (br.width() <= 0 || br.height() < minH) {
                if (rej.size < 6) rej.add("[小]${t.take(10)}")
                continue
            }
            if (!looksLikeText(t)) {   // OCR 把符号/图标认成文字的过滤
                if (rej.size < 6) rej.add("[符]${t.take(10)}")
                continue
            }
            // 2026-10-15：原来写死 2200 字 → 密集长文只翻几行（背离"一次请求整屏"）。
            // 改用**用户自己设的**单次上限（默认 12000），保持"整屏一次发出"
            if (total + t.length > maxChars) break
            val rect = android.graphics.Rect(
                (br.left * back).toInt(), (br.top * back).toInt(),
                (br.right * back).toInt(), (br.bottom * back).toInt()
            )
            if (rect.width() <= 0 || rect.height() <= 0) continue
            total += t.length
            val pair = sampleColors(bmp, br)
            val lhScreen = b.lineHeightPx * back
            val sp = if (st.immersiveFontAuto && lhScreen > 0f)
                (lhScreen / density * 0.72f).coerceIn(10f, 26f)
            else st.immersiveTextSize.toFloat()
            out.add(ImmersiveItem(out.size + 1, t, rect, pair.first, pair.second, sp))
            if (out.size >= 400) break      // 只作极端兜底（原 60 太紧）
        }
        if (rej.isNotEmpty()) DiagLog.log("immf", "被过滤样例：${rej.joinToString(" | ")}")
        return out
    }

    /** 从截屏采样：块内出现最多的颜色 = 页面底色；最显眼的异色 = 文字色 */
    private fun sampleColors(bmp: Bitmap, r: android.graphics.Rect): Pair<Int, Int> {
        val l = r.left.coerceIn(0, bmp.width - 1)
        val t = r.top.coerceIn(0, bmp.height - 1)
        val rr = r.right.coerceIn(l + 1, bmp.width)
        val bb = r.bottom.coerceIn(t + 1, bmp.height)
        val stepX = maxOf(1, (rr - l) / 48)
        val stepY = maxOf(1, (bb - t) / 48)
        val cnt = HashMap<Int, Int>()
        val sr = HashMap<Int, Long>()
        val sg = HashMap<Int, Long>()
        val sb = HashMap<Int, Long>()
        var n = 0
        var y = t
        while (y < bb) {
            var x = l
            while (x < rr) {
                val c = bmp.getPixel(x, y)
                val cr = (c shr 16) and 0xFF
                val cg = (c shr 8) and 0xFF
                val cb = c and 0xFF
                val key = ((cr shr 4) shl 8) or ((cg shr 4) shl 4) or (cb shr 4)
                cnt[key] = (cnt[key] ?: 0) + 1
                sr[key] = (sr[key] ?: 0L) + cr
                sg[key] = (sg[key] ?: 0L) + cg
                sb[key] = (sb[key] ?: 0L) + cb
                n++
                x += stepX
            }
            y += stepY
        }
        if (n == 0) return 0xFFFFFFFF.toInt() to 0xFF1A1A1A.toInt()
        val bgKey = cnt.maxByOrNull { it.value }!!.key
        fun avg(k: Int): Int {
            val c = cnt[k]!!.toLong().coerceAtLeast(1L)
            val r0 = (sr[k]!! / c).toInt()
            val g0 = (sg[k]!! / c).toInt()
            val b0 = (sb[k]!! / c).toInt()
            return (0xFF shl 24) or (r0 shl 16) or (g0 shl 8) or b0
        }
        val bg = avg(bgKey)
        var ink = 0
        var bestScore = 0
        for ((k, c) in cnt) {
            if (k == bgKey || c < 2) continue
            val col = avg(k)
            val d = colorDist(col, bg)
            val score = d * c
            if (d > 3600 && score > bestScore) {
                bestScore = score
                ink = col
            }
        }
        if (ink == 0) {
            val lum = (0.299 * ((bg shr 16) and 0xFF) +
                    0.587 * ((bg shr 8) and 0xFF) +
                    0.114 * (bg and 0xFF)) / 255.0
            ink = if (lum > 0.5) 0xFF1A1A1A.toInt() else 0xFFEDEDED.toInt()
        }
        return bg to ink
    }

    /**
     * OCR 乱码过滤：ML Kit 常把图标/几何符号/纯装饰点阵认成"文字"。
     * 判据：① 至少含一个字母或 CJK 字；② "有意义的字符"占比 ≥ 0.6。
     * 意义字符 = 字母 / 数字 / CJK / 空白 / 常见标点。纯符号串会被剔掉。
     */
    private fun looksLikeText(s: String): Boolean {
        if (s.isEmpty()) return false
        var meaningful = 0
        var letterish = 0
        var total = 0
        for (c in s) {
            total++
            when {
                c.isLetterOrDigit() -> {
                    meaningful++; letterish++
                }
                c.isWhitespace() -> meaningful++
                c in ".,!?:;'\"-()[]/\\%$¥€£+_=@#&*·。，、！？：；（）【】《》“”‘’…—～" -> meaningful++
                else -> {
                }
            }
        }
        if (total == 0) return false
        if (letterish == 0) return false
        return meaningful.toFloat() / total >= 0.6f
    }

    private fun colorDist(a: Int, b: Int): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
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
