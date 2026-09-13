package com.speedtrans.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.speedtrans.app.service.KeepAliveService
import com.speedtrans.app.store.SettingsStore
import com.speedtrans.app.theme.ThemeEngine
import com.speedtrans.app.ui.Wallpaper

/** 快捷主题条滚动位置（跨 recreate 恢复用） */
private var themeScrollX = 0

class MainActivity : AppCompatActivity() {

    private lateinit var store: SettingsStore

    /** 图标底色板（浅色主题用；深色主题自动换算为透明底+提亮色） */
    private val tintPairs: Map<String, Pair<Int, Int>> = mapOf(
        "theme" to (0xFFE5E4FC.toInt() to 0xFF5B6FEF.toInt()),
        "status" to (0xFFDDF3EE.toInt() to 0xFF35B69F.toInt()),
        "ovl" to (0xFFFFF0E2.toInt() to 0xFFF19A55.toInt()),
        "a11" to (0xFFE0EEFF.toInt() to 0xFF2188E8.toInt()),
        "api" to (0xFFE4E5FC.toInt() to 0xFF5D6DF2.toInt()),
        "tut" to (0xFFE7E3FF.toInt() to 0xFF6654E8.toInt()),
        "aset" to (0xFFDDF2F4.toInt() to 0xFF32B5BE.toInt()),
        "hist" to (0xFFFBE4E3.toInt() to 0xFFEA6B68.toInt()),
        "use" to (0xFFE4EFF6.toInt() to 0xFF426F92.toInt()),
        "tools" to (0xFFE9EEF4.toInt() to 0xFF405B78.toInt())
    )

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val imported = Wallpaper.importFrom(this, uri, Wallpaper.avatarSrcFile(this))
            if (!imported) {
                Toast.makeText(this, "图片导入失败，换一张试试", Toast.LENGTH_SHORT).show()
            } else {
                avatarCropReturn.launch(
                    Intent(this, CropActivity::class.java).putExtra(CropActivity.EXTRA_SLOT, "avatar")
                )
            }
        }
    }

    /** 头像取景返回：成功后刷新圆形头像 */
    private val avatarCropReturn =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == android.app.Activity.RESULT_OK) {
                refreshAvatar()
                Toast.makeText(this, "头像已更新（长按头像可恢复默认球）", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupEdgeToEdge()
        store = SettingsStore(this)

        Wallpaper.ensureMigrated(this)

        // 闪电随标题字号自适应：按“与闪译等高略高”的比例换算（系统大字号下也不掉队）
        run {
            val tvTitle = findViewById<TextView>(R.id.tvTitle)
            val ivBolt = findViewById<ImageView>(R.id.ivBolt)
            val dm = resources.displayMetrics
            val side = (tvTitle.paint.textSize * 1.28f).toInt()
                .coerceIn((24 * dm.density).toInt(), (48 * dm.density).toInt())
            val lp = ivBolt.layoutParams
            if (lp.width != side || lp.height != side) {
                lp.width = side
                lp.height = side
                ivBolt.layoutParams = lp
            }
        }

        // 头像：圆形裁剪显示 + 螺母换头像 + 长按恢复默认
        val ivAvatar = findViewById<ImageView>(R.id.ivAvatar)
        ivAvatar.clipToOutline = true
        ivAvatar.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        findViewById<View>(R.id.btnAvatarEdit).setOnClickListener { pickAvatar.launch("image/*") }
        ivAvatar.setOnLongClickListener {
            Wallpaper.avatarFile(this).delete()
            Wallpaper.avatarSrcFile(this).delete()
            refreshAvatar()
            Toast.makeText(this, "已恢复默认头像", Toast.LENGTH_SHORT).show()
            true
        }

        // 运行状态三行：点按 = 跳转（等效旧按钮）
        findViewById<View>(R.id.rowOverlay).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        findViewById<View>(R.id.rowA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<View>(R.id.rowApi).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 设置与工具
        findViewById<View>(R.id.rowTut).setOnClickListener { showPermissionTutorial() }
        findViewById<View>(R.id.rowApiSettings).setOnClickListener {
            com.speedtrans.app.ui.LauncherAliases.repair(this)
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.rowHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<View>(R.id.chevTheme).setOnClickListener { openThemePage() }
        findViewById<View>(R.id.headTheme).setOnClickListener { openThemePage() }

        bindQuickTheme()

        // 前台保活：划掉最近任务不影响悬浮球
        ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))
        requestNotificationPermission()
    }

    // ---------------- 主题应用（新版首页） ----------------

    private fun applyTheme() {
        val pal = ThemeEngine.current(this)
        val d = resources.displayMetrics.density
        val alpha = store.cardAlphaPct
        val light = Color.luminance(pal.bg) > 0.5f

        applyHomeFont()

        findViewById<View>(R.id.rootMainHost).setBackgroundColor(ThemeEngine.backdrop(pal))
        Wallpaper.applySlot(
            this, R.id.ivWallpaperMain, R.id.wpScrimMain,
            store.wpCrop("main"), store.wpDim("main", 50), store.wpEnabled("main"), pal.bg
        )

        // 卡片与内层（浓度可控）
        val cardColor = withAlpha(pal.card, alpha)
        val innerColor = withAlpha(pal.card, (alpha * 62 / 100).coerceAtLeast(20))
        for (id in intArrayOf(R.id.cardTheme, R.id.cardStatus, R.id.cardTools, R.id.cardUsage)) {
            findViewById<View>(id).background = ThemeEngine.cardDrawable(cardColor, 13f, d)
        }
        for (id in intArrayOf(R.id.groupA, R.id.groupB, R.id.statusPanel)) {
            findViewById<View>(id).background = ThemeEngine.cardDrawable(innerColor, 11f, d)
        }

        // 图标底块
        fun iconChip(bgId: Int, icId: Int, key: String) {
            val pair = tintPairs[key] ?: return
            val bg: Int
            val fg: Int
            if (light) {
                bg = pair.first
                fg = pair.second
            } else {
                fg = blend(pair.second, 0xFFFFFFFF.toInt(), 0.25f)
                bg = withAlpha(fg, 22)
            }
            findViewById<View>(bgId).background = ThemeEngine.cardDrawable(bg, 11f, d)
            findViewById<ImageView>(icId).setColorFilter(fg)
        }
        iconChip(R.id.chip_theme, R.id.ic_theme, "theme")
        iconChip(R.id.chip_status, R.id.ic_status, "status")
        iconChip(R.id.chip_ovl, R.id.ic_ovl, "ovl")
        iconChip(R.id.chip_a11, R.id.ic_a11, "a11")
        iconChip(R.id.chip_api, R.id.ic_api, "api")
        iconChip(R.id.chip_tut, R.id.ic_tut, "tut")
        iconChip(R.id.chip_aset, R.id.ic_aset, "aset")
        iconChip(R.id.chip_hist, R.id.ic_hist, "hist")
        iconChip(R.id.chip_use, R.id.ic_use, "use")
        iconChip(R.id.chip_tools, R.id.ic_tools, "tools")

        // 文本
        val titles = intArrayOf(
            R.id.tvTitle, R.id.tvThemeTitle, R.id.tvStatusTitle, R.id.tvToolsTitle,
            R.id.tvUseTitle, R.id.tvOvlTitle, R.id.tvA11Title, R.id.tvApiTitle,
            R.id.tvTutTitle, R.id.tvAsetTitle, R.id.tvHistTitle
        )
        titles.forEach { findViewById<TextView>(it).setTextColor(pal.text) }
        val subs = intArrayOf(
            R.id.tvSubtitle, R.id.tvThemeSub, R.id.tvOvlSub, R.id.tvA11Sub, R.id.tvApiSub,
            R.id.tvTutSub, R.id.tvAsetSub, R.id.tvHistSub, R.id.tvToolsLabelA, R.id.tvToolsLabelB,
            R.id.tvUsage, R.id.tvSign
        )
        subs.forEach { findViewById<TextView>(it).setTextColor(pal.subText) }

        // 箭头 / 分隔线 / 螺栓 / 螺母
        val chevC = withAlpha(pal.subText, 78)
        findViewById<ImageView>(R.id.chevTheme).setColorFilter(chevC)
        tintTagged(findViewById(R.id.homeCol), "chev", chevC)
        val divColor = Color.argb(46, 112, 150, 180)
        for (id in intArrayOf(R.id.divStatus1, R.id.divStatus2, R.id.divTools1)) {
            findViewById<View>(id).setBackgroundColor(divColor)
        }
        findViewById<ImageView>(R.id.ivBolt).setColorFilter(0xFFF4A66F.toInt())
        val nutColor = if (light) blend(pal.subText, pal.text, 0.35f) else blend(0xFFFFFFFF.toInt(), pal.subText, 0.2f)
        findViewById<ImageView>(R.id.btnAvatarEdit).setColorFilter(nutColor)

        // 系统栏图标明暗随主题底色走
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    /** 首页字号缩放：80~140% 作用到首页全部文字（大标题"闪译"豁免；闪电跟随标题、也不受影响） */
    private fun applyHomeFont() {
        val scale = store.homeFontPct / 100f
        val bases = arrayOf(
            R.id.tvSubtitle to 11.5f,
            R.id.tvThemeTitle to 15f, R.id.tvThemeSub to 10.5f,
            R.id.tvStatusTitle to 15f, R.id.tvStatusAll to 10.5f,
            R.id.tvOvlTitle to 12.5f, R.id.tvOvlSub to 10.5f, R.id.tvOvlChip to 10.5f,
            R.id.tvA11Title to 12.5f, R.id.tvA11Sub to 10.5f, R.id.tvA11Chip to 10.5f,
            R.id.tvApiTitle to 12.5f, R.id.tvApiSub to 10.5f, R.id.tvApiChip to 10.5f,
            R.id.tvToolsTitle to 15f, R.id.tvToolsLabelA to 11f, R.id.tvToolsLabelB to 11f,
            R.id.tvTutTitle to 12.5f, R.id.tvTutSub to 10.5f,
            R.id.tvAsetTitle to 12.5f, R.id.tvAsetSub to 10.5f,
            R.id.tvHistTitle to 12.5f, R.id.tvHistSub to 10.5f,
            R.id.tvUseTitle to 15f, R.id.tvUsage to 11.5f, R.id.tvSign to 10.5f
        )
        for ((id, base) in bases) {
            findViewById<android.widget.TextView>(id)
                .setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, base * scale)
        }
    }

    /** 全面屏：壁纸铺满整个屏幕（含状态栏/导航栏区域），滚动内容用 inset 让位 */
    @Suppress("DEPRECATION")
    private fun setupEdgeToEdge() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // 关闭"导航栏对比度强制层"：透明导航栏后面的浅色 scrim 就是那条"白带"（与皮肤无关的根因）
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootMain)) { v, insets ->
            val b = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, b.top, 0, b.bottom)
            insets
        }
    }

    /** 打开「主题色」专页（首页外观：主题球 + 主界面壁纸 + 首页显示） */
    private fun openThemePage() {
        startActivity(
            Intent(this, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_TAB, "theme")
        )
    }

    // ---------------- 头像 ----------------

    private fun refreshAvatar() {
        val iv = findViewById<ImageView>(R.id.ivAvatar)
        val f = Wallpaper.avatarFile(this)
        val bmp = if (f.exists()) Wallpaper.decode(f.absolutePath, 256, 512) else null
        iv.setImageBitmap(bmp ?: Wallpaper.defaultAvatar((56 * resources.displayMetrics.density).toInt()))
    }

    // ---------------- 快捷主题球 ----------------

    private fun bindQuickTheme() {
        val row = findViewById<LinearLayout>(R.id.quickThemeRow)
        row.removeAllViews()
        val pal = ThemeEngine.current(this)
        val cur = pal.id
        val d = resources.displayMetrics.density
        val size = (40 * d).toInt()
        val margin = (6 * d).toInt()
        ThemeEngine.palettes.forEach { p ->
            val sel = p.id == cur
            val v = TextView(this)
            v.layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = margin }
            v.gravity = android.view.Gravity.CENTER
            v.textSize = 13f
            v.setTextColor(Color.WHITE)
            v.text = if (sel) "✓" else ""
            v.background = ballDrawable(p.accent, sel, d)
            v.setOnClickListener {
                // 记住色球条滚动位置（recreate 后恢复，点右边的球不再跳回最左）
                themeScrollX = (row.parent as? HorizontalScrollView)?.scrollX ?: 0
                ThemeEngine.save(this, p.id)
                recreate()
            }
            row.addView(v)
        }
        val hsv = row.parent as? HorizontalScrollView
        hsv?.post { hsv.scrollTo(themeScrollX, 0) }
    }

    /** 色球：未选 = 直径 28 的纯色圆；选中 = 白隔 + 亮色外环 + 对勾位（视觉约 40） */
    private fun ballDrawable(accent: Int, selected: Boolean, d: Float): Drawable {
        if (!selected) {
            val g = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent)
            }
            val ld = LayerDrawable(arrayOf<Drawable>(g))
            val inset = (6 * d).toInt()
            ld.setLayerInset(0, inset, inset, inset, inset)
            return ld
        }
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke((2 * d).toInt().coerceAtLeast(1), blend(accent, 0xFFFFFFFF.toInt(), 0.45f))
        }
        val white = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        val ball = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(accent)
        }
        val ld = LayerDrawable(arrayOf<Drawable>(ring, white, ball))
        val r1 = (2 * d).toInt()
        val r2 = (5.5f * d).toInt()
        ld.setLayerInset(1, r1, r1, r1, r1)
        ld.setLayerInset(2, r2, r2, r2, r2)
        return ld
    }

    // ---------------- 状态刷新 ----------------

    override fun onResume() {
        super.onResume()
        applyTheme()
        refreshAvatar()

        val overlayOk = Settings.canDrawOverlays(this)
        setChip(findViewById(R.id.tvOvlChip), if (overlayOk) "已开启" else "未开启", overlayOk)

        val a11yOk = isAccessibilityEnabled()
        setChip(findViewById(R.id.tvA11Chip), if (a11yOk) "运行中" else "未开启", a11yOk)

        val hasKey = !store.apiKey.isNullOrBlank()
        setChip(findViewById(R.id.tvApiChip), if (hasKey) "已连接" else "未配置", hasKey)
        findViewById<TextView>(R.id.tvApiSub).text =
            if (hasKey) "模型：${store.model}" else "尚未配置，点击去设置"

        val allOk = overlayOk && a11yOk && hasKey
        val tvAll = findViewById<TextView>(R.id.tvStatusAll)
        val dotColor = if (allOk) 0xFF3AB49B.toInt() else 0xFFD98F45.toInt()
        tvAll.text = if (allOk) "所有服务正常运行" else "部分服务未就绪"
        tvAll.setTextColor(dotColor)
        findViewById<View>(R.id.ivStatusDot).background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(dotColor)
        }
    }

    private fun setChip(tv: TextView, text: String, ok: Boolean) {
        tv.text = text
        val d = resources.displayMetrics.density
        if (ok) {
            tv.setTextColor(0xFF2BA98F.toInt())
            tv.background = ThemeEngine.cardDrawable(Color.argb(31, 58, 180, 155), 999f, d)
        } else {
            tv.setTextColor(0xFFC0433A.toInt())
            tv.background = ThemeEngine.cardDrawable(Color.argb(26, 198, 40, 40), 999f, d)
        }
    }

    // ---------------- 小工具 ----------------

    private fun withAlpha(color: Int, pct: Int): Int {
        val a = pct.coerceIn(0, 100) * 255 / 100
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) * (1 - tt) + Color.alpha(b) * tt).toInt(),
            (Color.red(a) * (1 - tt) + Color.red(b) * tt).toInt(),
            (Color.green(a) * (1 - tt) + Color.green(b) * tt).toInt(),
            (Color.blue(a) * (1 - tt) + Color.blue(b) * tt).toInt()
        )
    }

    private fun tintTagged(root: View, tag: String, color: Int) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) tintTagged(root.getChildAt(i), tag, color)
        }
        if (root.tag == tag && root is ImageView) root.setColorFilter(color)
    }

    // ---------------- 小米权限图解（含防杀后台说明） ----------------

    private fun showPermissionTutorial() {
        val d = android.app.Dialog(this)
        val den = resources.displayMetrics.density
        val col = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((14 * den).toInt(), (10 * den).toInt(), (14 * den).toInt(), (14 * den).toInt())
        }
        val steps = listOf(
            "① 设置 → 辅助功能 → 权限管控「已下载的应用」" to R.drawable.tut_miui_1,
            "② 已下载的应用 → 找到「闪译悬浮球」" to R.drawable.tut_miui_2,
            "③ 打开「使用“闪译悬浮球”」开关" to R.drawable.tut_miui_3
        )
        steps.forEach { (cap, img) ->
            col.addView(android.widget.TextView(this).apply {
                text = cap
                textSize = 14f
                setPadding(0, (12 * den).toInt(), 0, (6 * den).toInt())
            })
            col.addView(android.widget.ImageView(this).apply {
                setImageResource(img)
                adjustViewBounds = true
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
            })
        }
        col.addView(android.widget.TextView(this).apply {
            text = "小米/MIUI 防杀后台（一次性设置）：\n" +
                    "1. 最近任务长按本应用卡片 → 锁定\n" +
                    "2. 应用详情 → 省电策略 → 无限制\n" +
                    "3. 应用详情 → 自启动 → 开启\n\n" +
                    "日常无需打开本应用，点球即用。"
            textSize = 12.5f
            setPadding(0, (14 * den).toInt(), 0, 0)
        })
        col.addView(android.widget.Button(this).apply {
            text = "关闭"
            setOnClickListener { d.dismiss() }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (16 * den).toInt() }
        })
        d.setContentView(android.widget.ScrollView(this).apply { addView(col) })
        d.show()
        val dm = resources.displayMetrics
        d.window?.setLayout((dm.widthPixels * 0.92f).toInt(), (dm.heightPixels * 0.88f).toInt())
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val s = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return s.contains(packageName)
    }
}
