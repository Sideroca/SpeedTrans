package com.speedtrans.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ServiceCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.speedtrans.app.AuthorizeActivity
import com.speedtrans.app.capture.ProjectionHolder
import com.speedtrans.app.store.SettingsStore
import com.speedtrans.app.translate.TranslateCoordinator
import java.io.File
import kotlin.math.hypot

/**
 * OCR 引擎主服务：普通前台服务（mediaProjection 类型）。
 * 职责：绘制悬浮球 + 截屏 + 端侧 OCR 取词 + 触发翻译。
 * 不依赖无障碍服务，权限温和且不易被 MIUI 清理。
 */
class ScreenBallService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        private const val TAG = "SpeedTrans"

        @Volatile
        var instance: ScreenBallService? = null
            private set

        private val recognizer by lazy {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var ball: View? = null
    private var ballParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android 14 时序要求：必须先取得用户授权（consent token），
        // 才能以 mediaProjection 类型进入前台。
        val rc = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (rc == -1 || data == null) {
            // 被系统重启且无授权数据：无法工作，等待用户重新授权
            stopSelf()
            return START_NOT_STICKY
        }
        return try {
            val sm = getSystemService(MediaProjectionManager::class.java) as MediaProjectionManager
            // 1) 拿投影（在 startForegroundService 后的宽限窗口内调用合法）
            ProjectionHolder.init(this, rc, data)
            // 2) 有 session 后进入前台（mediaProjection 类型，合规）
            startForegroundNotify()
            // 3) 画球并立即翻译一次
            mainHandler.post {
                showBall()
                mainHandler.postDelayed({ grabAndTranslate() }, 400)
            }
            START_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "projection init failed", e)
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        mainHandler.post {
            hideBall()
            ProjectionHolder.release()
        }
        super.onDestroy()
    }

    private fun startForegroundNotify() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("speedtrans_fg", "闪译运行状态", NotificationManager.IMPORTANCE_MIN).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
        val n = Notification.Builder(this, "speedtrans_fg")
            .setSmallIcon(com.speedtrans.app.R.drawable.ic_app)
            .setContentTitle("闪译运行中")
            .setContentText("悬浮球待命 · 划掉最近任务不影响使用")
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, 1, n,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
    }

    private fun wm(): WindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    // ---------------- 悬浮球（与无障碍引擎样式一致） ----------------

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
                    if (!dragging) grabAndTranslate()
                }
            }
            return true
        }
    }

    // ---------------- 截屏 + OCR + 翻译 ----------------

    private fun grabAndTranslate() {
        if (!Settings.canDrawOverlays(this)) return
        if (!ProjectionHolder.isReady) {
            authorize()
            return
        }
        val ov = TranslateCoordinator.overlay(this)
        ov.ensure()
        ov.showStatus("📸 正在截屏识别…")

        ProjectionHolder.capture(
            onBitmap = { bmp -> runOcr(bmp) },
            onFail = { e ->
                mainHandler.post {
                    ov.showStatus("⚠️ 截屏失败：${e?.message ?: "请重试"}")
                }
            }
        )
    }

    private fun runOcr(bmp: Bitmap) {
        recognizer.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { visionText ->
                val text = visionText.text.trim()
                if (text.isEmpty()) {
                    TranslateCoordinator.overlay(this).apply {
                        ensure()
                        showStatus("⚠️ 屏幕上没有识别到文字")
                    }
                } else {
                    TranslateCoordinator.startTranslate(this, text)
                }
            }
            .addOnFailureListener { e ->
                mainHandler.post {
                    TranslateCoordinator.overlay(this).apply {
                        ensure()
                        showStatus("⚠️ 识别失败：${e.message ?: ""}")
                    }
                }
            }
    }

    private fun authorize() {
        val i = Intent(this, AuthorizeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
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
