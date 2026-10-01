package com.speedtrans.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.speedtrans.app.service.BallService
import com.speedtrans.app.service.KeepAliveService
import com.speedtrans.app.settings.applyWallpaper
import com.speedtrans.app.settings.bindApi
import com.speedtrans.app.settings.bindBallAppearance
import com.speedtrans.app.settings.bindBarColor
import com.speedtrans.app.settings.bindIconStudio
import com.speedtrans.app.settings.bindLauncherSection
import com.speedtrans.app.settings.bindOcr
import com.speedtrans.app.settings.bindPanelAppearance
import com.speedtrans.app.settings.bindPanelButtons
import com.speedtrans.app.settings.bindPrompt
import com.speedtrans.app.settings.bindThemePicker
import com.speedtrans.app.settings.bindWallpaper
import com.speedtrans.app.settings.buildPropsPage
import com.speedtrans.app.settings.clearWall
import com.speedtrans.app.settings.doPinShortcut
import com.speedtrans.app.settings.fadedCard
import com.speedtrans.app.settings.importIconImage
import com.speedtrans.app.settings.importWallImage
import com.speedtrans.app.settings.refreshThinkingRow
import com.speedtrans.app.settings.refreshWallUi
import com.speedtrans.app.settings.restoreDefaultIcon
import com.speedtrans.app.settings.restyleCatChips
import com.speedtrans.app.settings.restylePropsFills
import com.speedtrans.app.settings.save
import com.speedtrans.app.settings.selectThemeCategory
import com.speedtrans.app.settings.styleThinkingChips
import com.speedtrans.app.store.SettingsStore
import com.speedtrans.app.theme.Palette
import com.speedtrans.app.theme.ShellSkin
import com.speedtrans.app.theme.ShellSkins
import com.speedtrans.app.theme.ThemeEngine
import com.speedtrans.app.translate.Providers
import com.speedtrans.app.translate.TranslateCoordinator
import com.speedtrans.app.translate.TranslateEngine
import com.speedtrans.app.ui.BeamView
import com.speedtrans.app.ui.CircuitTestView
import com.speedtrans.app.ui.ContainsAdapter
import com.speedtrans.app.ui.FlameCursor
import com.speedtrans.app.ui.GracefulScrollView
import com.speedtrans.app.ui.IconStudio
import com.speedtrans.app.ui.ScanlineView
import com.speedtrans.app.ui.Wallpaper
import java.io.File

/**
 * 设置页壳层：5 个页面的**状态与生命周期** + 壳层皮肤 + Cuff Links 导航 + 6 个 activity-result 启动器。
 *
 * 2026-10-01 拆分（1620 行 → 约 400 行）：页面本体按底弧五键拆进同包 `settings/` 下的兄弟文件，
 * 全部写成 `internal fun SettingsActivity.xxx()` **扩展函数**，而不是新类——因为
 * `registerForActivityResult` 必须注册在 Activity 上，状态不能搬走。
 *
 * | 文件 | 内容 |
 * |---|---|
 * | `settings/ApiPage.kt`       | 🔌 接口：服务商联想 / 思考档位 / 测试连接 |
 * | `settings/ThemePage.kt`     | 🎨 主题：分类 chips + 整套配色预览卡 |
 * | `settings/BallPage.kt`      | ⚡ 悬浮球：外观 / OCR 语言 / 译文面板 / 面板按钮 |
 * | `settings/DesktopPage.kt`   | 🖼 桌面：壁纸 / 图标工坊 / 桌面入口 |
 * | `settings/MorePage.kt`      | ✍️ 其他：撞色条选色 / 提示词 |
 * | `settings/PropsPanel.kt`    | 主题标签下的「属性设置」面板 + 全部样式工具 |
 * | `settings/SettingsSaver.kt` | 保存落盘 |
 * | `ui/ContainsAdapter.kt`     | 包含式匹配的下拉适配器 |
 *
 * 留在本文件的是**所有页面共用的东西**：`store` / `currentSkin` / `applySkin` / Cuff 导航，
 * 以及 `setupEdgeToEdge()`——**状态栏与导航栏 insets，跨 ROM（HyperOS/OriginOS/ColorOS/MagicOS）
 * 最敏感的一段，拆分时一行未动。**
 */
class SettingsActivity : AppCompatActivity() {

