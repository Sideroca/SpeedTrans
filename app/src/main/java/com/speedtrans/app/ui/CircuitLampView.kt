package com.speedtrans.app.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathEffect
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.min

/**
 * 测试连接小灯泡（实验室电路板风）：
 * 左右蜿蜒电线 + 中央灯泡；动漫厚描边。
 * 状态：IDLE 熄灭 / TESTING 电流流动 / OK 白→黄→绿闪光后常亮 / FAIL 暗红两闪。
 */
class CircuitLampView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class State { IDLE, TESTING, OK, FAIL }

    private var state = State.IDLE

    private val wire = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2.5f
    }
    private val bulbFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bulbEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val filament = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.6f
    }
    private val rays = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2f
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    private var flow = 0f
    private var flash = 1f
    // 预分配，避免 onDraw 每帧分配
    private val dashIntervals = FloatArray(2)
    private val wirePathCache = Path()
    private val filamentPath = Path()
    private val baseRect = RectF()
    private var glowGradOn: LinearGradient? = null
    private var glowGradOff: LinearGradient? = null
    private var flowAnim: ValueAnimator? = null
    private var flashAnim: ValueAnimator? = null
    private val embers = ArrayList<Ember>()

    private class Ember(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var t: Float, val life: Float, val r: Float, val color: Int
    )

    private val emberColors = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFFFE97C.toInt(), 0xFF7CE87C.toInt()
    )

    fun setState(s: State) {
        state = s
        flowAnim?.cancel(); flowAnim = null
        flashAnim?.cancel(); flashAnim = null
        if (s != State.OK) embers.clear()
        when (s) {
            State.TESTING -> {
                flow = 0f
                flowAnim = ValueAnimator.ofFloat(0f, 44f).apply {
                    duration = 700
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    addUpdateListener { flow = it.animatedValue as Float; invalidate() }
                    start()
                }
            }
            State.OK -> {
                flash = 0f
                val bw = width.toFloat(); val bh = height.toFloat()
                if (bw > 0f && bh > 0f) {
                    val br = min(bh * 0.30f, bw * 0.13f)
                    val bx = bw * 0.66f; val by = bh * 0.46f
                    repeat(8) { i ->
                        embers.add(
                            Ember(
                                bx + (Math.random().toFloat() - 0.5f) * br,
                                by - br * (0.9f + Math.random().toFloat() * 0.3f),
                                (Math.random().toFloat() - 0.5f) * 60f,
                                -(50f + Math.random().toFloat() * 90f),
                                0f, 0.45f + Math.random().toFloat() * 0.5f,
                                1.5f + Math.random().toFloat() * 2.5f,
                                emberColors[i % emberColors.size]
                            )
                        )
                    }
                }
                flashAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1200
                    addUpdateListener { a ->
                        val v = a.animatedValue as Float
                        val dt = (v - flash) * 1.2f
                        flash = v
                        val it2 = embers.iterator()
                        while (it2.hasNext()) {
                            val e = it2.next()
                            e.t += dt
                            e.x += e.vx * dt
                            e.y += e.vy * dt
                            e.vy += 60f * dt
                            if (e.t >= e.life) it2.remove()
                        }
                        invalidate()
                    }
                    start()
                }
            }
            State.FAIL -> {
                flash = 0f
                flashAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 900
                    addUpdateListener { flash = it.animatedValue as Float; invalidate() }
                    start()
                }
            }
            else -> invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || h <= 0) {
            glowGradOn = null; glowGradOff = null; return
        }
        val bulbR = min(h * 0.30f, w * 0.13f)
        val bulbX = w * 0.66f
        val bulbY = h * 0.46f
        glowGradOn = LinearGradient(
            bulbX - bulbR, bulbY - bulbR, bulbX + bulbR, bulbY + bulbR,
            Color.TRANSPARENT, 0x331EA5C7.toInt(), Shader.TileMode.CLAMP
        )
        glowGradOff = LinearGradient(
            bulbX - bulbR, bulbY - bulbR, bulbX + bulbR, bulbY + bulbR,
            Color.TRANSPARENT, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
    }

    override fun onDetachedFromWindow() {
        flowAnim?.cancel(); flashAnim?.cancel()
        flowAnim = null; flashAnim = null
        super.onDetachedFromWindow()
    }

    private fun wirePath(w: Float, h: Float, bulbX: Float, bulbR: Float): Path {
        val cy = h * 0.5f
        val p = wirePathCache
        p.reset()
        // 左侧主线：三段交替起伏（“折叠/水浸”感）
        val startX = 0f
        val endX = bulbX - bulbR * 1.25f
        p.moveTo(startX, cy)
        val loops = 3
        val seg = (endX - startX) / loops
        for (i in 0 until loops) {
            val x0 = startX + i * seg
            val up = if (i % 2 == 0) -1f else 1f
            p.cubicTo(
                x0 + seg * 0.22f, cy + up * h * 0.34f,
                x0 + seg * 0.78f, cy + up * h * 0.34f,
                x0 + seg, cy
            )
        }
        // 右侧短线：一个起伏出画
        p.moveTo(bulbX + bulbR * 1.25f, cy)
        p.cubicTo(
            bulbX + bulbR * 1.25f + (w - bulbX) * 0.3f, cy - h * 0.30f,
            bulbX + bulbR * 1.25f + (w - bulbX) * 0.6f, cy + h * 0.28f,
            w, cy
        )
        return p
    }

    @SuppressLint("DrawAllocation")  // DashPathEffect 相位每帧变化必须重建；仅连接测试 ~2s 内运行
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        val bulbR = min(h * 0.30f, w * 0.13f)
        val bulbX = w * 0.66f
        val bulbY = h * 0.46f

        val path = wirePath(w, h, bulbX, bulbR)
        when (state) {
            State.TESTING -> {
                wire.color = 0xB31EA5C7.toInt()
                dashIntervals[0] = 9f * d
                dashIntervals[1] = 8f * d
                wire.pathEffect = DashPathEffect(dashIntervals, -flow * d)
            }
            State.OK -> {
                wire.color = 0xFF7CE87C.toInt()
                wire.pathEffect = null
            }
            State.FAIL -> {
                wire.color = 0xFF8A4040.toInt()
                wire.pathEffect = null
            }
            else -> {
                wire.color = 0xFF565C66.toInt()
                wire.pathEffect = null
            }
        }
        canvas.drawPath(path, wire)

        // 灯泡玻璃
        val lit = state == State.OK
        val baseFill = when (state) {
            State.OK -> lerpColor(0xFFEAF2FF.toInt(), 0xFFFFD54F.toInt(), min(1f, flash * 2f))
            State.FAIL -> blink(flash, 0xFF3A2222.toInt(), 0xFF7A3030.toInt())
            State.TESTING -> 0xFF2A3038.toInt()
            else -> 0xFF20242B.toInt()
        }
        val finalFill = if (state == State.OK && flash > 0.6f)
            lerpColor(0xFFFFD54F.toInt(), 0xFF7CE87C.toInt(), (flash - 0.6f) / 0.4f)
        else baseFill
        glow.shader = if (lit || state == State.OK) glowGradOn else glowGradOff
        bulbFill.color = finalFill
        bulbEdge.color = when (state) {
            State.OK -> 0xFF7CE87C.toInt()
            State.FAIL -> 0xFFB06060.toInt()
            State.TESTING -> 0xFF1EA5C7.toInt()
            else -> 0xFF494F58.toInt()
        }
        // 呼吸光晕
        canvas.drawCircle(bulbX, bulbY, bulbR * (1.5f + if (state == State.OK) abs(flash - 0.5f) else 0f), glow)
        canvas.drawCircle(bulbX, bulbY, bulbR, bulbFill)
        canvas.drawCircle(bulbX, bulbY, bulbR, bulbEdge)

        // 灯丝（小锯齿）
        filament.color = if (lit || state == State.OK) 0xFFFFF3C4.toInt() else 0xFF565C66.toInt()
        val fw = bulbR * 0.9f
        filamentPath.reset()
        filamentPath.moveTo(bulbX - fw / 2, bulbY + bulbR * 0.35f)
        filamentPath.lineTo(bulbX - fw / 6, bulbY)
        filamentPath.lineTo(bulbX + fw / 6, bulbY + bulbR * 0.35f)
        filamentPath.lineTo(bulbX + fw / 2, bulbY)
        canvas.drawPath(filamentPath, filament)

        // 螺口底座
        bulbEdge.color = 0xFF494F58.toInt()
        baseRect.set(bulbX - bulbR * 0.55f, bulbY + bulbR * 0.85f, bulbX + bulbR * 0.55f, bulbY + bulbR * 1.5f)
        canvas.drawRect(baseRect, bulbFill.apply { color = 0xFF333941.toInt() })
        canvas.drawRect(baseRect, bulbEdge)

        // 成功光芒：8 根放射短线，随闪光收束
        if (state == State.OK && flash < 1f) {
            rays.color = Color.argb(((1f - flash) * 220).toInt(), 0xFF, 0xE9, 0x7C)
            for (i in 0 until 8) {
                val ang = Math.toRadians((i * 45).toDouble())
                val r0 = bulbR * (1.45f + flash * 0.5f)
                val r1 = r0 + bulbR * 0.55f * (1f - flash)
                canvas.drawLine(
                    bulbX + (r0 * kotlin.math.cos(ang)).toFloat(),
                    bulbY + (r0 * kotlin.math.sin(ang)).toFloat(),
                    bulbX + (r1 * kotlin.math.cos(ang)).toFloat(),
                    bulbY + (r1 * kotlin.math.sin(ang)).toFloat(),
                    rays
                )
            }
        }

        // 余烬（OK 闪光喷出，向上飘散，白/黄/绿三色）
        if (state == State.OK && embers.isNotEmpty()) {
            for (e in embers) {
                val k = (e.t / e.life).coerceIn(0f, 1f)
                rays.color = (((1f - k) * 210).toInt() shl 24) or (e.color and 0x00FFFFFF)
                rays.strokeWidth = e.r
                canvas.drawPoint(e.x, e.y, rays)
            }
        }
    }

    private fun lerpColor(c0: Int, c1: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val r = (Color.red(c0) + (Color.red(c1) - Color.red(c0)) * k).toInt()
        val g = (Color.green(c0) + (Color.green(c1) - Color.green(c0)) * k).toInt()
        val b = (Color.blue(c0) + (Color.blue(c1) - Color.blue(c0)) * k).toInt()
        return Color.argb(255, r, g, b)
    }

    private fun blink(t: Float, c0: Int, c1: Int): Int =
        if (abs(sin2(t * 2f * Math.PI.toFloat())) > 0.5f) c1 else c0

    private fun sin2(x: Float): Float = kotlin.math.sin(x)
}
