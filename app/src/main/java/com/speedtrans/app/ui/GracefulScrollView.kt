package com.speedtrans.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.OverScroller
import android.widget.ScrollView
import android.widget.SeekBar
import kotlin.math.abs

/**
 * 设置页专用 ScrollView：彻底解决两类"划不动"：
 * （v3）接管时先 smoothScrollBy(0,0) 掐死系统"获焦滚动"动画——修复"短滑被强制回正"；
 * ① 起点落在输入框上的滑动手势被 EditText 获焦后吞掉（框架拦截管线在部分 ROM 上被旁路）——
 *    超过阈值后本视图**手动接管**手势：直接 scrollBy，不依赖任何子视图让权、不依赖框架裁决；
 * ② 起点落在滑条（SeekBar）上时**绝不抢**——让滑条安安稳稳被拖动（修"调遮罩浓度时屏幕乱晃"）。
 */
class GracefulScrollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ScrollView(context, attrs) {

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val scroller = OverScroller(context)
    private var tracker: VelocityTracker? = null

    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false          // 手动接管中
    private var sliderGuard = false       // 手势起点在滑条上：全程不抢
    private var frameworkDrag = false     // 框架层已接管（正常路径）

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // 滑条起点的手势：保持让权（滑条要用）；其他子视图的"禁止拦截"请求一律驳回
        super.requestDisallowInterceptTouchEvent(if (sliderGuard) disallowIntercept else false)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (sliderGuard) return super.onInterceptTouchEvent(ev)
        val r = super.onInterceptTouchEvent(ev)
        if (r) frameworkDrag = true
        return r
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.abortAnimation()
                downX = ev.x
                downY = ev.y
                lastY = ev.y
                dragging = false
                frameworkDrag = false
                sliderGuard = isOnSeekBar(ev.x, ev.y)
                tracker?.recycle()
                tracker = VelocityTracker.obtain().apply { addMovement(ev) }
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(ev)
                if (dragging) {
                    val dy = lastY - ev.y
                    if (dy != 0f) scrollBy(0, dy.toInt())
                    lastY = ev.y
                    return true
                }
                if (!sliderGuard && !frameworkDrag &&
                    abs(ev.y - downY) > slop * 0.5f && abs(ev.y - downY) >= abs(ev.x - downX)
                ) {
                    // 框架没接管（被输入框获焦等吞掉）：手动接管
                    dragging = true
                    lastY = ev.y
                    // 关键：掐死系统"把获焦控件滚进视野"的平滑动画（否则它会和手指抢、短滑被拽回）
                    smoothScrollBy(0, 0)
                    // 并把焦点从输入框抢走（社区标准解法：否则 ScrollView 会持续"照顾"获焦控件）
                    findFocus()?.clearFocus()
                    // 再给子视图发 CANCEL 复位按压态
                    val cancel = MotionEvent.obtain(ev)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.addMovement(ev)
                if (dragging) {
                    dragging = false
                    flingUp()
                    tracker?.recycle()
                    tracker = null
                    return true
                }
                sliderGuard = false
                tracker?.recycle()
                tracker = null
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun flingUp() {
        val t = tracker ?: return
        t.computeCurrentVelocity(1000)
        val vy = -t.yVelocity.toInt()   // 手指上滑（负）→ 内容向下推进（正）
        if (abs(vy) > 200) {
            val maxY = maxOf(0, (getChildAt(0)?.height ?: 0) - height)
            scroller.fling(0, scrollY, 0, vy, 0, 0, 0, maxY)
            postInvalidateOnAnimation()
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            if (scroller.currY != scrollY) scrollTo(0, scroller.currY)
            postInvalidateOnAnimation()
        }
    }

    /** 手势起点是否落在滑条（SeekBar）上（递归命中测试） */
    private fun isOnSeekBar(x: Float, y: Float): Boolean {
        fun hit(v: View, lx: Float, ly: Float): Boolean {
            if (v.visibility != View.VISIBLE) return false
            val vx = lx - v.x
            val vy = ly - v.y
            if (vx < 0 || vy < 0 || vx > v.width || vy > v.height) return false
            if (v is SeekBar) return true
            if (v is ViewGroup) {
                for (i in v.childCount - 1 downTo 0) {
                    if (hit(v.getChildAt(i), vx, vy)) return true
                }
            }
            return false
        }
        for (i in childCount - 1 downTo 0) {
            if (hit(getChildAt(i), x, y)) return true
        }
        return false
    }
}
