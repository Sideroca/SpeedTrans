package com.speedtrans.app.game

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * 彩蛋：霓虹空气曲棍球（全屏，返回键退出）。
 * 由 Matt Cannon 的 CodePen 作品移植（原生 Kotlin/Canvas，零新依赖）。
 */
class AirHockeyActivity : AppCompatActivity() {

    private lateinit var game: AirHockeyView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 沉浸式全屏：隐藏状态栏与导航栏（边缘划入可临时唤出），专心打游戏
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        game = AirHockeyView(this)
        setContentView(game)
    }

    override fun onResume() {
        super.onResume()
        game.start()
    }

    override fun onPause() {
        game.stop()
        super.onPause()
    }
}
