package com.speedtrans.app

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.speedtrans.app.service.ScreenBallService

/**
 * 透明授权页：请求 MediaProjection 屏幕捕捉授权。
 * 授权结果交给 ScreenBallService（FGS 已在运行，符合 Android 14 要求），
 * 完成后自动触发一次翻译。
 */
class AuthorizeActivity : AppCompatActivity() {

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK && res.data != null) {
                val i = Intent(this, ScreenBallService::class.java)
                    .putExtra(ScreenBallService.EXTRA_RESULT_CODE, res.resultCode)
                    .putExtra(ScreenBallService.EXTRA_DATA, res.data)
                ContextCompat.startForegroundService(this, i)
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sm = getSystemService(MediaProjectionManager::class.java) as MediaProjectionManager
        launcher.launch(sm.createScreenCaptureIntent())
    }
}
