package com.speedtrans.app.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 闪译彩蛋：霓虹空气曲棍球（原生 Kotlin 移植版）
 *
 * 原作：Matt Cannon 的 CodePen「Air Hockey」（Canvas 2D，1440 行 JS，MIT © 2026）
 * https://codepen.io/matt-cannon/pen/yyVLNNj
 * 布局/文案按原作 CSS 1:1 对齐（两侧数据面板、GAME · SET · MATCH、PLAY AGAIN 切角按钮等）。
 * 本移植保持：虚拟画布 760×520、物理常量、CPU 五参数、慢动作/加速机制与霓虹配色。
 *
 * 性能约定：所有 Paint/Path/RectF 预分配，帧循环零 new（对齐项目的动画预分配惯例）。
 * 帧驱动：Choreographer + 固定 1/60s 步进（90/120Hz 屏也不会变快）。
 */
class AirHockeyView(context: Context) : View(context), Choreographer.FrameCallback {

    // ---------------- 虚拟画布 ----------------
    private val VW = 760f
    private val VH = 520f
    // 1:1 布局（原版 #outer：两侧 130px 数据面板 + 760×520 竞技场）
    private val PANEL = 130f
    private var scale = 1f
    private var offX = 0f
    private var offY = 0f

    // ---------------- 几何 / 物理常量（与原作一致） ----------------
    private val TABLE_X = 30f
    private val TABLE_Y = 30f
    private val TABLE_W = 700f
    private val TABLE_H = 460f
    private val CX = 380f
    private val CY = 260f
    private val GOAL_W = 160f
    private val GOAL_Y1 = CY - GOAL_W / 2
    private val GOAL_Y2 = CY + GOAL_W / 2
    private val PUCK_R = 14f
    private val MALLET_R = 24f
    private val MAX_SCORE = 7
    private val FRICTION = 0.995f
    private val WALL_BOUNCE = 0.82f
    private val CPU_SPEED = 4.6f
    private val CPU_REACT = 0.62f
    private val CPU_ERROR_Y = 26f
    private val CPU_MISTAKE_CHANCE = 0.018f
    private val CPU_MISTAKE_DUR = 42

    // ---------------- 颜色 ----------------
    private val C_BG = Color.parseColor("#04060a")
    private val C_PLAYER = Color.parseColor("#00d4ff")
    private val C_CPU = Color.parseColor("#ff2d55")
    private val C_GOLD = Color.parseColor("#ffc940")
    private val C_TABLE = Color.parseColor("#0a1018")
    private val C_LINE = Color.parseColor("#16202e")
    private val C_TEXT = Color.parseColor("#7d8ea3")

    // ---------------- 实体 ----------------
    private class Body {
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var r = 0f
    }

    private val puck = Body().apply { r = PUCK_R; x = CX; y = CY }
    private val player = Body().apply { r = MALLET_R; x = TABLE_X + 110f; y = CY }
    private val cpu = Body().apply { r = MALLET_R; x = VW - TABLE_X - 110f; y = CY }

    private var pvx = 0f
    private var pvy = 0f
    private var cpuHitCool = 0
    private var cpuMistake = 0
    private var cpuErrY = 0f

    // ---------------- 局面 ----------------
    private var state = 0          // 0 标题 / 1 对战 / 2 进球停顿 / 3 结束
    private var goalTimer = 0
    private var goalWho = 0        // 0 玩家 / 1 CPU（进球方）
    private var goalFlash = 0f
    private var goalMsgScale = 0f
    private var tick = 0L
    private var shakeAmt = 0f
    private var shakeX = 0f
    private var shakeY = 0f
    private var puckSpeedMult = 1f
    private var lastSpeedUpAt = 0
    private var speedUpMsg = ""
    private var speedUpTimer = 0
    private var overT = 0f          // 结算画面的落定进度（0→1）
    private var overTick = 0L
    private var pPower = 0; private var cPower = 0
    private var sloMo = false
    private var sloMoAlpha = 0f
    private var sloMoIntro = 0
    private var sloMoLabelTimer = 0
    private var sadFace = 0f
    private val sound = SoundKit()

    private val scoreP = IntArray(1)
    private val scoreC = IntArray(1)
    private var pStreak = 0; private var pBestStreak = 0; private var pTopSpeed = 0
    private var cStreak = 0; private var cBestStreak = 0; private var cTopSpeed = 0

    // ---------------- 特效 ----------------
    private class Particle {
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
        var t = 0f; var life = 1f; var size = 2f; var color = 0
    }

    private class Confetti {
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
        var rot = 0f; var vr = 0f; var w = 6f; var h = 10f; var color = 0
    }

    // 预分配对象池（修复击球瞬间的卡顿：原来每次击球 new 40 个粒子触发 GC 抖动）
    private val MAX_PART = 480
    private val parts = Array(MAX_PART) { Particle() }
    private var partN = 0
    private val MAX_CONF = 160
    private val confs = Array(MAX_CONF) { Confetti() }
    private var confN = 0
    private val trailX = FloatArray(18)
    private val trailY = FloatArray(18)
    private var trailN = 0

    // ---------------- 画笔（全部预分配） ----------------
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    // ---------------- 帧循环 ----------------
    private var running = false
    private var lastNanos = 0L
    private var acc = 0.0
    private val STEP_NS = 1_000_000_000L / 60L

