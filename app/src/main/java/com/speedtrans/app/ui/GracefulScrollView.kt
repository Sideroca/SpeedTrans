package com.speedtrans.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.ScrollView
import kotlin.math.abs

/**
 * 给"输入框吞手势"打补丁的 ScrollView：手指做纵向滑动超过 slop 时，外层立即接管手势。
 * （否则起点落在 EditText 上的滑动手势会被编辑框内部吞掉——"进页面先划几下划不动"的根因）
 */
class GracefulScrollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ScrollView(context, attrs) {

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // 驳回子视图（EditText 等）的"禁止拦截"请求——否则纵向滑动手势会被输入框整个吞掉（"划不动"根因）
        super.requestDisallowInterceptTouchEvent(false)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = abs(ev.y - downY)
                val dx = abs(ev.x - downX)
                if (dy > slop && dy > dx * 1.2f) {
                    return true   // 明确的纵向滑动：外层接管，保证页面能滚
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}
