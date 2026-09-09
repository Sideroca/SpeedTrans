package com.speedtrans.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.appcompat.widget.AppCompatEditText
import kotlin.math.min

/**
 * 火焰删除特效（火柴人风最小可爱版）：
 * 删除字符时，被删的字原地引燃——金黄→橙→深红，上飘摇曳后消散，附 2 颗小火星。
 * 只在删除瞬间存在；用户滚动/输入任何操作即无痕，极速红线零影响。
 * 已知边界：删除跨越换行时，火星统一落在删除点所在行（单行近似）。
 */
internal class BurnFx(private val host: View) {

    private class P(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val ch: Char?, val size: Float, var age: Float, val life: Float
    )

    private val list = ArrayList<P>(64)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var captured = ""
    private var lastNs = 0L

    /** beforeTextChanged 时调用：记住被删的字符片段 */
    fun capture(text: CharSequence?, start: Int, count: Int) {
        if (count <= 0) return
        val s = text?.toString() ?: return
        val end = min(start + count, s.length)
        if (start < end) captured = s.substring(start, end)
    }

    /** onTextChanged 时调用：在删除点上方喷火星 */
    fun spawn(delStart: Int, n: Int) {
        val snippet = captured
        captured = ""
        if (snippet.isEmpty() || n <= 0) return
        val tv = host as? TextView ?: return
        val layout = tv.layout ?: return
        val line = layout.getLineForOffset(delStart.coerceIn(0, layout.text.length))
        val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
        val x0 = layout.getPrimaryHorizontal(delStart.coerceIn(0, layout.text.length))
        val ts = tv.textSize
        paint.textSize = ts
        val per = paint.measureText(snippet) / snippet.length
        snippet.forEachIndexed { i, ch -> spawnOne(ch, x0 + i * per, y, ts) }
    }

    private fun spawnOne(ch: Char?, x: Float, y: Float, ts: Float) {
        if (list.size >= 140) return
        list.add(
            P(
                x, y - ts * 0.25f,
                (Math.random().toFloat() - 0.5f) * ts * 0.10f,
                -(ts * (0.6f + Math.random().toFloat() * 0.5f)),
                ch, ts * 0.95f, 0f, 0.5f + Math.random().toFloat() * 0.3f
            )
        )
        repeat(2) {
            list.add(
                P(
                    x + (Math.random().toFloat() - 0.5f) * ts, y - Math.random().toFloat() * ts * 0.5f,
                    (Math.random().toFloat() - 0.5f) * ts * 0.2f,
                    -(ts * (0.5f + Math.random().toFloat() * 0.7f)),
                    null, ts * 0.10f + Math.random().toFloat() * ts * 0.06f,
                    0f, 0.35f + Math.random().toFloat() * 0.3f
                )
            )
        }
    }

    /** 推进并绘制火星；返回 true = 还有火星存活（宿主需继续 invalidate） */
    fun draw(canvas: Canvas): Boolean {
        if (list.isEmpty()) {
            lastNs = 0L
            return false
        }
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 0.016f
        else ((now - lastNs) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
        lastNs = now
        val it = list.iterator()
        while (it.hasNext()) {
            val q = it.next()
            q.age += dt
            if (q.age >= q.life) {
                it.remove()
                continue
            }
            q.x += q.vx * dt
            q.y += q.vy * dt
            q.vy -= 30f * dt   // 火苗上飘
            val k = q.age / q.life
            paint.color = ramp(k)
            paint.alpha = ((1f - k) * 255).toInt()
            val ch = q.ch
            if (ch != null) {
                paint.textSize = q.size
                canvas.drawText(ch.toString(), q.x, q.y, paint)
            } else {
                canvas.drawCircle(q.x, q.y, q.size, paint)
            }
        }
        return list.isNotEmpty()
    }

    private fun ramp(k: Float): Int = when {
        k < 0.3f -> lerp(0xFFFFF3C4.toInt(), 0xFFFFB74D.toInt(), k / 0.3f)
        k < 0.7f -> lerp(0xFFFFB74D.toInt(), 0xFFFF7043.toInt(), (k - 0.3f) / 0.4f)
        else -> lerp(0xFFFF7043.toInt(), 0xFFBF360C.toInt(), (k - 0.7f) / 0.3f)
    }

    private fun lerp(c0: Int, c1: Int, t: Float): Int {
        val r = (Color.red(c0) + (Color.red(c1) - Color.red(c0)) * t).toInt()
        val g = (Color.green(c0) + (Color.green(c1) - Color.green(c0)) * t).toInt()
        val b = (Color.blue(c0) + (Color.blue(c1) - Color.blue(c0)) * t).toInt()
        return Color.argb(255, r, g, b)
    }
}

/** 带火焰删除特效的多行/单行文本输入（替换 EditText 使用，XML 直换类名） */
class BurnEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    private val fx = BurnFx(this)

    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {
        fx.capture(text, start, count)
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        fx.spawn(start, lengthBefore - lengthAfter)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (fx.draw(canvas)) postInvalidateOnAnimation()
    }
}

/** 带火焰删除特效的下拉输入（替换 AutoCompleteTextView 使用） */
class BurnAutoCompleteTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatAutoCompleteTextView(context, attrs) {

    private val fx = BurnFx(this)

    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {
        fx.capture(text, start, count)
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        fx.spawn(start, lengthBefore - lengthAfter)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (fx.draw(canvas)) postInvalidateOnAnimation()
    }
}
