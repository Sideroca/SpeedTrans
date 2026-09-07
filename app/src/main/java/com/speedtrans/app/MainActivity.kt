package com.speedtrans.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.speedtrans.app.service.KeepAliveService

class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlay: TextView
    private lateinit var tvA11y: TextView
    private lateinit var tvApi: TextView
    private lateinit var btnA11y: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOverlay = findViewById(R.id.tvOverlay)
        tvA11y = findViewById(R.id.tvA11y)
        tvApi = findViewById(R.id.tvApi)
        btnA11y = findViewById(R.id.btnA11y)

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        btnA11y.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnApi).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 前台保活服务：划掉最近任务卡片也不会终止悬浮球
        ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        val overlayOk = Settings.canDrawOverlays(this)
        tvOverlay.text = if (overlayOk) "✅ 悬浮窗权限已开启"
        else "❌ 悬浮窗权限未开启（悬浮球显示的前提）"
        tvOverlay.setTextColor(if (overlayOk) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())

        val a11yOk = isAccessibilityEnabled()
        if (a11yOk) {
            tvA11y.text = "✅ 无障碍服务运行中（悬浮球应常驻屏幕左侧）"
            tvA11y.setTextColor(0xFF2E7D32.toInt())
            btnA11y.text = "② 无障碍设置（正常时无需进入）"
        } else {
            tvA11y.text = "❌ 无障碍已掉线（多为 MIUI 清理后台所致）"
            tvA11y.setTextColor(0xFFC62828.toInt())
            btnA11y.text = "🚑 无障碍掉线 → 点此 10 秒修复"
        }

        val sp = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val hasKey = !sp.getString("api_key", "").isNullOrBlank()
        tvApi.text = if (hasKey) "✅ 翻译接口已配置"
        else "❌ 尚未配置翻译接口（接口地址 + API Key）"
        tvApi.setTextColor(if (hasKey) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val s = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return s.contains(packageName)
    }
}
