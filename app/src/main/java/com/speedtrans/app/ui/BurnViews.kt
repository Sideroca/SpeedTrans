package com.speedtrans.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.appcompat.widget.AppCompatEditText
import kotlin.math.min
import kotlin.math.sin

/**
 * 火焰删除特效（轻量增强版）：删字即焚。
 *
 * 被删的字原地引燃：字身白热 → 金黄 → 橙 → 深红，一边上飘一边摇曳；
 * 周围喷出火焰舌、火星与青烟，删除点留一瞬焦痕，另有暗红灰烬坠落。
 * 多字删除按字序微延迟启动，形成轻量「蔓延」感——不需要逐字状态机。
 *
 * 纯 Canvas 粒子（上限 160），只在删除瞬间运行；任何滚动/输入操作即无痕，
 * 极速红线零影响。边界：跨换行删除时，每个字各自落在自己所在行。
 */
internal class BurnFx(private val host: View) {

    private companion object {
        const val KIND_CHAR = 0    // 被删的字本身
        const val KIND_FLAME = 1   // 火焰舌
        const val KIND_SPARK = 2   // 火星
        const val KIND_SMOKE = 3   // 青烟
        const val KIND_ASH = 4     // 灰烬（下落）
        const val KIND_SCORCH = 5  // 焦痕（原地一闪）
        const val MAX_PARTICLES = 160
        const val MAX_CHARS = 12   // 单次删除最多引燃的字数（控制粒子上限）
    }

    private class P(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val text: String?, val size: Float, val life: Float,
        val kind: Int, delay: Float = 0f
    ) {
        var age: Float = -delay
    }

    private val list = ArrayList<P>(MAX_PARTICLES)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()          // 复用，避免每帧分配
    private var captured = ""
    private var lastNs = 0L

    /** beforeTextChanged 时调用：记住被删的字符片段 */
    fun capture(text: CharSequence?, start: Int, count: Int) {
        if (count <= 0) return
        val s = text?.toString() ?: return
        val end = min(start + count, s.length)
        if (start < end) captured = s.substring(start, end)
    }

    /** onTextChanged 时调用：在删除点逐字引燃 */
    fun spawn(delStart: Int, n: Int) {
        val snippet = captured
        captured = ""
        if (snippet.isEmpty() || n <= 0) return
        val tv = host as? TextView ?: return
        val layout = tv.layout ?: return
        val textLen = layout.text.length
        val ts = tv.textSize
        if (ts <= 0f) return
        val count = min(snippet.length, MAX_CHARS)
        for (i in 0 until count) {
            val off = (delStart + i).coerceIn(0, textLen)
            val line = layout.getLineForOffset(off)
            val cy = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
            val cx = layout.getPrimaryHorizontal(off)
            spawnOne(snippet[i], cx, cy, ts, i * 0.035f)   // 字序微延迟 = 轻量蔓延
        }
    }

    private fun add(p: P) {
        if (list.size < MAX_PARTICLES) list.add(p)
    }

    private fun rnd(): Float = Math.random().toFloat()

    private fun spawnOne(ch: Char, x: Float, y: Float, ts: Float, delay: Float) {
        // 焦痕：删除点原地一闪
        add(P(x, y + ts * 0.30f, 0f, 0f, null, ts * 0.34f, 0.30f, KIND_SCORCH, delay))
        // 字身：被删的字自己烧着上飘
        add(
            P(
                x, y - ts * 0.25f, (rnd() - 0.5f) * ts * 0.08f, -(ts * (0.55f + rnd() * 0.35f)),
                ch.toString(), ts * 0.95f, 0.55f + rnd() * 0.25f, KIND_CHAR, delay
            )
        )
        // 火焰舌 ×2：细长上冲
        repeat(2) { i ->
            add(
                P(
                    x + (i - 0.5f) * ts * 0.5f + (rnd() - 0.5f) * ts * 0.2f, y - ts * 0.1f,
                    (rnd() - 0.5f) * ts * 0.12f, -(ts * (0.7f + rnd() * 0.6f)),
                    null, ts * (0.34f + rnd() * 0.22f), 0.32f + rnd() * 0.22f, KIND_FLAME, delay + i * 0.02f
                )
            )
        }
        // 火星 ×2
        repeat(2) {
            add(
                P(
                    x + (rnd() - 0.5f) * ts * 0.8f, y - rnd() * ts * 0.6f,
                    (rnd() - 0.5f) * ts * 0.25f, -(ts * (0.5f + rnd() * 0.8f)),
                    null, ts * 0.08f + rnd() * ts * 0.06f, 0.30f + rnd() * 0.35f, KIND_SPARK, delay
                )
            )
        }
        // 青烟：慢、淡、后发
        add(
            P(
                x + (rnd() - 0.5f) * ts * 0.5f, y - ts * 0.5f,
                (rnd() - 0.5f) * ts * 0.10f, -(ts * (0.25f + rnd() * 0.20f)),
                null, ts * (0.30f + rnd() * 0.18f), 0.70f + rnd() * 0.40f, KIND_SMOKE, delay + 0.05f
            )
        )
        // 灰烬 ×1：暗红，下落
        add(
            P(
                x + (rnd() - 0.5f) * ts * 0.7f, y, (rnd() - 0.5f) * ts * 0.20f,
                ts * (0.10f + rnd() * 0.18f), null, ts * 0.05f + rnd() * ts * 0.03f,
                0.60f + rnd() * 0.40f, KIND_ASH, delay + 0.10f
            )
        )
    }

