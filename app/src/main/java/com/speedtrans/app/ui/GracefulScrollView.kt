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

    /**
     * 「自己接管拖动」总开关（2026-10-02 试验，轻响的洞见驱动）。
     *
     * ## 为什么要有这个开关
     *
     * 用户一句话点破：**"点一下就不动了 = 输入；手指一直在动 = 滑动" —— 这本来就该这么分。**
     * 而**原生 ScrollView 默认就是这么做的**（位移超过 touch slop 才拦，没超过就放给子控件点）✓
     *
     * 把它搞坏的是本类原来那个"故意不调 super.computeScroll()"的 hack ✗：
     * 框架的 scroller 永不完成 → `mIsBeingDragged` 长期为真 → **框架连点击都抢** ✗
     * → 原作者只好再写一套"自己接管"去压住它 ✗ → 之后又补了四五层 ✗✗
     * **等于我们在加代码修一个"自己造的、而且框架本来就有解"的问题。**
     *
     * ## 两个取值
     * · `false`（默认）＝ **完全交还框架**：点/划判定由框架做（它就是干这个的 ✓），
     *   我们只保留 `requestDisallowInterceptTouchEvent` 的"驳回让权"（那条是治 beta23
     *   「输入框吞手势」的正解 ✓）。接管路径、出口设卡、onLayout 恢复**全部停用** ✓
     * · `true` ＝ 走回老路（层层补丁那套），用于随时对照/回退 ✓
     *
     * 注：底部 dock 的竖直拖动代理与这个开关无关，它解决的是另一个问题（兄弟节点吃不到触摸）✓
     */
    private val TAKEOVER = false

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val scroller = OverScroller(context)
    private var tracker: VelocityTracker? = null

    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false        // 手动接管中（仅输入框起点的手势会发生）
    private var textGuard = false       // 本次手势起点在输入框上
    private var frameworkDrag = false   // 框架层已接管（正常路径）

    // 注：requestDisallowInterceptTouchEvent 的覆写见下方「临时诊断」区（那里带日志）

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!textGuard) {
            val rr = super.onInterceptTouchEvent(ev)
            // 框架接管了就记下来 → 我们的接管判定会因 frameworkDrag 而让路，避免两边抢
            if (rr) frameworkDrag = true
            if (ev.actionMasked != MotionEvent.ACTION_DOWN) diag("intercept(旁路) act=${ev.actionMasked} → $rr frameworkDrag=$frameworkDrag")
            return rr
        }
        val r = super.onInterceptTouchEvent(ev)
        if (ev.actionMasked != MotionEvent.ACTION_DOWN) diag("intercept act=${ev.actionMasked} → $r frameworkDrag=$frameworkDrag")
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
                onDraggable = isOnDraggable(ev.x, ev.y)
                touchActive = true
                // ❌ 原来这里 diagRing.clear() —— 结构上就是错的：
                //    打开弹窗要"长按标题"，而长按本身会产生一个 DOWN → 把刚要抓的证据清掉 ✗
                //    用户实测拿到的那张图里只剩一个 DOWN，就是这么来的。现在**不清**，
                //    靠 160 行环形缓冲保留最近 gestures，用 ▼ / ▲ 当分隔。
                downAt = android.os.SystemClock.uptimeMillis()
                lastDiagY = -1; anomaly = false
                diag("▼ DOWN x=${ev.x.toInt()} y=${ev.y.toInt()} textGuard=$textGuard scrollY=$scrollY")
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
                    // selfScroll 标记"这一次是我们自己发的" —— scrollTo 的出口卡子靠它放行
                    if (dy != 0f) {
                        selfScroll = true; scrollBy(0, dy.toInt()); selfScroll = false
                    }
                    lastY = ev.y
                    return true
                }
                // 接管判定（2026-10-01 **扩大**）：
                //   · 输入框起点：门槛仍只有 slop/2（beta23 的取舍，见下）
                //   · 其它起点：满 slop，且**排除起手就在滑条/横向滚动/开关上**的情况
                // 为什么必须扩大：诊断日志显示，非输入框起点时框架的 onInterceptTouchEvent
                // **连续 20 次全返回 false** → 于是没有任何人接管 → 从那些位置起手**完全滑不动**
                // （20~50% 高处的空白区尤其明显）。不能再把"能不能滚"押在框架的拦截判定上。
                val dyAbs = abs(ev.y - downY)
                val dxAbs = abs(ev.x - downX)
                val need = if (textGuard) slop * 0.5f else slop.toFloat()
                if (TAKEOVER && !frameworkDrag && !onDraggable && dyAbs > need && dyAbs >= dxAbs
                ) {
                    // 接管：掐死系统"把获焦控件滚进视野"的动画 + 抢走焦点 + 给子视图发 CANCEL
                    diag("TAKEOVER y0=${downY.toInt()} y=${ev.y.toInt()} textGuard=$textGuard")
                    dragging = true
                    lastY = ev.y
                    // ⚠️ 下面这两句**只对"输入框起点"做**（2026-10-02 修复）。
                    //
                    // 接管判定扩大后，它们会在**每一次拖动**上执行 ✗ —— 而 findFocus() 多半正是
                    // 某个 EditText → clearFocus() → **键盘自动收起** → 窗口 adjustResize 重排
                    // → 布局变化 → 滚动位置被重置 = **"滑一下就跳回最上端"，五个页面都中招**
                    //（用户实测：接管扩大之后才变成全局问题）。
                    // 而这两句的本来用途就是治输入框起点的病（迟到动画/获焦滚动），别的起点根本不需要。
                    if (textGuard) {
                        smoothScrollBy(0, 0)
                        findFocus()?.clearFocus()
                    }
                    // 2026-10-01：**不再主动收键盘**。
                    // 此前每次接管都调 hideSoftInputFromWindow —— 那是**同步 IME binder 调用**，
                    // 而接管门槛只有 slop/2（≈4dp），"手指在输入框上轻划一下"就打一次，主线程被卡住
                    // = "滑一下卡一下"。上一版用 imm.isActive 把关只解决了"键盘没开也调"，
                    // **键盘真开着时照样卡**——而接口页正是键盘常年开着的页面（用户实测反馈）。
                    // 不收键盘还有额外好处：窗口不发生 adjustResize 重排，页面更不容易"回正"。
                    // 键盘交给用户自己收（下滑/点空白处/返回键），这是绝大多数 App 的做法。
                    //
                    // 注：**没有**动那个 slop/2 门槛——调高它会让 4~8dp 之间的事件漏给 EditText，
                    // 正是 beta23「输入框吞手势」要治的病，见该提交的修复史。
                    val cancel = MotionEvent.obtain(ev)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.addMovement(ev)
                touchActive = false          // 抬手：恢复"把焦点框滚进视野"的正当行为
                diag("▲ UP scrollY=$scrollY")
                downAt = 0L
                // 检测到"一次跳一屏"级别的跳变 → 自动弹诊断（不用你长按任何东西）
                if (anomaly) post { showDiagDialog() }
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

    /**
     * 布局**不该**改变滚动位置。
     *
     * 但 ScrollView.onLayout 里有一条"把获焦控件滚进视野"：
     * `mChildToScrollTo != null → scrollToDescendant(child) → scrollBy(0, delta)` ——
     * 一旦某个 EditText 在布局期间持有/请求过焦点，它就会**无视用户当前的位置**把页面拽走。
     * 诊断日志抓到的正是它：`refreshThinkingRow` 改动高度 → requestLayout → onLayout → `scrollTo y=0`
     * （用户刚滚到 1090，被拽回 0）。
     *
     * 处理：布局前后对比，**位置被动了就恢复**。
     * · 手指在屏幕上时不插手（那时由我们的接管逻辑负责）
     * · 切页时 `selectCuff` 会主动 scrollTo(0,0)，那是"布局开始前"的值 → 会被 keepY 保留，不受影响 ✓
     */
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val keepY = scrollY
        super.onLayout(changed, l, t, r, b)
        if (TAKEOVER && !touchActive && scrollY != keepY) {
            diag("onLayout 把 scrollY $keepY → $scrollY，已恢复")
            super.scrollTo(0, keepY)
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
        // 交还框架时，**必须**把 super 调回来 —— 这正是那条"承重 hack"的解药：
        // 框架的 scroller 一旦能正常步进，mIsBeingDragged 就回到正常值，
        // 于是"点一下=点击、划一下=滚动"由框架自己判定 ✓（见 TAKEOVER 的说明）
        if (!TAKEOVER) super.computeScroll()
        if (scroller.computeScrollOffset()) {
            if (scroller.currY != scrollY) scrollTo(0, scroller.currY)
            postInvalidateOnAnimation()
        }
    }

    // ================= 临时诊断（2026-10-01 排查"跳顶/回弹"；查明即删）=================
    // 不写文件（前两版都栽在"猜路径"上）——只在内存里留存**最近一次手势**的完整时序，
    // 由设置页「长按标题」弹出的对话框展示，文本可选中复制。
    private var downAt = 0L
    /** 手指是否还在屏幕上（用来挡住"把获焦控件滚进视野"） */
    private var touchActive = false

    /** 本次手势起点是否在"自己会拖"的控件上（滑条/横向滚动/开关）—— 这类起点我们不接管 */
    private var onDraggable = false

    init {
        // 布局探针：**内容高度一变，屏幕上的东西就整体位移** —— 而 scrollY 纹丝不动。
        // 这正是"不滚动也会跳一下又回原地"的形态（接口页的 tierRow 会被整个清空重建）。
        // 挂在内容子视图上（延迟到挂载后），高度变化就记一条。
        post {
            getChildAt(0)?.addOnLayoutChangeListener { _, _, t, _, b, _, ot, _, ob ->
                val nh = b - t
                val oh = ob - ot
                if (nh != oh) {
                    diag("▼内容高度 $oh → $nh  (Δ${nh - oh})   ← 布局位移，不是滚动")
                    anomaly = true
                }
            }
        }
    }

    private fun caller(): String {
        val st = Throwable().stackTrace
        return st.drop(1).firstOrNull { !it.className.contains("GracefulScrollView") }
            ?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" } ?: "?"
    }

    private fun diag(line: String) {
        if (!DIAG) return
        val tag = if (downAt == 0L) "  起" else "+%4dms".format(android.os.SystemClock.uptimeMillis() - downAt)
        note("$tag  $line")
    }

    /** 供设置页弹出展示（长按标题触发） */
    fun diagDump(): String =
        (if (ringText().isEmpty()) "（还没有记录。先在页面上滑一次，再长按标题）"
        else "— 最近记录（▼=按下 ▲=抬起）—\n" + ringText()) +
        "\n\nDIAG=$DIAG  textGuard=$textGuard  dragging=$dragging  frameworkDrag=$frameworkDrag"

    private var lastDiagY = -1
    private var anomaly = false

    /** 我们自己正在滚（用来区分"框架想挪页面"与"我拖的"）——拖拽期间只许我们滚 */
    private var selfScroll = false

    override fun scrollTo(x: Int, y: Int) {
        diag("scrollTo y=$y  ← ${caller()}")
        // ⚠️⚠️ **出口设卡**：手指拖拽期间（我们已接管），框架任何"自己挪页面"的企图一律拦掉。
        //
        // 为什么在这里堵，而不是逐条堵：
        // ScrollView 有**三条**"把获焦控件滚进视野"的路 —— requestChildRectangleOnScreen、
        // requestChildFocus → scrollToDescendant、以及 onLayout 里的 mChildToScrollTo。
        // 2026-10-01 只堵了第一条，**没用**（用户实测）—— 因为后两条的闸门是框架的
        // `mIsBeingDragged`，而我们的接管路径**恰好不让框架置位**（用的是自己的 dragging 标志）
        // → 于是它们全部畅通 ✓✓ 这正是"接口页（输入框最多）一滑就跳一下"的真凶。
        // 而且它解释了"偶发"：mIsBeingDragged 取决于 mScroller 跑没跑完，会随时间变化 ✗
        //
        // 出口设卡则一次覆盖全部路径，且**只拦"不是我们发起的"滚动**（selfScroll 标记我们自己那一次）。
        if (TAKEOVER && dragging && !selfScroll) {
            diag("  ↑ **已拦下**（拖拽期间只许我滚）")
            return
        }
        // 异常判据：一次 scrollTo 里位置跳了**超过一屏**。用户滑动/惯性都是每帧几十到几百像素，
        // 绝不会一步跳一整屏 —— 命中的就是"跳一下/弹回"。
        // 门槛从"一整屏"降到 1/3 屏（原来太严，小跳根本触发不了）：
        // 手滑/惯性每帧只有几十~几百 px，绝不会一步跳 >1/3 屏。
        if (lastDiagY >= 0 && kotlin.math.abs(y - lastDiagY) > height / 3) anomaly = true
        lastDiagY = y
        super.scrollTo(x, y)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        diag("reqDisallow=$disallowIntercept (textGuard=$textGuard)  ← ${caller()}")
        super.requestDisallowInterceptTouchEvent(if (textGuard) false else disallowIntercept)
    }

    override fun requestChildRectangleOnScreen(child: View, rectangle: android.graphics.Rect, immediate: Boolean): Boolean {
        if (touchActive) {
            diag("reqChildRect **已拦下** immediate=$immediate ${child.javaClass.simpleName}  ← ${caller()}")
            return false
        }
        val r = super.requestChildRectangleOnScreen(child, rectangle, immediate)
        diag("reqChildRect immediate=$immediate ret=$r ${child.javaClass.simpleName}  ← ${caller()}")
        return r
    }

    /** 自动弹出诊断（检测到跳变时调用；也可由设置页长按标题手动调） */
    fun showDiagDialog() {
        try {
            val act = context as? android.app.Activity ?: return
            val tv = android.widget.TextView(act).apply {
                text = diagDump()
                textSize = 11f
                setTextIsSelectable(true)
                setPadding(28, 28, 28, 28)
            }
            android.app.AlertDialog.Builder(act)
                .setTitle("⚠️ 检测到页面跳变")
                .setView(android.widget.ScrollView(act).apply { addView(tv) })
                .setPositiveButton("关闭", null)
                .setNeutralButton("复制全部") { _, _ ->
                    val cm = act.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("diag", tv.text))
                    android.widget.Toast.makeText(act, "已复制，粘给 AI 即可", android.widget.Toast.LENGTH_SHORT).show()
                }
                .show()
        } catch (_: Throwable) {
        }
    }

    companion object {
        /** 诊断总开关：排查期置 true；查完置 false */
        const val DIAG = true

        /** 进程内共享的诊断环形缓冲：任何类都能 [note] 一笔（例：ApiPage.refreshThinkingRow） */
        private val ring = ArrayDeque<String>()
        private const val RING_MAX = 160

        fun note(line: String) {
            if (!DIAG) return
            ring.addLast(line)
            while (ring.size > RING_MAX) ring.removeFirst()
        }

        fun ringText(): String = ring.joinToString("\n")
    }
    // ==============================================================================


    /** 手势起点是否落在输入框（EditText 及其子类，含 Burn 系列）上 */
    /** 命中测试：(x,y) 处**最深**的可见子视图 */
    private fun deepestAt(x: Float, y: Float): View? {
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
            target(getChildAt(i), x, y)?.let { return it }
        }
        return null
    }

    /** 手势起点是否落在输入框（EditText 及其子类，含 Burn 系列）上 */
    private fun isOnTextInput(x: Float, y: Float): Boolean = deepestAt(x, y) is EditText

    /**
     * 手势起点是否落在"**自己会拖**"的控件上（滑条 / 横向滚动 / 开关类）。
     *
     * 这类控件**不该被页面抢走手势**（beta26「滑条防抢」的用意），所以起点在它们上面时
     * 我们不主动接管 —— 仍走框架那条路 + [requestDisallowInterceptTouchEvent] 的让权机制。
     */
    private fun isOnDraggable(x: Float, y: Float): Boolean {
        val v = deepestAt(x, y) ?: return false
        return v is android.widget.SeekBar ||
                v is android.widget.HorizontalScrollView ||
                v is android.widget.CompoundButton
    }
}
