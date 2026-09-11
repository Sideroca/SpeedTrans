package com.speedtrans.app

import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.speedtrans.app.store.SettingsStore
import com.speedtrans.app.ui.CropView
import com.speedtrans.app.ui.Wallpaper
import kotlin.math.max

/**
 * 取景页：中央是「手机屏幕比例的缩小版取景框」，框内 = 页面最终实际显示的内容。
 * 入口：设置页 → 🖼 页面壁纸 → 某一处的「取景」。
 */
class CropActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SLOT = "slot"   // "page" | "main"
    }

    private lateinit var store: SettingsStore
    private lateinit var crop: CropView
    private var slot = "page"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)
        slot = when (intent.getStringExtra(EXTRA_SLOT)) {
            "main" -> "main"
            "icon" -> "icon"
            else -> "page"
        }
        store = SettingsStore(this)

        crop = findViewById(R.id.cropView)
        val sb = findViewById<SeekBar>(R.id.sbZoom)
        val tvz = findViewById<TextView>(R.id.tvZoomVal)
        val isIcon = slot == "icon"

        val orig = if (isIcon) Wallpaper.iconOrigFile(this) else Wallpaper.origFile(this, slot)
        if (!orig.exists() || (!isIcon && store.wpOrig(slot).isEmpty())) {
            Toast.makeText(this, "请先为这一处选择一张图片", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (isIcon) {
            crop.fixedFrameWH = 1f
            findViewById<TextView>(R.id.tvCropHint).text = "拖动 / 双指缩放 · 选照片的哪一块（成品为浅空蓝底 + 照片）"
        }
        val bmp = Wallpaper.decode(
            orig.absolutePath,
            max(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels),
            3000
        )
        if (bmp == null) {
            Toast.makeText(this, "图片读取失败", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        crop.setBitmap(bmp)
        if (!isIcon) {
            val f = store.wpFrame(slot)
            crop.setState(f[0], f[1], f[2])
        }

        crop.onZoomChanged = { pct ->
            sb.progress = (pct - 100).coerceIn(0, 300)
            tvz.text = "$pct%"
        }
        // 初始同步（布局未完成时，onSizeChanged 之后的回调会再同步一次）
        sb.progress = ((crop.zoomMult() - 1f) * 100f).toInt().coerceIn(0, 300)
        tvz.text = "${(crop.zoomMult() * 100).toInt()}%"

        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) crop.setZoomMult(1f + p / 100f)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        findViewById<Button>(R.id.btnFit).setOnClickListener { crop.fit() }
        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnConfirm).setOnClickListener { save() }

        // 本页不走设置页皮肤系统，按钮手动染成暗色主题
        findViewById<Button>(R.id.btnFit).apply {
            backgroundTintList = ColorStateList.valueOf(0xFF252B34.toInt())
            setTextColor(0xFFDFE6EE.toInt())
        }
        findViewById<Button>(R.id.btnCancel).apply {
            backgroundTintList = ColorStateList.valueOf(0xFF252B34.toInt())
            setTextColor(0xFFDFE6EE.toInt())
        }
        findViewById<Button>(R.id.btnConfirm).apply {
            backgroundTintList = ColorStateList.valueOf(0xFF8EC9EE.toInt())
            setTextColor(0xFF111111.toInt())
        }
    }

    private fun save() {
        val nm = crop.normalized()
        if (slot == "icon") {
            val out = Wallpaper.iconCropFile(this)
            val ok = Wallpaper.bakeIcon(Wallpaper.iconOrigFile(this).absolutePath, nm[0], nm[1], nm[2], out)
            if (ok) {
                setResult(android.app.Activity.RESULT_OK)
                Toast.makeText(this, "取景完成，去创建桌面入口吧", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "生成失败，请重试", Toast.LENGTH_SHORT).show()
            }
            return
        }
        store.setWpFrame(slot, nm[0], nm[1], nm[2])
        val dm = resources.displayMetrics
        val outW = dm.widthPixels
        val outH = (outW / crop.frameAspectWH()).toInt().coerceAtLeast(1)
        val out = Wallpaper.cropFile(this, slot)
        val ok = Wallpaper.bake(
            this, Wallpaper.origFile(this, slot).absolutePath,
            nm[0], nm[1], nm[2], out, outW, outH
        )
        if (ok) {
            store.setWpCrop(slot, out.absolutePath)
            store.setWpEnabled(slot, true)
            Toast.makeText(this, "取景已保存", Toast.LENGTH_SHORT).show()
            setResult(android.app.Activity.RESULT_OK)
            finish()
        } else {
            Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show()
        }
    }
}