    internal lateinit var store: SettingsStore
    internal var selectedColor = "#E6FF4757"
    internal var selectedShape = "circle"
    internal var selectedSizeDp = 52

    internal val pickBallImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importImage(uri, File(filesDir, "ball_image"), "悬浮球图片已更新")
    }

    internal val pickShortcutImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importIconImage(uri)
    }

    internal val iconCropReturn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) doPinShortcut()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        Wallpaper.ensureMigrated(this)
        setContentView(R.layout.activity_settings)
        setupEdgeToEdge()
        store = SettingsStore(this)

        // 火把光标：接口页 + 桌面入口名称的输入框
        FlameCursor.apply(
            findViewById(R.id.acProvider), findViewById(R.id.etUrl),
            findViewById(R.id.etKey), findViewById(R.id.etModel), findViewById(R.id.etShortcutName)
        )

        applySkin()
        bindCuff()
        bindBarColor()
        bindWallpaper()
        bindIconStudio()
        bindThemePicker()
        bindApi()
        bindPrompt()
        bindBallAppearance()
        bindPanelAppearance()
        bindPanelButtons()
        bindOcr()
        bindLauncherSection()

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        // 还原成默认图标（闪译）：把三个桌面别名的组件状态恢复为清单默认
        findViewById<Button>(R.id.btnIconRestore).setOnClickListener { restoreDefaultIcon() }
        // 彩蛋：霓虹空气曲棍球（独立 Activity，不触碰任何翻译链路）
        findViewById<Button>(R.id.btnEgg).setOnClickListener {
            startActivity(android.content.Intent(this, com.speedtrans.app.game.AirHockeyActivity::class.java))
        }

        ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))
    }

    // ---------- 壳层皮肤与 Cuff Links 导航 ----------

    internal var currentSkin: ShellSkin? = null
    private var selectedCuff = 0
    companion object {
        /** 从外部直达某个标签页：接口 api / 主题 theme / 悬浮 ball / 桌面 desktop / 其他 more */
        const val EXTRA_TAB = "tab"

    }

    private val cuffItems = mutableListOf<CuffItem>()

    private data class CuffItem(
        val icon: TextView, val label: TextView, val dot: View
    )

    internal fun applySkin() {
        val skin = ShellSkins.current(this)
        currentSkin = skin
        findViewById<View>(R.id.rootSettings).setBackgroundColor(skin.bg)
        // 系统栏图标明暗随皮肤底色走（浅底黑图标 / 深底白图标）
        val lightBars = Color.luminance(skin.bg) > 0.5f
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBars
            // 导航栏=纯白，图标固定深色
            isAppearanceLightNavigationBars = true
        }
        // 导航栏收口色统一由 applyWallpaper()（含渐变桥）设置
        findViewById<ScanlineView>(R.id.fxScanlines).visibility =
            if (skin.scanline) View.VISIBLE else View.GONE
        findViewById<BeamView>(R.id.fxBeam).visibility =
            if (skin.beam) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvShellTitle).setTextColor(skin.accent)
        findViewById<View>(R.id.titleLine).setBackgroundColor(skin.accent)
        // （坞渐变与底部幕布统一由 applyWallpaper 决策：一处定色，避免多对象不同色）
        ShellSkins.applyShell(
            findViewById(R.id.rootSettings), skin,
            cardIds = setOf(R.id.tvUsage),
            subIds = setOf(R.id.tvProviderNote)
        )
        findViewById<View>(R.id.btnTuhun).background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(skin.panelBg)
            setStroke((1 * resources.displayMetrics.density).toInt(), skin.stroke)
        }
        findViewById<View>(R.id.cardContact).background =
            ShellSkins.cut(skin, resources.displayMetrics.density)
        ShellSkins.bindFocusGlow(
            this, { currentSkin ?: ShellSkins.current(this) },
            R.id.acProvider, R.id.etUrl, R.id.etKey, R.id.etModel, R.id.etMaxTokens, R.id.etTemp
        )
        refreshThinkingRow()
        styleThinkingChips()
        styleCuff()
        restylePropsFills()
        restyleCatChips()
        applyWallpaper()
        if (skin.beam) findViewById<BeamView>(R.id.fxBeam).start()
    }

    private fun bindCuff() {
        val row = findViewById<LinearLayout>(R.id.cuffRow)
        val d = resources.displayMetrics.density
        val defs = listOf(
            "🔌" to "接口", "🎨" to "主题", "⚡" to "悬浮",
            "🖼" to "桌面", "✍️" to "其他"
        )
        val sink = listOf(12, 5, 0, 5, 12)   // 弧形下沉：两端低、中间高
        defs.forEachIndexed { i, (emoji, label) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding((6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt(), 0)
            }
            val icon = TextView(this).apply {
                text = emoji
                textSize = 16f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams((38 * d).toInt(), (38 * d).toInt())
            }
            val lab = TextView(this).apply {
                text = label
                textSize = 10f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (5 * d).toInt() }
            }
            val dot = View(this).apply {
                rotation = 45f
                layoutParams = LinearLayout.LayoutParams((4 * d).toInt(), (4 * d).toInt())
                    .apply { topMargin = (4 * d).toInt() }
            }
            item.addView(icon)
            item.addView(lab)
            item.addView(dot)
            item.setOnClickListener { selectCuff(i) }
            item.translationY = sink[i] * d
            row.addView(item)
            cuffItems.add(CuffItem(icon, lab, dot))
        }
        selectCuff(0, animate = false)
        // 支持从首页等直达某个标签页（extra "tab"）
        when (intent?.getStringExtra(EXTRA_TAB)) {
            "api" -> selectCuff(0, animate = false)
            "theme" -> selectCuff(1, animate = false)
            "ball" -> selectCuff(2, animate = false)
            "desktop" -> selectCuff(3, animate = false)
            "more" -> selectCuff(4, animate = false)
        }
        // 底部 dock（底弧五键 + 保存）是 ScrollView 的**兄弟节点**，默认整条吃不到滚动。
        // 挂一个竖直拖动代理，让那条 208dp 高的带子也能拖动翻页（详见 GracefulScrollView）
        findViewById<GracefulScrollView>(R.id.settingsScroll)
            .attachVerticalDragProxy(findViewById(R.id.bottomDock))
    }

    private fun selectCuff(idx: Int, animate: Boolean = true) {
        selectedCuff = idx
        val pages = listOf(
            R.id.pageApi, R.id.pageTheme, R.id.pageBall,
            R.id.pageDesktop, R.id.pageMore
        )
        pages.forEachIndexed { i, id ->
            val v = findViewById<View>(id)
            v.visibility = if (i == idx) View.VISIBLE else View.GONE
            if (i == idx && animate) {
                v.translationX = 24f * resources.displayMetrics.density
                v.alpha = 0f
                v.animate().translationX(0f).alpha(1f).setDuration(320)
                    .setInterpolator(PathInterpolator(0.32f, 0f, 0.68f, 1f)).start()
            }
        }
        styleCuff()
    }

    private fun styleCuff() {
        val s = currentSkin ?: ShellSkins.current(this)
        val d = resources.displayMetrics.density
        cuffItems.forEachIndexed { i, it ->
            val active = i == selectedCuff
            it.icon.background = ShellSkins.cuffIconBg(s, active, d)
            it.icon.setTextColor(if (active) s.accent else s.subText)
            it.label.setTextColor(if (active) s.accent else s.subText)
            it.dot.setBackgroundColor(if (active) s.accentStrong else Color.TRANSPARENT)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshWallUi()
        if (themeCatProps) selectThemeCategory()
        if (currentSkin?.beam == true) findViewById<BeamView>(R.id.fxBeam).start()
    }

    override fun onPause() {
        super.onPause()
        findViewById<BeamView>(R.id.fxBeam).stop()
    }

    internal var themeCatModern = true
    internal var themeCatProps = false
    internal var propsBox: LinearLayout? = null
    internal val propsPrimaryButtons = ArrayList<Button>()
    internal val propsSecondaryButtons = ArrayList<Button>()
    internal val propsChips = ArrayList<Pair<TextView, String>>()

    // ---------- 智能接口（页面本体在 settings/ApiPage.kt）
    //            这两个字段与「保存」共用，故留在主类 ----------

    internal var thinkingLevel: String = "off"
    internal var lastProviderId: String? = null

    // ---------- 撞色条独立选色（页面本体在 settings/MorePage.kt）
    //            selectedBarColor / barPresets 与「保存」「图标工坊」共用，故留在主类 ----------

    internal var selectedBarColor: String? = null

    internal val barPresets = listOf(
        // 浅空蓝：工坊默认底色 + 撞色条可选（2026-09-11 用户钦定加入）
        "浅空蓝" to "#8EC9EE",
        // 釉色系（窑口学名，不带年号人名）
        "天青釉" to "#7FA9B0", "粉青釉" to "#A8C3B4", "梅子青" to "#6F9E7F", "影青" to "#E0F0E8",
        "牙白" to "#F2EDDE", "乌金釉" to "#3B3630", "钧窑天蓝" to "#6E8FB5", "钧窑紫红" to "#8E4A5B",
        "郎窑红" to "#A72126", "豇豆红" to "#C45A65", "胭脂水" to "#E7A6A6", "甜白" to "#F6F3EC",
        "霁蓝" to "#1E3A5F", "茶叶末" to "#6E5B3F", "孔雀绿" to "#1F8A70", "矾红" to "#C3272B",
        "青花钴蓝" to "#2E4E8F", "鳝鱼黄" to "#B89A6A",
        // 传统色系（染织/矿物/诗文）
        "玄色" to "#2B2B33", "石榴红" to "#F20C00", "绯红" to "#C04243", "柘黄" to "#C89B3C",
        "朱砂" to "#FF4C00", "故宫红墙" to "#8C1F28", "绛紫" to "#8C4356", "藕荷" to "#E4C6D0",
        "桃红" to "#F4A7B9", "海棠红" to "#DB5A6B", "天水碧" to "#D4F2E7", "月白" to "#D6ECF0",
        "竹青" to "#789262", "石绿" to "#57C3C2", "石青" to "#2E59A7", "黛蓝" to "#425066",
        "鸦青" to "#424C50", "玄青" to "#3D3B4F", "缃色" to "#F0C239", "赤金" to "#F2BE45",
        "赭石" to "#955539", "檀" to "#B36D61", "绾" to "#A98175", "琥珀" to "#CA6924",
        "烟霞" to "#D8A7B1", "艾绿" to "#A8BFA0"
    )

    // ---------- 壁纸槽位的文件选择器（@ActivityResult 启动器必须注册在 Activity 上，故留在主类） ----------

    internal val pickWallPage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importWallImage(uri, "page")
    }

    internal val pickWallMain = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importWallImage(uri, "main")
    }

    internal val cropReturn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshWallUi()
    }

    /** 全面屏：壁纸铺满整个屏幕（含状态栏/导航栏区域）；滚动区用 inset 让位、底部坞整体上移 */
    @Suppress("DEPRECATION")
    internal fun setupEdgeToEdge() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        // 导航栏：回到系统默认的纯白（用户钦定：不做任何染色）
        window.navigationBarColor = android.graphics.Color.WHITE
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }
        val d = resources.displayMetrics.density
        val scrollBaseBottom = (260 * d + 0.5f).toInt()
        val dockBaseMargin = (40 * d + 0.5f).toInt()
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsScroll)) { v, insets ->
            val b = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, b.top, 0, scrollBaseBottom + b.bottom)
            // 底部膜填充高度 = 坞下边距 + 导航栏高度 + 88dp（顶部还有一段渐入，覆盖带更宽）
            val foot = findViewById<View>(R.id.vwNavBridge)
            foot.layoutParams = foot.layoutParams.apply { height = dockBaseMargin + b.bottom + (120 * d).toInt() }
            insets
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomDock)) { v, insets ->
            val b = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            (v.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let { lp ->
                lp.bottomMargin = dockBaseMargin + b.bottom
                v.layoutParams = lp
            }
            insets
        }
    }

    private fun importImage(uri: Uri, target: File, okMsg: String) {
        runCatching {
            contentResolver.openInputStream(uri)!!.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            store.ballImagePath = target.absolutePath
            BallService.instance?.refreshBall()
            toast(okMsg)
        }.onFailure {
            toast("图片导入失败：${it.message ?: "未知错误"}")
        }
    }

    // ---------- OCR 识别语言（页面本体在 settings/BallPage.kt）
    //            langIds 与「保存」共用，故留在主类 ----------

    internal val langIds = listOf(
        R.id.cbLangLatin to "latin",
        R.id.cbLangChinese to "chinese",
        R.id.cbLangJapanese to "japanese",
        R.id.cbLangKorean to "korean",
        R.id.cbLangDevanagari to "devanagari"
    )

    internal fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

