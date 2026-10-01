package com.speedtrans.app.settings

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.speedtrans.app.R
import com.speedtrans.app.SettingsActivity
import com.speedtrans.app.service.BallService
import com.speedtrans.app.translate.TranslateCoordinator
import java.io.File

/**
 * 设置页 · 「⚡ 悬浮球面板」标签页（拆 SettingsActivity 第 2 步，2026-10-01）。
 *
 * 含四块：悬浮球外观 / OCR 识别语言 / 译文面板 / 面板按钮自定义。
 * 全部为 `SettingsActivity` 的扩展函数——**纯搬家，逻辑一行未动**；
 * 拆成扩展函数而不是新类，是因为 6 个 `registerForActivityResult` 启动器
 * 必须注册在 Activity 上，不能让状态搬到别处去。
 */

private val ballColors = listOf(
        "#E68EC9EE",
        "#E6FF4757", "#E62E86FF", "#E600C853",
        "#E6333333", "#E69C27B0", "#E6FF9500"
)
// ---------- 悬浮球外观 ----------

internal fun SettingsActivity.bindBallAppearance() {
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
                store.ballColorHex = hex
                BallService.instance?.refreshBall()
                for (j in 0 until row.childCount) {
                    paintSwatch(row.getChildAt(j), ballColors[j], j == i)
                }
            }
            row.addView(v)
        }

        // 形状两行两组（窄屏不再把"三角形"挤成竖排）；两组手动互斥
        val rgShapeRow1 = findViewById<RadioGroup>(R.id.rgShapeRow1)
        val rgShapeRow2 = findViewById<RadioGroup>(R.id.rgShapeRow2)
        val shapeIdOf = { s: String ->
            when (s) {
                "roundrect" -> R.id.rbRounded
                "cut" -> R.id.rbCut
                "triangle" -> R.id.rbTriangle
                "rect" -> R.id.rbRect
                else -> R.id.rbCircle
            }
        }
        (if (selectedShape == "triangle" || selectedShape == "rect") rgShapeRow2 else rgShapeRow1)
            .check(shapeIdOf(selectedShape))
        var shapeMutating = false   // 防止两组互清时的嵌套回调把选择改回去（"点两下才生效"的根因之一）
        val onShape = { id: Int ->
            if (!shapeMutating && id != -1) {
                shapeMutating = true
                selectedShape = when (id) {
                    R.id.rbRounded -> "roundrect"
                    R.id.rbCut -> "cut"
                    R.id.rbTriangle -> "triangle"
                    R.id.rbRect -> "rect"
                    else -> "circle"
                }
                (if (rgShapeRow1.checkedRadioButtonId == id) rgShapeRow2 else rgShapeRow1).clearCheck()
                // 即时预览：写入 + 刷新悬浮球（球浮在设置页之上，能直接看到）
                store.ballShape = selectedShape
                BallService.instance?.refreshBall()
                shapeMutating = false
            }
        }
        rgShapeRow1.setOnCheckedChangeListener { _, id -> if (id != -1) onShape(id) }
        rgShapeRow2.setOnCheckedChangeListener { _, id -> if (id != -1) onShape(id) }
        // 双保险：每个单选按钮再挂点击——"再点一次同一个形状"也重新应用+刷新（防 RadioGroup 不回调）
        for (rid in intArrayOf(R.id.rbCircle, R.id.rbRounded, R.id.rbCut, R.id.rbTriangle, R.id.rbRect)) {
            findViewById<android.widget.RadioButton>(rid).setOnClickListener { onShape(rid) }
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
            store.ballSizeDp = selectedSizeDp
            BallService.instance?.refreshBall()
        }

        findViewById<Button>(R.id.btnPickImage).setOnClickListener {
            pickBallImage.launch("image/*")
        }
        findViewById<Button>(R.id.btnClearImage).setOnClickListener {
            File(filesDir, "ball_image").delete()
            store.ballImagePath = ""
            BallService.instance?.refreshBall()
            toast("已恢复到默认")
        }
        // 内置图片球：吐魂（资源内置，零文件依赖）
        findViewById<View>(R.id.btnTuhun).setOnClickListener {
            store.ballImagePath = "res:ball_tuhun"
            BallService.instance?.refreshBall()
            toast("已切换为「吐魂」图片球")
        }
}
private fun SettingsActivity.paintSwatch(v: View, hex: String, selected: Boolean) {
        v.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(hex))
            setStroke(if (selected) (6 * resources.displayMetrics.density).toInt() else 0, Color.WHITE)
        }
}
private fun SettingsActivity.paintSwatch(v: View, color: Int, selected: Boolean) {
        v.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(if (selected) (6 * resources.displayMetrics.density).toInt() else 0, Color.WHITE)
        }
}
internal fun SettingsActivity.bindOcr() {
        // 翻译模式二选一（与通知栏按钮循环同步）
        findViewById<RadioGroup>(R.id.rgMode).check(
            if (store.translateMode == "ocr") R.id.rbModeOcr else R.id.rbModeText
        )
        val langs = store.ocrLanguages
        langIds.forEach { (id, code) ->
            findViewById<CheckBox>(id).isChecked = code in langs
        }

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

internal fun SettingsActivity.bindPanelAppearance() {
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

        // 译文文字大小（实时预览）
        val sbTs = findViewById<SeekBar>(R.id.sbTextSize)
        val tvTs = findViewById<TextView>(R.id.tvTextSizeVal)
        sbTs.max = 12
        sbTs.progress = store.overlayTextSize - 12
        tvTs.text = "${store.overlayTextSize}sp"
        sbTs.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = p + 12
                tvTs.text = "${v}sp"
                if (fromUser) {
                    store.overlayTextSize = v
                    TranslateCoordinator.liveTextSize(v)
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
}
// ---------- 面板按钮自定义 ----------

internal fun SettingsActivity.bindPanelButtons() {
        findViewById<Switch>(R.id.swShowCopy).isChecked = store.showCopy
        findViewById<Switch>(R.id.swShowClose).isChecked = store.showClose
        findViewById<Switch>(R.id.swAccumulate).isChecked = store.panelAccumulate

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
