package com.speedtrans.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.speedtrans.app.store.SettingsStore
import com.speedtrans.app.theme.ShellSkins
import com.speedtrans.app.theme.ThemeEngine
import com.speedtrans.app.ui.Wallpaper

/**
 * 主题色专页：色球分组（现代经典 / 中国传统色）+「属性设置」面板
 * （主界面壁纸 / 首页显示 / 设置页皮肤）。入口：首页「主题色」卡片。
 */
class ThemeActivity : AppCompatActivity() {

    private lateinit var store: SettingsStore
    private var propDialog: android.app.Dialog? = null
    private var ballCat = "modern"

    private val pickMain = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importWallImage(uri)
    }

    private val cropReturn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // 取景返回：数据已存，无需刷新
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme)
        store = SettingsStore(this)
        applySkin()
        buildCards()
    }

    override fun onResume() {
        super.onResume()
        applySkin()
    }

    // ---------------- 页面皮肤 ----------------

    private fun applySkin() {
        val pal = ThemeEngine.current(this)
        val d = resources.displayMetrics.density
        findViewById<View>(R.id.themeRoot).setBackgroundColor(ThemeEngine.backdrop(pal))
        findViewById<TextView>(R.id.tvThemePageTitle).setTextColor(pal.text)
        findViewById<TextView>(R.id.tvThemeHint).setTextColor(pal.subText)

        val back = findViewById<ImageView>(R.id.btnBack)
        back.setColorFilter(pal.text)
        back.rotation = 180f
        back.setOnClickListener { finish() }

        fun styleChip(chip: TextView, on: Boolean) {
            chip.background = ThemeEngine.cardDrawable(
                if (on) pal.accent else pal.card, 18f, d, pal.cardStroke
            )
            chip.setTextColor(
                if (on) {
                    if (Color.luminance(pal.accent) > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
                } else pal.text
            )
            chip.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val chipModern = findViewById<TextView>(R.id.chipModern)
        val chipChinese = findViewById<TextView>(R.id.chipChinese)
        styleChip(chipModern, ballCat == "modern")
        styleChip(chipChinese, ballCat == "chinese")
        chipModern.setOnClickListener {
            if (ballCat != "modern") {
                ballCat = "modern"
                applySkin()
                buildCards()
            }
        }
        chipChinese.setOnClickListener {
            if (ballCat != "chinese") {
                ballCat = "chinese"
                applySkin()
                buildCards()
            }
        }
        styleChip(findViewById(R.id.chipProp), false)
        findViewById<TextView>(R.id.chipProp).setOnClickListener { showProps() }

        window.statusBarColor = ThemeEngine.backdrop(pal)
        window.navigationBarColor = ThemeEngine.backdrop(pal)
        val light = Color.luminance(ThemeEngine.backdrop(pal)) > 0.5f
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    // ---------------- 主题卡片列表（复刻设置页「主题撞色」样式的行） ----------------

    private fun buildCards() {
        val pal = ThemeEngine.current(this)
        val cardRow = findViewById<LinearLayout>(R.id.boxCards)
        cardRow.removeAllViews()
        val list = ThemeEngine.palettes.filter { p ->
            if (ballCat == "modern") p.group == "modern" else p.group == "chinese"
        }
        list.forEach { p -> cardRow.addView(themeRow(p, p.id == pal.id)) }
    }

    /** 全宽主题行：左三段色条（整套搭配预览）+ 右主题名，整行可点，原地换装 */
    private fun themeRow(p: ThemeEngine.Palette, selected: Boolean): View {
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
                if (selected) (if (p.barBg != 0) p.barBg else p.accent) else p.card, 12f, d,
                if (selected) p.accent else p.cardStroke
            )
        }
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
            applySkin()
            buildCards()
        }
        return row
    }

    // ---------------- 属性设置面板 ----------------

    private fun showProps() {
        val pal = ThemeEngine.current(this)
        val den = resources.displayMetrics.density
        val dialog = android.app.Dialog(this)
        propDialog?.dismiss()
        propDialog = dialog

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * den).toInt(), (18 * den).toInt(), (20 * den).toInt(), (20 * den).toInt())
        }

        fun sectionLabel(t: String, topDp: Int) = TextView(this).apply {
            text = t
            textSize = 15f
            setTextColor(pal.text)
            setPadding(0, (topDp * den).toInt(), 0, (10 * den).toInt())
        }

        fun hint(t: String) = TextView(this).apply {
            text = t
            textSize = 12f
            setTextColor(pal.subText)
            setPadding(0, 0, 0, (12 * den).toInt())
        }

        // 标题行
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = "属性设置"
            textSize = 19f
            setTextColor(pal.text)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        titleRow.addView(TextView(this).apply {
            text = "✕"
            textSize = 17f
            setTextColor(pal.subText)
            setPadding((12 * den).toInt(), (6 * den).toInt(), (4 * den).toInt(), (6 * den).toInt())
            setOnClickListener { dialog.dismiss() }
        })
        col.addView(titleRow)

        // ---- 主界面壁纸 ----
        col.addView(sectionLabel("主界面壁纸", 18))
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnPick = Button(this).apply {
            text = "选择"
            singleLine = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnClear = Button(this).apply {
            text = "清除"
            singleLine = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = (10 * den).toInt() }
        }
        stylePrimary(btnPick, pal, den)
        styleSecondary(btnClear, pal, den)
        btnRow.addView(btnPick)
        btnRow.addView(btnClear)
        col.addView(btnRow)

        val sw = Switch(this).apply {
            setTextColor(pal.text)
            buttonTintList = android.content.res.ColorStateList.valueOf(pal.accent)
            isChecked = store.wpEnabled("main")
        }
        var swGuard = false
        sw.setOnCheckedChangeListener { _, c ->
            if (swGuard) return@setOnCheckedChangeListener
            if (c && store.wpCrop("main").isEmpty()) {
                toast("先选一张图再启用")
                swGuard = true
                sw.isChecked = false
                swGuard = false
                return@setOnCheckedChangeListener
            }
            store.setWpEnabled("main", c)
        }
        val swRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * den).toInt(), 0, 0)
        }
        swRow.addView(TextView(this).apply {
            text = "启用（应用到主界面）"
            textSize = 13f
            setTextColor(pal.text)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        swRow.addView(sw)
        col.addView(swRow)

        col.addView(
            sliderRow("遮罩浓度", 80, store.wpDim("main", 50), pal, den) { p -> store.setWpDim("main", p) }
        )

        btnPick.setOnClickListener {
            dialog.dismiss()
            pickMain.launch("image/*")
        }
        btnClear.setOnClickListener {
            clearWall()
            swGuard = true
            sw.isChecked = false
            swGuard = false
        }

        // ---- 首页显示 ----
        col.addView(sectionLabel("首页显示", 22))
        col.addView(
            sliderRow("卡片浓度（首页）", 70, store.cardAlphaPct - 30, pal, den) { p ->
                store.cardAlphaPct = p + 30
            }
        )
        col.addView(
            sliderRow("首页字号", 60, store.homeFontPct - 80, pal, den) { p -> store.homeFontPct = p + 80 }
        )

        // ---- 设置页皮肤 ----
        col.addView(sectionLabel("设置页皮肤", 22))
        col.addView(hint("导航骨架固定，换的是配色与氛围；「跟随主界面主题」= 当前主题套进设置页。"))
        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (2 * den).toInt(), 0, (4 * den).toInt())
        }
        val chips = listOf(
            TextView(this) to "ds_holo",
            TextView(this) to "follow_theme"
        )
        chips.forEachIndexed { idx, (chip, id) ->
            chip.text = if (id == "ds_holo") "炫酷黑" else "跟随主界面主题"
            chip.textSize = 13f
            chip.setPadding((18 * den).toInt(), (9 * den).toInt(), (18 * den).toInt(), (9 * den).toInt())
            chip.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { if (idx > 0) marginStart = (10 * den).toInt() }
            chip.setOnClickListener {
                ShellSkins.save(this, id)
                toast("已切换（设置页生效）")
                val nowId = ShellSkins.current(this).id
                chips.forEach { (c, cid) -> styleSkinChip(c, nowId == cid, pal, den) }
            }
            styleSkinChip(chip, ShellSkins.current(this).id == id, pal, den)
            chipRow.addView(chip)
        }
        col.addView(chipRow)

        col.background = ThemeEngine.cardDrawable(pal.card, 18f, den, blend(pal.bg, pal.accent, 0.12f))
        dialog.setContentView(android.widget.ScrollView(this).apply { addView(col) })
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        val dm = resources.displayMetrics
        dialog.window?.setLayout((dm.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun sliderRow(
        name: String, max: Int, start: Int, pal: ThemeEngine.Palette, den: Float, onSet: (Int) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (6 * den).toInt(), 0, (2 * den).toInt())
        }
        row.addView(TextView(this).apply {
            text = name
            textSize = 13f
            setTextColor(pal.text)
        })
        val sb = SeekBar(this).apply {
            this.max = max
            progress = start
            progressTintList = android.content.res.ColorStateList.valueOf(pal.accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(pal.accent)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valTv = TextView(this).apply {
            textSize = 13f
            setTextColor(pal.text)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams((56 * den).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        valTv.text = fmtFor(name, start)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sk: SeekBar?, p: Int, fromUser: Boolean) {
                valTv.text = fmtFor(name, p)
                if (fromUser) onSet(p)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        row.addView(sb)
        row.addView(valTv)
        return row
    }

    private fun fmtFor(name: String, p: Int): String = when (name) {
        "卡片浓度（首页）" -> "${p + 30}%"
        "首页字号" -> "${p + 80}%"
        else -> "$p%"
    }

    // ---------------- 壁纸流程 ----------------

    private fun importWallImage(uri: Uri) {
        val target = Wallpaper.origFile(this, "main")
        if (!Wallpaper.importFrom(this, uri, target)) {
            toast("图片导入失败")
            return
        }
        store.setWpOrig("main", target.absolutePath)
        store.setWpFrame("main", 0f, 0f, 1f)
        val dm = resources.displayMetrics
        val c = Wallpaper.cropFile(this, "main")
        if (Wallpaper.bake(this, target.absolutePath, 0f, 0f, 1f, c, dm.widthPixels, dm.heightPixels)) {
            store.setWpCrop("main", c.absolutePath)
        }
        store.setWpEnabled("main", true)
        openCrop()
    }

    private fun openCrop() {
        if (store.wpOrig("main").isEmpty() || !Wallpaper.origFile(this, "main").exists()) {
            toast("先点「选择」挑一张图片")
            return
        }
        cropReturn.launch(Intent(this, CropActivity::class.java).putExtra(CropActivity.EXTRA_SLOT, "main"))
    }

    private fun clearWall() {
        Wallpaper.origFile(this, "main").delete()
        Wallpaper.cropFile(this, "main").delete()
        store.setWpOrig("main", "")
        store.setWpCrop("main", "")
        store.setWpEnabled("main", false)
        toast("已清除")
    }

    // ---------------- 小工具 ----------------

    private fun toast(s: String) =
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show()

    private fun blend(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) * (1 - tt) + Color.alpha(b) * tt).toInt(),
            (Color.red(a) * (1 - tt) + Color.red(b) * tt).toInt(),
            (Color.green(a) * (1 - tt) + Color.green(b) * tt).toInt(),
            (Color.blue(a) * (1 - tt) + Color.blue(b) * tt).toInt()
        )
    }

    private fun stylePrimary(b: Button, pal: ThemeEngine.Palette, d: Float) {
        b.background = ThemeEngine.cardDrawable(pal.accent, 12f, d)
        b.setTextColor(
            if (Color.luminance(pal.accent) > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
        )
    }

    private fun styleSecondary(b: Button, pal: ThemeEngine.Palette, d: Float) {
        val dark = Color.luminance(pal.bg) < 0.4f
        val fill = blend(pal.bg, pal.accent, if (dark) 0.22f else 0.16f)
        val stroke = blend(fill, pal.accent, if (dark) 0.34f else 0.26f)
        b.background = ThemeEngine.cardDrawable(fill, 12f, d, stroke)
        b.setTextColor(
            if (Color.luminance(fill) > 0.5f) blend(pal.accent, 0xFF000000.toInt(), 0.30f)
            else blend(pal.accent, 0xFFFFFFFF.toInt(), 0.25f)
        )
    }

    private fun styleSkinChip(chip: TextView, selected: Boolean, pal: ThemeEngine.Palette, d: Float) {
        if (selected) {
            chip.background = ThemeEngine.cardDrawable(pal.accent, 999f, d)
            chip.setTextColor(
                if (Color.luminance(pal.accent) > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
            )
        } else {
            val fill = blend(pal.bg, pal.accent, if (Color.luminance(pal.bg) < 0.4f) 0.20f else 0.10f)
            chip.background = ThemeEngine.cardDrawable(fill, 999f, d, blend(fill, pal.accent, 0.30f))
            chip.setTextColor(pal.text)
        }
    }

}
