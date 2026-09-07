package com.speedtrans.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.speedtrans.app.capture.ProjectionHolder
import com.speedtrans.app.service.ScreenBallService
import com.speedtrans.app.store.SettingsStore

class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlay: TextView
    private lateinit var tvCapture: TextView
    private lateinit var tvA11y: TextView
    private lateinit var tvApi: TextView
    private lateinit var btnCapture: Button
    private lateinit var btnA11y: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOverlay = findViewById(R.id.tvOverlay)
        tvCapture = findViewById(R.id.tvCapture)
        tvA11y = findViewById(R.id.tvA11y)
        tvApi = findViewById(R.id.tvApi)
        btnCapture = findViewById(R.id.btnCapture)
        btnA11y = findViewById(R.id.btnA11y)

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        btnCapture.setOnClickListener { startActivity(Intent(this, AuthorizeActivity::class.java)) }
        btnA11y.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        findViewById<Button>(R.id.btnApi).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        requestNotificationPermission()
        // 注意：ScreenBallService 只能在用户完成屏幕捕捉授权后启动（Android 14 要求），
        // 由 AuthorizeActivity 在授权成功后启动。此处不得提前启动。
    }

    override fun onResume() {
        super.onResume()
        val st = SettingsStore(this)
        val ocrMode = st.engine == "ocr"

        // OCR 引擎：服务由授权流程启动（授权后长驻）；此处不主动启动

        val overlayOk = Settings.canDrawOverlays(this)
        tvOverlay.text = if (overlayOk) "✅ 悬浮窗权限已开启"
        else "❌ 悬浮窗权限未开启（悬浮球显示的前提）"
        tvOverlay.setTextColor(if (overlayOk) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())

        if (ocrMode) {
            tvCapture.visibility = View.VISIBLE
            btnCapture.visibility = View.VISIBLE
            if (ProjectionHolder.isReady) {
                tvCapture.text = "✅ 屏幕捕捉已就绪（一次授权，长期有效）"
                tvCapture.setTextColor(0xFF2E7D32.toInt())
                btnCapture.text = "屏幕捕捉设置（已就绪，无需操作）"
            } else {
                tvCapture.text = "❌ 屏幕捕捉未授权（点悬浮球时也会自动引导）"
                tvCapture.setTextColor(0xFFC62828.toInt())
                btnCapture.text = "📸 授权屏幕捕捉（一次性，温和权限）"
            }
        } else {
            tvCapture.visibility = View.GONE
            btnCapture.visibility = View.GONE
        }

        if (!ocrMode) {
            val a11yOk = isAccessibilityEnabled()
            tvA11y.visibility = View.VISIBLE
            btnA11y.visibility = View.VISIBLE
            if (a11yOk) {
                tvA11y.text = "✅ 无障碍引擎运行中（悬浮球应常驻屏幕左侧）"
                tvA11y.setTextColor(0xFF2E7D32.toInt())
                btnA11y.text = "② 无障碍设置（正常时无需进入）"
            } else {
                tvA11y.text = "❌ 无障碍已掉线（多为 MIUI 清理后台所致）"
                tvA11y.setTextColor(0xFFC62828.toInt())
                btnA11y.text = "🚑 无障碍掉线 → 点此 10 秒修复"
            }
        } else {
            tvA11y.visibility = View.VISIBLE
            btnA11y.visibility = View.VISIBLE
            tvA11y.text = "ℹ️ 当前使用 OCR 引擎取词，无需无障碍权限"
            tvA11y.setTextColor(0xFF777777.toInt())
            btnA11y.text = "（可选）切回无障碍引擎去设置里开启"
        }

        val hasKey = !st.apiKey.isNullOrBlank()
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
