package com.speedtrans.app.settings

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.speedtrans.app.R
import com.speedtrans.app.SettingsActivity
import com.speedtrans.app.theme.ShellSkins

/**
 * 设置页 · 「✍️ 其他」标签页（拆 SettingsActivity 第 7 步，2026-10-01）。
 * 含：撞色条独立选色 + 提示词。
 * 纯搬家，逻辑一行未动。
 */

internal fun SettingsActivity.bindBarColor() {
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
private fun SettingsActivity.styleBarRow() {
        val row = findViewById<LinearLayout>(R.id.barRow)
        val d = resources.displayMetrics.density
        val skin = currentSkin ?: ShellSkins.current(this)
        val follow = row.getChildAt(0) as TextView
        val followSel = selectedBarColor == null
        follow.background = ShellSkins.chipBg(skin, followSel, d, store.settingsCardAlphaPct)
        follow.setTextColor(ShellSkins.chipText(skin, followSel))
        barPresets.forEachIndexed { i, (_, hex) ->
            val sw = row.getChildAt(i + 1)
            val isSel = selectedBarColor == hex
            val fill = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(hex))
            }
            if (isSel) {
                // 选中环叠在色块之上（不内缩填充），避免"圆飞速缩小 + 白环"的忙碌感
                val ring = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x00000000)
                    setStroke((3 * d).toInt(), 0xFFFFFFFF.toInt())
                }
                sw.background = android.graphics.drawable.LayerDrawable(arrayOf(fill, ring))
            } else {
                sw.background = fill
            }
        }
}
// ---------- 提示词 ----------

internal fun SettingsActivity.bindPrompt() {
        findViewById<EditText>(R.id.etPrompt).setText(store.customPrompt)
        // 触发模式：手指按住标签行，浮现"二选一"规则说明（View.setTooltipText，API 26+）
        findViewById<TextView>(R.id.tvPromptLabel).setTooltipText(
            "二选一：留空 = 使用内置极速翻译词；填写任意内容 = 完全以你的为准（可删可改，也能加一句小小的问候）"
        )
}
