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
import com.speedtrans.app.ui.Wallpaper

/** 快捷主题条滚动位置（跨 recreate 恢复用） */
private var themeScrollX = 0

class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlay: TextView
    private lateinit var tvA11y: TextView
    private lateinit var tvApi: TextView
    private lateinit var btnA11y: Button

    /**
     * 旧"伪装"别名自愈：早期版本可在 闪译/备忘录/工具箱 三态循环（按钮已移除）。
     * 若设备上仍残留启用的别名，会在启动时复位——确保默认图标（浅空蓝·白球）能正常显示。
     */
    private fun repairLauncherAliases() {
        try {
            val pm = packageManager
            val pkg = packageName
            var repaired = false
            for (suffix in listOf(".main_blue", ".main_green")) {
                val cn = android.content.ComponentName(this, "$pkg$suffix")
                if (pm.getComponentEnabledSetting(cn) ==
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                ) {
                    pm.setComponentEnabledSetting(
                        cn,
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                        android.content.pm.PackageManager.DONT_KILL_APP
                    )
                    repaired = true
                }
            }
            val red = android.content.ComponentName(this, "$pkg.main_red")
            if (pm.getComponentEnabledSetting(red) ==
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            ) {
                pm.setComponentEnabledSetting(
                    red,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
                repaired = true
            }
            if (repaired) android.util.Log.i("SpeedTrans", "launcher aliases repaired → default icon")
        } catch (_: Exception) {
        }
    }

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
        findViewById<Button>(R.id.btnTut).setOnClickListener { showPermissionTutorial() }

        findViewById<Button>(R.id.btnApi).setOnClickListener {
            repairLauncherAliases()
        startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // 前台保活：划掉最近任务不影响悬浮球
        ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))
        requestNotificationPermission()
    }

    private fun applyTheme() {
        val pal = ThemeEngine.current(this)
        val st = SettingsStore(this)
        findViewById<View>(R.id.rootMainHost).setBackgroundColor(pal.bg)
        Wallpaper.applyTo(
            this, R.id.ivWallpaperMain, R.id.wpScrimMain,
            st.wallpaperPath, st.wallpaperDim, pal.bg, st.wallpaperOnMain
        )
        ThemeEngine.applyTo(
            findViewById(R.id.rootMain), pal,
            cardIds = setOf(R.id.tvOverlay, R.id.tvA11y, R.id.tvApi, R.id.tvUsage),
            subIds = setOf(R.id.tvSubtitle, R.id.tvSign)
        )
    }

    /** 主界面快捷主题条：点色球即换装（当前主题带描边高亮），撞色条背景 */
    private fun bindQuickTheme() {
        val row = findViewById<LinearLayout>(R.id.quickThemeRow)
        val pal = ThemeEngine.current(this)
        val cur = pal.id
        val d = resources.displayMetrics.density
        // 撞色条背景：用户独立选色 = 水浸渐变；未选则跟随主题平色
        val barOverride = SettingsStore(this).barColorOverride()
        val barBg = barOverride ?: pal.barBg
        row.background = if (barOverride != null)
            ThemeEngine.barGradient(barBg, 14f * d)
        else
            ThemeEngine.cardDrawable(barBg, 14f, d)
        row.setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
        val size = (30 * d).toInt()
        val margin = (8 * d).toInt()
        ThemeEngine.palettes.forEach { p ->
            val v = View(this)
            v.layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = margin }
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(p.accent)
                setStroke(
                    if (p.id == cur) (4 * d).toInt() else 0,
                    if (Color.luminance(barBg) > 0.5f) 0xFF777777.toInt() else 0xFFFFFFFF.toInt()
                )
            }
            v.setOnClickListener {
                // 记住色球条滚动位置（recreate 后恢复，点右边的球不再跳回最左）
                themeScrollX = (row.parent as? android.widget.HorizontalScrollView)?.scrollX ?: 0
                ThemeEngine.save(this, p.id)
                recreate()
            }
            row.addView(v)
        }
        val hsv = row.parent as? android.widget.HorizontalScrollView
        hsv?.post { hsv.scrollTo(themeScrollX, 0) }
    }

    /** 小米权限设置图解：3 张步骤截图（辅助功能 → 已下载的应用 → 闪译悬浮球） */
    private fun showPermissionTutorial() {
        val d = android.app.Dialog(this)
        val den = resources.displayMetrics.density
        val col = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((14 * den).toInt(), (10 * den).toInt(), (14 * den).toInt(), (14 * den).toInt())
        }
        val steps = listOf(
            "① 设置 → 辅助功能 → 权限管控「已下载的应用」" to R.drawable.tut_miui_1,
            "② 已下载的应用 → 找到「闪译悬浮球」" to R.drawable.tut_miui_2,
            "③ 打开「使用“闪译悬浮球”」开关" to R.drawable.tut_miui_3
        )
        steps.forEach { (cap, img) ->
            col.addView(android.widget.TextView(this).apply {
                text = cap
                textSize = 14f
                setPadding(0, (12 * den).toInt(), 0, (6 * den).toInt())
            })
            col.addView(android.widget.ImageView(this).apply {
                setImageResource(img)
                adjustViewBounds = true
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
            })
        }
        col.addView(android.widget.Button(this).apply {
            text = "关闭"
            setOnClickListener { d.dismiss() }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (16 * den).toInt() }
        })
        d.setContentView(android.widget.ScrollView(this).apply { addView(col) })
        d.show()
        val dm = resources.displayMetrics
        d.window?.setLayout((dm.widthPixels * 0.92f).toInt(), (dm.heightPixels * 0.88f).toInt())
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
