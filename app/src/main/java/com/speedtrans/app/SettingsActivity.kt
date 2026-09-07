package com.speedtrans.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
import com.speedtrans.app.theme.ThemeEngine
import com.speedtrans.app.translate.TranslateCoordinator
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var store: SettingsStore

    private var selectedColor = "#E6FF4757"
    private var selectedShapeCircle = true
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

        applyTheme()
        bindTabs()
        bindThemeRows()
        bindApi()
        bindPrompt()
        bindBallAppearance()
        bindPanelAppearance()
        bindPanelButtons()
        bindLauncherSection()

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }

        // 前台保活（与主界面一致，防止本页操作期间被杀）
        ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))
    }

    // ---------- 主题 ----------

    private fun applyTheme() {
        val pal = ThemeEngine.current(this)
        findViewById<View>(R.id.rootSettings).setBackgroundColor(pal.bg)
        ThemeEngine.applyTo(findViewById(R.id.rootSettings), pal)
    }

    private fun bindTabs() {
        val pal = ThemeEngine.current(this)
        val pages = listOf(
            R.id.pageApi, R.id.pageTheme, R.id.pageBall,
            R.id.pageDesktop, R.id.pageMore
        )
        val tabs = listOf(
            R.id.tvTabApi, R.id.tvTabTheme, R.id.tvTabBall,
            R.id.tvTabDesktop, R.id.tvTabMore
        )
        fun select(idx: Int) {
            pages.forEachIndexed { i, id ->
                findViewById<View>(id).visibility = if (i == idx) View.VISIBLE else View.GONE
            }
            tabs.forEachIndexed { i, id ->
                val t = findViewById<TextView>(id)
                t.background = null
                t.setTextColor(if (i == idx) pal.accent else pal.subText)
                t.typeface = if (i == idx) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        }
        tabs.forEachIndexed { i, id -> findViewById<View>(id).setOnClickListener { select(i) } }
        select(0)
    }

    private fun bindThemeRows() {
        val lightRow = findViewById<LinearLayout>(R.id.lightThemeRow)
        val darkRow = findViewById<LinearLayout>(R.id.darkThemeRow)
        val currentId = ThemeEngine.current(this).id
        val size = (44 * resources.displayMetrics.density).toInt()
        val margin = (8 * resources.displayMetrics.density).toInt()

        fun addTo(row: LinearLayout, list: List<Palette>) {
            list.forEach { p ->
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = margin }
                }
                val sw = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(size, size)
                    paintSwatch(this, p.accent, p.id == currentId)
                    setOnClickListener {
                        ThemeEngine.save(this@SettingsActivity, p.id)
                        recreate()
                    }
                }
                val name = TextView(this).apply {
                    text = p.name
                    textSize = 10f
                    setTextColor(ThemeEngine.current(this@SettingsActivity).subText)
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = (4 * resources.displayMetrics.density).toInt() }
                }
                item.addView(sw)
                item.addView(name)
                row.addView(item)
            }
        }
        addTo(lightRow, ThemeEngine.lightPalettes)
        addTo(darkRow, ThemeEngine.darkPalettes)
    }

    // ---------- 翻译接口 ----------

    private fun bindApi() {
        findViewById<EditText>(R.id.etUrl).setText(store.baseUrl)
        findViewById<EditText>(R.id.etKey).setText(store.apiKey)
        findViewById<EditText>(R.id.etModel).setText(store.model)
    }

    // ---------- 提示词 ----------

    private fun bindPrompt() {
        findViewById<EditText>(R.id.etPrompt).setText(store.customPrompt)
    }

    // ---------- 悬浮球外观 ----------

    private fun bindBallAppearance() {
        val etText = findViewById<EditText>(R.id.etBallText)
        etText.setText(store.ballText)
        selectedColor = store.ballColorHex
        selectedShapeCircle = store.ballCircle
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
        rgShape.check(if (selectedShapeCircle) R.id.rbCircle else R.id.rbRounded)
        rgShape.setOnCheckedChangeListener { _, id -> selectedShapeCircle = id == R.id.rbCircle }

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
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    // ---------- App 图标 / 桌面入口 ----------

    private fun bindLauncherSection() {
        findViewById<Button>(R.id.btnAliasRed).setOnClickListener { applyAlias(".main_red") }
        findViewById<Button>(R.id.btnAliasBlue).setOnClickListener { applyAlias(".main_blue") }
        findViewById<Button>(R.id.btnAliasGreen).setOnClickListener { applyAlias(".main_green") }
        findViewById<Button>(R.id.btnPinShortcut).setOnClickListener {
            pickShortcutImage.launch("image/*")
        }
    }

    private fun applyAlias(cls: String) {
        val pm = packageManager
        listOf(".main_red", ".main_blue", ".main_green").forEach {
            pm.setComponentEnabledSetting(
                ComponentName(packageName, "$packageName$it"),
                if (it == cls) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        toast("已切换 · 桌面图标稍后刷新（数秒到数分钟）\n若悬浮球消失：打开本App点「🚑修复」")
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
        store.customPrompt = findViewById<EditText>(R.id.etPrompt).text.toString()

        store.ballText = findViewById<EditText>(R.id.etBallText).text.toString()
        store.ballColorHex = selectedColor
        store.ballCircle = selectedShapeCircle
        store.ballSizeDp = selectedSizeDp

        store.overlayHeightPct = findViewById<SeekBar>(R.id.sbHeight).progress + 30
        store.overlayButtonScalePct = when (findViewById<RadioGroup>(R.id.rgBtnSize).checkedRadioButtonId) {
            R.id.rbBtnSmall -> 85
            R.id.rbBtnBig -> 125
            else -> 100
        }
        store.showCopy = findViewById<Switch>(R.id.swShowCopy).isChecked
        store.showClose = findViewById<Switch>(R.id.swShowClose).isChecked
        store.btnCloseLeft =
            findViewById<RadioGroup>(R.id.rgBtnSide).checkedRadioButtonId == R.id.rbSideLeft
        store.btnPaddingDp = findViewById<SeekBar>(R.id.sbBtnPad).progress

        BallService.instance?.refreshBall()
        TranslateCoordinator.closeOverlay()
        toast("已保存")
        finish()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
