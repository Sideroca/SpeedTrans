package com.speedtrans.app.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Surface

/**
 * 屏幕投影持有者：授权一次后由前台服务持续持有，
 * 进程活着则一直可用（无需重复授权）。
 */
object ProjectionHolder {

    private var projection: MediaProjection? = null
    private var vDisplay: android.hardware.display.VirtualDisplay? = null
    private var reader: android.view.ImageReader? = null
    private var handler: Handler? = null
    private var busy = false

    val isReady: Boolean get() = projection != null && reader != null

    fun init(context: Context, resultCode: Int, data: Intent) {
        release()
        val sm = context.getSystemService(MediaProjectionManager::class.java) as MediaProjectionManager
        val h = Handler(Looper.getMainLooper())
        handler = h
        val mp = sm.getMediaProjection(resultCode, data)
        val dm: DisplayMetrics = context.resources.displayMetrics
        val r = android.view.ImageReader.newInstance(
            dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2
        )
        mp.createVirtualDisplay(
            "SpeedTransCap",
            dm.widthPixels, dm.heightPixels, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, h
        )
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                release()
            }
        }, h)
        projection = mp
        reader = r
    }

    /**
     * 截取当前屏幕（异步回调，回调线程 = 主线程）。
     */
    fun capture(onBitmap: (Bitmap) -> Unit, onFail: (Throwable?) -> Unit) {
        val r = reader
        val h = handler
        if (r == null || h == null || busy) {
            onFail(null)
            return
        }
        busy = true
        var grabbed = false

        fun process(img: android.view.ImageReader.Image) {
            try {
                val plane = img.planes[0]
                val buf = plane.rowBuffer
                val rowStride = plane.rowStride
                val pixStride = plane.pixelStride
                val raw = Bitmap.createBitmap(rowStride / pixStride, img.height, Bitmap.Config.ARGB_8888)
                buf.rewind()
                raw.copyPixelsFromBuffer(buf)
                img.close()
                val bmp = if (rowStride == raw.width * pixStride) raw
                else Bitmap.createBitmap(raw, 0, 0, img.width, img.height)
                busy = false
                onBitmap(bmp)
            } catch (e: Exception) {
                busy = false
                onFail(e)
            }
        }

        fun tryAcquire() {
            if (grabbed) return
            val img = try {
                r.acquireLatestImage()
            } catch (e: Exception) {
                null
            }
            if (img != null) {
                grabbed = true
                process(img)
            }
        }

        r.setOnImageAvailableListener({
            tryAcquire()
        }, h)

        // 镜像屏有持续渲染帧；静态画面兜底：1 秒后强取一次
        h.postDelayed({
            if (!grabbed) {
                tryAcquire()
                if (!grabbed) {
                    busy = false
                    onFail(null)
                }
            }
        }, 1000)
    }

    fun release() {
        try {
            vDisplay?.release()
        } catch (_: Exception) {}
        try {
            projection?.stop()
        } catch (_: Exception) {}
        projection = null
        vDisplay = null
        reader = null
        busy = false
    }
}
