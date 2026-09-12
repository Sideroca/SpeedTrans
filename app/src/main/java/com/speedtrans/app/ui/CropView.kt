package com.speedtrans.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 取景视图：图片在下（单指拖动 / 双指缩放），中央是「手机屏幕比例的缩小版取景框」。
 * 框内 = 页面最终实际显示的内容；框外压暗。归一化状态：nx/ny（÷框宽高）、nz（相对最小适配比例）。
 */
class CropView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var bmp: Bitmap? = null
    private var iw = 0f
    private var ih = 0f
    private var minS = 1f

    private var x = 0f
    private var y = 0f
    private var s = 1f

    private var pendingFit = false
    private var hasPendingState = false
    private var pnx = 0f
    private var pny = 0f
    private var pnz = 1f

    private var lastX = 0f
    private var lastY = 0f
    private var multi = false
    private var lastDist = 0f
    private var lastMidX = 0f
    private var lastMidY = 0f

    private val d = resources.displayMetrics.density
    private val frame = RectF()
    private val frameRadius = 12f * d
    private val matrix = Matrix()
    private val imgPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xA005070A.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        color = 0xE6FFFFFF.toInt()
    }
    private val fullPath = Path()
    private val holePath = Path()
    private val scrimPaint = Paint().apply { color = 0xFF000000.toInt() }
    private val ghostFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ghostStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ghostText = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 缩放百分比（相对最小适配）变化回调，供滑条同步 */
    var onZoomChanged: ((Int) -> Unit)? = null

    /** 固定取景框宽高比（0 = 跟随视图比例 → 壁纸模式；1 = 正方形 → 图标模式）。需在首次布局前设置 */
    var fixedFrameWH = 0f

    /** 首页映射：框内叠加"首页样式"半透明剪影（仅首页壁纸）。mapAlpha 0..1；scrimPct 0..100 模拟遮罩浓度 */
    var mapEnabled = false
    var mapAlpha = 0.6f
    var scrimPct = 0

    fun setMapping(enabled: Boolean, alpha: Float, scrim: Int) {
        mapEnabled = enabled
        mapAlpha = alpha.coerceIn(0f, 1f)
        scrimPct = scrim.coerceIn(0, 100)
        invalidate()
    }

    fun setBitmap(b: Bitmap) {
        bmp = b
        iw = b.width.toFloat()
        ih = b.height.toFloat()
        if (frame.width() > 0f) {
            recomputeMin()
            doFit()
        } else {
            pendingFit = true
        }
    }

    /** 取景框宽高比（w/h），供烘焙输出保持同比例 */
    fun frameAspectWH(): Float = if (frame.height() > 0f) frame.width() / frame.height() else 0.45f

    fun fit() {
        if (frame.width() > 0f) {
            recomputeMin()
            doFit()
        } else {
            pendingFit = true
        }
    }

    fun setState(nx: Float, ny: Float, nz: Float) {
        if (frame.width() <= 0f) {
            hasPendingState = true
            pnx = nx
            pny = ny
            pnz = nz
            return
        }
        applyStateNow(nx, ny, nz)
    }

    fun normalized(): FloatArray =
        if (frame.width() <= 0f) floatArrayOf(0f, 0f, 1f)
        else floatArrayOf(x / frame.width(), y / frame.height(), (s / minS).coerceIn(1f, 4f))

    fun zoomMult(): Float = if (minS > 0f) s / minS else 1f

    fun setZoomMult(m: Float) {
        s = minS * m.coerceIn(1f, 4f)
        clampAll()
        invalidate()
        notifyZoom()
    }

    private fun doFit() {
        s = minS
        x = 0f
        y = 0f
        clampAll()
        invalidate()
        notifyZoom()
    }

    private fun applyStateNow(nx: Float, ny: Float, nz: Float) {
        s = minS * nz.coerceIn(1f, 4f)
        x = nx * frame.width()
        y = ny * frame.height()
        clampAll()
        invalidate()
        notifyZoom()
    }

    private fun notifyZoom() {
        onZoomChanged?.invoke((zoomMult() * 100).toInt())
    }

    private fun recomputeMin() {
        if (frame.width() <= 0f || iw <= 0f) return
        minS = max(frame.width() / iw, frame.height() / ih)
    }

    private fun clampAll() {
        if (minS <= 0f || frame.width() <= 0f) return
        s = s.coerceIn(minS, minS * 4f)
        val mx = max(0f, (iw * s - frame.width()) / 2f)
        val my = max(0f, (ih * s - frame.height()) / 2f)
        x = x.coerceIn(-mx, mx)
        y = y.coerceIn(-my, my)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || h <= 0) return
        val fw: Float
        val fh: Float
        if (fixedFrameWH > 0f) {
            fw = min(h * 0.64f, w * 0.86f)
            fh = fw / fixedFrameWH
        } else {
            fh = h * 0.64f
            fw = min(fh * w.toFloat() / h.toFloat(), w * 0.66f)
        }
        frame.set((w - fw) / 2f, (h - fh) / 2f, (w + fw) / 2f, (h + fh) / 2f)
        recomputeMin()
        when {
            hasPendingState -> {
                hasPendingState = false
                pendingFit = false
                applyStateNow(pnx, pny, pnz)
            }
            pendingFit -> {
                pendingFit = false
                doFit()
            }
            else -> clampAll()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val b = bmp ?: return
        matrix.reset()
        matrix.postScale(s, s)
        matrix.postTranslate(width / 2f + x - s * iw / 2f, height / 2f + y - s * ih / 2f)
        canvas.drawBitmap(b, matrix, imgPaint)

        // 首页映射剪影：框内叠加（静止锚，不随图片拖动/缩放；先铺"遮罩浓度"暗度，再叠剪影）
        if (mapEnabled && mapAlpha > 0.01f) {
            val save = canvas.save()
            holePath.reset()
            holePath.addRoundRect(frame, frameRadius, frameRadius, Path.Direction.CW)
            canvas.clipPath(holePath)
            if (scrimPct > 0) {
                scrimPaint.alpha = (scrimPct * 2.04f).toInt().coerceIn(0, 235)
                canvas.drawRect(frame, scrimPaint)
            }
            drawHomeGhost(canvas, mapAlpha)
            canvas.restoreToCount(save)
        }

        // 框外压暗：整屏减去圆角取景框
        fullPath.reset()
        fullPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        holePath.reset()
        holePath.addRoundRect(frame, frameRadius, frameRadius, Path.Direction.CW)
        fullPath.op(holePath, Path.Op.DIFFERENCE)
        canvas.drawPath(fullPath, dimPaint)

        // 框线
        canvas.drawRoundRect(frame, frameRadius, frameRadius, borderPaint)
    }

    /** 画"首页样式"半透明剪影：标题 + 四张卡（真机比例）+ 签名 */
    private fun drawHomeGhost(canvas: Canvas, k: Float) {
        val f = frame
        val u = f.width()
        val top = f.top
        val h = f.height()
        fun a(base: Int) = (base * k).toInt().coerceIn(0, 255)
        ghostText.typeface = android.graphics.Typeface.DEFAULT_BOLD
        ghostText.textSize = u * 0.062f
        ghostText.color = android.graphics.Color.argb(a(235), 255, 255, 255)
        canvas.drawText("闪译 SpeedTrans", f.left + u * 0.06f, top + h * 0.073f, ghostText)
        ghostText.typeface = android.graphics.Typeface.DEFAULT
        ghostText.textSize = u * 0.030f
        ghostText.color = android.graphics.Color.argb(a(150), 255, 255, 255)
        canvas.drawText("点一下悬浮球 → 立即流式翻译屏幕文字（英→中）", f.left + u * 0.06f, top + h * 0.097f, ghostText)
        ghostStroke.strokeWidth = u * 0.013f
        ghostStroke.color = android.graphics.Color.argb(a(200), 255, 255, 255)
        canvas.drawCircle(f.left + u * 0.845f, top + h * 0.075f, u * 0.055f, ghostStroke)
        canvas.drawCircle(f.left + u * 0.90f, top + h * 0.032f, u * 0.012f, ghostStroke)
        val cardX = f.left + u * 0.055f
        val cardW = u * 0.89f
        val r = u * 0.035f
        val cards = listOf(
            0.115f to 0.132f,
            0.263f to 0.195f,
            0.472f to 0.300f,
            0.786f to 0.118f
        )
        cards.forEach { (tp, hp) ->
            val cy = top + h * tp
            val ch = h * hp
            ghostFill.color = android.graphics.Color.argb(a(95), 255, 255, 255)
            canvas.drawRoundRect(cardX, cy, cardX + cardW, cy + ch, r, r, ghostFill)
            ghostStroke.color = android.graphics.Color.argb(a(70), 255, 255, 255)
            canvas.drawRoundRect(cardX, cy, cardX + cardW, cy + ch, r, r, ghostStroke)
        }
        ghostText.color = android.graphics.Color.argb(a(200), 30, 34, 40)
        ghostText.textSize = u * 0.040f
        listOf("主题色" to 0.115f, "运行状态" to 0.263f, "设置与工具" to 0.472f, "使用方法" to 0.786f).forEach { (n, tp) ->
            canvas.drawText(n, cardX + u * 0.05f, top + h * tp + u * 0.075f, ghostText)
        }
        ghostText.textSize = u * 0.028f
        ghostText.textAlign = Paint.Align.CENTER
        ghostText.color = android.graphics.Color.argb(a(140), 255, 255, 255)
        canvas.drawText("✦ glm5.3flash · 为极速而生的闪译", f.centerX(), top + h * 0.945f, ghostText)
        ghostText.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x
                lastY = e.y
                multi = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multi = true
                lastDist = dist(e)
                if (lastDist <= 1f) lastDist = 1f
                lastMidX = midX(e)
                lastMidY = midY(e)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!multi) {
                    x += e.x - lastX
                    y += e.y - lastY
                    lastX = e.x
                    lastY = e.y
                    clampAll()
                    invalidate()
                } else {
                    val dNow = dist(e)
                    if (lastDist > 1f && dNow > 1f) {
                        s *= dNow / lastDist
                        x += midX(e) - lastMidX
                        y += midY(e) - lastMidY
                        clampAll()
                        invalidate()
                        notifyZoom()
                    }
                    lastDist = dNow
                    lastMidX = midX(e)
                    lastMidY = midY(e)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                multi = false
                if (e.pointerCount >= 1) {
                    lastX = e.getX(0)
                    lastY = e.getY(0)
                }
            }
            MotionEvent.ACTION_UP -> {
                multi = false
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> multi = false
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun dist(e: MotionEvent): Float =
        if (e.pointerCount >= 2) hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)) else 0f

    private fun midX(e: MotionEvent): Float =
        if (e.pointerCount >= 2) (e.getX(0) + e.getX(1)) / 2f else e.x

    private fun midY(e: MotionEvent): Float =
        if (e.pointerCount >= 2) (e.getY(0) + e.getY(1)) / 2f else e.y
}
