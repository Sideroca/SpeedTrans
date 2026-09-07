package com.speedtrans.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.speedtrans.app.service.KeepAliveService
import com.speedtrans.app.store.SettingsStore
import com.speedtrans.app.theme.ThemeEngine

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

        applyTheme()
        bindQuickTheme()

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

        // 前台保活：划掉最近任务不影响悬浮球
        ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))
        requestNotificationPermission()
    }

    private fun applyTheme() {
        val pal = ThemeEngine.current(this)
        findViewById<View>(R.id.rootMain).setBackgroundColor(pal.bg)
        ThemeEngine.applyTo(
            findViewById(R.id.rootMain), pal,
            cardIds = setOf(R.id.tvOverlay, R.id.tvA11y, R.id.tvApi, R.id.tvUsage),
            subIds = setOf(R.id.tvSubtitle, R.id.tvSign)
        )
    }

    /** 主界面快捷主题条：点色球即换装（当前主题带描边高亮） */
    private fun bindQuickTheme() {
        val row = findViewById<LinearLayout>(R.id.quickThemeRow)
        val pal = ThemeEngine.current(this)
        val cur = pal.id
        val size = (30 * resources.displayMetrics.density).toInt()
        val margin = (8 * resources.displayMetrics.density).toInt()
        ThemeEngine.palettes.forEach { p ->
            val v = View(this)
            v.layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = margin }
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(p.accent)
                setStroke(
                    if (p.id == cur) (4 * resources.displayMetrics.density).toInt() else 0,
                    if (Color.luminance(pal.bg) > 0.5f) 0xFF999999.toInt() else 0xFFFFFFFF.toInt()
                )
            }
            v.setOnClickListener {
                ThemeEngine.save(this, p.id)
                recreate()
            }
            row.addView(v)
        }
    }

    override fun onResume() {
        super.onResume()
        val st = SettingsStore(this)

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
            tvA11y.text = "❌ 无障碍未开启（一次性设置，MIUI 会弹「危险」确认，放心允许）"
            tvA11y.setTextColor(0xFFC62828.toInt())
            btnA11y.text = "🚑 去开启无障碍（10 秒）"
        }

        val hasKey = !st.apiKey.isNullOrBlank()
        tvApi.text = if (hasKey) "✅ 翻译接口已配置（模型：${st.model}）"
        else "❌ 尚未配置翻译接口"
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
