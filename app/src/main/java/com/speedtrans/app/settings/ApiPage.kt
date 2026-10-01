package com.speedtrans.app.settings

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.speedtrans.app.R
import com.speedtrans.app.SettingsActivity
import com.speedtrans.app.theme.ShellSkins
import com.speedtrans.app.translate.Providers
import com.speedtrans.app.translate.TranslateEngine
import com.speedtrans.app.ui.CircuitTestView
import com.speedtrans.app.ui.ContainsAdapter

/**
 * 设置页 · 「🔌 接口」标签页（拆 SettingsActivity 第 6 步，2026-10-01）。
 * 含：服务商联想（ContainsAdapter）、思考档位、测试连接。
 * 纯搬家，逻辑一行未动。
 */

internal fun SettingsActivity.bindApi() {
        val acProvider = findViewById<AutoCompleteTextView>(R.id.acProvider)
        val etUrl = findViewById<AutoCompleteTextView>(R.id.etUrl)
        val etKey = findViewById<EditText>(R.id.etKey)
        val etModel = findViewById<AutoCompleteTextView>(R.id.etModel)
        val tvNote = findViewById<TextView>(R.id.tvProviderNote)
        // 钥匙提示：不再自动填入（防止无意间把密钥显示在屏幕上）；有已存钥匙时显示"点此填入"
        val tvKeyHint = findViewById<TextView>(R.id.tvKeyHint)
        tvKeyHint.setOnClickListener {
            val id = lastProviderId ?: return@setOnClickListener
            val k = store.providerKeyOf(id)
            if (!k.isNullOrBlank()) {
                etKey.setText(k)
                tvKeyHint.visibility = View.GONE
            }
        }
        // 高级参数（可留空用默认）
        findViewById<EditText>(R.id.etMaxTokens).setText(store.maxTokens.toString())
        findViewById<EditText>(R.id.etTemp).setText(
            if (store.temperature < 0f) "" else store.temperature.toString()
        )

        thinkingLevel = store.thinkingLevel
        lastProviderId = Providers.match(store.baseUrl)?.id

        // 包含式联想（浏览器式）：输入任意片段都能命中，不再要求前缀
        acProvider.setAdapter(
            ContainsAdapter(
                this,
                Providers.all.map { it.label },
                Providers.all.map { "${it.label} ${it.id} ${it.alias}" }
            )
        )
        acProvider.threshold = 1
        etUrl.setAdapter(ContainsAdapter(this, Providers.all.filter { it.url.isNotEmpty() }.map { it.url }))
        etModel.setAdapter(
            ContainsAdapter(this, Providers.all.flatMap { p -> p.models }.distinct())
        )

        // 服务商选择：按候选文本解析——过滤后位置会漂移，绝不能按 pos 索引全量表
        acProvider.setOnItemClickListener { parent, _, pos, _ ->
            val label = parent.getItemAtPosition(pos)?.toString()
                ?: return@setOnItemClickListener
            val p = Providers.all.firstOrNull { it.label == label }
                ?: return@setOnItemClickListener
            // 记住旧服务商的钥匙；换服务商后清空钥匙框（旧家钥匙不随行；新家若存过钥匙，右侧有「点此填入」提示）
            Providers.match(etUrl.text.toString())?.id?.let { old ->
                if (old != p.id) {
                    store.setProviderKey(old, etKey.text.toString())
                    etKey.setText("")
                }
            }
            if (p.url.isNotEmpty()) etUrl.setText(p.url)
            if (p.models.isNotEmpty()) etModel.setText(p.models.first())
            // 不自动填入已存钥匙（防泄漏）→ 显示可点提示
            tvKeyHint.visibility =
                if (!store.providerKeyOf(p.id).isNullOrBlank()) View.VISIBLE else View.GONE
            tvNote.text = p.note
            thinkingLevel = p.levels.firstOrNull()?.second ?: "off"
            lastProviderId = p.id
            refreshThinkingRow()
        }

        etUrl.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val u = s?.toString() ?: ""
                val p = Providers.match(u)
                tvNote.text = p?.note ?: ""
                // 手改地址导致服务商变化时：显示名跟随 + 换上记过的钥匙（没存过则不动，防止误清）
                if (p?.id != lastProviderId) {
                    lastProviderId = p?.id
                    acProvider.setText(p?.label ?: "自定义", false)
                    // 不自动填入已存钥匙（防泄漏）→ 显示可点提示
                    tvKeyHint.visibility =
                        if (!store.providerKeyOf(p?.id ?: "").isNullOrBlank()) View.VISIBLE else View.GONE
                }
                refreshThinkingRow()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        val initP = Providers.match(store.baseUrl)
        val showProvider = when {
            initP != null -> initP.label
            store.baseUrl.isNotEmpty() || store.apiKey.isNotEmpty() -> "自定义"
            else -> ""
        }
        acProvider.setText(showProvider, false)
        tvNote.text = initP?.note ?: ""
        etUrl.setText(store.baseUrl)
        etKey.setText(store.apiKey)
        etModel.setText(store.model)
        refreshThinkingRow()

        val lamp = findViewById<CircuitTestView>(R.id.lampTest)
        findViewById<Button>(R.id.btnTest).setOnClickListener {
            // 先落字段再测试（未保存也能测）
            store.baseUrl = etUrl.text.toString()
            store.apiKey = etKey.text.toString()
            store.model = etModel.text.toString()
            lamp.setState(CircuitTestView.State.TESTING)
            TranslateEngine(store).testConnection { msg ->
                val ok = msg.startsWith("✅")
                runOnUiThread {
                    lamp.setState(if (ok) CircuitTestView.State.OK else CircuitTestView.State.FAIL)
                    tvNote.text = msg
                }
            }
        }
}
internal fun SettingsActivity.refreshThinkingRow() {
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
internal fun SettingsActivity.styleThinkingChips() {
        val s = currentSkin ?: ShellSkins.current(this)
        val row = findViewById<LinearLayout>(R.id.tierRow)
        val url = findViewById<AutoCompleteTextView>(R.id.etUrl).text.toString()
        val p = Providers.match(url) ?: return
        val d = resources.displayMetrics.density
        for (i in 0 until row.childCount) {
            val c = row.getChildAt(i) as TextView
            val selected = p.levels.getOrNull(i)?.second == thinkingLevel
            c.background = ShellSkins.chipBg(s, selected, d, store.settingsCardAlphaPct)
            c.setTextColor(ShellSkins.chipText(s, selected))
        }
}
