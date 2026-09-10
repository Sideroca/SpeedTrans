package com.speedtrans.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 测试连接动画（电路版 · 新拟物）——由 预览-测试连接-电路版.html 1:1 移植。
 *
 * 画布 760×460 等比适配；元素：凹槽导线 + 河水式绿色填充 + 电源（左下 −|+）+ 开关（右下）+ 💡emoji 灯泡。
 * 状态：IDLE 灰暗(开关断开) / TESTING 开关闭合+电流从正极缓缓填充 / OK 绿光+200 / FAIL 熄灭+开关弹回+404。
 * 设计与实现约定：所有 Paint/Path 预分配；帧驱动 = Choreographer；虚拟画布坐标与设计稿一致。
 */
class CircuitTestView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs), Choreographer.FrameCallback {

    enum class State { IDLE, TESTING, OK, FAIL }

    // ---------------- 虚拟画布 ----------------
    private val VW = 760f
    private val VH = 460f
    private var scale = 1f
    private var offX = 0f
    private var offY = 0f

    // ---------------- 几何（与设计稿一致） ----------------
    private val L = 110f; private val T = 104f; private val R = 650f; private val B = 376f
    private val RAD = 26f
    private val bulbX = 470f; private val bulbY = T; private val bulbR = 54f
    private val batM = 188f; private val batP = 210f          // 电源：− 在左、+ 在右（左下角）
    private val swA = 420f; private val swB = 512f; private val swLever = 92f
    private val swOpen = -0.62f

    // ---------------- 配色（新拟物） ----------------
    private val C_SURFACE = Color.parseColor("#E9EDF3")
    private val C_HI = 0xF2FFFFFF.toInt()
    private val C_LO = 0x4D6074A0.toInt()
    private val C_FLOOR = Color.parseColor("#D7DFEA")
    private val C_METAL = Color.parseColor("#EDF1F7")
    private val C_INK = Color.parseColor("#6B7789")
    private val C_GREEN = Color.parseColor("#2FBF68")
    private val C_GREEN_DEEP = Color.parseColor("#1E9E52")
    private val C_GREEN_HI = Color.parseColor("#CFF7E0")
    private val C_RED = Color.parseColor("#E05252")

    // ---------------- 状态 ----------------
    private var state = State.IDLE
    private var stateAt = SystemClock.elapsedRealtime()
    private var running = false

    // ---------------- 画笔（预分配） ----------------
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.MONOSPACE
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val wirePath = Path()
    private val fluidPath = Path()
    private val measure = PathMeasure()
    private var pathLen = 0f

    init {
        buildPaths()
    }

    // ---------------- 路径：导线（含电源/开关缺口）+ 电流路径 ----------------
    private fun buildPaths() {
        wirePath.reset()
        // 导线：正极(210,B) → 右下角 → 右上角 → 左上角 → 左下角 → 开关左触点(420,B)
        wirePath.moveTo(batP, B); wirePath.lineTo(R, B); wirePath.lineTo(R, T)
        wirePath.lineTo(L, T); wirePath.lineTo(L, B); wirePath.lineTo(swA, B)
        // 开关右触点 → 电源负极
        wirePath.moveTo(swB, B); wirePath.lineTo(batM, B)

        fluidPath.reset()
        fluidPath.moveTo(batP, B); fluidPath.lineTo(R, B); fluidPath.lineTo(R, T)
        fluidPath.lineTo(L, T); fluidPath.lineTo(L, B); fluidPath.lineTo(swA, B)
        fluidPath.lineTo(swB, B); fluidPath.lineTo(batM, B)
        measure.setPath(fluidPath, false)
        pathLen = measure.length
    }

    // ---------------- 对外 API（与旧灯泡一致的使用方式） ----------------
    fun setState(s: State) {
        if (state == s) return
        state = s
        stateAt = SystemClock.elapsedRealtime()
        invalidate()
    }

