package com.speedtrans.app.ui

import android.os.Build
import android.widget.TextView
import androidx.appcompat.app.AppCompatResources

/**
 * 火把光标：输入框光标换成小火把（火苗 + 木杆）。
 * 光标由应用自绘（textCursorDrawable），与输入法无关；
 * 程序化 setter 需 API 29+（本项目真机基线 Android 13+，全覆盖）。
 */
object FlameCursor {

    fun apply(vararg ets: TextView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        ets.forEach { et ->
            et.textCursorDrawable = AppCompatResources.getDrawable(et.context, R.drawable.cursor_torch)
        }
    }
}
