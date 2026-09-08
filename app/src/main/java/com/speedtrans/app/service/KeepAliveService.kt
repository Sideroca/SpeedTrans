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
        when (intent?.action) {
            ACTION_TOGGLE -> {
                val st = SettingsStore(this)
                st.translateMode = when (st.translateMode) {
                    "smart" -> "text"
                    "text" -> "ocr"
                    else -> "smart"
                }
            }
        }
        // 任何命令都重建通知：标题始终显示当前模式（含设置页保存后的同步）
        startForeground()
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
            "text" -> "📄 仅文本"
            "ocr" -> "🖼 仅识图"
            else -> "🤖 智能"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle("闪译 · $modeName")
            .setContentText("点球即翻 · 划掉通知不影响使用")
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
