package com.speedtrans.app.settings

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.speedtrans.app.R
import com.speedtrans.app.SettingsActivity
import com.speedtrans.app.settings.applyWallpaper
import com.speedtrans.app.settings.clearWall
import com.speedtrans.app.theme.Palette
import com.speedtrans.app.theme.ShellSkins
import com.speedtrans.app.theme.ThemeEngine

/**
 * 设置页 · 「属性设置」面板（拆 SettingsActivity 第 4 步，2026-10-01）。
 *
 * 含：buildPropsPage（首页壁纸→卡片浓度→首页字号→设置页壁纸→设置页皮肤）
 * 以及它那一整套样式工具（sliderRow / stylePrimary / styleSecondary / styleSkinChip /
 * blend / fadedCard / fmtFor / restylePropsFills）。
 *
 * 说明：这个面板原先被劈成两半——`buildPropsPage` 写在「主题选择」分区里，
 * 它的样式工具散落在 900 行开外的另一个分区。本次合并归位，**纯搬家，逻辑一行未动**。
 */

/** 整页版「属性设置」：首页壁纸 → 卡片浓度 → 首页字号 → 设置页壁纸 → 设置页皮肤 */
internal fun SettingsActivity.buildPropsPage() {
        val pal = ThemeEngine.current(this)
        val den = resources.displayMetrics.density
        val box = findViewById<LinearLayout>(R.id.themePropsRow)
        box.removeAllViews()
        propsBox = box
        propsPrimaryButtons.clear()
        propsSecondaryButtons.clear()
        propsChips.clear()
        box.background = ThemeEngine.cardDrawable(fadedCard(pal.card), 18f, den, blend(pal.bg, pal.accent, 0.12f))
        box.setPadding((16 * den).toInt(), (12 * den).toInt(), (16 * den).toInt(), (16 * den).toInt())

        val contentW = (resources.displayMetrics.widthPixels * 0.92f).toInt() - (40 * den).toInt()
        val wpBtnW = ((contentW - (10 * den).toInt()) * 0.85f / 2f).toInt()
        val wpBtnH = (42 * den).toInt()

        fun gap(dp: Int) {
            box.addView(android.widget.Space(this).apply {
                layoutParams = LinearLayout.LayoutParams(1, (dp * den).toInt())
            })
        }
        fun sectionLabel(t: String) = TextView(this).apply {
            text = t
            textSize = 15f
            setTextColor(pal.text)
            setPadding(0, 0, 0, (10 * den).toInt())
        }
        fun hint(t: String) = TextView(this).apply {
            text = t
            textSize = 12f
            setTextColor(pal.subText)
            setPadding(0, 0, 0, (12 * den).toInt())
        }
        fun buttonPair(pick: Button, clear: Button): LinearLayout {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            stylePrimary(pick, pal, den)
            styleSecondary(clear, pal, den)
            row.addView(pick)
            row.addView(clear)
            return row
        }
        fun switchRow(label: String, sw: Switch): LinearLayout {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (8 * den).toInt(), 0, (4 * den).toInt())
            }
            r.addView(TextView(this).apply {
                text = label
                textSize = 13f
                setTextColor(pal.text)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            r.addView(sw)
            return r
        }

        // ---------- ① 首页壁纸 ----------
        box.addView(sectionLabel("首页壁纸"))
        val pickMain = Button(this).apply {
            text = "选择"; isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(wpBtnW, wpBtnH)
        }
        val clearMain = Button(this).apply {
            text = "清除"; isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(wpBtnW, wpBtnH).apply { marginStart = (10 * den).toInt() }
        }
        propsPrimaryButtons.add(pickMain)
        propsSecondaryButtons.add(clearMain)
        box.addView(buttonPair(pickMain, clearMain))
        val swMain = Switch(this).apply {
            setTextColor(pal.text)
            buttonTintList = android.content.res.ColorStateList.valueOf(pal.accent)
            isChecked = store.wpEnabled("main")
        }
        var guardMain = false
        swMain.setOnCheckedChangeListener { _, c ->
            if (guardMain) return@setOnCheckedChangeListener
            if (c && store.wpCrop("main").isEmpty()) {
                toast("先选一张图再启用")
                guardMain = true; swMain.isChecked = false; guardMain = false
                return@setOnCheckedChangeListener
            }
            store.setWpEnabled("main", c)
        }
        box.addView(switchRow("启用（应用到首页）", swMain))
        box.addView(sliderRow("遮罩浓度", 80, store.wpDim("main", 50), pal, den) { p -> store.setWpDim("main", p) })
        pickMain.setOnClickListener { pickWallMain.launch("image/*") }
        clearMain.setOnClickListener {
            clearWall("main")
            guardMain = true; swMain.isChecked = false; guardMain = false
        }

        // ---------- ② 卡片浓度 ----------
        gap(19)
        box.addView(sliderRow("卡片浓度", 70, store.cardAlphaPct - 30, pal, den) { p -> store.cardAlphaPct = p + 30 })

        // ---------- ③ 首页字号 ----------
        gap(15)
        box.addView(sliderRow("首页字号", 60, store.homeFontPct - 80, pal, den) { p -> store.homeFontPct = p + 80 })

        // ---------- ④ 设置页壁纸 ----------
        gap(19)
        box.addView(sectionLabel("设置页壁纸（5 个标签页共用）"))
        val pickPage = Button(this).apply {
            text = "选择"; isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(wpBtnW, wpBtnH)
        }
        val clearPage = Button(this).apply {
            text = "清除"; isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(wpBtnW, wpBtnH).apply { marginStart = (10 * den).toInt() }
        }
        propsPrimaryButtons.add(pickPage)
        propsSecondaryButtons.add(clearPage)
        box.addView(buttonPair(pickPage, clearPage))
        val swPage = Switch(this).apply {
            setTextColor(pal.text)
            buttonTintList = android.content.res.ColorStateList.valueOf(pal.accent)
            isChecked = store.wpEnabled("page")
        }
        var guardPage = false
        swPage.setOnCheckedChangeListener { _, c ->
            if (guardPage) return@setOnCheckedChangeListener
            if (c && store.wpCrop("page").isEmpty()) {
                toast("先选一张图再启用")
                guardPage = true; swPage.isChecked = false; guardPage = false
                return@setOnCheckedChangeListener
            }
            store.setWpEnabled("page", c)
            applyWallpaper()
        }
        box.addView(switchRow("启用（应用到设置页）", swPage))
        box.addView(sliderRow("遮罩浓度", 80, store.wpDim("page", 50), pal, den) { p ->
            store.setWpDim("page", p)
            applyWallpaper()
        })
        // 设置页卡片浓度：面板/卡片底的不透明度（100 = 现状）；拖动即全页生效
        gap(12)
        box.addView(
            sliderRow("卡片浓度", 100, store.settingsCardAlphaPct, pal, den, fmt = { "$it%" }) { p ->
                store.settingsCardAlphaPct = p
                // 大盒子本身 + 全页（皮肤面板）实时生效
                propsBox?.background = ThemeEngine.cardDrawable(
                    fadedCard(pal.card), 18f, den, blend(pal.bg, pal.accent, 0.12f)
                )
                applySkin()
            }
        )
        pickPage.setOnClickListener { pickWallPage.launch("image/*") }
        clearPage.setOnClickListener {
            clearWall("page")
            guardPage = true; swPage.isChecked = false; guardPage = false
        }

        // ---------- ⑤ 设置页皮肤 ----------
        gap(19)
        box.addView(sectionLabel("设置页皮肤"))
        box.addView(hint("导航骨架固定，换的是配色与氛围；「跟随主界面主题」= 当前主题套进设置页。"))
        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (2 * den).toInt(), 0, (4 * den).toInt())
        }
        val chips = listOf(TextView(this) to "ds_holo", TextView(this) to "follow_theme")
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
                applySkin()
                toast("已切换（设置页生效）")
                val nowId = ShellSkins.current(this).id
                chips.forEach { (c, cid) -> styleSkinChip(c, nowId == cid, pal, den) }
            }
            styleSkinChip(chip, ShellSkins.current(this).id == id, pal, den)
            propsChips.add(chip to id)
            chipRow.addView(chip)
        }
        box.addView(chipRow)
}
// ---------- 属性设置面板 ----------

