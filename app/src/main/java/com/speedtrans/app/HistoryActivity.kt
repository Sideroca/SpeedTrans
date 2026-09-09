package com.speedtrans.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.speedtrans.app.store.HistoryStore
import com.speedtrans.app.theme.ThemeEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 最近翻译历史：纯本地（filesDir/history.json，最多 200 条）。
 * 入口在主界面（悬浮页空间留给极速）；点卡片 = 复制全文。
 */
class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pal = ThemeEngine.current(this)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val scroll = ScrollView(this).apply { setBackgroundColor(pal.bg) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        scroll.addView(box)
        setContentView(scroll)

        val title = TextView(this).apply {
            text = "🕘 最近翻译"
            textSize = 24f
            setTextColor(pal.text)
            typeface = Typeface.DEFAULT_BOLD
        }
        box.addView(title)
        box.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(2)).apply { topMargin = dp(8) }
            setBackgroundColor(pal.accent)
        })

        val entries = HistoryStore.load(this)
        if (entries.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "暂无历史。点球翻译过的内容会出现在这里（仅存本机，最多 200 条）。"
                textSize = 13f
                setTextColor(pal.subText)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(16) }
            })
        }
        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        entries.forEach { e ->
            val card = TextView(this).apply {
                val time = fmt.format(Date(e.time))
                text = "$time · ${e.task}" +
                        (if (e.source.isNotBlank()) "\n原文：${e.source}" else "") +
                        "\n${e.text}"
                textSize = 13f
                setTextColor(pal.text)
                background = ThemeEngine.cardDrawable(pal.card, 12f, d, pal.cardStroke)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10) }
                setOnClickListener {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("translation", e.text))
                    Toast.makeText(this@HistoryActivity, "已复制全文", Toast.LENGTH_SHORT).show()
                }
            }
            box.addView(card)
        }

        if (entries.isNotEmpty()) {
            box.addView(Button(this).apply {
                text = "清空历史"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(16) }
                backgroundTintList = android.content.res.ColorStateList.valueOf(pal.accent)
                setTextColor(if (android.graphics.Color.luminance(pal.accent) > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt())
                setOnClickListener {
                    HistoryStore.clear(this@HistoryActivity)
                    Toast.makeText(this@HistoryActivity, "已清空", Toast.LENGTH_SHORT).show()
                    recreate()
                }
            })
        }
    }
}
