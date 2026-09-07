package com.speedtrans.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat

/**
 * 前台保活服务：挂住进程，用户划掉最近任务时无障碍服务与悬浮球不受影响。
 */
class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "闪译运行状态", NotificationManager.IMPORTANCE_MIN).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.speedtrans.app.R.drawable.ic_app)
            .setContentTitle("闪译运行中")
            .setContentText("悬浮球待命 · 划掉最近任务不影响使用")
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFY_ID, n,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    companion object {
        private const val CHANNEL_ID = "keep_alive"
        private const val NOTIFY_ID = 1
    }
}