private fun SettingsActivity.sliderRow(
        name: String, max: Int, start: Int, pal: Palette, den: Float,
        fmt: ((Int) -> String)? = null,
        onSet: (Int) -> Unit
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
        valTv.text = fmt?.invoke(start) ?: fmtFor(name, start)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sk: SeekBar?, p: Int, fromUser: Boolean) {
                valTv.text = fmt?.invoke(p) ?: fmtFor(name, p)
                if (fromUser) onSet(p)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        row.addView(sb)
        row.addView(valTv)
        return row
}
/** 属性设置页的按钮/胶囊：按当前主题+浓度重刷（拖动浓度滑条/换肤时实时生效） */
internal fun SettingsActivity.restylePropsFills() {
        val pal = ThemeEngine.current(this)
        val den = resources.displayMetrics.density
        propsPrimaryButtons.forEach { stylePrimary(it, pal, den) }
        propsSecondaryButtons.forEach { styleSecondary(it, pal, den) }
        val nowId = ShellSkins.current(this).id
        propsChips.forEach { (chip, sid) -> styleSkinChip(chip, nowId == sid, pal, den) }
}
/** 设置页卡片浓度：给主题色叠 alpha（默认取当前浓度；100 = 原样） */
internal fun SettingsActivity.fadedCard(color: Int, pct: Int = store.settingsCardAlphaPct): Int {
        if (pct >= 100) return color
        return (color and 0x00FFFFFF) or (((255 * pct.coerceIn(0, 100)) / 100) shl 24)
}
private fun SettingsActivity.fmtFor(name: String, p: Int): String = when (name) {
        "卡片浓度" -> "${p + 30}%"
        "首页字号" -> "${p + 80}%"
        else -> "$p%"
}
private fun SettingsActivity.stylePrimary(b: Button, pal: Palette, d: Float) {
        // 填充跟随"设置页卡片浓度"（文字不动）
        b.background = ThemeEngine.cardDrawable(fadedCard(pal.accent), 12f, d)
        b.setTextColor(
            if (Color.luminance(pal.accent) > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
        )
}
private fun SettingsActivity.styleSecondary(b: Button, pal: Palette, d: Float) {
        val dark = Color.luminance(pal.bg) < 0.4f
        val fill = blend(pal.bg, pal.accent, if (dark) 0.22f else 0.16f)
        val stroke = blend(fill, pal.accent, if (dark) 0.34f else 0.26f)
        b.background = ThemeEngine.cardDrawable(fadedCard(fill), 12f, d, fadedCard(stroke))
        b.setTextColor(
            if (Color.luminance(fill) > 0.5f) blend(pal.accent, 0xFF000000.toInt(), 0.30f)
            else blend(pal.accent, 0xFFFFFFFF.toInt(), 0.25f)
        )
}
private fun SettingsActivity.styleSkinChip(chip: TextView, selected: Boolean, pal: Palette, d: Float) {
        if (selected) {
            chip.background = ThemeEngine.cardDrawable(fadedCard(pal.accent), 999f, d)
            chip.setTextColor(
                if (Color.luminance(pal.accent) > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
            )
        } else {
            val fill = blend(pal.bg, pal.accent, if (Color.luminance(pal.bg) < 0.4f) 0.20f else 0.10f)
            chip.background = ThemeEngine.cardDrawable(fadedCard(fill), 999f, d, fadedCard(blend(fill, pal.accent, 0.30f)))
            chip.setTextColor(pal.text)
        }
}
private fun SettingsActivity.blend(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) * (1 - tt) + Color.alpha(b) * tt).toInt(),
            (Color.red(a) * (1 - tt) + Color.red(b) * tt).toInt(),
            (Color.green(a) * (1 - tt) + Color.green(b) * tt).toInt(),
            (Color.blue(a) * (1 - tt) + Color.blue(b) * tt).toInt()
        )
}
