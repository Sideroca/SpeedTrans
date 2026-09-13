package com.speedtrans.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.EditText
import android.widget.OverScroller
import android.widget.ScrollView
import kotlin.math.abs

/**
 * 设置页专用 ScrollView（v4：只对"起点在输入框上"的手势特殊处理）。
 *
 * 原则（≈"别的软件"的做法）：
 * - 起点在输入框（EditText）上：接管——超过半步阈值直接手动 scrollBy + 惯性；
 *   否则 EditText 获焦后，框架的拦截管线在部分 ROM 上会被旁路，页面划不动。
 * - 起点在其它任何地方（滑条、自定义拖动控件、空白……）：完全交还给控件自己和系统——
 *   尊重一切"禁止拦截"请求、不插手、不抢。滑条/拖动控件从此不可能被页面抢走。
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
    private var dragging = false        // 手动接管中（仅输入框起点的手势会发生）
    private var textGuard = false       // 本次手势起点在输入框上
    private var frameworkDrag = false   // 框架层已接管（正常路径）

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // 输入框起点：驳回让权（保证我们能接管）；其余起点：一律尊重子控件/系统的决定
        super.requestDisallowInterceptTouchEvent(if (textGuard) false else disallowIntercept)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!textGuard) return super.onInterceptTouchEvent(ev)
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
                textGuard = isOnTextInput(ev.x, ev.y)
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
                if (textGuard && !frameworkDrag &&
                    abs(ev.y - downY) > slop * 0.5f && abs(ev.y - downY) >= abs(ev.x - downX)
                ) {
                    // 接管：掐死系统"把获焦控件滚进视野"的动画 + 抢走焦点 + 给子视图发 CANCEL
                    dragging = true
                    lastY = ev.y
                    smoothScrollBy(0, 0)
                    findFocus()?.clearFocus()
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
                textGuard = false
                tracker?.recycle()
                tracker = null
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun flingUp() {
        val t = tracker ?: return
        t.computeCurrentVelocity(1000)
        val vy = -t.yVelocity.toInt()
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

    /** 手势起点是否落在输入框（EditText 及其子类，含 Burn 系列）上 */
    private fun isOnTextInput(x: Float, y: Float): Boolean {
        fun target(v: View, lx: Float, ly: Float): View? {
            if (v.visibility != View.VISIBLE) return null
            val vx = lx - v.x
            val vy = ly - v.y
            if (vx < 0 || vy < 0 || vx > v.width || vy > v.height) return null
            if (v is ViewGroup) {
                for (i in v.childCount - 1 downTo 0) {
                    target(v.getChildAt(i), vx, vy)?.let { return it }
                }
            }
            return v
        }
        for (i in childCount - 1 downTo 0) {
            target(getChildAt(i), x, y)?.let { return it is EditText }
        }
        return false
    }
}
