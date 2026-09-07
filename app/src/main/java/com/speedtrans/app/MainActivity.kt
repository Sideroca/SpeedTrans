package com.speedtrans.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlay: TextView
    private lateinit var tvA11y: TextView
    private lateinit var tvApi: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOverlay = findViewById(R.id.tvOverlay)
        tvA11y = findViewById(R.id.tvA11y)
        tvApi = findViewById(R.id.tvApi)

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        findViewById<Button>(R.id.btnA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnApi).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val overlayOk = Settings.canDrawOverlays(this)
        tvOverlay.text = if (overlayOk) "✅ 悬浮窗权限已开启"
        else "❌ 悬浮窗权限未开启（悬浮球显示的前提）"
        tvOverlay.setTextColor(if (overlayOk) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())

        val a11yOk = isAccessibilityEnabled()
        tvA11y.text = if (a11yOk) "✅ 无障碍服务已开启（屏幕上应已出现红色悬浮球）"
        else "❌ 无障碍服务未开启（抓取屏幕文字的核心）"
        tvA11y.setTextColor(if (a11yOk) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())

        val sp = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val hasKey = !sp.getString("api_key", "").isNullOrBlank()
        tvApi.text = if (hasKey) "✅ 翻译接口已配置"
        else "❌ 尚未配置翻译接口（接口地址 + API Key）"
        tvApi.setTextColor(if (hasKey) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
    }

    private fun isAccessibilityEnabled(): Boolean {
        val s = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return s.contains(packageName)
    }
}
