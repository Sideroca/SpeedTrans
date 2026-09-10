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
