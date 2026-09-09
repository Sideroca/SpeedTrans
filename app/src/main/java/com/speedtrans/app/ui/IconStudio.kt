package com.speedtrans.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * 快捷图标工坊：两种轨道系样式 + 44 色库自由配色，Canvas 直出位图。
 * 点珠坐标由轨道参数方程计算——珠子永远在轨道上，不靠手调。
 */
object IconStudio {

    const val STYLE_GRID = 0   // 网格轨道球（经纬网格 + 交点珠 + 外环）
    const val STYLE_RING = 1   // 双轨道环（交叉椭圆 + 环上珠 + 核心珠）

    /** 椭圆参数点：中心 cx,cy，轴 rx,ry，整体旋转 deg，取参数角 theta */
    private fun orbPoint(
        cx: Float, cy: Float, rx: Float, ry: Float, deg: Float, theta: Float
    ): Pair<Float, Float> {
        val rad = Math.toRadians(deg.toDouble())
        val cosD = cos(rad).toFloat()
        val sinD = sin(rad).toFloat()
        val t = Math.toRadians(theta.toDouble())
        val x0 = rx * cos(t).toFloat()
        val y0 = ry * sin(t).toFloat()
        return (cx + x0 * cosD - y0 * sinD) to (cy + x0 * sinD + y0 * cosD)
    }

    fun generate(styleId: Int, bg: Int, fg: Int, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg)
        val s = size.toFloat()
        val cx = s * 0.5f
        val cy = s * 0.52f
        val sw = s * 0.018f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = sw
            color = fg
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg }
        fun dotAt(x: Float, y: Float, r: Float = s * 0.016f) = c.drawCircle(x, y, r, dot)

        if (styleId == STYLE_GRID) {
            // 网格轨道球（图 1）：经纬网格球 + 外轨道环
            val r = s * 0.30f
            for (k in listOf(0.12f, 0.24f, 0.30f)) {
                c.drawOval(RectF(cx - s * k, cy - r, cx + s * k, cy + r), p)
            }
            for (k in listOf(0.10f, 0.20f)) {
                c.drawOval(RectF(cx - r, cy - s * k, cx + r, cy + s * k), p)
            }
            c.save()
            c.rotate(-12f, cx, cy)
            c.drawOval(RectF(cx - s * 0.42f, cy - s * 0.13f, cx + s * 0.42f, cy + s * 0.13f), p)
            c.restore()
            // 珠：心珠 + 球面 6 点 + 纬线两端 + 外轨道 3 点
            dotAt(cx, cy, s * 0.035f)
            listOf(90f, 270f, 45f, 135f, 225f, 315f).forEach { θ ->
                orbPoint(cx, cy, r, r, 0f, θ).let { dotAt(it.first, it.second) }
            }
            orbPoint(cx, cy, r, s * 0.10f, 0f, 0f).let { dotAt(it.first, it.second) }
            orbPoint(cx, cy, r, s * 0.10f, 0f, 180f).let { dotAt(it.first, it.second) }
            listOf(60f, 200f, 320f).forEach { θ ->
                orbPoint(cx, cy, s * 0.42f, s * 0.13f, -12f, θ).let { dotAt(it.first, it.second) }
            }
        } else {
            // 双轨道环（图 2）：交叉双椭圆 + 同心细环
            val rx = s * 0.38f
            val ry = s * 0.30f
            c.save(); c.rotate(30f, cx, cy)
            c.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), p)
            c.restore()
            c.save(); c.rotate(-30f, cx, cy)
            c.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), p)
            c.restore()
            c.drawCircle(cx, cy, s * 0.36f, p.apply { strokeWidth = sw * 0.5f })
            // 珠：核珠 + 双轨道极点 4 颗 + 同心环上下 2 颗
            c.drawCircle(cx, cy, s * 0.055f, dot)
            orbPoint(cx, cy, rx, ry, 30f, 90f).let { dotAt(it.first, it.second, s * 0.020f) }
            orbPoint(cx, cy, rx, ry, 30f, 270f).let { dotAt(it.first, it.second, s * 0.020f) }
            orbPoint(cx, cy, rx, ry, -30f, 90f).let { dotAt(it.first, it.second, s * 0.020f) }
            orbPoint(cx, cy, rx, ry, -30f, 270f).let { dotAt(it.first, it.second, s * 0.020f) }
            orbPoint(cx, cy, s * 0.36f, s * 0.36f, 0f, 90f).let { dotAt(it.first, it.second, s * 0.016f) }
            orbPoint(cx, cy, s * 0.36f, s * 0.36f, 0f, 270f).let { dotAt(it.first, it.second, s * 0.016f) }
        }
        return bmp
    }
}
