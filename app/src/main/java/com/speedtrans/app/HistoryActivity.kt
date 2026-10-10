package com.speedtrans.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.speedtrans.app.store.HistoryStore
import com.speedtrans.app.theme.Palette
import com.speedtrans.app.theme.ThemeEngine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * 最近翻译历史（本地 filesDir/history.json，最多 200 条）。
 *
 * 2026-10-01 重做，四件事：
 *
 * ① **进得去** —— 原来在 onCreate 里**主线程**读盘 + 解析整份 JSON，再**一次性建 200 个 TextView**、
 *    每个塞着**完整译文** → 页面出来之前先卡一大截（用户反馈"点一下要等好久才进去"）。
 *    现在：界面骨架先出，读盘解析丢后台线程；列表**分页渲染**（一次 40 条 + 底部「加载更多」）。
 *
 * ② **节选** —— 译文默认只显示 3 行。用的是 `maxLines`，**不是截字符串**：
 *    TextView 布局到第 3 行就停，省下来的正是最贵的那部分（还不用担心把长文本切得难看）。
 *
 * ③ **展开条** —— 卡片底部一条**贯穿卡片宽度、高 54dp** 的条，中间只有一个微不足道的 ▾。
 *    照《夕汀前端规范》"小符号 + 大判定"里『列表 显示全部/收起：整条可点（≥54dp），左右贯穿卡片宽度』。
 *
 * ④ **搜索 + 日期筛选** —— 关键词实时过滤（原文摘要 / 译文 / 任务）；全部 / 今天 / 昨天 / 近 7 天。
 *
 * 保留原行为：点卡片正文 = 复制全文 · 底部「清空历史」。
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var box: LinearLayout
    private lateinit var emptyTip: TextView
    private lateinit var moreBtn: Button
    private lateinit var clearBtn: Button
    private lateinit var fmt: SimpleDateFormat
    private val chips = ArrayList<TextView>()

    private var all: List<HistoryStore.Entry> = emptyList()
    private var filtered: List<HistoryStore.Entry> = emptyList()
    private var keyword = ""
    private var dateMode = 0                  // 0 全部 / 1 今天 / 2 昨天 / 3 近 7 天
    private var shown = 0
    /** 展开状态按"时间戳"记（条目唯一）——过滤、翻页、重渲都不会丢 */
    private val expanded = HashSet<Long>()

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pal = ThemeEngine.current(this)
        fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

        // ---------- 顶部固定区：标题 / 搜索 / 日期筛选 ----------
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pal.bg)
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(8))
        }
        head.addView(TextView(this).apply {
            text = "🕘 最近翻译"
            textSize = 24f
            setTextColor(pal.text)
            typeface = Typeface.DEFAULT_BOLD
        })
        head.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(2)).apply { topMargin = dp(8) }
            setBackgroundColor(pal.accent)
        })

        head.addView(EditText(this).apply {
            hint = "🔍 搜原文 / 译文关键词"
            textSize = 14f
            setTextColor(pal.text)
            setHintTextColor(pal.subText)
            setSingleLine(true)
            background = ThemeEngine.cardDrawable(pal.card, 10f, d, pal.cardStroke)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    keyword = s?.toString()?.trim().orEmpty()
                    applyFilter()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        })

        // 日期胶囊：每个 34dp 高（低于规范要求的大判定，但这是筛选器不是列表行；横向排列互不重叠）
        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        listOf("全部", "今天", "昨天", "近 7 天").forEachIndexed { i, label ->
            val chip = TextView(this).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(9), dp(16), dp(9))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { if (i > 0) marginStart = dp(8) }
                setOnClickListener {
                    dateMode = i
                    styleChips()
                    applyFilter()
                }
            }
            chips.add(chip)
            chipRow.addView(chip)
        }
        head.addView(chipRow)
        root.addView(head)

        // 顶部整块下移：**这个页面原来没做 edge-to-edge 适配**（MainActivity / SettingsActivity 都做了，
        // 就它漏了）——所以在强制 edge-to-edge 的 ROM 上标题直接钻到状态栏底下（用户实测"被切了"）。
        // 补上标准两步：先声明"我自己管 insets"，再按状态栏高度给标题让位。
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            head.setPadding(dp(20), dp(22) + bars.top, dp(20), dp(8))
            // 底部也给导航栏让位（列表最后一条别被导航条压住）
            box.setPadding(dp(20), dp(2), dp(20), dp(24) + bars.bottom)
            insets
        }

        // ---------- 列表区 ----------
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(2), dp(20), dp(24))
        }
        emptyTip = TextView(this).apply {
            textSize = 13f
            setTextColor(pal.subText)
            setPadding(0, dp(16), 0, 0)
            text = "载入中…"
        }
        box.addView(emptyTip)

        moreBtn = Button(this).apply {
            textSize = 14f
            setOnClickListener {
                shown += PAGE
                renderList()
            }
        }
        clearBtn = Button(this).apply {
            text = "清空历史"
            textSize = 14f
            backgroundTintList = android.content.res.ColorStateList.valueOf(pal.accent)
            setTextColor(if (Color.luminance(pal.accent) > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt())
            setOnClickListener {
                HistoryStore.clear(this@HistoryActivity)
                all = emptyList(); filtered = emptyList(); expanded.clear(); shown = 0
                Toast.makeText(this@HistoryActivity, "已清空", Toast.LENGTH_SHORT).show()
                renderList()
            }
        }

        root.addView(
            ScrollView(this).apply { addView(box) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)
        styleChips()

        // ---------- 后台读盘 + 解析；界面已经出来了 ----------
        thread {
            val list = HistoryStore.load(this)
            runOnUiThread {
                all = list
                applyFilter()
            }
        }
    }

    private fun styleChips() {
        val pal = ThemeEngine.current(this)
        chips.forEachIndexed { i, c ->
            val on = i == dateMode
            c.background = ThemeEngine.cardDrawable(
                if (on) pal.accent else pal.card, 999f, d, pal.cardStroke
            )
            c.setTextColor(
                if (on) (if (Color.luminance(pal.accent) > 0.5f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt())
                else pal.text
            )
            c.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    /** 关键词 + 日期过滤 → 从头渲染 */
    private fun applyFilter() {
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val startOfYesterday = startOfToday - 24L * 3600 * 1000
        val startOf7 = startOfToday - 6L * 24 * 3600 * 1000

        filtered = all.filter { e ->
            val kwOk = keyword.isEmpty() ||
                    e.source.contains(keyword, true) ||
                    e.text.contains(keyword, true) ||
                    e.task.contains(keyword, true) ||
                    dateMatches(e.time, keyword)          // ★ 搜索框里直接填日期也能搜
            kwOk && when (dateMode) {
                1 -> e.time >= startOfToday
                2 -> e.time in startOfYesterday until startOfToday
                3 -> e.time >= startOf7
                else -> true
            }
        }
        shown = 0
        renderList()
    }

    /**
     * 关键词里带日期也能搜（2026-10-02 轻响要求：要能"具体到某一天"）。
     *
     * 一套输入多吃几种写法，用户不用记格式：
     *   `2026-10-01` · `10-01` · `10/1` · `10.1` · `20261001`
     * 另外认「今天 / 昨天」两个词（和上面的日期胶囊互为补充 ✓）
     */
    private fun dateMatches(t: Long, kw: String): Boolean {
        if (kw.isEmpty()) return false
        if (kw == "今天" || kw == "昨天") {
            val startOfToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            return if (kw == "今天") t >= startOfToday
            else t >= startOfToday - 24L * 3600 * 1000 && t < startOfToday
        }
        val c = Calendar.getInstance().apply { timeInMillis = t }
        val y = c.get(Calendar.YEAR)
        val mo = c.get(Calendar.MONTH) + 1
        val d = c.get(Calendar.DAY_OF_MONTH)
        val forms = listOf(
            "%04d-%02d-%02d".format(y, mo, d),
            "%02d-%02d".format(mo, d),
            "%d-%d".format(mo, d),
            "%02d/%02d".format(mo, d),
            "%d/%d".format(mo, d),
            "%02d.%02d".format(mo, d),
            "%d.%d".format(mo, d),
            "%04d%02d%02d".format(y, mo, d),
            "%02d月%02d日".format(mo, d),
            "%d月%d日".format(mo, d)
        )
        return forms.any { it.equals(kw, ignoreCase = true) }
    }

    /** 分页渲染：先建 PAGE 条，底部「加载更多」 */
    private fun renderList() {
        val pal = ThemeEngine.current(this)
        box.removeAllViews()
        box.addView(emptyTip)

        if (filtered.isEmpty()) {
            emptyTip.text = if (all.isEmpty()) "暂无历史。点球翻译过的内容会出现在这里（仅存本机，最多 200 条）。"
            else "没有符合条件的记录（换个关键词或日期试试）。"
            return
        }
        emptyTip.text = ""

        shown = minOf(shown + PAGE, filtered.size)
        for (i in 0 until shown) box.addView(card(filtered[i], pal))

        if (shown < filtered.size) {
            moreBtn.text = "加载更多（还剩 ${filtered.size - shown} 条）"
            box.addView(moreBtn, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) })
        }
        box.addView(clearBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(18) })
    }

    /** 一条记录：正文（点=复制全文）+ 底部展开条（贯穿宽度 54dp，符号极小、判定极大） */
    private fun card(e: HistoryStore.Entry, pal: Palette): View {
        val cardBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ThemeEngine.cardDrawable(pal.card, 12f, d, pal.cardStroke)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        // 交互（2026-10-01 轻响定稿）：
        //   · 点卡片 / 点底部条 = **展开·收起**
        //   · **长按卡片 = 复制全文**
        // 为什么把复制从"点"改成"长按"：原来整张卡都是"点一下=复制"，而卡片极大，
        // **手指搭上去、或轻微抖动（不足 slop）都会判成一次点击** → 想滑动却先弹一次"已复制"。
        // 复制是个**需要刻意**的动作，交给长按正好；点留给"看得更多"。
        // 局部函数只能引用"声明在它之前"的局部变量 → 两个 View 先置空声明，下面再赋值
        var body: TextView? = null
        var toggle: LinearLayout? = null
        var on = expanded.contains(e.time)

        fun applyState() {
            body?.maxLines = if (on) Int.MAX_VALUE else COLLAPSED_LINES
            body?.ellipsize = if (on) null else TextUtils.TruncateAt.END
            // 展开后三点退得更淡（收起时才需要它提示"还有更多"）
            val a = if (on) 0.35f else 0.85f
            toggle?.let { t -> for (i in 0 until t.childCount) t.getChildAt(i).alpha = a }
            toggle?.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                // 展开的判定给大、收起的判定给小（防误收）
                dp(if (on) TOGGLE_DP_COLLAPSE else TOGGLE_DP_EXPAND)
            )
        }

        fun flip() {
            on = !on
            if (on) expanded.add(e.time) else expanded.remove(e.time)
            applyState()                                  // 只改这一张卡，不整表重渲
        }

        body = TextView(this).apply {
            text = buildString {
                append(fmt.format(Date(e.time))).append(" · ").append(e.task)
                if (e.source.isNotBlank()) append("\n原文：").append(e.source)
                append('\n').append(e.text)
            }
            textSize = 13f
            setTextColor(pal.text)
            setPadding(dp(14), dp(12), dp(14), dp(4))
            setOnClickListener { flip() }
            setOnLongClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("translation", e.text))
                Toast.makeText(this@HistoryActivity, "已复制全文", Toast.LENGTH_SHORT).show()
                true
            }
        }

        // 判定条 = 一排**真圆点**（不再用「…」字符）：
        //   字符方案的坑：`…` 的字形坐在**基线**上（点在 em 框底部）→ 即便 gravity=center 把行框居中，
        //   点看着还是偏下；而且展开态那条只有 20dp、18sp 行高 ≈25dp，字形直接**溢出条外**。
        //   改成画三个圆点：字体无关、绝对居中、大小恒等于设定值（跨 ROM 也一致）。
        toggle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setOnClickListener { flip() }
            repeat(3) { i ->
                addView(View(this@HistoryActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(4), dp(4)).apply {
                        if (i > 0) marginStart = dp(4)
                    }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(pal.subText)
                    }
                })
            }
        }
        cardBox.addView(body!!)
        cardBox.addView(toggle)     // 判定条：整卡宽，无底色无分隔线（不外显）
        applyState()
        return cardBox
    }

    private companion object {
        /** 一次渲染多少条 */
        const val PAGE = 40

        /**
         * 折叠时显示几行。13sp 下一行约 20~22 个汉字，取 12 行 ≈ **240 字**，
         * 保证「至少 200 个汉字」（轻响 2026-10-01 要求；原来 3 行只有六十来字，太少）。
         */
        const val COLLAPSED_LINES = 12

        /**
         * 判定条高度（dp）：**整卡宽 × 这个高度**，但**不做任何可见外显**
         * —— 无分隔线、无底色、只中间一个省略号。
         *
         * 两个方向**不一样大**（轻响 2026-10-01 定的模式）：
         * · 折叠时是"**展开**" —— 你正想找内容，判定给大一点，好点中；
         * · 展开时是"**收起**" —— 多半是误碰，判定给小一点，别把看了一半的收回去。
         *
         * 32dp 是原 48dp 缩掉 1/3 的结果（上下各收 1/6），轻响要求把"上下的空白"压下来。
         */
        const val TOGGLE_DP_EXPAND = 32
        const val TOGGLE_DP_COLLAPSE = 20
    }
}
