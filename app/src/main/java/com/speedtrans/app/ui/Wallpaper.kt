package com.speedtrans.app.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.ImageView
import java.io.File

/**
 * 页面壁纸：垫底层的纯装饰图片。
 * 零交互——无监听、不抢焦点、不进无障碍树（importantForAccessibility=no），
 * 触摸全部落在上层的真实 UI；换皮肤/换主题不影响（壁纸层在皮肤色之上、氛围层之下）。
 */
object Wallpaper {

    /** SAF Uri → 拷贝到私有目录，成功返回 true */
    fun importFrom(context: Context, uri: Uri, target: File): Boolean = try {
        context.contentResolver.openInputStream(uri)!!.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        true
    } catch (_: Exception) {
        false
    }

    /** 按长边两级采样解码，防竖版大图 OOM（横图行为与旧版一致） */
    fun decode(path: String, reqLongEdge: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= reqLongEdge) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (_: Exception) {
        null
    }

    /**
     * 应用壁纸：image 垫底图 + scrim 半透明底色遮罩（保证文字可读）。
     * @param dim 遮罩浓度 0~80（底色不透明度百分比）
     * @return 壁纸是否处于显示状态
     */
    fun applyTo(
        activity: Activity, imageId: Int, scrimId: Int,
        path: String, dim: Int, baseColor: Int, enabled: Boolean
    ): Boolean {
        val iv = activity.findViewById<ImageView>(imageId)
        val scrim = activity.findViewById<View>(scrimId)
        if (!enabled || path.isEmpty()) {
            iv.visibility = View.GONE
            scrim.visibility = View.GONE
            iv.setImageDrawable(null)
            return false
        }
        val dm = activity.resources.displayMetrics
        val bmp = decode(path, maxOf(dm.widthPixels, dm.heightPixels))
        if (bmp == null) {
            iv.visibility = View.GONE
            scrim.visibility = View.GONE
            return false
        }
        iv.setImageBitmap(bmp)
        iv.visibility = View.VISIBLE
        val a = ((dim.coerceIn(0, 80) / 100f) * 255).toInt()
        scrim.setBackgroundColor(
            Color.argb(a, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        )
        scrim.visibility = if (a == 0) View.GONE else View.VISIBLE
        return true
    }
}