    init {
        setBackgroundColor(C_BG)
        isFocusable = true
    }

    fun start() {
        if (running) return
        sound.init()
        running = true
        lastNanos = 0L
        acc = 0.0
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (lastNanos == 0L) lastNanos = frameTimeNanos
        var d = frameTimeNanos - lastNanos
        lastNanos = frameTimeNanos
        if (d > 100_000_000L) d = 100_000_000L
        acc += d.toDouble()
        var steps = 0
        while (acc >= STEP_NS && steps < 4) {
            tick()
            acc -= STEP_NS.toDouble()
            steps++
        }
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val layW = VW + PANEL * 2f
        scale = min(w / layW, h / VH)
        offX = (w - layW * scale) / 2f
        offY = (h - VH * scale) / 2f
    }

    // ---------------- 输入：相对拖动（手指不挡球拍） ----------------
    private var touching = false
    private var lastVX = 0f
    private var lastVY = 0f

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val vx = (e.x - offX) / scale - PANEL
        val vy = (e.y - offY) / scale
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 标题/结束页右上角：点按开关音效
                val nearSound = vx > VW - 46f && vx < VW - 6f && vy > 6f && vy < 46f   // 收紧到图标本体，防误触
                if ((state == 0 || state == 3) && nearSound) {
                    sound.muted = !sound.muted
                    muteToast = 70
                } else if (state == 0 || state == 3) {
                    startGame()
                } else {
                    touching = true
                    lastVX = vx; lastVY = vy
                }
            }
            MotionEvent.ACTION_MOVE -> if (touching) {
                val dx = vx - lastVX
                val dy = vy - lastVY
                lastVX = vx; lastVY = vy
                player.x = clamp(player.x + dx, TABLE_X + MALLET_R + 2, CX - 10)
                player.y = clamp(player.y + dy, TABLE_Y + MALLET_R + 2, TABLE_Y + TABLE_H - MALLET_R - 2)
                pvx = pvx * 0.4f + dx * 0.6f
                pvy = pvy * 0.4f + dy * 0.6f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touching = false
        }
        return true
    }

    override fun onDetachedFromWindow() {
        sound.release()
        super.onDetachedFromWindow()
    }

    private fun clamp(v: Float, a: Float, b: Float) = max(a, min(b, v))

    // ---------------- 流程 ----------------
    private fun startGame() {
        state = 1
        scoreP[0] = 0; scoreC[0] = 0
        pStreak = 0; cStreak = 0; pTopSpeed = 0; cTopSpeed = 0
        pPower = 0; cPower = 0
        pBestStreak = 0; cBestStreak = 0
        puckSpeedMult = 1f
        lastSpeedUpAt = 0
        sloMo = false; sloMoAlpha = 0f; sloMoIntro = 0; sloMoLabelTimer = 0
        sadFace = 0f
        overT = 0f
        speedUpTimer = 0
        partN = 0; confN = 0
        resetRound(0)
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun resetRound(server: Int) {
        puck.x = CX; puck.y = CY
        puck.vx = if (server == 0) -2.2f else 2.2f
        puck.vy = (Random.nextFloat() - 0.5f) * 1.6f
        player.x = TABLE_X + 110f; player.y = CY
        cpu.x = VW - TABLE_X - 110f; cpu.y = CY
        pvx = 0f; pvy = 0f
        cpuHitCool = 0; cpuMistake = 0
        trailN = 0
    }

    private fun goalScored(who: Int) {
        if (state != 1) return
        state = 2
        goalTimer = 100
        goalWho = who
        goalFlash = 1f
        goalMsgScale = 0f
        if (who == 0) { scoreP[0]++; pStreak++; pBestStreak = max(pBestStreak, pStreak); cStreak = 0 }
        else { scoreC[0]++; cStreak++; cBestStreak = max(cBestStreak, cStreak); pStreak = 0 }

        val total = scoreP[0] + scoreC[0]
        if (total % 2 == 0 && total > lastSpeedUpAt) {
            lastSpeedUpAt = total
            puckSpeedMult = min(puckSpeedMult + 0.14f, 2f)
            val msgs = arrayOf("SPEEDING UP!", "FASTER!!", "KICK IT UP!", "NO MERCY!", "LIGHT SPEED!", "HOLD ON!!")
            speedUpMsg = msgs[min(total / 2 - 1, msgs.size - 1).coerceAtLeast(0)]
            speedUpTimer = 130
            sound.playSpeed()
        }
        val gx = if (who == 0) TABLE_X else VW - TABLE_X
        val gcol = if (who == 0) C_PLAYER else C_CPU
        burst(gx, CY, gcol, 40)
        burst(puck.x, puck.y, C_GOLD, 30)
        shake(8f)
        sound.playGoal()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        // 赛点慢动作（原作：任一方到 MAX_SCORE-1 触发，一局只进一次）
        if ((scoreP[0] == MAX_SCORE - 1 || scoreC[0] == MAX_SCORE - 1) && !sloMo) {
            sloMo = true
            sloMoIntro = 80
            sloMoLabelTimer = 170
        }
    }

    // ---------------- 每帧更新 ----------------
    private fun tick() {
        tick++
        if (goalFlash > 0f) goalFlash = max(0f, goalFlash - 0.04f)
        if (goalMsgScale < 1f) goalMsgScale = min(1f, goalMsgScale + 0.08f)
        if (speedUpTimer > 0) speedUpTimer--
        if (sloMoIntro > 0) sloMoIntro--
        if (sloMoLabelTimer > 0) sloMoLabelTimer--
        if (sloMo) sloMoAlpha = min(1f, sloMoAlpha + 0.055f) else sloMoAlpha = max(0f, sloMoAlpha - 0.07f)
        if (sadFace > 0f) sadFace = max(0f, sadFace - 0.01f)

        if (shakeAmt > 0.3f) {
            shakeX = (Random.nextFloat() - 0.5f) * shakeAmt * 2f
            shakeY = (Random.nextFloat() - 0.5f) * shakeAmt * 2f
            shakeAmt *= 0.72f
        } else { shakeX = 0f; shakeY = 0f; shakeAmt = 0f }

        val ts = if (sloMo) 0.55f else 1f

        if (state == 2) {
            goalTimer--
            if (goalTimer <= 0) {
                if (scoreP[0] >= MAX_SCORE || scoreC[0] >= MAX_SCORE) {
                    state = 3
                    overTick = tick
                    sadFace = if (scoreC[0] >= MAX_SCORE) 1f else 0f
                    burst(CX, CY, C_GOLD, 80)                  // 原作：终局金色爆裂
                    if (scoreP[0] >= MAX_SCORE) { spawnConfetti(80); sound.playWin() } else sound.playLose()
                } else {
                    resetRound(if (goalWho == 0) 1 else 0)
                    state = 1
                }
            }
        }

        if (state == 3) {
            overT = min(1f, overT + 0.045f)
            if (scoreP[0] >= MAX_SCORE) {
                val dt = tick - overTick
                if (dt == 24L || dt == 48L) spawnConfetti(80)   // 原作：0/400/800ms 三波彩带
            }
        }
        if (state == 1) {
            updateCPU(ts)
            updatePuck(ts)
            updateParticles(ts)
        } else if (state == 2) {
            updateParticles(ts)
        }
        updateConfetti()
    }

    // ---------------- 玩家 / CPU ----------------
    // （玩家位置由触摸直接驱动；此处只做速度衰减，供碰撞使用）
    private fun updatePlayer(ts: Float) {
        pvx *= 0.86f * ts
        pvy *= 0.86f * ts
    }

    private fun updateCPU(ts: Float) {
        val halfW = VW / 2
        val homeX = VW - TABLE_X - 110f
        val minX = halfW + 10f
        val maxX = VW - TABLE_X - MALLET_R - 2f
        val minY = TABLE_Y + MALLET_R + 2f
        val maxY = TABLE_Y + TABLE_H - MALLET_R - 2f

        if (Random.nextFloat() < CPU_MISTAKE_CHANCE && cpuMistake == 0 && puck.vx > 0f) {
            cpuMistake = CPU_MISTAKE_DUR
            cpuErrY = (Random.nextFloat() - 0.5f) * CPU_ERROR_Y * 2f
        }
        if (cpuMistake > 0) cpuMistake--
        if (cpuHitCool > 0) cpuHitCool--

        val err = if (cpuMistake > 0) cpuErrY else 0f
        val puckOnMySide = puck.x > halfW
        val puckToMe = puck.vx > 0f

        val nearTop = cpu.y < minY + 20f
        val nearBottom = cpu.y > maxY - 20f
        val nearSide = cpu.x > maxX - 20f
        val cornered = (nearTop || nearBottom) && nearSide
        val farHome = hypot((cpu.x - homeX).toDouble(), (cpu.y - CY).toDouble()).toFloat() > 150f

        var tx: Float
        var ty: Float
        if (cornered || (farHome && !puckToMe)) {
            tx = homeX; ty = CY
        } else if (puckOnMySide && puckToMe) {
            val frames = max(1f, min((cpu.x - puck.x) / max(0.5f, puck.vx), 60f))
            tx = clamp(puck.x + puck.vx * frames * CPU_REACT, minX, maxX)
            ty = clamp(puck.y + puck.vy * frames * CPU_REACT + err, minY, maxY)
        } else if (puckOnMySide) {
            tx = clamp(puck.x - 8f, minX, maxX - 30f)
            ty = clamp(puck.y + err, minY, maxY)
        } else {
            tx = homeX
            ty = clamp(puck.y * 0.5f + CY * 0.5f + err * 0.3f, minY, maxY)
        }

        val px = cpu.x; val py = cpu.y
        val dx = tx - cpu.x; val dy = ty - cpu.y
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dist > 0.1f) {
            val step = min(dist, CPU_SPEED * ts)
            cpu.x += dx / dist * step
            cpu.y += dy / dist * step
        }
        cpu.x = clamp(cpu.x, minX, maxX)
        cpu.y = clamp(cpu.y, minY, maxY)
        cpu.vx = cpu.x - px
        cpu.vy = cpu.y - py
    }

    // ---------------- 冰球 ----------------
    private fun updatePuck(ts: Float) {
        updatePlayer(ts)
        if (ts != 1f) { puck.vx *= ts; puck.vy *= ts }

        val spd = hypot(puck.vx.toDouble(), puck.vy.toDouble()).toFloat()
        // 拖尾
        if (trailN < 18) trailN++ else {
            for (i in 0 until 17) { trailX[i] = trailX[i + 1]; trailY[i] = trailY[i + 1] }
        }
        trailX[trailN - 1] = puck.x; trailY[trailN - 1] = puck.y

        if (spd < 0.8f) {
            puck.vx += (Random.nextFloat() - 0.5f) * 0.18f
            puck.vy += (Random.nextFloat() - 0.5f) * 0.18f
        } else if (spd < 2.5f) {
            puck.vx += (Random.nextFloat() - 0.5f) * 0.06f
            puck.vy += (Random.nextFloat() - 0.5f) * 0.06f
        }

        puck.x += puck.vx
        puck.y += puck.vy
        puck.vx *= FRICTION
        puck.vy *= FRICTION

        val tx = TABLE_X; val ty = TABLE_Y; val tw = TABLE_W; val th = TABLE_H
        if (puck.y - puck.r < ty) {
            puck.y = ty + puck.r; puck.vy = abs(puck.vy) * WALL_BOUNCE
            spark(puck.x, ty, C_PLAYER)
        }
        if (puck.y + puck.r > ty + th) {
            puck.y = ty + th - puck.r; puck.vy = -abs(puck.vy) * WALL_BOUNCE
            spark(puck.x, ty + th, C_PLAYER)
        }
        if (puck.x - puck.r < tx) {
            if (puck.y > GOAL_Y1 && puck.y < GOAL_Y2) { goalScored(1); return }
            puck.x = tx + puck.r; puck.vx = abs(puck.vx) * WALL_BOUNCE
            spark(tx, puck.y, C_CPU)
        }
        if (puck.x + puck.r > tx + tw) {
            if (puck.y > GOAL_Y1 && puck.y < GOAL_Y2) { goalScored(0); return }
            puck.x = tx + tw - puck.r; puck.vx = -abs(puck.vx) * WALL_BOUNCE
            spark(tx + tw, puck.y, C_CPU)
        }

        collide(puck, player, true)
        collide(puck, cpu, false)

        if (ts != 1f && state == 1) { puck.vx /= ts; puck.vy /= ts }
    }

    private fun collide(pk: Body, mallet: Body, isPlayer: Boolean) {
        val dx = pk.x - mallet.x
        val dy = pk.y - mallet.y
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val minDist = pk.r + mallet.r
        if (dist >= minDist || dist < 0.01f) return

        if (!isPlayer && cpuHitCool > 0) {
            val nx2 = dx / dist; val ny2 = dy / dist
            pk.x += nx2 * (minDist - dist)
            pk.y += ny2 * (minDist - dist)
            return
        }

        val nx = dx / dist; val ny = dy / dist
        pk.x += nx * (minDist - dist)
        pk.y += ny * (minDist - dist)

        val mvx = if (isPlayer) pvx * 1.8f else mallet.vx
        val mvy = if (isPlayer) pvy * 1.8f else mallet.vy
        val relVX = pk.vx - mvx
        val relVY = pk.vy - mvy
        val dot = relVX * nx + relVY * ny
        if (dot >= 0f) return

        val restitution = if (isPlayer) 1.3f else 1.1f
        val impulse = -(1f + restitution) * dot
        pk.vx += impulse * nx
        pk.vy += impulse * ny

        var spd = hypot(pk.vx.toDouble(), pk.vy.toDouble()).toFloat()
        val cap = (if (isPlayer) 20f else 16f) * puckSpeedMult
        if (spd > cap) { pk.vx = pk.vx / spd * cap; pk.vy = pk.vy / spd * cap; spd = cap }
        if (!isPlayer) cpuHitCool = 20

        val mph = Math.round(spd * 4f)
        if (isPlayer) { if (mph > pTopSpeed) pTopSpeed = mph } else { if (mph > cTopSpeed) cTopSpeed = mph }
        if (spd > 14f) { if (isPlayer) pPower++ else cPower++ }   // POWER HITS（原作阈值 14）

        if (spd > 3f) {
            burst(pk.x, pk.y, if (isPlayer) C_PLAYER else C_CPU, min((spd * 1.5f).toInt(), 40))
            sound.playHit(spd)
            if (isPlayer) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (spd > 19f) shake(min((spd - 19f) * 0.4f, 3f))
    }

    // ---------------- 特效 ----------------
    private fun burst(x: Float, y: Float, color: Int, n: Int) {
        var i = 0
        while (i < n && partN < MAX_PART) {
            val p = parts[partN++]
            val a = Random.nextFloat() * 6.2832f
            val v = 1f + Random.nextFloat() * 5f
            p.x = x; p.y = y
            p.vx = kotlin.math.cos(a) * v; p.vy = kotlin.math.sin(a) * v
            p.t = 0f
            p.life = 18f + Random.nextFloat() * 22f
            p.size = 1.5f + Random.nextFloat() * 2.5f
            p.color = color
            i++
        }
    }

    private fun spark(x: Float, y: Float, color: Int) {
        sound.playWall()
        var i = 0
        while (i < 6 && partN < MAX_PART) {
            val p = parts[partN++]
            p.x = x; p.y = y
            p.vx = (Random.nextFloat() - 0.5f) * 4f
            p.vy = (Random.nextFloat() - 0.5f) * 4f
            p.t = 0f
            p.life = 8f + Random.nextFloat() * 8f
            p.size = 1.2f + Random.nextFloat() * 1.6f
            p.color = color
            i++
        }
    }

    private fun updateParticles(ts: Float) {
        var i = 0
        while (i < partN) {
            val p = parts[i]
            p.t += ts
            p.x += p.vx * ts
            p.y += p.vy * ts
            p.vx *= 0.96f; p.vy *= 0.96f
            if (p.t >= p.life) {
                partN--
                val tmp = parts[i]; parts[i] = parts[partN]; parts[partN] = tmp
            } else i++
        }
    }

    private fun spawnConfetti(n: Int) {
        val cols = intArrayOf(C_PLAYER, C_CPU, C_GOLD, Color.WHITE)
        var i = 0
        while (i < n && confN < MAX_CONF) {
            val c = confs[confN++]
            c.x = Random.nextFloat() * VW
            c.y = -20f - Random.nextFloat() * 200f
            c.vx = (Random.nextFloat() - 0.5f) * 2f
            c.vy = 1.5f + Random.nextFloat() * 2.5f
            c.rot = 0f
            c.vr = (Random.nextFloat() - 0.5f) * 0.3f
            c.w = 5f + Random.nextFloat() * 5f
            c.h = 8f + Random.nextFloat() * 8f
            c.color = cols[i % cols.size]
            i++
        }
    }

    private fun updateConfetti() {
        var i = 0
        while (i < confN) {
            val c = confs[i]
            c.x += c.vx; c.y += c.vy; c.rot += c.vr; c.vy += 0.02f
            if (c.y > VH + 30f) {
                confN--
                val tmp = confs[i]; confs[i] = confs[confN]; confs[confN] = tmp
            } else i++
        }
    }

    private fun shake(a: Float) { shakeAmt = max(shakeAmt, a) }

    // ---------------- 绘制 ----------------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(C_BG)
        canvas.save()
        canvas.translate(offX, offY)
        canvas.scale(scale, scale)
        drawPanels(canvas)
        canvas.save()
        canvas.translate(PANEL + shakeX, shakeY)
        drawTable(canvas)
        drawParticles(canvas)
        drawTrailAndPuck(canvas)
        drawMallet(canvas, cpu, C_CPU)
        drawMallet(canvas, player, C_PLAYER)
        drawGoalFlash(canvas)
        drawSpeedUpMsg(canvas)
        drawConfetti(canvas)
        if (state == 0) drawReady(canvas)
        if (state == 0 || state == 3) drawSoundIcon(canvas)
        if (state == 3) drawOver(canvas)
        if (sadFace > 0f) drawSadFace(canvas)
        if (sloMoAlpha > 0f) drawVignette(canvas)
        canvas.restore()
        canvas.restore()
    }

    private fun glowAt(canvas: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Float) {
        glowPaint.shader = RadialGradient(
            x, y, r,
            Color.argb((90 * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color)),
            Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, r, glowPaint)
    }

    private fun drawTable(canvas: Canvas) {
        fill.shader = LinearGradient(
            0f, TABLE_Y, 0f, TABLE_Y + TABLE_H,
            Color.parseColor("#0b1220"), Color.parseColor("#060a12"), Shader.TileMode.CLAMP
        )
        rect.set(TABLE_X, TABLE_Y, TABLE_X + TABLE_W, TABLE_Y + TABLE_H)
        canvas.drawRoundRect(rect, 22f, 22f, fill)
        fill.shader = null   // 切回纯色模式（后面还要用 fill 画纯色）

        line.color = Color.parseColor("#1d2c40"); line.strokeWidth = 2.5f
        canvas.drawRoundRect(rect, 22f, 22f, line)

        // 中线 + 中圈
        line.color = Color.parseColor("#14202f"); line.strokeWidth = 2f
        canvas.drawLine(CX, TABLE_Y + 6f, CX, TABLE_Y + TABLE_H - 6f, line)
        canvas.drawCircle(CX, CY, 70f, line)
        canvas.drawCircle(CX, CY, 6f, fill.apply { color = Color.parseColor("#1d2c40") })

        // 球门（左=玩家青 / 右=CPU 红）
        line.strokeWidth = 6f
        line.color = C_PLAYER
        canvas.drawLine(TABLE_X, GOAL_Y1, TABLE_X, GOAL_Y2, line)
        line.color = C_CPU
        canvas.drawLine(TABLE_X + TABLE_W, GOAL_Y1, TABLE_X + TABLE_W, GOAL_Y2, line)

        // GOAL 字样
        text.color = Color.parseColor("#1b2a3d"); text.textSize = 15f
        canvas.save()
        canvas.rotate(-90f, TABLE_X + 18f, CY)
        canvas.drawText("GOAL", TABLE_X + 18f, CY + 5f, text)
        canvas.restore()
        canvas.save()
        canvas.rotate(90f, TABLE_X + TABLE_W - 18f, CY)
        canvas.drawText("GOAL", TABLE_X + TABLE_W - 18f, CY + 5f, text)
        canvas.restore()
    }

    private fun drawParticles(canvas: Canvas) {
        var pi = 0
        while (pi < partN) {
            val p = parts[pi]
            pi++
            val a = max(0f, 1f - p.t / p.life)
            fill.color = p.color
            fill.alpha = (a * 220).toInt()
            canvas.drawCircle(p.x, p.y, p.size * (0.4f + a * 0.6f), fill)
        }
        fill.alpha = 255
    }

    private fun drawTrailAndPuck(canvas: Canvas) {
        // 拖尾
        var i = 0
        while (i < trailN - 1) {
            val a = (i.toFloat() / trailN) * 0.35f
            fill.color = C_PLAYER
            fill.alpha = (a * 255).toInt()
            canvas.drawCircle(trailX[i], trailY[i], PUCK_R * (0.35f + 0.55f * i / trailN), fill)
            i++
        }
        fill.alpha = 255
        glowAt(canvas, puck.x, puck.y, PUCK_R * 3.2f, Color.WHITE, 0.8f)
        fill.color = Color.WHITE
        canvas.drawCircle(puck.x, puck.y, PUCK_R, fill)
        fill.color = Color.parseColor("#9fd8ff")
        canvas.drawCircle(puck.x, puck.y, PUCK_R * 0.55f, fill)
    }

    private fun drawMallet(canvas: Canvas, m: Body, color: Int) {
        glowAt(canvas, m.x, m.y, m.r * 2.6f, color, 0.9f)
        fill.color = Color.parseColor("#0a0f16")
        canvas.drawCircle(m.x, m.y, m.r, fill)
        line.color = color; line.strokeWidth = 5f
        canvas.drawCircle(m.x, m.y, m.r - 2f, line)
        fill.color = color
        canvas.drawCircle(m.x, m.y, m.r * 0.28f, fill)
    }

    /** 进球闪现（1:1：色纱 + 发光 GOAL! + 小字 YOU SCORE / CPU SCORES） */
    private fun drawGoalFlash(canvas: Canvas) {
        if (goalFlash <= 0f) return
        val col = if (goalWho == 0) C_PLAYER else C_CPU
        fill.color = col
        fill.alpha = (goalFlash * 60).toInt()
        canvas.drawRect(0f, 0f, VW, VH, fill)
        fill.alpha = 255
        val ease = 1f - (1f - goalMsgScale) * (1f - goalMsgScale) * (1f - goalMsgScale)
        canvas.save()
        canvas.translate(CX, CY)
        canvas.scale(ease, ease)
        text.textAlign = Paint.Align.CENTER
        text.letterSpacing = 0f
        text.textSize = 64f
        text.color = col
        text.setShadowLayer(40f, 0f, 0f, col)
        canvas.drawText("GOAL!", 0f, -10f, text)
        text.setShadowLayer(0f, 0f, 0f, 0)
        text.letterSpacing = 0.45f
        text.textSize = 13f
        text.color = col
        text.alpha = 190
        canvas.drawText(if (goalWho == 0) "YOU SCORE" else "CPU SCORES", 0f, 22f, text)
        text.alpha = 255
        canvas.restore()
    }

    /** 提速提示（1:1：Slam 入场 + 黑描边 + 金橙渐变 + 辉光） */
    private fun drawSpeedUpMsg(canvas: Canvas) {
        if (speedUpTimer <= 0) return
        val t = speedUpTimer / 130f
        val scale = if (t > 0.85f) 0.5f + (1f - (t - 0.85f) / 0.15f) * 0.5f else 1f
        val alpha = if (t < 0.2f) t / 0.2f else 1f
        canvas.save()
        canvas.translate(CX, CY - 60f)
        canvas.scale(scale, scale)
        text.textAlign = Paint.Align.CENTER
        text.letterSpacing = 0f
        text.textSize = 34f
        text.alpha = (alpha * 255).toInt()
        text.color = Color.BLACK
        canvas.drawText(speedUpMsg, 2f, 2f, text)          // 粗黑描边
        text.shader = LinearGradient(
            -100f, -30f, 100f, 10f, C_GOLD, 0xFFFF6820.toInt(), Shader.TileMode.CLAMP
        )
        text.setShadowLayer(24f, 0f, 0f, C_GOLD)
        canvas.drawText(speedUpMsg, 0f, 0f, text)
        text.setShadowLayer(0f, 0f, 0f, 0)
        text.shader = null
        text.alpha = 255
        canvas.restore()
    }

    private fun drawConfetti(canvas: Canvas) {
        var ci = 0
        while (ci < confN) {
            val c = confs[ci]
            ci++
            canvas.save()
            canvas.rotate(c.rot * 57.3f, c.x, c.y)
            fill.color = c.color
            canvas.drawRect(c.x - c.w / 2, c.y - c.h / 2, c.x + c.w / 2, c.y + c.h / 2, fill)
            canvas.restore()
        }
    }

    /** 两侧数据面板（1:1 复刻原作 #stat-left/#stat-right） */
    private fun drawPanels(canvas: Canvas) {
        fill.color = Color.parseColor("#080D14")
        rect.set(0f, 0f, PANEL, VH); canvas.drawRoundRect(rect, 16f, 16f, fill)
        rect.set(VW + PANEL, 0f, VW + PANEL * 2f, VH); canvas.drawRoundRect(rect, 16f, 16f, fill)
        line.color = 0x0DFFFFFF; line.strokeWidth = 1f
        rect.set(0f, 0f, PANEL, VH); canvas.drawRoundRect(rect, 16f, 16f, line)
        rect.set(VW + PANEL, 0f, VW + PANEL * 2f, VH); canvas.drawRoundRect(rect, 16f, 16f, line)
        panel(canvas, 0f, C_PLAYER, "YOU", scoreP[0], pStreak, pTopSpeed, pPower)
        panel(canvas, VW + PANEL, C_CPU, "CPU", scoreC[0], cStreak, cTopSpeed, cPower)
    }

    private fun panel(canvas: Canvas, x0: Float, col: Int, name: String,
                      score: Int, streak: Int, top: Int, power: Int) {
        val cx = x0 + PANEL / 2f
        text.textAlign = Paint.Align.CENTER
        text.letterSpacing = 0.4f; text.textSize = 10f; text.color = col
        canvas.drawText(name, cx, 30f, text)
        text.letterSpacing = -0.04f; text.textSize = 52f
        text.setShadowLayer(20f, 0f, 0f, col)          // 比分辉光
        canvas.drawText(score.toString(), cx, 88f, text)
        text.setShadowLayer(0f, 0f, 0f, 0)
        line.color = 0x12FFFFFF; line.strokeWidth = 1f
        canvas.drawLine(x0 + 10f, 104f, x0 + PANEL - 10f, 104f, line)
        row(canvas, x0, 128f, "STREAK", streak.toString())
        row(canvas, x0, 152f, "TOP SPEED", top.toString())
        row(canvas, x0, 176f, "POWER HITS", power.toString())
        text.textAlign = Paint.Align.CENTER
        text.letterSpacing = 0.2f; text.textSize = 9f; text.color = 0x33FFFFFF
        canvas.drawText("FIRST TO 7 WINS", cx, 204f, text)
    }

    private fun row(canvas: Canvas, x0: Float, y: Float, label: String, value: String) {
        text.textAlign = Paint.Align.LEFT
        text.letterSpacing = 0.15f; text.textSize = 9f; text.color = 0x4DFFFFFF
        canvas.drawText(label, x0 + 12f, y, text)
        text.textAlign = Paint.Align.RIGHT
        text.letterSpacing = 0f; text.textSize = 12f; text.color = 0xBFFFFFFF.toInt()
        canvas.drawText(value, x0 + PANEL - 12f, y, text)
    }

    private var muteToast = 0

    private fun drawSoundIcon(canvas: Canvas) {
        text.textSize = 22f
        text.textAlign = Paint.Align.RIGHT
        text.color = if (sound.muted) Color.parseColor("#5a6678") else C_PLAYER
        canvas.drawText(if (sound.muted) "🔇" else "🔊", VW - 16f, 32f, text)
        if (muteToast > 0) {   // 切换音效的可见反馈（免得"以为误触关了"却不知道）
            text.textSize = 16f
            text.textAlign = Paint.Align.CENTER
            text.color = if (sound.muted) 0xFF9AA4B2.toInt() else C_PLAYER
            text.alpha = (muteToast / 70f * 255f).toInt().coerceIn(0, 255)
            canvas.drawText(if (sound.muted) "🔇 已静音" else "🔊 声音开启", CX, 84f, text)
            text.alpha = 255
        }
    }

    /** Ready 屏（1:1 复刻原作 .ready-*：白色 AIR + 蓝辉光 HOCKEY + 金副标 + 呼吸提示） */
    private fun drawReady(canvas: Canvas) {
        fill.color = Color.argb(102, 4, 6, 10)
        canvas.drawRect(0f, 0f, VW, VH, fill)
        text.textAlign = Paint.Align.CENTER
        text.letterSpacing = 0f
        text.textSize = 58f; text.color = Color.WHITE
        canvas.drawText("AIR", CX, 218f, text)
        text.color = C_PLAYER
        text.setShadowLayer(30f, 0f, 0f, C_PLAYER)
        canvas.drawText("HOCKEY", CX, 278f, text)
        text.setShadowLayer(0f, 0f, 0f, 0)
        text.letterSpacing = 0.85f; text.textSize = 12f; text.color = C_GOLD
        canvas.drawText("FIRST TO 7 WINS", CX, 310f, text)
        val a = 0.4f + 0.6f * (0.5f + 0.5f * kotlin.math.sin(tick * 0.045f))
        text.letterSpacing = 0.3f; text.textSize = 14f; text.color = Color.WHITE
        text.alpha = (a * 255).toInt()
        canvas.drawText("TAP TO PLAY", CX, 358f, text)
        text.alpha = 255
        text.letterSpacing = 0.12f; text.textSize = 11f; text.color = 0x38FFFFFF
        canvas.drawText("拖动球拍 · 先到 7 分 · 返回键退出", CX, 392f, text)
    }

    /** 结算屏（1:1 复刻原作 #gameover-screen：表情 + 发光标题 + 副标 + 金比分 + PLAY AGAIN 切角钮） */
    private fun drawOver(canvas: Canvas) {
        val win = scoreP[0] >= MAX_SCORE
        // 胜 = 轻纱；败 = 暗红底（原作 .lose-state: rgba(20,4,8,.9)）
        fill.color = if (win) Color.argb((102 * overT).toInt(), 4, 6, 10)
        else Color.argb((230 * overT).toInt(), 20, 4, 8)
        canvas.drawRect(0f, 0f, VW, VH, fill)
        val k = 0.78f + 0.22f * overT                    // 落定动画
        text.textAlign = Paint.Align.CENTER
        text.letterSpacing = 0f
        canvas.save(); canvas.translate(CX, 150f); canvas.scale(k, k)
        text.textSize = 60f
        canvas.drawText(if (win) "😄" else "😢", 0f, 0f, text)
        canvas.restore()
        val col = if (win) C_PLAYER else C_CPU
        text.textSize = 54f; text.color = col
        text.setShadowLayer(28f, 0f, 0f, col)
        canvas.save(); canvas.translate(CX, 232f); canvas.scale(k, k)
        canvas.drawText(if (win) "YOU WIN" else "CPU WINS", 0f, 0f, text)
        canvas.restore()
        text.setShadowLayer(0f, 0f, 0f, 0)
        text.letterSpacing = 0.7f; text.textSize = 16f; text.color = 0x99FFFFFF.toInt()
        canvas.drawText(if (win) "GAME · SET · MATCH" else "BETTER LUCK NEXT TIME", CX, 272f, text)
        text.letterSpacing = 0f; text.textSize = 30f; text.color = C_GOLD
        text.setShadowLayer(20f, 0f, 0f, C_GOLD)
        canvas.drawText("${scoreP[0]} – ${scoreC[0]}", CX, 322f, text)
        text.setShadowLayer(0f, 0f, 0f, 0)
        drawPlayAgain(canvas)
    }

    private fun drawPlayAgain(canvas: Canvas) {
        val w = 240f; val h = 50f; val cut = 12f; val cy = 378f
        path.reset()
        path.moveTo(CX - w / 2 + cut, cy - h / 2)
        path.lineTo(CX + w / 2, cy - h / 2)
        path.lineTo(CX + w / 2 - cut, cy + h / 2)
        path.lineTo(CX - w / 2, cy + h / 2)
        path.close()
        line.color = C_PLAYER; line.strokeWidth = 2f
        canvas.drawPath(path, line)
        text.letterSpacing = 0.3f; text.textSize = 13f; text.color = C_PLAYER
        canvas.drawText("PLAY AGAIN", CX, cy + 5f, text)
    }

    private fun drawSadFace(canvas: Canvas) {
        val r = 52f
        glowAt(canvas, CX, CY - 30f, r * 2.2f, C_CPU, sadFace)
        fill.color = Color.parseColor("#1a0a0a")
        canvas.drawCircle(CX, CY - 30f, r, fill)
        line.color = C_CPU; line.strokeWidth = 3f
        canvas.drawCircle(CX, CY - 30f, r, line)
        line.strokeWidth = 4f
        // X 眼
        canvas.drawLine(CX - 26f, CY - 44f, CX - 12f, CY - 30f, line)
        canvas.drawLine(CX - 12f, CY - 44f, CX - 26f, CY - 30f, line)
        canvas.drawLine(CX + 12f, CY - 44f, CX + 26f, CY - 30f, line)
        canvas.drawLine(CX + 26f, CY - 44f, CX + 12f, CY - 30f, line)
        // 撇嘴
        path.reset()
        path.moveTo(CX - 22f, CY - 6f)
        path.quadTo(CX, CY - 20f, CX + 22f, CY - 6f)
        canvas.drawPath(path, line)
    }

    /**
     * 赛点电影感叠加（原作同名效果；压暗幅度按用户钦定收敛：边缘 65%→15%，
     * letterbox 条 0.88→0.20，只留"一点压迫感"，不压屏）。
     */
    private fun drawVignette(canvas: Canvas) {
        val a = sloMoAlpha
        if (a > 0f) {
            glowPaint.shader = RadialGradient(
                CX, CY, VH * 0.75f,
                Color.argb(0, 0, 0, 0),
                Color.argb((64 * a).toInt(), 0, 0, 0),          // 0.25 × 255 = 64（用户钦定 -25%）（用户钦定的轻压暗）
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, VW, VH, glowPaint)
            fill.color = Color.argb((64 * a).toInt(), 0, 0, 0)   // 0.25 × 255 = 64
            canvas.drawRect(0f, 0f, VW, 24f * a, fill)
            canvas.drawRect(0f, VH - 24f * a, VW, VH, fill)
        }
        if (sloMoLabelTimer > 0) {
            val fadeIn = min(sloMoLabelTimer / 20f, 1f)
            val fadeOut = if (sloMoLabelTimer < 30) sloMoLabelTimer / 30f else 1f
            val pulse = 0.88f + kotlin.math.sin(tick * 0.12f) * 0.12f
            text.textAlign = Paint.Align.CENTER
            text.letterSpacing = 0f
            text.textSize = 18f
            text.color = C_GOLD
            text.setShadowLayer(14f, 0f, 0f, C_GOLD)
            text.alpha = (255 * min(fadeIn, fadeOut) * pulse * max(a, 0.2f)).coerceIn(0f, 255f).toInt()
            canvas.drawText("⚡  GAME POINT  ⚡", CX, 52f, text)
            text.alpha = 255
            text.setShadowLayer(0f, 0f, 0f, 0)
        }
    }
}
