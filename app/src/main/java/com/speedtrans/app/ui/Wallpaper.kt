package com.speedtrans.app.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.view.View
import android.widget.ImageView
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 页面壁纸 v2：两处独立（page = 设置页 / main = 主界面）。
 * - 每槽：原图（orig）→ 取景（归一化参数 nx/ny/nz）→ 烘焙「屏幕上要显示的那一块」（crop）→ 装饰层直接放它。
 * - 取景参数与分辨率无关：nx/ny = 图相对取景框中心的偏移 ÷ 框宽/高；nz = 相对最小适配比例的缩放（1~4）。
 * - 零交互装饰层原则不变：无监听、不抢焦点、不进无障碍树（布局里已处理）。
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

    /** 槽位文件：原图 / 已取景图 */
    fun origFile(context: Context, slot: String): File = File(context.filesDir, "wp_${slot}_orig")
    fun cropFile(context: Context, slot: String): File = File(context.filesDir, "wp_${slot}_crop")

    /** 桌面快捷方式图标用的临时文件：原图 / 成品（选图 → 取景 → 生成） */
    fun iconOrigFile(context: Context): File = File(context.filesDir, "wp_icon_orig")
    fun iconCropFile(context: Context): File = File(context.filesDir, "wp_icon_crop")

    /**
     * 采样解码：目标长边 + 硬上限（防大图 OOM）+ 按 EXIF 自动摆正（横拍竖存不歪）。
     */
    fun decode(path: String, reqLongEdge: Int, hardCap: Int = 3000): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longEdge / (sample * 2) >= reqLongEdge) sample *= 2
        while (longEdge / sample > hardCap) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        if (bmp == null) null else applyExifOrientation(bmp, path)
    } catch (_: Exception) {
        null
    }

    /** EXIF 方向修正（异常时原样返回） */
    @Suppress("DEPRECATION")
    private fun applyExifOrientation(bmp: Bitmap, path: String): Bitmap {
        return try {
            val ori = android.media.ExifInterface(path).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
            val m = android.graphics.Matrix()
            when (ori) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                else -> return bmp
            }
            val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (r !== bmp) bmp.recycle()
            r
        } catch (_: Exception) {
            bmp
        }
    }

    /**
     * 按取景参数烘焙「屏幕显示图」。outW×outH 的宽高比应与取景框一致（框里看到什么 = 这里烘焙什么）。
     */
    fun bake(
        context: Context, origPath: String, nx: Float, ny: Float, nz: Float,
        out: File, outW: Int, outH: Int
    ): Boolean {
        return try {
            val src = decode(origPath, max(outW, outH), 3000) ?: return false
            val iw = src.width.toFloat()
            val ih = src.height.toFloat()
            val minS = max(outW / iw, outH / ih)
            val s = minS * nz.coerceIn(1f, 4f)
            val mx = max(0f, (iw * s - outW) / 2f)
            val my = max(0f, (ih * s - outH) / 2f)
            val x = (nx * outW).coerceIn(-mx, mx)
            val y = (ny * outH).coerceIn(-my, my)
            // 取景框（居中）在图像坐标里对应的区域
            val halfW = outW / 2f / s
            val halfH = outH / 2f / s
            val ccx = iw / 2f - x / s
            val ccy = ih / 2f - y / s
            val l = (ccx - halfW).coerceIn(0f, iw)
            val t = (ccy - halfH).coerceIn(0f, ih)
            val r = (ccx + halfW).coerceIn(0f, iw)
            val b = (ccy + halfH).coerceIn(0f, ih)
            if (r - l < 2f || b - t < 2f) {
                src.recycle()
                return false
            }
            val outBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val c = Canvas(outBmp)
            c.drawColor(Color.BLACK)
            c.drawBitmap(
                src,
                Rect(l.roundToInt(), t.roundToInt(), r.roundToInt(), b.roundToInt()),
                Rect(0, 0, outW, outH),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            val ok = try {
                out.outputStream().use { os ->
                    if (src.hasAlpha()) outBmp.compress(Bitmap.CompressFormat.PNG, 100, os)
                    else outBmp.compress(Bitmap.CompressFormat.JPEG, 92, os)
                }
            } catch (_: Exception) {
                false
            }
            outBmp.recycle()
            src.recycle()
            ok
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 生成桌面快捷方式图标（B 版式：浅空蓝底 + 照片 78%，512×512）。
     * 参数含义与壁纸一致（此模式下取景框为正方形）。
     */
    fun bakeIcon(origPath: String, nx: Float, ny: Float, nz: Float, out: File): Boolean {
        return try {
            val size = 512
            val inset = (size * 0.11f).roundToInt()
            val ps = size - inset * 2
            val src = decode(origPath, size, 1024) ?: return false
            val iw = src.width.toFloat()
            val ih = src.height.toFloat()
            val minS = max(ps / iw, ps / ih)
            val s = minS * nz.coerceIn(1f, 4f)
            val mx = max(0f, (iw * s - ps) / 2f)
            val my = max(0f, (ih * s - ps) / 2f)
            val x = (nx * ps).coerceIn(-mx, mx)
            val y = (ny * ps).coerceIn(-my, my)
            val half = ps / 2f / s
            val ccx = iw / 2f - x / s
            val ccy = ih / 2f - y / s
            val l = (ccx - half).coerceIn(0f, iw)
            val t = (ccy - half).coerceIn(0f, ih)
            val r = (ccx + half).coerceIn(0f, iw)
            val b = (ccy + half).coerceIn(0f, ih)
            if (r - l < 2f || b - t < 2f) {
                src.recycle()
                return false
            }
            val outBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(outBmp)
            c.drawColor(0xFF8EC9EE.toInt())
            c.drawBitmap(
                src,
                Rect(l.roundToInt(), t.roundToInt(), r.roundToInt(), b.roundToInt()),
                Rect(inset, inset, inset + ps, inset + ps),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            val ok = try {
                out.outputStream().use { os -> outBmp.compress(Bitmap.CompressFormat.PNG, 100, os) }
            } catch (_: Exception) {
                false
            }
            outBmp.recycle()
            src.recycle()
            ok
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 把某个槽位的壁纸应用到 Activity 的装饰层（imageId 图 / scrimId 遮罩）。
     * 同一文件不重复解码（ImageView.tag 记录 path@lastModified）。
     */
    fun applySlot(
        activity: Activity, imageId: Int, scrimId: Int,
        cropPath: String, dim: Int, enabled: Boolean, baseColor: Int
    ): Boolean {
        val iv = activity.findViewById<ImageView>(imageId)
        val scrim = activity.findViewById<View>(scrimId)
        if (!enabled) {
            iv.visibility = View.GONE
            scrim.visibility = View.GONE
            return false
        }
        if (cropPath.isEmpty() || !File(cropPath).exists()) {
            iv.setImageDrawable(null)
            iv.tag = null
            iv.visibility = View.GONE
            scrim.visibility = View.GONE
            return false
        }
        val key = cropPath + "@" + File(cropPath).lastModified()
        if (iv.tag != key || iv.drawable == null) {
            val dm = activity.resources.displayMetrics
            val bmp = decode(cropPath, max(dm.widthPixels, dm.heightPixels), 3000)
            if (bmp == null) {
                iv.setImageDrawable(null)
                iv.tag = null
                iv.visibility = View.GONE
                scrim.visibility = View.GONE
                return false
            }
            iv.setImageBitmap(bmp)
            iv.tag = key
        }
        iv.visibility = View.VISIBLE
        val a = ((dim.coerceIn(0, 80) / 100f) * 255).toInt()
        scrim.setBackgroundColor(
            Color.argb(a, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        )
        scrim.visibility = if (a == 0) View.GONE else View.VISIBLE
        return true
    }

    // ---------- 首页头像 ----------

    /** 头像成品文件（PNG） */
    fun avatarFile(context: Context): File = File(context.filesDir, "avatar_crop")

    /** 头像源图（导入的原始图，留作重裁用） */
    fun avatarSrcFile(context: Context): File = File(context.filesDir, "avatar_src")

    /** 头像取景烤图：按方形取景参数输出 256×256（圆形显示由 UI 裁剪） */
    fun bakeAvatar(context: Context, nx: Float, ny: Float, nz: Float, out: File): Boolean =
        bake(context, avatarSrcFile(context).absolutePath, nx, ny, nz, out, 256, 256)

    /** 导入头像：中心裁方 → 256×256 PNG 存盘 */
    fun saveAvatar(context: Context, uri: Uri): Boolean {
        return try {
            val src = avatarSrcFile(context)
            if (!importFrom(context, uri, src)) return false
            val bmp = decode(src.absolutePath, 256, 512) ?: return false
            val side = if (bmp.width < bmp.height) bmp.width else bmp.height
            val x = (bmp.width - side) / 2
            val y = (bmp.height - side) / 2
            val sq = Bitmap.createBitmap(bmp, x, y, side, side)
            val out = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            c.drawBitmap(sq, Rect(0, 0, side, side), Rect(0, 0, 256, 256), Paint(Paint.FILTER_BITMAP_FLAG))
            val ok = try {
                avatarFile(context).outputStream().use { os -> out.compress(Bitmap.CompressFormat.PNG, 100, os) }
            } catch (_: Exception) {
                false
            }
            out.recycle()
            if (sq !== bmp) sq.recycle()
            bmp.recycle()
            ok
        } catch (_: Exception) {
            false
        }
    }

    /** 默认头像：浅空蓝底 + 白环 + 白点（与品牌球一致） */
    fun defaultAvatar(sizePx: Int): Bitmap {
        val size = sizePx.coerceAtLeast(48)
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val f = size / 2f
        p.color = 0xFF8EC9EE.toInt()
        c.drawCircle(f, f, f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.075f
        p.color = Color.WHITE
        c.drawCircle(f, f, size * 0.30f, p)
        p.style = Paint.Style.FILL
        c.drawCircle(size * 0.70f, size * 0.27f, size * 0.10f, p)
        return b
    }

    /**
     * 旧版单张壁纸 → 两槽位迁移（幂等）。
     * 旧图复制给两处作起点（默认中心适配），旧开关/浓度分别继承；之后各自独立。
     */
    fun ensureMigrated(context: Context) {
        val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (sp.getBoolean("wp2_migrated", false)) return
        val edit = sp.edit()
        val oldPath = sp.getString("wallpaper_path", "") ?: ""
        val oldDim = sp.getInt("wallpaper_dim", 50).coerceIn(0, 80)
        edit.putBoolean("wp_page_en", sp.getBoolean("wallpaper_settings", true))
        edit.putBoolean("wp_main_en", sp.getBoolean("wallpaper_main", true))
        edit.putInt("wp_page_dim", oldDim)
        edit.putInt("wp_main_dim", oldDim)
        if (oldPath.isNotEmpty()) {
            try {
                val srcOld = File(oldPath)
                if (srcOld.exists()) {
                    val dm = context.resources.displayMetrics
                    for (slot in listOf("page", "main")) {
                        val o = origFile(context, slot)
                        srcOld.copyTo(o, overwrite = true)
                        edit.putString("wp_${slot}_orig", o.absolutePath)
                        val c = cropFile(context, slot)
                        if (bake(context, o.absolutePath, 0f, 0f, 1f, c, dm.widthPixels, dm.heightPixels)) {
                            edit.putString("wp_${slot}_crop", c.absolutePath)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        edit.putBoolean("wp2_migrated", true)
        edit.apply()
    }
}
