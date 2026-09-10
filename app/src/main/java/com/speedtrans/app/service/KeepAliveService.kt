package com.speedtrans.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.speedtrans.app.R
import com.speedtrans.app.store.SettingsStore

/**
 * 前台保活 + 控制中心：
 * 常驻通知（划不掉），带两个按钮——「切换模式」「🖼 识图翻译」。
 * 模式：📄 仅文本（默认）/ 🖼 仅识图，与设置页同步。
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
                st.translateMode = if (st.translateMode == "ocr") "text" else "ocr"
            }
        }
        // 任何命令都重建通知：标题始终显示当前模式（含设置页保存后的同步）
        startForeground()
        // 注：旧版「点通知后自动把通知栏再拉下来」的无障碍动作已删除（智能模式时代残留，会造成通知栏"下滑→收缩→再下滑"的抖动）
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
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                // Android 14+：specialUse 类型（该常量 API 34 才有）
                ServiceCompat.startForeground(
                    this, NOTIFY_ID, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                // API 26~33：specialUse 在旧平台无定义，退回两参重载（类型取清单声明）
                startForeground(NOTIFY_ID, buildNotification())
            }
        } catch (e: Exception) {
            // Android 12+ 后台重启等场景可能抛 ForegroundServiceStartNotAllowedException：不闪退
            Log.e(TAG, "startForeground failed", e)
        }
    }

    private fun buildNotification(): Notification {
        val st = SettingsStore(this)
        val modeName = when (st.translateMode) {
            "ocr" -> "🖼 仅识图"
            else -> "📄 仅文本"
        }
        // 大图标：浅空蓝双轨道球（IconStudio 现场生成，配色与图标工坊同源）
        val ringIcon = com.speedtrans.app.ui.IconStudio.generate(
            com.speedtrans.app.ui.IconStudio.STYLE_RING,
            0xFF8EC9EE.toInt(), 0xFFFFFFFF.toInt(), 192
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setLargeIcon(ringIcon)
            .setColor(0xFF8EC9EE.toInt())
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
        private const val TAG = "SpeedTrans"
        private const val CHANNEL_ID = "keep_alive"
        private const val NOTIFY_ID = 1
        const val ACTION_TOGGLE = "com.speedtrans.app.action.TOGGLE_MODE"
    }
}
