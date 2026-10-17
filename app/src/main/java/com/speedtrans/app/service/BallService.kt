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
        // 2026-10-17 「小贴」：TYPE_VIEW_SCROLLED(4096) 不能直接当"用户操作"——
        // Chrome 工具栏收起/页面布局变化都会发这个事件，点球后 40ms 就把它误关了
        // （日志里连续多次 type=4096 → close）。现在只认点击/触摸；滚动还要求
        // 过了启动保护期且 scrollDelta 非 0。
        // 2026-10-17：不再用 TYPE_VIEW_SCROLLED 关闭。轮播/视频/动画自己滚动
        // 也会发这个事件，会把译文瞬间关掉。真正的用户滚动会先来
        // TYPE_TOUCH_INTERACTION_START，由下面这一支关闭。
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
        ) {
            if (ImmersiveCoordinator.active) {
                // 点悬浮球本身也可能补一个 click/touch 事件；刚启动的 1 秒内不理
                if (ImmersiveCoordinator.recentlyStarted(1000L)) return
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
        val widthPx = when (shape) {
            "rect" -> (sizePx * 0.62f).toInt()          // 长方形（竖）：宽收窄
            "rectH" -> sizePx                            // 长方形（横）= 竖版长宽对调
            else -> sizePx
        }

        val imagePath0 = st.ballImagePath
        // 图片球"原样显示"：按图片自身比例定盒子（高=尺寸，宽按比例，限幅），不裁切、不套形状
        var asIsW = widthPx
        if (st.ballImageAsIs && imagePath0.isNotEmpty()) {
            try {
                val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                if (imagePath0.startsWith("res:")) {
                    val id = resources.getIdentifier(imagePath0.removePrefix("res:"), "drawable", packageName)
                    if (id != 0) BitmapFactory.decodeResource(resources, id, b)
                } else BitmapFactory.decodeFile(imagePath0, b)
                if (b.outWidth > 0 && b.outHeight > 0) {
                    val ar = b.outWidth.toFloat() / b.outHeight
                    asIsW = (sizePx * ar).toInt().coerceIn((sizePx * 0.5f).toInt(), (sizePx * 2.6f).toInt())
                }
            } catch (_: Exception) {
            }
        }
        val ballW = if (st.ballImageAsIs && imagePath0.isNotEmpty()) asIsW else widthPx
        val hPx = if (shape == "rectH") (sizePx * 0.62f).toInt() else sizePx    // 横版高度 = 竖版宽度（长宽对调）
        val lp = WindowManager.LayoutParams(
            ballW, hPx,
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
                if (st.ballImageAsIs) {
                    // 原样显示：保留比例与透明区，不裁切、不套形状（盒子仍按图片比例）
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(0x00000000)
                } else {
                    // 中心裁剪填满球面：横图竖图都不留空边（业界头像裁剪标准做法）
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(v: View, o: Outline) {
                            ballOutline(o, shape, v.width, v.height, st.ballSizeDp / 4)
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
        // 2026-10-17：普通文本模式也只扫 WebView 子树（若存在），
        // 地址栏/标签栏/Chrome 菜单这些原生 UI 不再混进译文、也不再发给 AI。
        a11yOwned.clear()
        val anchor = if (root != null) findWebAnchor(root) else null
        val target = anchor ?: root
        val screenH = resources.displayMetrics.heightPixels
        val c = if (target != null) TextCollector.collectWithRects(
            target, packageName,
            topLimit = 0,
            // 当前屏 + 半屏，够衔接，又不会把整篇文章 8000 字全发出去
            bottomLimit = (screenH * 3) / 2
        ) else TextCollector.Collected("", emptyList())
        if (Build.VERSION.SDK_INT < 33) a11yOwned.forEach { runCatching { it.recycle() } }
        a11yOwned.clear()
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
        a11yOwned.clear()
        val st = SettingsStore(this)
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

    private fun startImmersiveInner(retry: Int = 0) {
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

        // 2026-10-17 「小贴」：无痕替换需要一张截屏取页面底色（不做 OCR，文字仍走无障碍）。
        // 截屏失败就退回深色卡片，功能不受影响。
        captureImmersive(retry)
    }

    private fun captureImmersive(retry: Int) {
        ocrBusy = true
        try {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val hw = result.hardwareBuffer
                        val raw = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                        hw.close()
                        mainHandler.post { finishCapture(raw, retry) }
                    }

                    override fun onFailure(errorCode: Int) {
                        mainHandler.post { finishCapture(null, retry) }
                    }
                }
            )
        } catch (e: Exception) {
            DiagLog.log("imm", "截屏异常：${e.message?.take(60)} → 深色卡兜底")
            ocrBusy = false
            mainHandler.post { finishCapture(null, retry) }
        }
    }

    private fun finishCapture(raw: Bitmap?, retry: Int) {
        val items = try {
            collectA11yItems(raw)
        } catch (e: Exception) {
            DiagLog.log("imm", "取词异常：${e.javaClass.simpleName}: ${e.message?.take(60)}")
            emptyList()
        } finally {
            raw?.recycle()
        }
        startImmersiveWithItems(items, retry)
    }

    private fun startImmersiveWithItems(items: List<ImmersiveItem>, retry: Int) {
        ocrBusy = false
        DiagLog.log("imm", "无障碍节点条目=${items.size}")
        if (items.isEmpty() && retry < 3) {
            // 页面/窗口可能瞬时还没准备好（Cloudflare 验证、首屏渲染）→ 多给几次机会
            com.speedtrans.app.util.DiagLog.log("a11y", "第 ${retry + 1} 次为空 → 300ms 后重试")
            mainHandler.postDelayed({ startImmersiveInner(retry + 1) }, 300)
            return
        }
        if (items.isEmpty()) {
            toast("没读到可读文本：页面可能还没加载完，等 1 秒再点一次；图片/隐私页请用「仅识图」")
            return
        }
        val aw = windows.firstOrNull { it.isActive && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        ImmersiveCoordinator.start(
            this, aw?.root?.packageName?.toString(), items, aw?.id ?: -1
        )
    }

    /** 无障碍节点取词：每个"可见文本节点"一条（文本 + 真实屏幕矩形），不做行合并 */
    /** 本次遍历 getChild() 出来的节点（API<33 需回收，否则节点池耗尽） */
    private val a11yOwned = ArrayList<AccessibilityNodeInfo>()

    /**
     * 只取"屏幕上看得见"的那段文字（屏幕外的一律不翻）：
     * ① 有逐字矩形 → 取"字符中心落在视口内"的区间（最准），并返回这些字符的并集矩形；
     * ② 没有 → 按"块在屏幕上方露出的比例 + 可见高度比例"估算区间，退回节点框。
     */
    private data class VisibleText(
        val text: String,
        val rect: android.graphics.Rect,
        val glyph: Boolean,
        /** true = 没有逐字矩形，用 StaticLayout 估算出来的行框 */
        val estimated: Boolean = false
    )

    private fun visibleText(
        n: AccessibilityNodeInfo,
        raw: String,
        rect: android.graphics.Rect,
        vp: android.graphics.Rect
    ): VisibleText {
        if (raw.isEmpty()) return VisibleText(raw, android.graphics.Rect(rect), false)
        val rects = charRects(n, raw)
        if (rects != null) {
            var a = -1
            var b = -1
            for (i in rects.indices) {
                val rf = rects[i] ?: continue
                // 用矩形相交而不是"字符中心"：边缘上只露出一半的行也算可见，
                // 否则卡片的第一个可见行会被整行丢掉，看起来又"偏下"了。
                if (rf.bottom > vp.top.toFloat() && rf.top < vp.bottom.toFloat()) {
                    if (a < 0) a = i
                    b = i
                }
            }
            if (a >= 0 && b >= a) {
                // 2026-10-17 「小贴」：不再只拿逐字矩形判断可见区间，**顺手把它的并集当卡片矩形**。
                // 节点框含 padding/行高/点击热区，逐字框才是文字真正占的地方。
                val union = android.graphics.Rect(Int.MAX_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)
                for (i in a..b) {
                    val rf = rects[i] ?: continue
                    union.left = minOf(union.left, rf.left.toInt())
                    union.top = minOf(union.top, rf.top.toInt())
                    union.right = maxOf(union.right, rf.right.toInt())
                    union.bottom = maxOf(union.bottom, rf.bottom.toInt())
                }
                if (union.width() > 0 && union.height() > 0 && union.intersect(vp)) {
                    val aa = a.coerceIn(0, raw.length - 1)
                    val bb = b.coerceIn(aa, raw.length - 1)
                    val t = raw.substring(aa, bb + 1).trim()
                    if (t.isNotEmpty()) return VisibleText(t, union, true)
                }
            }
        }
        val h = rect.height()
        if (h <= 0) return VisibleText(raw, android.graphics.Rect(rect), false)
        val above = ((-rect.top).coerceAtLeast(0)).toFloat() / h
        val visH = (minOf(rect.bottom, vp.bottom) - maxOf(rect.top, vp.top)).coerceAtLeast(0).toFloat()
        val s0 = (raw.length * above).toInt().coerceIn(0, raw.length)
        val e0 = (raw.length * (above + visH / h)).toInt().coerceIn(s0, raw.length)
        val clipped = android.graphics.Rect(rect)
        clipped.intersect(vp)
        return VisibleText(raw.substring(s0, e0).trim(), clipped, false)
    }

    /**
     * 2026-10-17 「小贴」：把一个节点按**视觉行**切开（一行一张无痕补丁）。
     * 同时按 x 间距断栏：同一水平带上的左右两栏不会并成一条超宽行。
     * 拿不到逐字矩形 → 退回整节点一条（深色卡兜底）。
     */
    private fun visibleLines(
        n: AccessibilityNodeInfo,
        raw: String,
        rect: android.graphics.Rect,
        vp: android.graphics.Rect,
        allowFallback: Boolean = true
    ): List<VisibleText> {
        if (raw.isEmpty()) return emptyList()
        val rects = charRects(n, raw)
            ?: return if (allowFallback) fallbackLines(raw, rect, vp) else emptyList()

        class Line(
            val start: Int,
            var end: Int,
            var left: Float,
            var top: Float,
            var right: Float,
            var bottom: Float,
            var cy: Float,
            var h: Float
        )

        val lines = ArrayList<Line>()
        val gap = dp(24).toFloat()
        for (i in rects.indices) {
            val rf = rects[i] ?: continue
            if (rf.bottom <= vp.top.toFloat() || rf.top >= vp.bottom.toFloat()) continue
            val cy = (rf.top + rf.bottom) / 2f
            val last = lines.lastOrNull()
            val same = last != null &&
                    kotlin.math.abs(cy - last.cy) <= maxOf(6f, last.h * 0.6f) &&
                    rf.left - last.right <= gap && last.left - rf.right <= gap
            if (!same) {
                lines.add(Line(i, i, rf.left, rf.top, rf.right, rf.bottom, cy, rf.height()))
            } else {
                last.end = i
                last.left = minOf(last.left, rf.left)
                last.top = minOf(last.top, rf.top)
                last.right = maxOf(last.right, rf.right)
                last.bottom = maxOf(last.bottom, rf.bottom)
                last.cy = (last.top + last.bottom) / 2f
                last.h = maxOf(last.h, rf.height())
            }
        }
        if (lines.isEmpty()) return if (allowFallback) fallbackLines(raw, rect, vp) else emptyList()
        // 有些原生 App 会把整段文字返回成一个“超高的单行”矩形；这种情况同样用 StaticLayout 拆。
        if (lines.size == 1 && (lines[0].bottom - lines[0].top) > dp(60)) {
            return if (allowFallback) fallbackLines(raw, rect, vp) else emptyList()
        }

        val out = ArrayList<VisibleText>(lines.size)
        for (ln in lines) {
            val union = android.graphics.Rect(Int.MAX_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)
            var any = false
            for (i in ln.start..ln.end) {
                val rf = rects[i] ?: continue
                any = true
                union.left = minOf(union.left, rf.left.toInt())
                union.top = minOf(union.top, rf.top.toInt())
                union.right = maxOf(union.right, rf.right.toInt())
                union.bottom = maxOf(union.bottom, rf.bottom.toInt())
            }
            if (!any || union.width() <= 0 || union.height() <= 0) continue
            if (!union.intersect(vp)) continue
            val t = raw.substring(ln.start, ln.end + 1).trim()
            if (t.isNotEmpty()) out.add(VisibleText(t, union, true))
        }
        return if (out.isEmpty()) {
            if (allowFallback) fallbackLines(raw, rect, vp) else emptyList()
        } else out
    }

    /**
     * 2026-10-17 「小贴」：原生 App（Qwen/Compose/普通 TextView）经常不提供逐字矩形，
     * 一个长文本节点只能拿到整段大矩形。这里用 StaticLayout 按节点宽度估算换行，
     * 再按行切成多个矩形——仍然只用无障碍文字，不截图 OCR。
     */
    private fun fallbackLines(
        raw: String,
        rect: android.graphics.Rect,
        vp: android.graphics.Rect
    ): List<VisibleText> {
        if (raw.isEmpty() || rect.width() <= 2 || rect.height() <= 2) return emptyList()
        fun one(): List<VisibleText> {
            val r = android.graphics.Rect(rect)
            if (!r.intersect(vp)) return emptyList()
            val t = raw.trim()
            return if (t.isEmpty()) emptyList() else listOf(VisibleText(t, r, false))
        }
        return try {
            val density = resources.displayMetrics.density
            val baseSp = SettingsStore(this).immersiveTextSize.toFloat().coerceIn(10f, 24f)
            val width = rect.width()

            fun buildLayout(textSizePx: Float): android.text.StaticLayout {
                val paint = android.text.TextPaint().apply {
                    textSize = textSizePx
                    typeface = android.graphics.Typeface.DEFAULT
                    isAntiAlias = true
                }
                return android.text.StaticLayout.Builder
                    .obtain(raw, 0, raw.length, paint, width)
                    .setIncludePad(false)
                    .setLineSpacing(0f, 1f)
                    .build()
            }

            fun layoutHeight(l: android.text.StaticLayout): Int =
                if (l.lineCount > 0) l.getLineBottom(l.lineCount - 1) else 0

            val basePx = baseSp * density
            var layout = buildLayout(basePx)
            if (layoutHeight(layout) <= 0) return one()
            val scale = rect.height().toFloat() / layoutHeight(layout)
            val targetPx = (basePx * scale).coerceIn(8f * density, 24f * density)
            layout = buildLayout(targetPx)
            if (layoutHeight(layout) <= 0) return one()
            val yScale = rect.height().toFloat() / layoutHeight(layout)
            val out = ArrayList<VisibleText>()
            for (i in 0 until layout.lineCount) {
                val start = layout.getLineStart(i)
                val end = layout.getLineEnd(i)
                if (end <= start || start >= raw.length) continue
                val t = raw.substring(start, end.coerceAtMost(raw.length)).trim()
                if (t.isEmpty()) continue
                val top = rect.top + (layout.getLineTop(i) * yScale).toInt()
                val bottom = rect.top + (layout.getLineBottom(i) * yScale).toInt()
                if (bottom <= top) continue
                val lr = android.graphics.Rect(rect.left, top, rect.right, bottom)
                if (!lr.intersect(vp)) continue
                out.add(VisibleText(t, lr, true, estimated = true))
            }
            if (out.isEmpty()) one() else out
        } catch (_: Exception) {
            one()
        }
    }

    /** 逐字矩形（屏幕坐标）；拿不到返回 null。数组里个别字符可为 null（屏幕外）。 */
    private fun charRects(n: AccessibilityNodeInfo, text: String): ArrayList<android.graphics.RectF?>? {
        return try {
            val args = android.os.Bundle().apply {
                // 2026-10-17 「小贴」：必须用**字符位置**专用参数键。
                // 之前误用了 ACTION_ARGUMENT_SELECTION_*（文本选择参数），TextView/Chrome
                // 读不到 start/length → 直接返回 → 所有节点都拿不到逐字矩形（日志 字形框=0/N）。
                putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
                putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, text.length)
            }
            if (!n.refreshWithExtraData(
                    AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, args
                )
            ) return null
            @Suppress("DEPRECATION")
            val arr: Array<*> = (n.extras.getParcelableArray(
                AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
            ) ?: return null)
            if (arr.size < text.length) return null
            val res = ArrayList<android.graphics.RectF?>(arr.size)
            for (o in arr) res.add(o as? android.graphics.RectF)
            res
        } catch (_: Exception) {
            null
        }
    }

    private fun collectA11yItems(bmp: Bitmap? = null): List<ImmersiveItem> {
        val st = SettingsStore(this)
        val density = resources.displayMetrics.density
        val t0 = android.os.SystemClock.elapsedRealtime()
        a11yOwned.clear()
        val rootNode = rootInActiveWindow ?: return emptyList()
        val anchor = findWebAnchor(rootNode)
        val root = anchor ?: rootNode
        val anchorRect = android.graphics.Rect()
        if (anchor != null) anchor.getBoundsInScreen(anchorRect)
        val dm = resources.displayMetrics
        val viewport = android.graphics.Rect(0, 0, dm.widthPixels, dm.heightPixels)

        // ── 后序遍历：记录所有"带文字"节点 + 标记其子树里是否还有带文字节点 ──
        class C(
            val node: AccessibilityNodeInfo,
            val text: String,
            val fromDesc: Boolean,
            val rect: android.graphics.Rect,
            var childHasText: Boolean
        )
        val all = ArrayList<C>()
        var visited = 0
        fun dfs(n: AccessibilityNodeInfo?, depth: Int): Boolean {
            if (n == null || depth > 80) return false
            if (n.packageName?.toString() == packageName) return false
            visited++
            val rawText = n.text?.toString()?.trim()?.ifEmpty { null }
            val raw = rawText ?: n.contentDescription?.toString()?.trim()?.ifEmpty { null }
            var childHas = false
            for (i in 0 until n.childCount) {
                val c = try { n.getChild(i) } catch (_: Exception) { null } ?: continue
                a11yOwned.add(c)
                if (dfs(c, depth + 1)) childHas = true
            }
            if (raw != null) {
                val r = android.graphics.Rect()
                n.getBoundsInScreen(r)
                all.add(C(n, raw, rawText == null, r, childHas))
                return true
            }
            return childHas
        }
        dfs(root, 0)

        // ── 判别式：只收"最深的带文字节点"（子树内没有别的带文字节点）→ 天然去重、不会整树清零 ──
        val deepest = all.filter { !it.childHasText }

        // 装饰图片的 contentDescription 往往在页面重复很多次（截图里的“图案背景”）。
        // 同一描述出现 ≥3 次 → 当装饰/背景处理，不翻译；唯一按钮描述（View docs）不受影响。
        val descFreq = HashMap<String, Int>()
        for (c in deepest) {
            if (c.fromDesc) descFreq[c.text] = (descFreq[c.text] ?: 0) + 1
        }

        // ── 剩余极少数过滤（每条计数，日志自证）──
        val drop = HashMap<String, Int>()
        fun dropIt(k: String) { drop[k] = (drop[k] ?: 0) + 1 }
        val seen = HashSet<String>()
        val out = ArrayList<ImmersiveItem>()
        var total = 0
        var glyphUsed = 0
        var seamlessUsed = 0
        val descSamples = ArrayList<String>()
        val vp = android.graphics.Rect(viewport)
        outer@ for (c in deepest) {
            // 同一 contentDescription 重复出现 ≥3 次 = 装饰图片/背景（“图案背景”）
            if (c.fromDesc && (descFreq[c.text] ?: 0) >= 3) {
                if (descSamples.size < 6) descSamples.add("重复:${c.text.take(18)}")
                dropIt("重复图片描述"); continue
            }
            // 2026-10-17 「小贴」：只靠 contentDescription 的可点击/可聚焦节点 = 图标/按钮/logo，
            // 不是正文（截图里的「打开菜单」「英国广播公司」就是这类；BBC 的描述有 31 个字符，
            // 所以不能用"短文本"判断）。先丢，连逐字矩形都不用取。
            if (c.fromDesc && (c.node.isClickable || c.node.isFocusable)) {
                if (descSamples.size < 6) {
                    descSamples.add("${c.text.take(18)}#${c.node.className}")
                }
                dropIt("图标描述"); continue
            }
            // 2026-10-17：有些页面（Google 结果、Chrome 虚拟节点）文字可见但节点矩形无效。
            // 这类节点再用逐字矩形试一次；逐字矩形也没有才丢。
            val lines = if (c.rect.width() <= 0 || c.rect.height() <= 0) {
                if (!c.node.isVisibleToUser) { dropIt("矩形无效"); continue }
                visibleLines(
                    c.node, c.text,
                    android.graphics.Rect(0, 0, viewport.width(), viewport.height()),
                    vp,
                    allowFallback = false
                )
            } else {
                val nr = android.graphics.Rect(c.rect)
                if (!nr.intersect(viewport)) { dropIt("不在视口"); continue }
                // 2026-10-17：按视觉行切开，一行一条；左右栏用 x 间距断开。
                visibleLines(c.node, c.text, c.rect, vp)
            }
            for (vt in lines) {
                val t = vt.text
                if (t.isEmpty()) { dropIt("空"); continue }
                if (isMostlyCjk(t)) { dropIt("纯中文"); continue }
                if (!t.any { it.isLetter() }) { dropIt("无字母"); continue }
                if (isWholeUrl(t)) { dropIt("整条URL"); continue }
                val r = android.graphics.Rect(vt.rect)
                if (r.width() <= 0 || r.height() <= 0) { dropIt("矩形无效"); continue }
                if (!r.intersect(viewport)) { dropIt("不在视口"); continue }
                // 7~11px 的“行”多半是边缘残片/分隔线，单独成卡只会切字
                if (r.height() < dp(6)) { dropIt("行高异常"); continue }
                // 极窄节点（单个字母/标点/图标旁的字）单独建卡会变成一条竖字，
                // 锚点又被贴到屏幕左边缘 → 就是截图里左上角那种诡异叠字。
                if (r.width() < dp(12)) { dropIt("窄条"); continue }
                if (r.left <= 0 && r.width() < dp(60)) { dropIt("左边缘窄条"); continue }
                val key = t + "@" + r.left + "," + r.top + "," + r.width() + "x" + r.height()
                if (!seen.add(key)) { dropIt("重复"); continue }
                if (total + t.length > st.maxChars) { dropIt("超字数上限"); continue }
                total += t.length
                if (vt.glyph) glyphUsed++

                // 无痕替换：从截屏采样这一行的页面底色/字色。采样不可靠（图片/渐变）
                // 就继续用深色卡片兜底。
                val colors = if (bmp != null && vt.glyph) sampleLineColors(bmp, r) else null
                val seamless = colors?.ok == true
                val bg = colors?.bg ?: 0
                val ink = colors?.ink ?: 0
                if (seamless) seamlessUsed++
                // 字号优先跟随这一行的实际高度（immersiveFontAuto），否则用固定字号。
                // 估算行（原生 App 没逐字矩形）的行高不可靠，直接用用户固定字号，避免 24sp 巨字。
                val autoSp = if (vt.estimated) {
                    st.immersiveTextSize.toFloat()
                } else {
                    (r.height() / density * 0.95f).coerceIn(10f, 18f)
                }
                val sp = if (st.immersiveFontAuto) autoSp else st.immersiveTextSize.toFloat()
                out.add(ImmersiveItem(out.size + 1, t, r, bg, ink, sp, seamless))
                if (out.size >= 300) break@outer
            }
        }
        // 2026-10-17 「小贴」：同一屏里 a11y 节点会互相嵌套/重叠（例如整段节点 + 段内一行）。
        // 先把重叠项合并成一个视觉块，再做“整屏统一风格”，避免一张白底一张黑卡。
        val beforeMerge = out.size
        // 先并掉重叠节点，再把同一栏里紧挨着的行并成段落卡：
        // 行级卡会各自长高，后一张压前一张；段落卡只长一次，内部行不会互盖。
        val cleaned = applyGlobalStyle(
            mergeAdjacentLines(mergeOverlapping(out)),
            st.immersiveSeamlessPct
        )
        out.clear()
        out.addAll(cleaned)
        // 阅读序：从上到下、从左到右（也决定提示词顺序；叠放顺序在 overlay 里按面积再排）
        out.sortWith(compareBy({ it.rect.top }, { it.rect.left }))
        // 排序后重编号 1..N：否则模型看到的是跳号（⟦5⟧⟦2⟧…），结构化输出更容易乱
        for (i in out.indices) out[i] = out[i].copy(index = i + 1)

        // ── 自检日志：一份日志即可定案 ──
        val ar = if (anchor != null) {
            val rr = android.graphics.Rect(); anchor.getBoundsInScreen(rr)
            "(${rr.left},${rr.top}) ${rr.width()}x${rr.height()} class=${anchor.className}"
        } else "无"
        com.speedtrans.app.util.DiagLog.log(
            "a11y",
            "锚点=$ar | 访问=$visited 文本节点=${all.size} 最深=${deepest.size} " +
                    "保留=$beforeMerge→合并后=${out.size} 字形框=$glyphUsed/$beforeMerge " +
                    "无痕=$seamlessUsed/$beforeMerge 丢弃=${drop} " +
                    "图标样例=$descSamples 字符=$total " +
                    "耗时=${android.os.SystemClock.elapsedRealtime() - t0}ms"
        )
        val sample = out.take(5).joinToString(" | ") { "${it.index}:\"${it.original.take(16)}\"@${it.rect.left},${it.rect.top}" }
        com.speedtrans.app.util.DiagLog.log("a11y", "抽样：$sample")
        if (all.isNotEmpty() && out.isEmpty()) {
            com.speedtrans.app.util.DiagLog.log("a11y", "⚠️ 有文本却被清空 → 规则=${drop.keys}")
        }
        if (Build.VERSION.SDK_INT < 33) a11yOwned.forEach { runCatching { it.recycle() } }
        a11yOwned.clear()
        return out
    }

    private fun rectArea(r: android.graphics.Rect): Long =
        r.width().toLong() * r.height().toLong()

    private fun mergeText(a: String, b: String): String {
        val na = a.trim().replace(Regex("\\s+"), " ")
        val nb = b.trim().replace(Regex("\\s+"), " ")
        if (na.isEmpty()) return nb
        if (nb.isEmpty()) return na
        if (na.contains(nb, ignoreCase = true)) return na
        if (nb.contains(na, ignoreCase = true)) return nb
        return "$na $nb"
    }

    /**
     * 2026-10-17 「小贴」：无障碍树里“整段节点”和“段内一行”经常同时存在，矩形互相重叠；
     * 不合并就会一张白卡一张黑卡/小卡压大卡。这里按相交面积 ≥ 较小者 25% 合并成视觉块。
     */
    private fun mergeOverlapping(items: List<ImmersiveItem>): List<ImmersiveItem> {
        if (items.size <= 1) return items
        val n = items.size
        val parent = IntArray(n) { it }

        fun find(x0: Int): Int {
            var x = x0
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        // 传递闭包：A 与 B 重叠、B 与 C 重叠 → A/B/C 必须是同一块。
        // 之前用“贪心 + 只查已有组”，大组扩张后会和新组再次重叠（日志里同 y 两张卡）。
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val inter = android.graphics.Rect(items[i].rect)
                if (!inter.intersect(items[j].rect)) continue
                val minA = minOf(rectArea(items[i].rect), rectArea(items[j].rect))
                if (minA > 0 && rectArea(inter) * 100 / minA >= 25) union(i, j)
            }
        }

        val buckets = LinkedHashMap<Int, MutableList<Int>>()
        for (i in 0 until n) {
            buckets.getOrPut(find(i)) { mutableListOf() }.add(i)
        }

        return buckets.values.mapIndexed { outIndex, ids ->
            val group = ids.map { items[it] }
            val base = group.maxByOrNull { rectArea(it.rect) } ?: group.first()
            val rect = android.graphics.Rect(base.rect)
            var text = base.original
            var bg = base.bg
            var ink = base.textColor
            var sp = base.textSizeSp
            var seamless = base.seamless
            for (item in group.sortedWith(compareBy({ it.rect.top }, { it.rect.left }))) {
                if (item === base) continue
                rect.union(item.rect)
                text = mergeText(text, item.original)
                sp = minOf(sp, item.textSizeSp)
                if (bg == 0 && item.bg != 0) {
                    bg = item.bg
                    ink = item.textColor
                }
                seamless = seamless || item.seamless
            }
            ImmersiveItem(outIndex + 1, text, rect, bg, ink, sp, seamless)
        }
    }

    /**
     * 同一栏里紧挨着的视觉行 → 合并成一张段落卡。
     * 行级卡各自按译文长高时会互相压；段落卡只有一张，译文在卡内自然排版。
     */
    private fun mergeAdjacentLines(items: List<ImmersiveItem>): List<ImmersiveItem> {
        if (items.size <= 1) return items
        class M(
            var text: String,
            val rect: android.graphics.Rect,
            var bg: Int,
            var ink: Int,
            var sp: Float,
            var seamless: Boolean,
            var lines: Int
        )
        val groups = ArrayList<M>()
        val gapPx = dp(8)
        val maxLinesPerCard = 4
        for (item in items.sortedWith(compareBy({ it.rect.top }, { it.rect.left }))) {
            val last = groups.lastOrNull()
            if (last == null) {
                groups.add(M(item.original, android.graphics.Rect(item.rect), item.bg, item.textColor, item.textSizeSp, item.seamless, 1))
                continue
            }
            val xOverlap = minOf(last.rect.right, item.rect.right) - maxOf(last.rect.left, item.rect.left)
            val minW = maxOf(1, minOf(last.rect.width(), item.rect.width()))
            val vGap = item.rect.top - last.rect.bottom
            val sameColumn = xOverlap * 2 >= minW
            if (sameColumn && vGap in -gapPx..gapPx && last.lines < maxLinesPerCard) {
                last.rect.union(item.rect)
                last.text = mergeText(last.text, item.original)
                last.sp = minOf(last.sp, item.textSizeSp)
                if (last.bg == 0 && item.bg != 0) {
                    last.bg = item.bg
                    last.ink = item.textColor
                }
                last.seamless = last.seamless || item.seamless
                last.lines++
            } else {
                groups.add(M(item.original, android.graphics.Rect(item.rect), item.bg, item.textColor, item.textSizeSp, item.seamless, 1))
            }
        }
        return groups.mapIndexed { i, g ->
            ImmersiveItem(i + 1, g.text, g.rect, g.bg, g.ink, g.sp, g.seamless)
        }
    }

    /**
     * 一屏一策：不允许一半白底一半黑卡。
     * 无痕覆盖率达到 60% 就整屏用同一套页面底色/字色；否则整屏统一深色卡。
     * 中间值交给 overlay 按“无痕程度”混合。
     */
    private fun applyGlobalStyle(items: List<ImmersiveItem>, pct: Int): List<ImmersiveItem> {
        if (items.isEmpty() || pct <= 0) return items.map { it.copy(seamless = false) }
        val good = items.filter { it.seamless && it.bg != 0 }
        if (good.isEmpty()) return items.map { it.copy(seamless = false) }
        if (good.size * 100 / items.size < 60) return items.map { it.copy(seamless = false) }

        val counts = HashMap<Int, Int>()
        good.forEach { counts[it.bg] = (counts[it.bg] ?: 0) + 1 }
        val bg = counts.maxByOrNull { it.value }?.key ?: 0xFFFFFFFF.toInt()
        val lum = (0.299 * ((bg shr 16) and 0xFF) +
                0.587 * ((bg shr 8) and 0xFF) +
                0.114 * (bg and 0xFF)) / 255.0
        val ink = if (lum > 0.55) 0xFF000000.toInt() else 0xFFF5F5F5.toInt()
        return items.map { it.copy(bg = bg, textColor = ink, seamless = true) }
    }

    /** 一行文字的无痕配色：页面底色 + 文字色；ok=false 表示背景不纯，回退深色卡 */
    private data class LineColors(val bg: Int, val ink: Int, val ok: Boolean)

    private fun sampleLineColors(bmp: Bitmap, screenRect: android.graphics.Rect): LineColors? {
        if (screenRect.width() <= 0 || screenRect.height() <= 0) return null
        val dm = resources.displayMetrics
        val sx = bmp.width.toFloat() / dm.widthPixels
        val sy = bmp.height.toFloat() / dm.heightPixels
        val br = android.graphics.Rect(
            (screenRect.left * sx).toInt(),
            (screenRect.top * sy).toInt(),
            (screenRect.right * sx).toInt(),
            (screenRect.bottom * sy).toInt()
        )
        if (!br.intersect(0, 0, bmp.width, bmp.height)) return null
        if (br.width() <= 0 || br.height() <= 0) return null

        // 先取行内出现最多的颜色当底色、最显眼的异色当字色
        val (bg0, ink0) = sampleColors(bmp, br)
        val bg = bg0
        val ink = if (colorDist(bg, ink0) >= 1600) ink0 else {
            val lum = (0.299 * ((bg shr 16) and 0xFF) +
                    0.587 * ((bg shr 8) and 0xFF) +
                    0.114 * (bg and 0xFF)) / 255.0
            if (lum > 0.55) 0xFF1A1A1A.toInt() else 0xFFEDEDED.toInt()
        }

        // 看行的外圈是否接近纯色：纯色 → 可以无痕；图片/渐变 → 深色卡兜底
        val pad = maxOf(2, (br.height() * 0.18f).toInt())
        val x0 = (br.left - pad).coerceAtLeast(0)
        val y0 = (br.top - pad).coerceAtLeast(0)
        val x1 = (br.right + pad).coerceAtMost(bmp.width - 1)
        val y1 = (br.bottom + pad).coerceAtMost(bmp.height - 1)
        val samples = ArrayList<Int>(48)
        val stepX = maxOf(1, (x1 - x0) / 12)
        val stepY = maxOf(1, (y1 - y0) / 6)
        var x = x0
        while (x <= x1) {
            samples.add(bmp.getPixel(x, y0))
            samples.add(bmp.getPixel(x, y1))
            x += stepX
        }
        var y = y0
        while (y <= y1) {
            samples.add(bmp.getPixel(x0, y))
            samples.add(bmp.getPixel(x1, y))
            y += stepY
        }
        val close = samples.count { colorDist(it, bg) < 1200 }
        val ok = samples.size >= 12 &&
                close * 100 / samples.size >= 70 &&
                colorDist(bg, ink) >= 1800
        return LineColors(bg, ink, ok)
    }

    /** "整条就是 URL/域名"（只在整条时丢；包含链接的正文不丢） */
    private fun isWholeUrl(t: String): Boolean {
        val s0 = t.trim()
        if (s0.contains(' ')) return false
        if (s0.startsWith("http://") || s0.startsWith("https://")) return true
        return Regex("^[A-Za-z0-9%._-]+\\.[A-Za-z]{2,}(/\\S*)?$").matches(s0)
    }

    /**
     * 行级精确：用"逐字矩形"把节点文本按行切开（每行一个矩形）。
     * 拿不到（节点不提供/异常）→ 返回 null，调用方退回"整节点一块"。
     */
    private fun splitLines(
        n: AccessibilityNodeInfo,
        text: String
    ): List<Pair<String, android.graphics.Rect>>? {
        return try {
            val args = android.os.Bundle().apply {
                putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
                putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, text.length)
            }
            if (!n.refreshWithExtraData(
                    AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, args
                )
            ) return null
            @Suppress("DEPRECATION")
            val arr: Array<*> = (n.extras.getParcelableArray(
                AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
            ) ?: return null)
            if (arr.size < text.length || text.isEmpty()) return null
            val rects = ArrayList<android.graphics.RectF>(arr.size)
            for (o in arr) {
                val rf = o as? android.graphics.RectF ?: return null
                rects.add(rf)
            }
            val res = ArrayList<Pair<String, android.graphics.Rect>>()
            var start = 0
            var i = 1
            var curMid = (rects[0].top + rects[0].bottom) / 2f
            var curH = rects[0].height()

            fun flush(endExclusive: Int) {
                if (endExclusive <= start) return
                val r = android.graphics.Rect(Int.MAX_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)
                for (k in start until endExclusive) {
                    val rf = rects[k]
                    r.left = minOf(r.left, rf.left.toInt())
                    r.top = minOf(r.top, rf.top.toInt())
                    r.right = maxOf(r.right, rf.right.toInt())
                    r.bottom = maxOf(r.bottom, rf.bottom.toInt())
                }
                val seg = text.substring(start, endExclusive).trim()
                if (seg.isNotEmpty() && r.width() > 0 && r.height() > 0) res.add(seg to r)
            }

            while (i < text.length) {
                val m = (rects[i].top + rects[i].bottom) / 2f
                if (kotlin.math.abs(m - curMid) > maxOf(6f, curH * 0.6f)) {
                    flush(i); start = i; curMid = m; curH = rects[i].height()
                }
                i++
            }
            flush(text.length)
            com.speedtrans.app.util.DiagLog.log("a11y", "逐字矩形→行数=${res.size}")
            if (res.isEmpty()) null else res
        } catch (e: Exception) {
            com.speedtrans.app.util.DiagLog.log("a11y", "逐字矩形不可用：${e.message?.take(40)}")
            null
        }
    }

    private fun findWebAnchor(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0L
        val q = java.util.ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var guard = 0
        while (q.isNotEmpty() && guard++ < 6000) {
            val n = q.removeFirst()
            if (n.className?.toString() == "android.webkit.WebView" && n.isVisibleToUser) {
                val rr = android.graphics.Rect()
                n.getBoundsInScreen(rr)
                val area = rr.width().toLong() * rr.height()
                if (area > bestArea) {
                    bestArea = area
                    best = n
                }
            }
            for (i in 0 until n.childCount) {
                val c = try { n.getChild(i) } catch (_: Exception) { null } ?: continue
                a11yOwned.add(c)
                q.add(c)
            }
        }
        val dm = resources.displayMetrics
        val screenArea = dm.widthPixels.toLong() * dm.heightPixels
        return if (best != null && bestArea >= screenArea * 35 / 100) best else null
    }

    /** 像 URL / 域名 / 查询串（浏览器地址栏那类，不该被当成正文翻译） */
    private fun looksLikeUrl(t: String): Boolean {
        val s0 = t.trim()
        if (s0.contains("://") || s0.contains("www.")) return true
        if (Regex("[A-Za-z0-9-]+\\.[A-Za-z]{2,}(/|\\b)").containsMatchIn(s0) &&
            s0.count { it == ' ' } <= 2
        ) return true
        for (tld in listOf(".com/", ".org/", ".net/", ".co/", ".uk/", ".io/", ".dev/", ".gov/", ".edu/")) {
            if (s0.contains(tld, true)) return true
        }
        if (s0.length >= 40 && !s0.contains(' ')) return true
        return false
    }

    private fun isMostlyCjk(t: String): Boolean {
        var cjk = 0
        var letters = 0
        for (c in t) {
            if (c.isLetter()) {
                letters++
                val b = Character.UnicodeBlock.of(c)
                if (b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                    b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                    b == Character.UnicodeBlock.HIRAGANA ||
                    b == Character.UnicodeBlock.KATAKANA ||
                    b == Character.UnicodeBlock.HANGUL_SYLLABLES
                ) cjk++
            }
        }
        return letters > 0 && cjk * 100 / letters >= 50
    }

    /** 该节点是否有"带文字的子孙"（有 → 它是容器，跳过；只取真正承载文字的叶子） */
    private fun hasTextChild(n: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > 6) return false
        for (i in 0 until n.childCount) {
            val c = try { n.getChild(i) } catch (_: Exception) { null } ?: continue
            val t = c.text?.toString()?.trim()
            if (!t.isNullOrEmpty()) return true
            if (hasTextChild(c, depth + 1)) return true
        }
        return false
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
        "rectH" -> GradientDrawable().apply {
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
            "rect", "rectH" -> o.setRect(0, 0, w, h)
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
