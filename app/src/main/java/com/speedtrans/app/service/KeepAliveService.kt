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
            ACTION_TOGGLE_BALL -> {
                // 隐藏 = 摘窗（零误触）；服务照常连着，所以不影响自启动 / 无障碍状态。
                val st = SettingsStore(this)
                st.ballHidden = !st.ballHidden
                BallService.instance?.syncBallVisibility()
            }
        }
        // 任何命令都重建通知：标题始终显示当前模式（含设置页保存后的同步）
        startForeground()
        // 注：旧版「点通知后自动把通知栏再拉下来」的无障碍动作已删除（智能模式时代残留，会造成通知栏"下滑→收缩→再下滑"的抖动）
        return START_STICKY
    }

    /**
     * 用户**主动把闪译从最近任务里划掉** → 悬浮球自动消失（轻响 2026-10-02 需求）。
     *
     * `Service.onTaskRemoved()` 是这件事唯一可靠的官方入口（API 14 起就有，零权限）。
     * 为什么放在保活服务而不是无障碍服务：无障碍服务的生命周期由系统托管，
     * 能不能收到这个回调不确定；保活服务是我们自己起的普通前台服务，一定收得到 ✓
     *
     * 三点边界（都验证过）：
     * · **只摘球，不停服务** —— 无障碍照常连着 → 不影响自启动，那 10 秒不用重等 ✓
     * · **可恢复** —— 通知条上的「召唤悬浮球」还在（它来自前台服务）✓
     * · **返回键退出不触发** —— 只是退到后台、任务还在 → 球该留着 ✓
     *
     * ROM 差异：不回调的机型本来就会直接杀进程 → 球同样消失，**结果一致** ✓
     * （这台机器上如果球晚几秒才消失，就是系统回调的节奏，不是没生效）
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val st = SettingsStore(this)
        st.ballHidden = true
        BallService.instance?.syncBallVisibility()   // 服务不在时是空操作，不会崩
        startForeground()                            // 通知条文案同步成"已隐藏"
        super.onTaskRemoved(rootIntent)
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

        /** 通知条左按钮：隐藏 / 召唤悬浮球 */
        const val ACTION_TOGGLE_BALL = "com.speedtrans.app.action.TOGGLE_BALL"

        /**
         * 无障碍服务是否**在系统设置里启用**（这才是"掉线"的真判据）。
         *
         * 为什么需要它：`BallService.instance` 只在"**已经连上**"时才非空。刚开机、刚被系统重绑、
         * 或**刚划掉最近任务的那几秒**里实例就是 null —— 但那**不代表用户没开** ✗。
         * 拿"实例在不在"当告警判据，用户会看到「让我去开启」而其实权限好好的（真机实测反馈），
         * 白白误导人去折腾 ✓
         */
        private fun a11yEnabled(ctx: Context): Boolean = try {
            val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE)
                    as? android.view.accessibility.AccessibilityManager
            val want = android.content.ComponentName(ctx, BallService::class.java)
            am?.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )?.any { info ->
                android.content.ComponentName.unflattenFromString(info.id) == want
            } ?: false
        } catch (_: Throwable) {
            false
        }

        /**
         * 无障碍是否可用：
         * · 实例在 → 一定可用 ✓
         * · 实例不在、但设置里启用 → 视为"**正在重连**"，**不告警** ✓（它会自己回来）
         * · 实例不在、设置里也没启用 → 才是真的掉线 → 通知栏改告警态 ✓
         */
        private fun ballAlive(ctx: Context): Boolean =
            BallService.instance != null || a11yEnabled(ctx)

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
            // 通知条 = 三态遥控器（2026-10-01 轻响钦定）：
            //   ① 服务掉线 → 告警 + 去开启
            //   ② 球已隐藏 → 「召唤悬浮球」（点通知条本身仍是切换模式，不变）
            //   ③ 正常     → 「隐藏悬浮球」
            // 注意：通知条**本身**的点击始终是"切换模式"（现行制度，用户点一下就切），
            // 所以原先那个「切换模式」action 是冗余的 —— 让位给球开关。
            return when {
                !ballAlive(ctx) -> b
                    .setContentTitle("⚠️ 闪译 · 无障碍服务未运行")
                    .setContentText("悬浮球已失效 · 点此重新开启")
                    .setContentIntent(a11yPI(ctx))
                    .addAction(0, "去开启", a11yPI(ctx))
                    .build()

                st.ballHidden -> b
                    .setContentTitle("闪译 · $modeName（球已隐藏）")
                    .setContentText("点按通知条切换模式，长按召唤悬浮球")
                    .setContentIntent(togglePI(ctx))
                    .addAction(0, "召唤悬浮球", ballTogglePI(ctx))
                    .addAction(0, "🖼 识图翻译", ocrPI(ctx))
                    .build()

                else -> b
                    .setContentTitle("闪译 · $modeName")
                    // 文案（轻响钦定 2026-10-01）：点按 = 切换模式；**长按 = 展开通知**露出「隐藏悬浮球」按钮。
                    // 长按展开是 **MIUI/HyperOS 给的手势**（AOSP 那边是下拉展开）——按钮本身一直在，
                    // 跟 ROM 无关（addAction 是 4.1 起的核心 API）。换非 MIUI 机型时把这句改成
                    // "长按/下拉" 即可两边通吃。
                    .setContentText("点按通知条切换模式，长按关闭悬浮球")
                    .setContentIntent(togglePI(ctx))   // 点通知条 = 立即循环切换（折叠态也生效）
                    .addAction(0, "隐藏悬浮球", ballTogglePI(ctx))
                    .addAction(0, "🖼 识图翻译", ocrPI(ctx))
                    .build()
            }
        }

        /** 悬浮球显隐开关（摘窗/重建，服务不掉） */
        private fun ballTogglePI(ctx: Context): PendingIntent = PendingIntent.getService(
            ctx, 4,
            Intent(ctx, KeepAliveService::class.java).setAction(ACTION_TOGGLE_BALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