    /** 推进并绘制；返回 true = 还有粒子存活（宿主需继续 invalidate） */
    fun draw(canvas: Canvas): Boolean {
        if (list.isEmpty()) {
            lastNs = 0L
            return false
        }
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 0.016f
        else ((now - lastNs) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
        lastNs = now
        val iter = list.iterator()
        while (iter.hasNext()) {
            val q = iter.next()
            q.age += dt
            if (q.age < 0f) continue          // 延迟启动（蔓延）
            if (q.age >= q.life) {
                iter.remove()
                continue
            }
            val k = q.age / q.life
            when (q.kind) {
                KIND_CHAR -> {
                    q.x += q.vx * dt
                    q.y += q.vy * dt
                    q.vy -= 26f * dt
                    q.x += sin(q.age * 14f + q.y * 0.05f) * 18f * dt
                    paint.textSize = q.size * (1f - 0.25f * k)
                    paint.color = flameColor(k)
                    paint.alpha = ((1f - k * k) * 255f).toInt().coerceIn(0, 255)
                    q.text?.let { s -> canvas.drawText(s, q.x, q.y, paint) }
                }
                KIND_FLAME -> {
                    q.x += q.vx * dt
                    q.y += q.vy * dt
                    q.x += sin(q.age * 18f + q.x * 0.07f) * 26f * dt
                    val r = q.size * (1f - 0.55f * k)
                    oval.set(q.x - r * 0.62f, q.y - r, q.x + r * 0.62f, q.y + r)
                    paint.color = flameColor(k)
                    paint.alpha = ((1f - k * k) * 235f).toInt().coerceIn(0, 255)
                    canvas.drawOval(oval, paint)
                }
                KIND_SPARK -> {
                    q.x += q.vx * dt
                    q.y += q.vy * dt
                    q.vy -= 40f * dt
                    paint.color = flameColor(k)
                    paint.alpha = ((1f - k) * 255f).toInt().coerceIn(0, 255)
                    canvas.drawCircle(q.x, q.y, q.size * (1f - 0.6f * k), paint)
                }
                KIND_SMOKE -> {
                    q.x += q.vx * dt
                    q.y += q.vy * dt
                    q.x += sin(q.age * 5f + q.x * 0.03f) * 12f * dt
                    paint.color = 0xFF3A3A3A.toInt()
                    paint.alpha = (sin(k * Math.PI.toFloat()) * 90f).toInt().coerceIn(0, 90)
                    canvas.drawCircle(q.x, q.y, q.size * (0.7f + k * 0.9f), paint)
                }
                KIND_ASH -> {
                    q.x += q.vx * dt
                    q.y += q.vy * dt
                    val flick = if (sin(q.age * 30f + q.x) > 0f) 1f else 0.45f
                    paint.color = 0xFFB0603A.toInt()
                    paint.alpha = ((1f - k) * 200f * flick).toInt().coerceIn(0, 200)
                    canvas.drawCircle(q.x, q.y, q.size, paint)
                }
                KIND_SCORCH -> {
                    val r = q.size * (1f - 0.4f * k)
                    oval.set(q.x - r * 0.9f, q.y - r * 0.35f, q.x + r * 0.9f, q.y + r * 0.35f)
                    paint.color = 0xFF241109.toInt()
                    paint.alpha = ((1f - k) * 120f).toInt().coerceIn(0, 120)
                    canvas.drawOval(oval, paint)
                }
            }
        }
        return list.isNotEmpty()
    }

    /** 白热 → 金黄 → 橙 → 深红 */
    private fun flameColor(k: Float): Int = when {
        k < 0.12f -> lerp(0xFFFFFDE7.toInt(), 0xFFFFF176.toInt(), k / 0.12f)
        k < 0.35f -> lerp(0xFFFFF176.toInt(), 0xFFFFB300.toInt(), (k - 0.12f) / 0.23f)
        k < 0.60f -> lerp(0xFFFFB300.toInt(), 0xFFFF6D00.toInt(), (k - 0.35f) / 0.25f)
        k < 0.82f -> lerp(0xFFFF6D00.toInt(), 0xFFD84315.toInt(), (k - 0.60f) / 0.22f)
        else -> lerp(0xFFD84315.toInt(), 0xFF8E1B0A.toInt(), (k - 0.82f) / 0.18f)
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
