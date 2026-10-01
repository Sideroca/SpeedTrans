package com.speedtrans.app.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.speedtrans.app.R
import com.speedtrans.app.SettingsActivity
import com.speedtrans.app.CropActivity
import com.speedtrans.app.MainActivity
import com.speedtrans.app.theme.ShellSkins
import com.speedtrans.app.ui.IconStudio
import com.speedtrans.app.ui.Wallpaper

/**
 * 设置页 · 「🖼 桌面」标签页（拆 SettingsActivity 第 3 步，2026-10-01）。
 * 含：快捷图标工坊 / 页面壁纸 / App 图标与桌面入口。
 * 全部是 `SettingsActivity` 的扩展函数——**纯搬家，逻辑一行未动**。
 *
 * ⚠️ 注意：`setupEdgeToEdge()`（状态栏/导航栏 insets）**没有**搬进来，它留在主类——
 * 那是跨 ROM（HyperOS/OriginOS/ColorOS/MagicOS）最敏感的一段，拆分时一行不动。
 */

internal var studioStyle = IconStudio.STYLE_GRID
internal var studioBg = "#8EC9EE"
internal fun SettingsActivity.bindIconStudio() {
        val preview = findViewById<ImageView>(R.id.ivStudioPreview)
        val styleRow = findViewById<LinearLayout>(R.id.studioStyleRow)
        val bgRow = findViewById<LinearLayout>(R.id.studioBgRow)
        val d = resources.displayMetrics.density

        // 图案纹色按背景明度自动反色：浅底深纹、深底白纹（44 色全可用的前提）
        fun fgFor(bgHex: String): Int {
            val bgInt = Color.parseColor(bgHex)
            return if (android.graphics.Color.luminance(bgInt) > 0.55f) 0xFF2B2B33.toInt() else 0xFFFFFFFF.toInt()
        }

        fun refresh() {
            preview.setImageBitmap(
                IconStudio.generate(studioStyle, Color.parseColor(studioBg), fgFor(studioBg), 400)
            )
        }

        // 样式两选
        val styles = listOf("网格轨道球" to IconStudio.STYLE_GRID, "双轨道环" to IconStudio.STYLE_RING)
        styles.forEach { (label, id) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 12f
                tag = id
                setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (8 * d).toInt() }
                setOnClickListener {
                    studioStyle = id
                    restyleChips(styleRow) { (it as Int) == studioStyle }
                    refresh()
                }
            }
            styleRow.addView(chip)
        }

        // 44 色库双色行：就地改描边，不打断滚动
        fun buildColorRow(row: LinearLayout, extra: List<String> = emptyList(), onPick: (String) -> Unit) {
            (extra + barPresets.map { it.second }).forEach { hex ->
                val sw = View(this).apply {
                    tag = hex
                    layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt())
                        .apply { marginEnd = (8 * d).toInt() }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor(hex))
                    }
                    setOnClickListener {
                        onPick(hex)
                        for (i in 0 until row.childCount) {
                            val v = row.getChildAt(i)
                            val g = v.background as? GradientDrawable ?: continue
                            g.setStroke(
                                if ((v.tag as String) == hex) (4 * d).toInt() else 0,
                                Color.WHITE
                            )
                        }
                    }
                }
                row.addView(sw)
            }
        }
        // 色板 = 浅空蓝（默认底色）+ 44 中国色库（均来自 barPresets）
        buildColorRow(bgRow) { studioBg = it; refresh() }

        // 默认选中：背景=浅空蓝（复刻豆包图 1）
        restyleChips(styleRow) { (it as Int) == studioStyle }
        markColor(bgRow, studioBg, d)
        refresh()

        findViewById<LinearLayout>(R.id.cardContact).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val body = findViewById<TextView>(R.id.tvContactBody).text.toString()
                .replace("\n点一下复制", "")
            cm.setPrimaryClip(ClipData.newPlainText("flash", body))
            toast("已复制（含 QQ、打赏与仓库地址）")
        }

        findViewById<Button>(R.id.btnPinStudio).setOnClickListener {
            val sm = getSystemService(Context.SHORTCUT_SERVICE) as android.content.pm.ShortcutManager
            if (!sm.isRequestPinShortcutSupported) {
                toast("当前桌面不支持固定快捷方式")
                return@setOnClickListener
            }
            val bmp = IconStudio.generate(
                studioStyle, Color.parseColor(studioBg), fgFor(studioBg), 192
            )
            val name = findViewById<EditText>(R.id.etShortcutName).text.toString().ifBlank { "闪译" }
            val info = android.content.pm.ShortcutInfo.Builder(this, "studio_${System.currentTimeMillis()}")
                .setShortLabel(name)
                .setLongLabel(name)
                .setIcon(android.graphics.drawable.Icon.createWithBitmap(bmp))
                .setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
                .build()
            sm.requestPinShortcut(info, null)
            toast("请在系统弹窗中确认添加到桌面；应用本体可用「切换桌面图标」伪装隐藏")
        }
}
private fun SettingsActivity.restyleChips(row: LinearLayout, isOn: (Any) -> Boolean) {
        val skin = currentSkin ?: ShellSkins.current(this)
        val d = resources.displayMetrics.density
        for (i in 0 until row.childCount) {
            val c = row.getChildAt(i) as TextView
            val on = isOn(c.tag)
            c.background = ShellSkins.chipBg(skin, on, d, store.settingsCardAlphaPct)
            c.setTextColor(ShellSkins.chipText(skin, on))
        }
}
private fun SettingsActivity.markColor(row: LinearLayout, hex: String, d: Float) {
        for (i in 0 until row.childCount) {
            val v = row.getChildAt(i)
            if (v.tag == hex) {
                (v.background as GradientDrawable)
                    .setStroke((4 * d).toInt(), Color.WHITE)
            }
        }
}
internal var suppressWallUi = false
/** 导入新图到某个壁纸槽位（page=设置页 / main=主界面）：存原图 + 默认中心适配，随即进入取景 */
internal fun SettingsActivity.importWallImage(uri: Uri, slot: String) {
        val target = Wallpaper.origFile(this, slot)
        if (!Wallpaper.importFrom(this, uri, target)) {
            toast("图片导入失败")
            return
        }
        store.setWpOrig(slot, target.absolutePath)
        store.setWpFrame(slot, 0f, 0f, 1f)
        val dm = resources.displayMetrics
        val c = Wallpaper.cropFile(this, slot)
        if (Wallpaper.bake(this, target.absolutePath, 0f, 0f, 1f, c, dm.widthPixels, dm.heightPixels)) {
            store.setWpCrop(slot, c.absolutePath)
        }
        store.setWpEnabled(slot, true)
        refreshWallUi()
        openCrop(slot)
}
internal fun SettingsActivity.openCrop(slot: String) {
        if (store.wpOrig(slot).isEmpty() || !Wallpaper.origFile(this, slot).exists()) {
            toast("先点「选图/更换」挑一张图片")
            return
        }
        cropReturn.launch(Intent(this, CropActivity::class.java).putExtra(CropActivity.EXTRA_SLOT, slot))
}
internal fun SettingsActivity.clearWall(slot: String) {
        Wallpaper.origFile(this, slot).delete()
        Wallpaper.cropFile(this, slot).delete()
        store.setWpOrig(slot, "")
        store.setWpCrop(slot, "")
        store.setWpEnabled(slot, false)
        refreshWallUi()
        toast("已清除")
}
internal fun SettingsActivity.bindWallpaper() {
        // 设置页壁纸控件已搬入「属性设置」面板（主题标签 → 属性设置）
        refreshWallUi()
}
/** 同步壁纸区控件到存储值，并应用「设置页」壁纸（从取景页返回时也会走这里） */
internal fun SettingsActivity.refreshWallUi() {
        suppressWallUi = true
        suppressWallUi = false
        applyWallpaper()
}
internal fun SettingsActivity.applyWallpaper() {
        val skin = currentSkin ?: ShellSkins.current(this)
        val shown = Wallpaper.applySlot(
            this, R.id.ivWallpaper, R.id.wpScrim,
            store.wpCrop("page"), store.wpDim("page", 50), store.wpEnabled("page"), skin.bg
        )
        // 底部收口统一（一处决策）：幕布+坞渐变同色——有壁纸时用"软件内部色"（卡片/面板色），
        // 无壁纸时用皮肤底。透明→实色一张连续渐变，一路铺到屏幕最底。
        val tone = if (shown) skin.panelBg else skin.bg
        findViewById<View>(R.id.vwNavBridge).background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, tone, tone)
        )
        findViewById<LinearLayout>(R.id.bottomDock).background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, tone)
        )
}
internal val aliasOrder = listOf(
        ".main_red" to "闪译", ".main_blue" to "备忘录", ".main_green" to "工具箱"
)
internal fun SettingsActivity.bindLauncherSection() {
        findViewById<Button>(R.id.btnPinShortcut).setOnClickListener {
            pickShortcutImage.launch("image/*")
        }
}
/** 还原成默认图标：三个别名全部回到清单默认（红=启用，蓝/绿=关闭） */
internal fun SettingsActivity.restoreDefaultIcon() {
        val pm = packageManager
        for ((cls, _) in aliasOrder) {
            try {
                pm.setComponentEnabledSetting(
                    ComponentName(packageName, "$packageName$cls"),
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    PackageManager.DONT_KILL_APP
                )
            } catch (_: Exception) {
            }
        }
        toast("已还原成默认图标（闪译）")
}
/** 单按钮循环：闪译(红) → 备忘录(蓝) → 工具箱(绿) → 闪译，互斥启用 */
private fun SettingsActivity.cycleAlias() {
        val pm = packageManager
        val cur = aliasOrder.indexOfFirst { (cls, _) ->
            pm.getComponentEnabledSetting(ComponentName(packageName, "$packageName$cls")) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }.let { if (it < 0) 0 else it }
        val next = aliasOrder[(cur + 1) % aliasOrder.size]
        aliasOrder.forEach { (cls, _) ->
            pm.setComponentEnabledSetting(
                ComponentName(packageName, "$packageName$cls"),
                if (cls == next.first) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        toast("桌面图标已切换为「${next.second}」· 刷新需数秒到数分钟")
}
internal fun SettingsActivity.importIconImage(uri: Uri) {
        val target = Wallpaper.iconOrigFile(this)
        if (!Wallpaper.importFrom(this, uri, target)) {
            toast("图片导入失败")
            return
        }
        // 极小图给个轻提示（不拦截）
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.absolutePath, bounds)
            if (bounds.outWidth in 1..199 || bounds.outHeight in 1..199) {
                toast("图片较小，图标可能偏糊——建议换一张更大的")
            }
        } catch (_: Exception) {
        }
        iconCropReturn.launch(
            Intent(this, CropActivity::class.java).putExtra(CropActivity.EXTRA_SLOT, "icon")
        )
}
/** ①选图 → ②进方形取景 → ③回来自动创建桌面入口（照片满铺版） */
internal fun SettingsActivity.doPinShortcut() {
        runCatching {
            val name = findViewById<EditText>(R.id.etShortcutName).text.toString().ifBlank { "闪译" }
            val sm = getSystemService(android.content.Context.SHORTCUT_SERVICE) as android.content.pm.ShortcutManager
            if (!sm.isRequestPinShortcutSupported) {
                toast("当前桌面不支持固定快捷方式")
                return
            }
            val f = Wallpaper.iconCropFile(this)
            if (!f.exists()) {
                toast("图标生成失败，请重试")
                return
            }
            val bmp = Wallpaper.decode(f.absolutePath, 512, 1024) ?: return
            val info = android.content.pm.ShortcutInfo.Builder(this, "entry_${name}_${System.currentTimeMillis()}")
                .setShortLabel(name)
                .setLongLabel(name)
                .setIcon(android.graphics.drawable.Icon.createWithBitmap(bmp))
                .setIntent(
                    Intent(this, MainActivity::class.java)
                        .setAction(Intent.ACTION_MAIN)
                )
                .build()
            sm.requestPinShortcut(info, null)
            toast("请在系统弹窗中确认添加到桌面。若未出现弹窗：\n系统设置 → 应用管理 → 闪译 → 权限管理 → 「桌面快捷方式」→ 允许，再试一次")
        }.onFailure {
            toast("创建失败：${it.message ?: "未知错误"}")
        }
}
