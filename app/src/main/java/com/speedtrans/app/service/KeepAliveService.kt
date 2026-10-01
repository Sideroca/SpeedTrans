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
        ensureChannel(this)
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

    private fun startForeground() {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                // Android 14+：specialUse 类型（该常量 API 34 才有）
                ServiceCompat.startForeground(
                    this, NOTIFY_ID, build(this),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                // API 26~33：specialUse 在旧平台无定义，退回两参重载（类型取清单声明）
                startForeground(NOTIFY_ID, build(this))
            }
        } catch (e: Exception) {
            // Android 12+ 后台重启等场景可能抛 ForegroundServiceStartNotAllowedException：不闪退
            Log.e(TAG, "startForeground failed", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SpeedTrans"
        private const val CHANNEL_ID = "keep_alive"
        private const val NOTIFY_ID = 1
        const val ACTION_TOGGLE = "com.speedtrans.app.action.TOGGLE_MODE"

        /** 无障碍服务是否在线（掉线时通知栏改告警态） */
        private fun ballAlive(): Boolean = BallService.instance != null

        /**
         * 刷新常驻通知：无障碍服务掉线/恢复时由 BallService 调用。
         *
         * 只重画通知内容，**不动前台服务归属**——所以不需要 startService
         * （Android 8+ 从后台 startService 会被系统拦掉）。先看通知在不在，
         * 不在就不凭空造一条无人收回的常驻通知。
         */
        fun refreshBallState(ctx: Context) {
            try {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.activeNotifications.none { it.id == NOTIFY_ID }) return
                ensureChannel(ctx)
                nm.notify(NOTIFY_ID, build(ctx))
            } catch (_: Throwable) {
            }
        }

        private fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "闪译运行状态", NotificationManager.IMPORTANCE_MIN).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
            )
        }

        private fun build(ctx: Context): Notification {
            val st = SettingsStore(ctx)
            val modeName = when (st.translateMode) {
                "ocr" -> "🖼 仅识图"
                else -> "📄 仅文本"
            }
            val b = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFF8EC9EE.toInt())
                .setOngoing(true)
            return if (ballAlive()) {
                b.setContentTitle("闪译 · $modeName")
                    .setContentText("点通知条切换模式 · 点球即翻")
                    .setContentIntent(togglePI(ctx))   // 点通知条 = 立即循环切换（折叠态也生效）
                    .addAction(0, "切换模式", togglePI(ctx))
                    .addAction(0, "🖼 识图翻译", ocrPI(ctx))
                    .build()
            } else {
                // 无障碍被系统关掉（重启/被清理/应用更新）→ 悬浮球已失效。
                // 不说的话用户只会觉得"闪译坏了"，这里给一条能直接点进去重开的告警。
                b.setContentTitle("⚠️ 闪译 · 无障碍服务未运行")
                    .setContentText("悬浮球已失效 · 点此重新开启")
                    .setContentIntent(a11yPI(ctx))
                    .addAction(0, "去开启", a11yPI(ctx))
                    .build()
            }
        }

        private fun togglePI(ctx: Context): PendingIntent = PendingIntent.getService(
            ctx, 1,
            Intent(ctx, KeepAliveService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun ocrPI(ctx: Context): PendingIntent = PendingIntent.getService(
            ctx, 2,
            Intent(ctx, BallService::class.java).setAction(BallService.ACTION_OCR),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun a11yPI(ctx: Context): PendingIntent = PendingIntent.getActivity(
            ctx, 3,
            Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
