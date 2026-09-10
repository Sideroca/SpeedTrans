package com.speedtrans.app.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 程序化音效（零资源 / 零依赖）：清脆冷静的玻璃质感。
 * 全部 PCM 在初始化时合成一次，播放走 MODE_STATIC 的 AudioTrack（重播只多一次 reload）。
 * 设计：高频短瞬态(6~8ms 噪声) + 2~3 个正弦分音 + 快速指数衰减 = "叮/嗒"；进球是一声干净的铃，不做低频轰。
 */
class SoundKit {

    private val SR = 44100

    private var hit: AudioTrack? = null
    private var wall: AudioTrack? = null
    private var goal: AudioTrack? = null
    private var win: AudioTrack? = null
    private var lose: AudioTrack? = null
    private var speed: AudioTrack? = null
    private var dead = false

    var muted = false

    fun init() {
        if (hit != null || dead) return
        try {
            // 击球：清脆"嗒"（2.6k/3.9k/5.2k 分音 + 噪声瞬态，55/s 快衰减）
            hit = track(render(70, floatArrayOf(2600f, 3900f, 5200f), floatArrayOf(1f, 0.45f, 0.2f), 55f, 0.40f))
            // 撞墙：更轻更高（3.4k/5.1k，85/s）
            wall = track(render(40, floatArrayOf(3400f, 5100f), floatArrayOf(1f, 0.3f), 85f, 0.28f, 0.6f))
            // 进球：一声干净的铃（880/1320/1760 + 一丝 220 暖底，6.5/s 缓衰减，无低频轰）
            goal = track(render(520, floatArrayOf(880f, 1320f, 1760f, 220f), floatArrayOf(1f, 0.5f, 0.25f, 0.2f), 6.5f, 0.05f))
            // 胜利：轻盈上行琶音 C5-E5-G5-C6
            win = track(melody(arrayOf(523.25f to 0, 659.25f to 150, 783.99f to 300, 1046.5f to 450), 220, 4.5f, 0.5f))
            // 失败：柔和下行两音（392 → 311），冷静不刺耳
            lose = track(melody(arrayOf(392f to 0, 311.13f to 260), 260, 5.5f, 0.45f))
            // 提速提示：一声轻快的 D6 小铃
            speed = track(render(180, floatArrayOf(1174.66f, 1760f), floatArrayOf(1f, 0.3f), 11f, 0.05f, 0.5f))
        } catch (_: Throwable) {
            dead = true
        }
    }

    /** 合成：分音叠加 + 起始噪声瞬态；decay 越大衰减越快 */
    private fun render(
        durMs: Int, freqs: FloatArray, amps: FloatArray, decay: Float, noise: Float, amp: Float = 1f
    ): ShortArray {
        val n = (SR * durMs / 1000).coerceAtLeast(64)
        val out = ShortArray(n)
        val noiseN = (n * 0.05f).toInt().coerceAtLeast(1)
        for (i in 0 until n) {
            val t = i.toFloat() / SR
            var v = 0f
            for (k in freqs.indices) v += sin((2.0 * PI * freqs[k] * t).toFloat()) * amps[k]
            if (i < noiseN) {
                val g = 1f - i.toFloat() / noiseN
                v += (Random.nextFloat() * 2f - 1f) * noise * g * 3f
            }
            val env = exp(-decay * t)
            val s = (v * env * amp * 0.30f).coerceIn(-1f, 1f)
            out[i] = (s * 32767f).toInt().toShort()
        }
        return out
    }

    /** 旋律：一条时间轴上叠加若干音符（软起音 + 指数衰减） */
    private fun melody(notes: Array<Pair<Float, Int>>, noteMs: Int, decay: Float, amp: Float): ShortArray {
        val totalMs = notes.maxOf { it.second } + noteMs + 140
        val n = SR * totalMs / 1000
        val out = ShortArray(n)
        for ((f, startMs) in notes) {
            val s0 = SR * startMs / 1000
            val len = SR * noteMs / 1000
            for (i in 0 until len) {
                val idx = s0 + i
                if (idx >= n) break
                val t = i.toFloat() / SR
                val env = exp(-decay * t) * (1f - exp(-260f * t))
                val v = sin((2.0 * PI * f * t).toFloat()) + 0.3f * sin((2.0 * PI * f * 2f * t).toFloat())
                val cur = out[idx] / 32767f
                out[idx] = ((cur + v * env * amp * 0.30f).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }
        }
        return out
    }

    private fun track(buf: ShortArray): AudioTrack {
        val tr = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SR)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            buf.size * 2,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        tr.write(buf, 0, buf.size)
        return tr
    }

    private fun play(tr: AudioTrack?, vol: Float) {
        if (muted || dead || tr == null) return
        try {
            tr.stop()
            tr.reloadStaticData()
            tr.setVolume(vol.coerceIn(0f, 1f))
            tr.play()
        } catch (_: Throwable) {
        }
    }

    fun playHit(spd: Float) = play(hit, 0.34f + min(spd / 26f, 0.30f))
    fun playWall() = play(wall, 0.30f)
    fun playGoal() = play(goal, 0.55f)
    fun playWin() = play(win, 0.50f)
    fun playLose() = play(lose, 0.45f)
    fun playSpeed() = play(speed, 0.42f)

    fun release() {
        for (t in arrayOf(hit, wall, goal, win, lose, speed)) {
            try { t?.stop() } catch (_: Throwable) {}
            try { t?.release() } catch (_: Throwable) {}
        }
        hit = null; wall = null; goal = null; win = null; lose = null; speed = null
    }
}
