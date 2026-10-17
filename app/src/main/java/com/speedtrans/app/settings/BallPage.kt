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
                "rectH" -> R.id.rbRectH
                else -> R.id.rbCircle
            }
        }
        (if (selectedShape == "triangle" || selectedShape == "rect" || selectedShape == "rectH") rgShapeRow2 else rgShapeRow1)
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
                    R.id.rbRectH -> "rectH"
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
        for (rid in intArrayOf(R.id.rbCircle, R.id.rbRounded, R.id.rbCut, R.id.rbTriangle, R.id.rbRect, R.id.rbRectH)) {
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

        findViewById<Switch>(R.id.swImgAsIs).isChecked = store.ballImageAsIs
        findViewById<Switch>(R.id.swImgAsIs).setOnCheckedChangeListener { _, on ->
            store.ballImageAsIs = on
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

        // 吐魂球与「切角八边形」单选圆点对齐（球心同一垂直线）；只平移，不影响布局。
        val tuhunBall = findViewById<View>(R.id.btnTuhun)
        val tuhunBlock = tuhunBall.parent as? View
        val cutOption = findViewById<View>(R.id.rbCut)
        if (tuhunBlock != null) {
            fun alignTuhun() {
                if (cutOption.width <= 0 || tuhunBall.width <= 0) return
                val cut = IntArray(2)
                val ball = IntArray(2)
                cutOption.getLocationOnScreen(cut)
                tuhunBall.getLocationOnScreen(ball)
                val target = cut[0] + cutOption.width / 2f
                val current = ball[0] + tuhunBall.width / 2f
                tuhunBlock.translationX = target - current
            }
            tuhunBlock.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    alignTuhun()
                }
            })
            // 页面切换/字体加载会二次布局，补两次延迟对齐
            tuhunBlock.postDelayed({ alignTuhun() }, 250)
            tuhunBlock.postDelayed({ alignTuhun() }, 700)
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
        listOf(R.id.sbMaxChars).forEach { fixSeekDrag(it) }
        // 翻译模式（仅文本 / 仅识图）已改为只由通知条点按切换（2026-10-15 轻响：设置页该模块删除）
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
        listOf(R.id.sbHeight, R.id.sbTextSize).forEach { fixSeekDrag(it) }
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
        listOf(R.id.sbBtnPad).forEach { fixSeekDrag(it) }
        findViewById<Switch>(R.id.swShowCopy).isChecked = store.showCopy
        findViewById<Switch>(R.id.swShowClose).isChecked = store.showClose
        findViewById<Switch>(R.id.swAccumulate).isChecked = store.panelAccumulate

        findViewById<Switch>(R.id.swCornerClose).isChecked = store.showCornerClose

        val rgSide = findViewById<RadioGroup>(R.id.rgBtnSide)
        rgSide.check(if (store.btnCloseLeft) R.id.rbSideLeft else R.id.rbSideRight)

        val sbPad = findViewById<SeekBar>(R.id.sbBtnPad)
        val tvPad = findViewById<TextView>(R.id.tvBtnPadVal)
        sbPad.progress = store.btnPaddingDp.coerceIn(0, 24)
        tvPad.text = "${store.btnPaddingDp}dp"
        findViewById<TextView>(R.id.btnPadDefault).setOnClickListener {
            // 默认值：8dp（SettingsStore 的默认）
            sbPad.progress = 8
            tvPad.text = "8dp"
            store.btnPaddingDp = 8
            TranslateCoordinator.liveEdgePadding(8)
        }
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

// ---------- 沉浸式翻译（2026-10-14） ----------

internal fun SettingsActivity.bindImmersive() {
        listOf(R.id.sbImmSeamless, R.id.sbImmCover, R.id.sbImmTextSize).forEach { fixSeekDrag(it) }
        findViewById<Switch>(R.id.swImmFontAuto).isChecked = store.immersiveFontAuto

        val sbS = findViewById<SeekBar>(R.id.sbImmSeamless)
        val tvS = findViewById<TextView>(R.id.tvImmSeamlessVal)
        sbS.progress = store.immersiveSeamlessPct
        tvS.text = "${store.immersiveSeamlessPct}%"
        sbS.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tvS.text = "$p%"
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val sbC = findViewById<SeekBar>(R.id.sbImmCover)
        val tvC = findViewById<TextView>(R.id.tvImmCoverVal)
        sbC.progress = (store.immersiveCoverAlphaPct - 60).coerceIn(0, 40)
        tvC.text = "${store.immersiveCoverAlphaPct}%"
        sbC.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tvC.text = "${p + 60}%"
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val sbT = findViewById<SeekBar>(R.id.sbImmTextSize)
        val tvT = findViewById<TextView>(R.id.tvImmTextSizeVal)
        sbT.max = 12
        sbT.progress = (store.immersiveTextSize - 12).coerceIn(0, 12)
        tvT.text = "${store.immersiveTextSize}sp"
        sbT.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tvT.text = "${p + 12}sp"
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
}

/**
 * 滑条手势修复（2026-10-15 真机复现）：
 * 拖动滑条时，设置页的滚动容器会把触摸**截走** → 变成"调滑条 = 整页上下滚"。
 * 做法：按下/移动期间禁止父容器拦截；抬起再放开。
 */
internal fun SettingsActivity.fixSeekDrag(id: Int) {
    findViewById<SeekBar>(id).setOnTouchListener { v, e ->
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_MOVE ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL ->
                v.parent?.requestDisallowInterceptTouchEvent(false)
        }
        false
    }
}
