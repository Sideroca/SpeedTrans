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
 * - **盖在滚动区之上的兄弟节点**（设置页底部 dock）：见 [attachVerticalDragProxy] ——
 *   兄弟之间没有触摸穿透，不挂代理的话那条带子上的滑动会被无声丢弃。
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
        if (r) {
            frameworkDrag = true
            // 框架层开始接管：同样掐掉"获焦滚动"动画并抢走焦点（迟到动画是"回正"的残余来源）
            smoothScrollBy(0, 0)
            findFocus()?.clearFocus()
        }
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
                if (textGuard) {
                    // 输入框获焦会在 DOWN 之后"迟到启动"把控件滚进视野的动画——当帧结束即杀
                    post { smoothScrollBy(0, 0) }
                }
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
                    // 顺手收起键盘（键盘弹出引起的窗口重排也是"回正"来源之一）
                    // 2026-10-01 修「小幅度滑动就卡顿」：只在键盘**确实开着**时才收。
                    // 此前是无条件调 hideSoftInputFromWindow —— 那是一次**同步的 IME binder 调用**，
                    // 而这条接管路径的触发门槛只有 slop/2（≈4dp）：手指在输入框上轻轻一划就打一次，
                    // 主线程被 binder 卡住，表现就是"滑一下卡一下"。
                    // imm.isActive() 是纯本地判断（不跨进程），几乎免费。
                    // 注意：**没有**动那个 slop/2 门槛——调高它会让 4~8dp 之间的事件漏给 EditText，
                    // 正是 beta23「输入框吞手势」要治的病，见该提交的修复史。
                    val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                            as? android.view.inputmethod.InputMethodManager
                    if (imm != null && imm.isActive) imm.hideSoftInputFromWindow(windowToken, 0)
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

    private fun flingUp() = flingWith(tracker)

    /** 用给定的速度追踪器起一段惯性滚动（输入框路径与底部 dock 代理**共用同一套**，不复制第二份） */
    private fun flingWith(t: VelocityTracker?) {
        val tr = t ?: return
        tr.computeCurrentVelocity(1000)
        val vy = -tr.yVelocity.toInt()
        if (abs(vy) > 200) {
            val maxY = maxOf(0, (getChildAt(0)?.height ?: 0) - height)
            scroller.fling(0, scrollY, 0, vy, 0, 0, 0, maxY)
            postInvalidateOnAnimation()
        }
    }

    /**
     * 让「盖在滚动区之上的兄弟节点」也能拖动滚动。
     *
     * 场景：设置页底部 dock（底弧五键 + 保存）是 ScrollView 的**兄弟节点**，不在它里面。
     * Android 的命中测试按几何走、**兄弟之间没有触摸穿透** —— 手指落在那条约 208dp 高的带上，
     * 事件全被 dock 子树吃掉（按钮接走点击、空白处谁也不接直接丢弃），滚动区永远收不到。
     * 用户感受到的就是"从这 5 个键起手，怎么滑都不动"。
     *
     * 做法：只有**竖直**且超过 touch slop 的拖动才接管，位移逐像素喂给 scrollBy（跟手 1:1），
     * 松手交给同一套 scroller 做惯性；没超过 slop 一律放行 —— **按钮点击照常**。
     * 这一路不碰键盘、不 clearFocus，所以没有"输入框那条慢路径"的开销。
     */
    fun attachVerticalDragProxy(proxy: View) {
        val l = buildProxyListener()
        // ⚠️ 必须"递归挂满"整棵子树，不能只挂 proxy 自己：
        // ViewGroup.dispatchTouchEvent 里，一旦某个**子 View 吃掉了 DOWN**（底弧五键、保存按钮都是可点击的），
        // 后续 MOVE 就只发给那个子 View —— 父层的 OnTouchListener **根本不会被调用**。
        // 2026-10-01 首版只挂了 proxy 自己，结果"只有按钮之间的空白处能拖，按在按钮上完全无效"（实测反馈）。
        fun hook(v: View) {
            v.setOnTouchListener(l)
            if (v is ViewGroup) for (i in 0 until v.childCount) hook(v.getChildAt(i))
        }
        hook(proxy)
    }

    private fun buildProxyListener() = object : View.OnTouchListener {
            private var x0 = 0f
            private var y0 = 0f
            private var lastY = 0f
            private var taking = false
            private var sendingCancel = false
            private var acc = 0f                       // 亚像素余额：慢速拖动时 dy<1px，直接 toInt() 会丢位移、发涩
            private var vt: VelocityTracker? = null

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                if (sendingCancel) return false        // 我们自己发出去的 CANCEL，放它走
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        scroller.abortAnimation()
                        x0 = e.x; y0 = e.y; lastY = e.y
                        taking = false
                        acc = 0f
                        vt?.recycle()
                        vt = VelocityTracker.obtain().apply { addMovement(e) }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        vt?.addMovement(e)
                        if (taking) {
                            acc += lastY - e.y
                            val step = acc.toInt()
                            if (step != 0) { scrollBy(0, step); acc -= step }
                            lastY = e.y
                            return true                     // 接管后由我们吃掉，按钮收不到 UP → 不会误触发点击
                        }
                        val ddx = e.x - x0
                        val ddy = e.y - y0
                        // 只认竖直：横向手势与点击一概不插手
                        if (abs(ddy) > slop && abs(ddy) >= abs(ddx)) {
                            taking = true
                            lastY = e.y
                            // 收掉按钮按压态：不发 CANCEL 的话，被压住的那个键会一直"按着"
                            sendingCancel = true
                            val c = MotionEvent.obtain(e)
                            c.action = MotionEvent.ACTION_CANCEL
                            v.dispatchTouchEvent(c)
                            c.recycle()
                            sendingCancel = false
                            return true
                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        vt?.addMovement(e)
                        if (taking) {
                            taking = false
                            flingWith(vt)
                            vt?.recycle(); vt = null
                            return true
                        }
                        vt?.recycle(); vt = null
                    }
                }
                return false                                // 没接管 → 原样放行，按钮点击不受影响
            }
        }

    override fun computeScroll() {
        // ⚠️⚠️ 故意**不调** super.computeScroll() —— 这是承重设计，别"修"它。
        //
        // 不调 → ScrollView 自己的 mScroller 永远步进不了 → 一旦 fling 过就永远
        // isFinished()==false → 于是每次 ACTION_DOWN，框架的
        // `mIsBeingDragged = !mScroller.isFinished()` 都是 true → **父层会抢走第一个 MOVE**。
        // 这正是"从输入框起手也能滑动屏幕"的实现方式：
        //   · EditText 会喊 requestDisallowInterceptTouchEvent(true)，被本类 override 驳回
        //     （textGuard 为真时强制传 false）→ 父层保留拦截权 → 抢成功 → 页面能滑。
        //   · 滑条起点 textGuard=false → "别拦我"照准 → 父层不拦 → 滑条不会被抢。
        // 2026-10-01 教训：加 super 后 mScroller 恢复正常，父层不再抢，
        // 结果"所有页面的输入框起手都滑不动"（实测反馈），已回滚。
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
