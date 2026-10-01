package com.speedtrans.app.settings

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.speedtrans.app.R
import com.speedtrans.app.SettingsActivity
import com.speedtrans.app.settings.buildPropsPage
import com.speedtrans.app.settings.fadedCard
import com.speedtrans.app.theme.Palette
import com.speedtrans.app.theme.ThemeEngine

/**
 * 设置页 · 「🎨 主题」标签页（拆 SettingsActivity 第 5 步，2026-10-01）。
 * 含：分类 chips（现代经典/中国传统色/属性设置）+ 整套配色预览卡 + 换主题后的原地刷新。
 * 纯搬家，逻辑一行未动。
 */

// ---------- 主题选择：分类 + 整套配色预览卡 ----------

internal fun SettingsActivity.bindThemePicker() {
        findViewById<TextView>(R.id.chipModern).setOnClickListener {
            themeCatProps = false; themeCatModern = true; selectThemeCategory()
        }
        findViewById<TextView>(R.id.chipChinese).setOnClickListener {
            themeCatProps = false; themeCatModern = false; selectThemeCategory()
        }
        findViewById<TextView>(R.id.chipProps).setOnClickListener {
            themeCatProps = true; selectThemeCategory()
        }
        selectThemeCategory()
}
/** 主题分类选择器（主题变更后原地重画，不 recreate 不跳页） */
internal fun SettingsActivity.selectThemeCategory() {
        val pal = ThemeEngine.current(this)
        val cardRow = findViewById<LinearLayout>(R.id.themeCardRow)

        val list = if (themeCatModern) ThemeEngine.palettes.filter { it.group == "modern" }
                   else ThemeEngine.palettes.filter { it.group == "chinese" }
        // 分类按钮与全页「设置页卡片浓度」同口径（此前漏了这一处，浓度拖到 0 它们也纹丝不动）
        restyleCatChips()

        val propsRow = findViewById<LinearLayout>(R.id.themePropsRow)
        if (themeCatProps) {
            cardRow.visibility = View.GONE
            propsRow.visibility = View.VISIBLE
            buildPropsPage()
        } else {
            propsRow.visibility = View.GONE
            cardRow.visibility = View.VISIBLE
            cardRow.removeAllViews()
            list.forEach { p -> cardRow.addView(themeRow(p, p.id == pal.id)) }
        }
}
/** 全宽主题行：左三段色条（整套搭配预览）+ 右主题名，整行可点 */
private fun SettingsActivity.themeRow(p: Palette, selected: Boolean): View {
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
private fun SettingsActivity.refreshAfterThemeChange() {
        applySkin()
        selectThemeCategory()
}
internal fun SettingsActivity.restyleCatChips() {
        styleCatChip(findViewById(R.id.chipModern), themeCatModern && !themeCatProps)
        styleCatChip(findViewById(R.id.chipChinese), !themeCatModern && !themeCatProps)
        styleCatChip(findViewById(R.id.chipProps), themeCatProps)
}
/**
 * 主题分类按钮（现代经典 / 🏮中国传统色 / 属性设置）：与「设置页卡片浓度」同口径。
 * · 填充 = 浓度；选中态保底 [CAT_CHIP_FLOOR]%，浓度再低也认得出现在在哪一类
 * · 描边 = 浓度（与同页 styleSecondary 口径一致）
 * · 文字只跟一半：(100+浓度)/2，低浓度下仍然可读
 */
/** 分类按钮「选中态」填充的浓度保底（%）：浓度再低也守住"当前分类"的辨识度 */
private const val CAT_CHIP_FLOOR = 30

private fun SettingsActivity.styleCatChip(chip: TextView, on: Boolean) {
        val pal = ThemeEngine.current(this)
        val den = resources.displayMetrics.density
        val c = store.settingsCardAlphaPct
        chip.background = ThemeEngine.cardDrawable(
            fadedCard(if (on) pal.accent else pal.card, if (on) maxOf(c, CAT_CHIP_FLOOR) else c),
            18f, den,
            fadedCard(pal.cardStroke, c)
        )
        val base = if (on) {
            if (Color.luminance(pal.accent) > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
        } else pal.text
        chip.setTextColor(fadedCard(base, (100 + c) / 2))
        chip.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
}
