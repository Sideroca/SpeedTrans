package com.speedtrans.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 旧"伪装"别名自愈：早期版本支持 闪译/备忘录/工具箱 三态循环（按钮已移除）。
 * 若设备上仍残留启用的别名，会在主界面启动 / 无障碍服务连接时自动复位——
 * 确保默认图标（浅空蓝·白球）能正常显示。
 */
object LauncherAliases {

    fun repair(context: Context) {
        try {
            val pm = context.packageManager
            val pkg = context.packageName
            for (suffix in listOf(".main_blue", ".main_green")) {
                val cn = ComponentName(pkg, "$pkg$suffix")
                if (pm.getComponentEnabledSetting(cn) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                ) {
                    pm.setComponentEnabledSetting(
                        cn,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
            val red = ComponentName(pkg, "$pkg.main_red")
            if (pm.getComponentEnabledSetting(red) ==
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            ) {
                pm.setComponentEnabledSetting(
                    red,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (_: Exception) {
        }
    }
}
