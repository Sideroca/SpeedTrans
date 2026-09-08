package com.speedtrans.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * 设置页壳层氛围动效（死亡搁浅·全息皮肤专用装饰，纯自绘，零跨模块影响）。
 */

/** 扫描线纹理 + 上下暗角（静态层，透明度极低不影响阅读） */
class ScanlineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var topGrad: LinearGradient? = null
    private var botGrad: LinearGradient? = null
    private var w = 0
    private var h = 0

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        this.w = w; this.h = h
        if (w <= 0 || h <= 0) return
        topGrad = LinearGradient(0f, 0f, 0f, h * 0.20f,
            0x660A0D12.toInt(), 0x000A0D12.toInt(), Shader.TileMode.CLAMP)
        botGrad = LinearGradient(0f, h * 0.72f, 0f, h.toFloat(),
            0x000A0D12.toInt(), 0x990A0D12.toInt(), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (w <= 0 || h <= 0) return
        val d = resources.displayMetrics.density
        val step = 3f * d
        val lineH = d
        p.shader = null
        p.color = 0x0F1EA5C7.toInt()   // ≈6% 青蓝
        var y = 0f
        while (y < h) {
            canvas.drawRect(0f, y, w.toFloat(), y + lineH, p)
            y += step
        }
        topGrad?.let { p.shader = it; canvas.drawRect(0f, 0f, w.toFloat(), h * 0.20f, p); p.shader = null }
        botGrad?.let { p.shader = it; canvas.drawRect(0f, h * 0.72f, w.toFloat(), h.toFloat(), p); p.shader = null }
    }
}

/** 扫描光束：只在屏幕下方 ~35% 高度带内往返（轻响钦定位置），4s 线性循环 */
class BeamView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val beam = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private var w = 0
    private var h = 0
    private var t = 0f
    private var anim: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        this.w = w; this.h = h
        if (w <= 0) return
        beam.shader = LinearGradient(0f, 0f, w.toFloat(), 0f,
            intArrayOf(0x001EA5C7, 0x801EA5C7, 0x001EA5C7),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        glow.shader = LinearGradient(0f, 0f, w.toFloat(), 0f,
            intArrayOf(0x001EA5C7, 0x201EA5C7, 0x001EA5C7),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (w <= 0 || h <= 0) return
        val d = resources.displayMetrics.density
        val band = h * 0.35f
        val top = h - band
        val y = top + t * (band - 6f * d)
        canvas.drawRect(0f, y - 2f * d, w.toFloat(), y + 4f * d, glow)
        canvas.drawRect(0f, y, w.toFloat(), y + 2f * d, beam)
    }

    fun start() {
        if (anim != null) return
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener {
                t = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        anim?.cancel()
        anim = null
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }
}

/** Cuff Links 弧线装饰：上弯半椭圆细线 */
class ArcView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x1F1EA5C7.toInt()   // 12% 青蓝
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val d = resources.displayMetrics.density
        p.strokeWidth = d
        val r = RectF(0f, 0f, width.toFloat(), height * 2f)
        canvas.drawArc(r, 180f, 180f, false, p)
    }
}
