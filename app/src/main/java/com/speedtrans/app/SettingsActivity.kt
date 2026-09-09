package com.speedtrans.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.text.Editable
import android.text.TextWatcher
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
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
import com.speedtrans.app.store.SettingsStore
import com.speedtrans.app.theme.Palette
import com.speedtrans.app.theme.ShellSkin
import com.speedtrans.app.theme.ShellSkins
import com.speedtrans.app.theme.ThemeEngine
import com.speedtrans.app.translate.Providers
import com.speedtrans.app.translate.TranslateCoordinator
import com.speedtrans.app.translate.TranslateEngine
import com.speedtrans.app.ui.BeamView
import com.speedtrans.app.ui.CircuitLampView
import com.speedtrans.app.ui.ScanlineView
import com.speedtrans.app.ui.Wallpaper
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var store: SettingsStore
    private var selectedColor = "#E6FF4757"
    private var selectedShape = "circle"
    private var selectedSizeDp = 52

    private val ballColors = listOf(
        "#E6FF4757", "#E62E86FF", "#E600C853",
        "#E6333333", "#E69C27B0", "#E6FF9500"
    )

    private val pickBallImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importImage(uri, File(filesDir, "ball_image"), "悬浮球图片已更新")
    }

    private val pickShortcutImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pinShortcut(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = SettingsStore(this)

        applySkin()
        bindCuff()
        bindSkinRow()
        bindBarColor()
        bindWallpaper()
        bindThemePicker()
        bindApi()
        bindPrompt()
        bindBallAppearance()
        bindPanelAppearance()
        bindPanelButtons()
        bindOcr()
        bindLauncherSection()

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }

        ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))
    }

    // ---------- 壳层皮肤与 Cuff Links 导航 ----------

    private var currentSkin: ShellSkin? = null
    private var selectedCuff = 0
    private val cuffItems = mutableListOf<CuffItem>()

    private data class CuffItem(
        val icon: TextView, val label: TextView, val dot: View
    )

    private fun applySkin() {
        val skin = ShellSkins.current(this)
        currentSkin = skin
        findViewById<View>(R.id.rootSettings).setBackgroundColor(skin.bg)
        findViewById<ScanlineView>(R.id.fxScanlines).visibility =
            if (skin.scanline) View.VISIBLE else View.GONE
        findViewById<BeamView>(R.id.fxBeam).visibility =
            if (skin.beam) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvShellTitle).setTextColor(skin.accent)
        findViewById<View>(R.id.titleLine).setBackgroundColor(skin.accent)
        findViewById<LinearLayout>(R.id.bottomDock).background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, skin.bg)
        )
        ShellSkins.applyShell(
            findViewById(R.id.rootSettings), skin,
            cardIds = setOf(R.id.tvUsage),
            subIds = setOf(R.id.tvProviderNote)
        )
        ShellSkins.bindFocusGlow(
            this, { currentSkin ?: ShellSkins.current(this) },
            R.id.acProvider, R.id.etUrl, R.id.etKey, R.id.etModel
        )
        refreshThinkingRow()
        styleThinkingChips()
        styleSkinChips()
        styleCuff()
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
        if (currentSkin?.beam == true) findViewById<BeamView>(R.id.fxBeam).start()
    }

    override fun onPause() {
        super.onPause()
        findViewById<BeamView>(R.id.fxBeam).stop()
    }

    // ---------- 主题选择：分类 + 整套配色预览卡 ----------

    private fun bindThemePicker() {
        findViewById<TextView>(R.id.chipModern).setOnClickListener { themeCatModern = true; selectThemeCategory() }
        findViewById<TextView>(R.id.chipChinese).setOnClickListener { themeCatModern = false; selectThemeCategory() }
        selectThemeCategory()
    }

    private var themeCatModern = true

    /** 主题分类选择器（主题变更后原地重画，不 recreate 不跳页） */
    private fun selectThemeCategory() {
        val pal = ThemeEngine.current(this)
        val chipModern = findViewById<TextView>(R.id.chipModern)
        val chipChinese = findViewById<TextView>(R.id.chipChinese)
        val cardRow = findViewById<LinearLayout>(R.id.themeCardRow)
        val d = resources.displayMetrics.density

        val list = if (themeCatModern) ThemeEngine.palettes.filter { it.group == "modern" }
                   else ThemeEngine.palettes.filter { it.group == "chinese" }
        fun style(chip: TextView, on: Boolean) {
            chip.background = ThemeEngine.cardDrawable(
                if (on) pal.accent else pal.card, 18f, d, pal.cardStroke)
            chip.setTextColor(if (on) {
                if (Color.luminance(pal.accent) > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
            } else pal.text)
            chip.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        style(chipModern, themeCatModern)
        style(chipChinese, !themeCatModern)

        cardRow.removeAllViews()
        list.forEach { p -> cardRow.addView(themeRow(p, p.id == pal.id)) }
    }

    /** 全宽主题行：左三段色条（整套搭配预览）+ 右主题名，整行可点 */
    private fun themeRow(p: Palette, selected: Boolean): View {
        val d = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * d).toInt() }
            background = ThemeEngine.cardDrawable(
                if (selected) p.barBg else p.card, 12f, d,
                if (selected) p.accent else p.cardStroke
            )
        }
        // 三段色条：accent 一半，card/bg 各四分之一 —— 整套搭配一目了然
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams((96 * d).toInt(), (30 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = (6 * d)
                setColor(p.bg)
            }
            clipToOutline = true
        }
        listOf(p.accent to 0.5f, p.card to 0.25f, p.bg to 0.25f).forEach { (col, w) ->
            val seg = View(this).apply { setBackgroundColor(col) }
            seg.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w)
            strip.addView(seg)
        }
        row.addView(strip)

        val name = TextView(this).apply {
            text = p.name
            textSize = 13f
            setTextColor(if (selected) p.accent else p.text)
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (10 * d).toInt()
            }
        }
        row.addView(name)
        row.setOnClickListener {
            ThemeEngine.save(this, p.id)
            refreshAfterThemeChange()
        }
        return row
    }

    /** 主题变更后原地刷新：不 recreate——页签、滚动位置、当前分页全部保持 */
    private fun refreshAfterThemeChange() {
        applySkin()
        selectThemeCategory()
    }

    // ---------- 翻译接口 ----------

    private var thinkingLevel: String = "off"

    private fun bindApi() {
        val acProvider = findViewById<AutoCompleteTextView>(R.id.acProvider)
        val etUrl = findViewById<AutoCompleteTextView>(R.id.etUrl)
        val etKey = findViewById<EditText>(R.id.etKey)
        val etModel = findViewById<AutoCompleteTextView>(R.id.etModel)
        val tvNote = findViewById<TextView>(R.id.tvProviderNote)

        thinkingLevel = store.thinkingLevel

        acProvider.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line,
                Providers.all.map { it.label })
        )
        acProvider.setOnItemClickListener { _, _, pos, _ ->
            val p = Providers.all[pos]
            if (p.url.isNotEmpty()) etUrl.setText(p.url)
            if (p.models.isNotEmpty()) etModel.setText(p.models.first())
            tvNote.text = p.note
            thinkingLevel = p.levels.firstOrNull()?.second ?: "off"
            refreshThinkingRow()
        }

        etUrl.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line,
                Providers.all.filter { it.url.isNotEmpty() }.map { it.url })
        )
        val modelAdapter = ArrayAdapter(
            this, android.R.layout.simple_dropdown_item_1line, mutableListOf<String>()
        )
        etModel.setAdapter(modelAdapter)

        etUrl.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val u = s?.toString() ?: ""
                val p = Providers.match(u)
                tvNote.text = p?.note ?: ""
                if (p != null) {
                    modelAdapter.clear()
                    modelAdapter.addAll(p.models)
                }
                refreshThinkingRow()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        etUrl.setText(store.baseUrl)
        etKey.setText(store.apiKey)
        etModel.setText(store.model)
        refreshThinkingRow()

        val lamp = findViewById<CircuitLampView>(R.id.lampTest)
        findViewById<Button>(R.id.btnTest).setOnClickListener {
            // 先落字段再测试（未保存也能测）
            store.baseUrl = etUrl.text.toString()
            store.apiKey = etKey.text.toString()
            store.model = etModel.text.toString()
            lamp.setState(CircuitLampView.State.TESTING)
            TranslateEngine(store).testConnection { msg ->
                val ok = msg.startsWith("✅")
                runOnUiThread {
                    lamp.setState(if (ok) CircuitLampView.State.OK else CircuitLampView.State.FAIL)
                    tvNote.text = msg
                }
            }
        }
    }

    private fun refreshThinkingRow() {
        val tv = findViewById<TextView>(R.id.tvThinkingLabel)
        val row = findViewById<LinearLayout>(R.id.tierRow)
        val url = findViewById<AutoCompleteTextView>(R.id.etUrl).text.toString()
        val p = Providers.match(url)
        if (p == null || p.levels.isEmpty()) {
            tv.text = "🧠 思考档位（按接口地址自动识别服务商）"
            row.visibility = View.GONE
            return
        }
        tv.text = "🧠 思考档位 · ${p.label}"
        row.visibility = View.VISIBLE
        if (p.levels.none { it.second == thinkingLevel }) thinkingLevel = p.levels.first().second
        row.removeAllViews()
        val d = resources.displayMetrics.density
        p.levels.forEach { (label, value) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 12f
                setPadding((14 * d).toInt(), (7 * d).toInt(), (14 * d).toInt(), (7 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (8 * d).toInt() }
                setOnClickListener { thinkingLevel = value; styleThinkingChips() }
            }
            row.addView(chip)
        }
        styleThinkingChips()
    }

    private fun styleThinkingChips() {
        val s = currentSkin ?: ShellSkins.current(this)
        val row = findViewById<LinearLayout>(R.id.tierRow)
        val url = findViewById<AutoCompleteTextView>(R.id.etUrl).text.toString()
        val p = Providers.match(url) ?: return
        val d = resources.displayMetrics.density
        for (i in 0 until row.childCount) {
            val c = row.getChildAt(i) as TextView
            val selected = p.levels.getOrNull(i)?.second == thinkingLevel
            c.background = ShellSkins.chipBg(s, selected, d)
            c.setTextColor(ShellSkins.chipText(s, selected))
        }
    }

    private fun bindSkinRow() {
        val row = findViewById<LinearLayout>(R.id.skinRow)
        row.removeAllViews()
        val d = resources.displayMetrics.density
        ShellSkins.list(this).forEach { sk ->
            val chip = TextView(this).apply {
                text = sk.name
                textSize = 13f
                tag = sk.id
                setPadding((14 * d).toInt(), (9 * d).toInt(), (14 * d).toInt(), (9 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (8 * d).toInt() }
                setOnClickListener {
                    ShellSkins.save(this@SettingsActivity, sk.id)
                    applySkin()
                }
            }
            row.addView(chip)
        }
        styleSkinChips()
    }

    private fun styleSkinChips() {
        val s = currentSkin ?: ShellSkins.current(this)
        val row = findViewById<LinearLayout>(R.id.skinRow)
        val curId = ShellSkins.current(this).id
        val d = resources.displayMetrics.density
        for (i in 0 until row.childCount) {
            val c = row.getChildAt(i) as TextView
            val selected = c.tag == curId
            c.background = ShellSkins.chipBg(s, selected, d)
            c.setTextColor(ShellSkins.chipText(s, selected))
        }
    }

    // ---------- 撞色条独立选色 ----------

    private var selectedBarColor: String? = null

    private val barPresets = listOf(
        "天水碧" to "#D4F2E7", "月白" to "#D6ECF0", "青白" to "#E0F0E8", "粉青釉" to "#A8C3B4",
        "梅子青" to "#6F9E7F", "竹青" to "#789262", "孔雀绿" to "#1F8A70", "石绿" to "#57C3C2",
        "天青釉" to "#7FA9B0", "钧窑天蓝" to "#6E8FB5", "青花钴" to "#2E4E8F", "霁蓝" to "#1E3A5F",
        "黛蓝" to "#425066", "鸦青" to "#424C50", "玄青" to "#3D3B4F", "藕荷" to "#E4C6D0",
        "桃红" to "#F4A7B9", "海棠红" to "#DB5A6B", "豇豆红" to "#C45A65", "胭脂水" to "#E7A6A6",
        "郎窑红" to "#A72126", "矾红" to "#C3272B", "朱砂" to "#FF4C00", "故宫红墙" to "#8C1F28",
        "绛紫" to "#8C4356", "钧窑紫红" to "#8E4A5B", "缃色" to "#F0C239", "赤金" to "#F2BE45",
        "鳝鱼黄" to "#B89A6A", "茶叶末" to "#6E5B3F", "赭石" to "#955539", "檀" to "#B36D61",
        "绾" to "#A98175", "琥珀" to "#CA6924", "烟霞" to "#D8A7B1", "艾背" to "#A8BFA0"
    )

    private fun bindBarColor() {
        selectedBarColor = store.barColorHex.ifEmpty { null }
        val row = findViewById<LinearLayout>(R.id.barRow)
        row.removeAllViews()
        val d = resources.displayMetrics.density
        val follow = TextView(this).apply {
            text = "跟随主题"
            textSize = 12f
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (10 * d).toInt() }
            setOnClickListener { selectedBarColor = null; styleBarRow() }
        }
        row.addView(follow)
        barPresets.forEach { (_, hex) ->
            val sw = View(this).apply {
                layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt())
                    .apply { marginEnd = (10 * d).toInt() }
                setOnClickListener { selectedBarColor = hex; styleBarRow() }
            }
            row.addView(sw)
        }
        styleBarRow()
    }

    private fun styleBarRow() {
        val row = findViewById<LinearLayout>(R.id.barRow)
        val d = resources.displayMetrics.density
        val skin = currentSkin ?: ShellSkins.current(this)
        val follow = row.getChildAt(0) as TextView
        val followSel = selectedBarColor == null
        follow.background = ShellSkins.chipBg(skin, followSel, d)
        follow.setTextColor(ShellSkins.chipText(skin, followSel))
        barPresets.forEachIndexed { i, (_, hex) ->
            val sw = row.getChildAt(i + 1)
            val isSel = selectedBarColor == hex
            sw.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(hex))
                setStroke(if (isSel) (4 * d).toInt() else 0, 0xFFFFFFFF.toInt())
            }
        }
    }

    // ---------- 页面壁纸 ----------

    private val pickWallpaper = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val target = File(filesDir, "page_wallpaper")
            if (Wallpaper.importFrom(this, uri, target)) {
                store.wallpaperPath = target.absolutePath
                applyWallpaper()
                toast("壁纸已更新")
            } else {
                toast("图片导入失败")
            }
        }
    }

    private fun bindWallpaper() {
        findViewById<Button>(R.id.btnPickWallpaper).setOnClickListener { pickWallpaper.launch("image/*") }
        findViewById<Button>(R.id.btnClearWallpaper).setOnClickListener {
            File(filesDir, "page_wallpaper").delete()
            store.wallpaperPath = ""
            applyWallpaper()
            toast("已清除壁纸")
        }
        findViewById<Switch>(R.id.swWallSettings).apply {
            isChecked = store.wallpaperOnSettings
            setOnCheckedChangeListener { _, c ->
                store.wallpaperOnSettings = c
                applyWallpaper()
            }
        }
        findViewById<Switch>(R.id.swWallMain).apply {
            isChecked = store.wallpaperOnMain
            setOnCheckedChangeListener { _, c ->
                store.wallpaperOnMain = c
                applyWallpaper()
            }
        }
        val sb = findViewById<SeekBar>(R.id.sbWallDim)
        val tv = findViewById<TextView>(R.id.tvWallDimVal)
        sb.progress = store.wallpaperDim
        tv.text = "${store.wallpaperDim}%"
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sk: SeekBar?, p: Int, fromUser: Boolean) {
                tv.text = "$p%"
                if (fromUser) {
                    store.wallpaperDim = p
                    applyWallpaper()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun applyWallpaper() {
        val skin = currentSkin ?: ShellSkins.current(this)
        Wallpaper.applyTo(
            this, R.id.ivWallpaper, R.id.wpScrim,
            store.wallpaperPath, store.wallpaperDim, skin.bg, store.wallpaperOnSettings
        )
    }

    // ---------- 提示词 ----------

    private fun bindPrompt() {
        findViewById<EditText>(R.id.etPrompt).setText(store.customPrompt)
        // 触发模式：手指按住标签行，浮现"二选一"规则说明（View.setTooltipText，API 26+）
        findViewById<TextView>(R.id.tvPromptLabel).setTooltipText(
            "二选一：留空 = 使用内置极速翻译词；填写任意内容 = 完全以你的为准（可删可改，也能加一句小小的问候）"
        )
    }

    // ---------- 悬浮球外观 ----------

    private fun bindBallAppearance() {
        val etText = findViewById<EditText>(R.id.etBallText)
        etText.setText(store.ballText)
        selectedColor = store.ballColorHex
        selectedShape = store.ballShape
        selectedSizeDp = store.ballSizeDp

        val row = findViewById<LinearLayout>(R.id.colorRow)
        val dp40 = (40 * resources.displayMetrics.density).toInt()
        ballColors.forEachIndexed { i, hex ->
            val v = View(this)
            v.layoutParams = LinearLayout.LayoutParams(dp40, dp40).apply {
                marginEnd = (10 * resources.displayMetrics.density).toInt()
            }
            paintSwatch(v, hex, hex == selectedColor)
            v.setOnClickListener {
                selectedColor = hex
                for (j in 0 until row.childCount) {
                    paintSwatch(row.getChildAt(j), ballColors[j], j == i)
                }
            }
            row.addView(v)
        }

        val rgShape = findViewById<RadioGroup>(R.id.rgShape)
        rgShape.check(
            when (selectedShape) {
                "roundrect" -> R.id.rbRounded
                "cut" -> R.id.rbCut
                "triangle" -> R.id.rbTriangle
                else -> R.id.rbCircle
            }
        )
        rgShape.setOnCheckedChangeListener { _, id ->
            selectedShape = when (id) {
                R.id.rbRounded -> "roundrect"
                R.id.rbCut -> "cut"
                R.id.rbTriangle -> "triangle"
                else -> "circle"
            }
        }

        val rgSize = findViewById<RadioGroup>(R.id.rgSize)
        rgSize.check(
            when (selectedSizeDp) {
                44 -> R.id.rbSmall
                60 -> R.id.rbBig
                else -> R.id.rbMid
            }
        )
        rgSize.setOnCheckedChangeListener { _, id ->
            selectedSizeDp = when (id) {
                R.id.rbSmall -> 44
                R.id.rbBig -> 60
                else -> 52
            }
        }

        findViewById<Button>(R.id.btnPickImage).setOnClickListener {
            pickBallImage.launch("image/*")
        }
        findViewById<Button>(R.id.btnClearImage).setOnClickListener {
            File(filesDir, "ball_image").delete()
            store.ballImagePath = ""
            BallService.instance?.refreshBall()
            toast("已恢复文字球")
        }
    }

    private fun paintSwatch(v: View, hex: String, selected: Boolean) {
        v.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(hex))
            setStroke(if (selected) (6 * resources.displayMetrics.density).toInt() else 0, Color.WHITE)
        }
    }

    private fun paintSwatch(v: View, color: Int, selected: Boolean) {
        v.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(if (selected) (6 * resources.displayMetrics.density).toInt() else 0, Color.WHITE)
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

    // ---------- OCR：语言 / 阈值 / 双击窗 / 游戏检测 ----------

    private val langIds = listOf(
        R.id.cbLangLatin to "latin",
        R.id.cbLangChinese to "chinese",
        R.id.cbLangJapanese to "japanese",
        R.id.cbLangKorean to "korean",
        R.id.cbLangDevanagari to "devanagari"
    )

    private fun bindOcr() {
        // 翻译模式三选一（与通知栏按钮循环同步）
        findViewById<RadioGroup>(R.id.rgMode).check(
            when (store.translateMode) {
                "text" -> R.id.rbModeText
                "ocr" -> R.id.rbModeOcr
                else -> R.id.rbModeSmart
            }
        )
        val langs = store.ocrLanguages
        langIds.forEach { (id, code) ->
            findViewById<CheckBox>(id).isChecked = code in langs
        }
        findViewById<Switch>(R.id.swGameDetect).isChecked = store.gameAutoDetect

        val sbT = findViewById<SeekBar>(R.id.sbThreshold)
        val tvT = findViewById<TextView>(R.id.tvThresholdVal)
        sbT.progress = (store.smartThresholdChars - 5).coerceIn(0, 95)
        tvT.text = "${store.smartThresholdChars}字"
        sbT.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tvT.text = "${p + 5}字"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val sbM = findViewById<SeekBar>(R.id.sbMaxChars)
        val tvM = findViewById<TextView>(R.id.tvMaxCharsVal)
        sbM.progress = ((store.maxChars - 4000) / 1000).coerceIn(0, 46)
        tvM.text = "${store.maxChars}字"
        sbM.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tvM.text = "${p * 1000 + 4000}字"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    // ---------- 译文面板 ----------

    private fun bindPanelAppearance() {
        val sb = findViewById<SeekBar>(R.id.sbHeight)
        val tvVal = findViewById<TextView>(R.id.tvHeightVal)
        sb.progress = (store.overlayHeightPct - 30).coerceIn(0, 70)
        tvVal.text = "${store.overlayHeightPct}%"
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tvVal.text = "${p + 30}%"
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val rg = findViewById<RadioGroup>(R.id.rgBtnSize)
        rg.check(
            when (store.overlayButtonScalePct) {
                85 -> R.id.rbBtnSmall
                125 -> R.id.rbBtnBig
                else -> R.id.rbBtnMid
            }
        )
    }

    // ---------- 面板按钮自定义 ----------

    private fun bindPanelButtons() {
        findViewById<Switch>(R.id.swShowCopy).isChecked = store.showCopy
        findViewById<Switch>(R.id.swShowClose).isChecked = store.showClose

        val rgSide = findViewById<RadioGroup>(R.id.rgBtnSide)
        rgSide.check(if (store.btnCloseLeft) R.id.rbSideLeft else R.id.rbSideRight)

        val sbPad = findViewById<SeekBar>(R.id.sbBtnPad)
        val tvPad = findViewById<TextView>(R.id.tvBtnPadVal)
        sbPad.progress = store.btnPaddingDp.coerceIn(0, 24)
        tvPad.text = "${store.btnPaddingDp}dp"
        sbPad.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tvPad.text = "${p}dp"
                if (fromUser) {
                    // 实时预览：拖动即生效（面板开着直接变，关着则下次翻译生效）
                    store.btnPaddingDp = p
                    TranslateCoordinator.liveEdgePadding(p)
                }
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    // ---------- App 图标 / 桌面入口 ----------

    private val aliasOrder = listOf(
        ".main_red" to "闪译", ".main_blue" to "备忘录", ".main_green" to "工具箱"
    )

    private fun bindLauncherSection() {
        findViewById<Button>(R.id.btnAliasCycle).setOnClickListener { cycleAlias() }
        findViewById<Button>(R.id.btnPinShortcut).setOnClickListener {
            pickShortcutImage.launch("image/*")
        }
    }

    /** 单按钮循环：闪译(红) → 备忘录(蓝) → 工具箱(绿) → 闪译，互斥启用 */
    private fun cycleAlias() {
        val pm = packageManager
        val cur = aliasOrder.indexOfFirst { (cls, _) ->
            pm.getComponentEnabledSetting(ComponentName(packageName, "$packageName$cls")) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }.let { if (it < 0) 0 else it }
        val next = aliasOrder[(cur + 1) % aliasOrder.size]
        aliasOrder.forEach { (cls, _) ->
            pm.setComponentEnabledSetting(
                ComponentName(packageName, "$packageName$cls"),
                if (cls == next.first) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        toast("桌面图标已切换为「${next.second}」· 刷新需数秒到数分钟")
    }

    private fun pinShortcut(uri: Uri) {
        runCatching {
            val name = findViewById<EditText>(R.id.etShortcutName).text.toString().ifBlank { "闪译" }
            val sm = getSystemService(android.content.Context.SHORTCUT_SERVICE) as android.content.pm.ShortcutManager
            if (!sm.isRequestPinShortcutSupported) {
                toast("当前桌面不支持固定快捷方式")
                return
            }
            val bmp = decodeScaled(uri, 192)
            val info = android.content.pm.ShortcutInfo.Builder(this, "entry_${name}")
                .setShortLabel(name)
                .setLongLabel(name)
                .setIcon(android.graphics.drawable.Icon.createWithBitmap(bmp))
                .setIntent(
                    Intent(this, MainActivity::class.java)
                        .setAction(Intent.ACTION_MAIN)
                )
                .build()
            sm.requestPinShortcut(info, null)
            toast("请在系统弹窗中确认添加到桌面。若未出现弹窗：\n系统设置 → 应用管理 → 闪译 → 权限管理 → 「桌面快捷方式」→ 允许，再试一次")
        }.onFailure {
            toast("创建失败：${it.message ?: "未知错误"}")
        }
    }

    private fun decodeScaled(uri: Uri, target: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, opts)!!
        }
    }

    // ---------- 保存 ----------

    private fun save() {
        store.baseUrl = findViewById<EditText>(R.id.etUrl).text.toString()
        store.apiKey = findViewById<EditText>(R.id.etKey).text.toString()
        store.model = findViewById<EditText>(R.id.etModel).text.toString()
        store.thinkingLevel = thinkingLevel
        store.customPrompt = findViewById<EditText>(R.id.etPrompt).text.toString()

        store.ballText = findViewById<EditText>(R.id.etBallText).text.toString()
        store.ballColorHex = selectedColor
        store.ballShape = selectedShape
        store.ballSizeDp = selectedSizeDp

        store.overlayHeightPct = findViewById<SeekBar>(R.id.sbHeight).progress + 30
        store.overlayButtonScalePct = when (findViewById<RadioGroup>(R.id.rgBtnSize).checkedRadioButtonId) {
            R.id.rbBtnSmall -> 85
            R.id.rbBtnBig -> 125
            else -> 100
        }
        store.translateMode = when (findViewById<RadioGroup>(R.id.rgMode).checkedRadioButtonId) {
            R.id.rbModeText -> "text"
            R.id.rbModeOcr -> "ocr"
            else -> "smart"
        }
        store.ocrLanguages = langIds.mapNotNull { (id, code) ->
            if (findViewById<CheckBox>(id).isChecked) code else null
        }.toSet()
        store.gameAutoDetect = findViewById<Switch>(R.id.swGameDetect).isChecked
        store.smartThresholdChars = findViewById<SeekBar>(R.id.sbThreshold).progress + 5
        store.maxChars = findViewById<SeekBar>(R.id.sbMaxChars).progress * 1000 + 4000
        store.showCopy = findViewById<Switch>(R.id.swShowCopy).isChecked
        store.showClose = findViewById<Switch>(R.id.swShowClose).isChecked
        store.btnCloseLeft =
            findViewById<RadioGroup>(R.id.rgBtnSide).checkedRadioButtonId == R.id.rbSideLeft
        store.btnPaddingDp = findViewById<SeekBar>(R.id.sbBtnPad).progress
        store.barColorHex = selectedBarColor ?: ""

        BallService.instance?.refreshBall()
        TranslateCoordinator.closeOverlay()
        // 通知栏标题显示当前模式，保存后立即同步
        ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))
        toast("已保存")
        finish()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
