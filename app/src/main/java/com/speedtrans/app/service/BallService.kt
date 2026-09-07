package com.speedtrans.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.TextView
import com.speedtrans.app.store.SettingsStore
import com.speedtrans.app.translate.TextCollector
import com.speedtrans.app.translate.TranslateCoordinator
import java.io.File
import kotlin.math.hypot

/**
 * 无障碍服务：绘制悬浮球 + 抓取屏幕文字 + 触发翻译编排。
 */
class BallService : AccessibilityService() {

    companion object {
        private const val TAG = "SpeedTrans"

        @Volatile
        var instance: BallService? = null
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var ball: View? = null
    private var ballParams: WindowManager.LayoutParams? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        mainHandler.post { showBall() }
    }

    override fun onDestroy() {
        instance = null
        mainHandler.post { hideBall() }
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    private fun wm(): WindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun showBall() {
        if (!Settings.canDrawOverlays(this) || ball != null) return
        val st = SettingsStore(this)
        val sizePx = dp(st.ballSizeDp)

        val lp = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(12)
        lp.y = dp(180)

        val imgPath = st.ballImagePath
        val view: View = if (imgPath.isNotEmpty() && File(imgPath).exists()) {
            ImageView(this).apply {
                setImageBitmap(decodeScaled(imgPath, sizePx * 2))
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, o: Outline) {
                        if (st.ballCircle) {
                            o.setOval(0, 0, v.width, v.height)
                        } else {
                            o.setRoundRect(
                                0, 0, v.width, v.height,
                                dp(st.ballSizeDp / 4).toFloat()
                            )
                        }
                    }
                }
            }
        } else {
            TextView(this).apply {
                text = st.ballText
                textSize = st.ballSizeDp * 0.34f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    if (st.ballCircle) {
                        shape = GradientDrawable.OVAL
                    } else {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(st.ballSizeDp / 4).toFloat()
                    }
                    setColor(st.ballColorInt)
                }
            }
        }

        view.setOnTouchListener(BallTouchListener(lp))
        try {
            wm().addView(view, lp)
            ball = view
            ballParams = lp
        } catch (e: Exception) {
            Log.e(TAG, "showBall", e)
        }
    }

    private fun hideBall() {
        ball?.let { try { wm().removeView(it) } catch (_: Exception) {} }
        ball = null
        ballParams = null
    }

    fun refreshBall() {
        mainHandler.post { hideBall(); showBall() }
    }

    private inner class BallTouchListener(private val lp: WindowManager.LayoutParams) :
        View.OnTouchListener {

        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (dragging || hypot(dx, dy) > 14f) {
                        dragging = true
                        lp.x = (startX + dx).toInt()
                        lp.y = (startY + dy).toInt()
                        try {
                            wm().updateViewLayout(v, lp)
                        } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) startTranslate()
                }
            }
            return true
        }
    }

    fun startTranslate() {
        val root: AccessibilityNodeInfo? = rootInActiveWindow
        val text = if (root != null) TextCollector.collect(root) else ""
        TranslateCoordinator.startTranslate(this, text)
    }

    private fun decodeScaled(path: String, target: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, opts)
    }
}
