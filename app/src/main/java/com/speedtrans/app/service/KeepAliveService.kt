package com.speedtrans.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.speedtrans.app.R
import com.speedtrans.app.store.SettingsStore

/**
 * 前台保活 + 控制中心：
 * 常驻通知（划不掉），带两个按钮——「切换模式」「🖼 识图翻译」。
 * 模式：🤖 智能（默认）/ 📄 仅文本 / 🖼 仅识图，手动选择永远优先。
 */
class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fromToggle = intent?.action == ACTION_TOGGLE
        when (intent?.action) {
            ACTION_TOGGLE -> {
                val st = SettingsStore(this)
                st.translateMode = if (st.translateMode == "ocr") "text" else "ocr"
            }
        }
        // 任何命令都重建通知：标题始终显示当前模式（含设置页保存后的同步）
        startForeground()
        // 点通知条触发时系统会收起通知栏——借无障碍全局动作拉回来（无障碍未开则优雅降级）
        if (fromToggle && BallService.instance != null) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                BallService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                )
            }, 250)
        }
        return START_STICKY
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "闪译运行状态", NotificationManager.IMPORTANCE_MIN).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }

    private fun startForeground() {
        ServiceCompat.startForeground(
            this, NOTIFY_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun buildNotification(): Notification {
        val st = SettingsStore(this)
        val modeName = when (st.translateMode) {
            "ocr" -> "🖼 仅识图"
            else -> "📄 仅文本"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("闪译 · $modeName")
            .setContentText("点通知条切换模式 · 点球即翻")
            .setContentIntent(togglePendingIntent())   // 点通知条 = 立即循环切换（折叠态也生效）
            .setOngoing(true)
            .addAction(0, "切换模式", togglePendingIntent())
            .addAction(0, "🖼 识图翻译", ocrPendingIntent())
            .build()
    }

    private fun togglePendingIntent(): PendingIntent = PendingIntent.getService(
        this, 1,
        Intent(this, KeepAliveService::class.java).setAction(ACTION_TOGGLE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun ocrPendingIntent(): PendingIntent = PendingIntent.getService(
        this, 2,
        Intent(this, BallService::class.java).setAction(BallService.ACTION_OCR),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "keep_alive"
        private const val NOTIFY_ID = 1
        const val ACTION_TOGGLE = "com.speedtrans.app.action.TOGGLE_MODE"
    }
}