    fun state(): State = state

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!running) {
            running = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onDetachedFromWindow() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        scale = min(w / VW, h / VH)
        offX = (w - VW * scale) / 2f
        offY = (h - VH * scale) / 2f
    }

    private fun sinceSec(): Float = (SystemClock.elapsedRealtime() - stateAt) / 1000f
    private fun nowSec(): Float = SystemClock.elapsedRealtime() / 1000f
    private fun easeInOut(k: Float): Float =
        if (k < 0.5f) 2f * k * k else 1f - (2f - 2f * k) * (2f - 2f * k) / 2f
    private fun easeOutCubic(k: Float): Float = 1f - (1f - k) * (1f - k) * (1f - k)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(C_SURFACE)
        canvas.save()
        canvas.translate(offX, offY)
        canvas.scale(scale, scale)

        val since = sinceSec()
        val dim = when (state) {
            State.IDLE -> 0.55f
            State.FAIL -> 1f - 0.4f * min(1f, since / 0.4f)
            else -> 1f
        }
        val leverAng = when (state) {
            State.TESTING -> swOpen + (0f - swOpen) * easeOutCubic(min(1f, since / 0.28f))
            State.OK -> 0f
            State.FAIL -> swOpen * easeOutCubic(min(1f, since / 0.16f))
            else -> swOpen
        }

        drawGroove(canvas, dim)
        drawFluid(canvas, since, dim)
        drawSwitch(canvas, leverAng, dim)
        drawBattery(canvas, dim)
        drawBulb(canvas, since)
        drawLabel(canvas)
        canvas.restore()
    }

    /** 凹陷导流槽：上左内阴影 + 下右高光 + 槽底 */
    private fun drawGroove(canvas: Canvas, alpha: Float) {
        canvas.save()
        canvas.translate(-1.4f, -1.4f)
        line.color = C_LO; line.strokeWidth = 13f; line.alpha = (255 * alpha).toInt()
        canvas.drawPath(wirePath, line)
        canvas.restore()
        canvas.save()
        canvas.translate(1.3f, 1.3f)
        line.color = C_HI; line.strokeWidth = 13f; line.alpha = (255 * alpha).toInt()
        canvas.drawPath(wirePath, line)
        canvas.restore()
        line.color = C_FLOOR; line.strokeWidth = 12f; line.alpha = (255 * alpha).toInt()
        canvas.drawPath(wirePath, line)
    }

    /** 河水填充：从正极推进；填满后槽内持续流动（虚线相位） */
    private fun drawFluid(canvas: Canvas, since: Float, dim: Float) {
        var progress = 0f
        var alpha = 1f
        when (state) {
            State.TESTING -> progress = easeInOut(min(1f, since / 2.4f))
            State.OK -> progress = 1f
            State.FAIL -> {
                progress = 1f
                alpha = max(0f, 1f - since / 0.55f)
            }
            else -> return
        }
        if (progress <= 0f || alpha <= 0f) return
        val upto = pathLen * progress
        val dst = Path()
        measure.getSegment(0f, upto, dst, true)

        line.alpha = (255 * 0.9f * alpha).toInt()
        line.color = C_GREEN_DEEP; line.strokeWidth = 8.5f; line.pathEffect = null
        canvas.drawPath(dst, line)
        line.alpha = (255 * alpha).toInt()
        line.color = C_GREEN; line.strokeWidth = 7f
        canvas.drawPath(dst, line)
        line.color = C_GREEN_HI; line.strokeWidth = 2.6f; line.alpha = (255 * 0.55f * alpha).toInt()
        canvas.drawPath(dst, line)

        // 流动纹理（相位随时间走）
        line.alpha = (255 * 0.5f * alpha).toInt()
        line.color = Color.parseColor("#E9FFF2"); line.strokeWidth = 2.4f
        line.pathEffect = android.graphics.DashPathEffect(floatArrayOf(26f, 118f), -(nowSec() * 130f) % 144f)
        canvas.drawPath(dst, line)
        line.pathEffect = null

        // 推进头光点
        if (state == State.TESTING && progress < 1f) {
            val pos = FloatArray(2)
            val tan = FloatArray(2)
            measure.getPosTan(upto, pos, tan)
            glow.shader = RadialGradient(
                pos[0], pos[1], 26f,
                Color.argb(200, 90, 220, 140), Color.argb(0, 90, 220, 140), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(pos[0], pos[1], 26f, glow)
            fill.color = Color.parseColor("#D9FFE8")
            canvas.drawCircle(pos[0], pos[1], 7.5f, fill)
        }
        line.alpha = 255
    }

    /** 开关：软凸小杆 + 两个触点 */
    private fun drawSwitch(canvas: Canvas, ang: Float, alpha: Float) {
        val ex = swA + kotlin.math.cos(ang) * swLever
        val ey = B + sin(ang) * swLever
        canvas.save()
        canvas.translate(2.6f, 2.8f)
        line.color = 0x616074A0.toInt(); line.strokeWidth = 12f; line.alpha = (255 * alpha).toInt()
        canvas.drawLine(swA, B, ex, ey, line)
        canvas.restore()
        canvas.save()
        canvas.translate(-1.6f, -1.8f)
        line.color = C_HI; line.strokeWidth = 12f; line.alpha = (255 * alpha).toInt()
        canvas.drawLine(swA, B, ex, ey, line)
        canvas.restore()
        line.color = C_METAL; line.strokeWidth = 9f; line.alpha = (255 * alpha).toInt()
        canvas.drawLine(swA, B, ex, ey, line)
        // 触点
        fill.color = C_METAL; fill.alpha = (255 * alpha).toInt()
        canvas.drawCircle(swA, B, 6.5f, fill)
        canvas.drawCircle(swB, B, 6.5f, fill)
        fill.alpha = 255
    }

    /** 电源：左短(−) 右长(+) */
    private fun drawBattery(canvas: Canvas, alpha: Float) {
        drawBar(canvas, batM, B - 18f, batM, B + 18f, alpha)
        drawBar(canvas, batP, B - 34f, batP, B + 34f, alpha)
        text.color = C_INK; text.textSize = 26f; text.alpha = (255 * alpha).toInt()
        canvas.drawText("−", batM, B - 44f, text)
        canvas.drawText("+", batP, B - 60f, text)
        text.alpha = 255
    }

    private fun drawBar(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, alpha: Float) {
        canvas.save()
        canvas.translate(2.6f, 2.8f)
        line.color = 0x616074A0.toInt(); line.strokeWidth = 12f; line.alpha = (255 * alpha).toInt()
        canvas.drawLine(x1, y1, x2, y2, line)
        canvas.restore()
        line.color = C_METAL; line.strokeWidth = 9f; line.alpha = (255 * alpha).toInt()
        canvas.drawLine(x1, y1, x2, y2, line)
    }

    /** 💡 软盘 + 状态氛围 + emoji */
    private fun drawBulb(canvas: Canvas, since: Float) {
        val cx = bulbX; val cy = bulbY - 34f; val r = bulbR
        // 软盘
        fill.color = Color.parseColor("#F2F6FC")
        canvas.drawCircle(cx, cy, r, fill)
        fill.color = Color.parseColor("#DDE4EF")
        canvas.drawCircle(cx, cy + 6f, r * 0.92f, fill)
        fill.color = Color.parseColor("#F8FAFF")
        canvas.drawCircle(cx, cy - 2f, r * 0.86f, fill)
        when (state) {
            State.OK -> {
                val a = min(1f, since / 0.35f) * (0.8f + 0.2f * sin(nowSec() * 0.9f))
                glow.shader = RadialGradient(
                    cx, cy, r * 2f,
                    Color.argb((90 * a).toInt(), 47, 191, 104), Color.argb(0, 47, 191, 104),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx, cy, r * 2f, glow)
                line.color = C_GREEN; line.strokeWidth = 3f
                canvas.drawCircle(cx, cy, r - 2f, line)
            }
            State.TESTING -> {
                line.color = 0x552FBF68; line.strokeWidth = 2f
                canvas.drawCircle(cx, cy, r - 2f, line)
            }
            else -> { }
        }
        text.textSize = 60f
        text.alpha = when (state) {
            State.OK, State.TESTING -> 255
            else -> 110
        }
        canvas.drawText("💡", cx, cy + 20f, text)
        text.alpha = 255
    }

    /** 右下角状态码：ing / 200 / 404 */
    private fun drawLabel(canvas: Canvas) {
        val txt = when (state) {
            State.TESTING -> "ing"
            State.OK -> "200"
            State.FAIL -> "404"
            else -> return
        }
        text.color = when (state) {
            State.TESTING, State.OK -> C_GREEN
            else -> C_RED
        }
        text.textSize = 40f
        text.textAlign = Paint.Align.RIGHT
        val a = if (state == State.TESTING) 0.55f + 0.45f * sin(nowSec() * 3f) else 1f
        text.alpha = (255 * a).toInt()
        canvas.drawText(txt, R, B + 56f, text)
        text.alpha = 255
        text.textAlign = Paint.Align.CENTER
    }
}
