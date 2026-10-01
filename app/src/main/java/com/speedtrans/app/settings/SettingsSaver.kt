package com.speedtrans.app.settings

import android.content.Intent
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import androidx.core.content.ContextCompat
import com.speedtrans.app.R
import com.speedtrans.app.SettingsActivity
import com.speedtrans.app.service.BallService
import com.speedtrans.app.service.KeepAliveService
import com.speedtrans.app.translate.Providers
import com.speedtrans.app.translate.TranslateCoordinator
import com.speedtrans.app.translate.TranslateEngine

/**
 * 设置页 · 保存落盘（拆 SettingsActivity 第 7 步，2026-10-01）。
 * 把界面上的选择一次性写回 SettingsStore。**纯搬家，逻辑一行未动。**
 */

// ---------- 保存 ----------

internal fun SettingsActivity.save() {
        store.baseUrl = findViewById<EditText>(R.id.etUrl).text.toString()
        store.apiKey = findViewById<EditText>(R.id.etKey).text.toString()
        store.model = findViewById<EditText>(R.id.etModel).text.toString()
        // 高级参数：留空/非法 = 回落默认（容错）
        store.maxTokens = findViewById<EditText>(R.id.etMaxTokens).text.toString().trim()
            .toIntOrNull()?.coerceIn(0, 200000) ?: 8192
        store.temperature = findViewById<EditText>(R.id.etTemp).text.toString().trim()
            .toFloatOrNull()?.coerceIn(-1f, 2f) ?: -1f
        // 钥匙按服务商归档，切回来不用重贴
        Providers.match(store.baseUrl)?.let { store.setProviderKey(it.id, store.apiKey) }
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
            R.id.rbModeOcr -> "ocr"
            else -> "text"
        }
        store.ocrLanguages = langIds.mapNotNull { (id, code) ->
            if (findViewById<CheckBox>(id).isChecked) code else null
        }.toSet()
        store.maxChars = findViewById<SeekBar>(R.id.sbMaxChars).progress * 1000 + 4000
        store.showCopy = findViewById<Switch>(R.id.swShowCopy).isChecked
        store.showClose = findViewById<Switch>(R.id.swShowClose).isChecked
        store.panelAccumulate = findViewById<Switch>(R.id.swAccumulate).isChecked
        store.btnCloseLeft =
            findViewById<RadioGroup>(R.id.rgBtnSide).checkedRadioButtonId == R.id.rbSideLeft
        store.btnPaddingDp = findViewById<SeekBar>(R.id.sbBtnPad).progress
        store.barColorHex = selectedBarColor ?: ""
        // 保存后预热新地址的连接（换服务商后首字同样快）
        try {
            com.speedtrans.app.translate.TranslateEngine(store).warmUp()
        } catch (_: Exception) {
        }

        BallService.instance?.refreshBall()
        TranslateCoordinator.closeOverlay()
        // 通知栏标题显示当前模式，保存后立即同步
        ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))
        toast("已保存")
        finish()
}
