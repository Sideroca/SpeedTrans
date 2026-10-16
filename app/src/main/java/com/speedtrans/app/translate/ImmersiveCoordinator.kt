package com.speedtrans.app.translate

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.speedtrans.app.overlay.ImmersiveItem
import com.speedtrans.app.overlay.ImmersiveOverlay
import com.speedtrans.app.store.SettingsStore
import com.speedtrans.app.util.DiagLog
import okhttp3.Call

/**
 * 沉浸式翻译编排（2026-10-15 · **混合自适应 / 兼容优先**）。
 *
 * 设计原则：**对齐不能押在"模型听不听话"上**（用户可接任意 LLM）。
 *  - 首选：一次请求 + 让模型按 ⟦n⟧ 逐块回（快、省）。
 *  - **兜底：逐块独立请求**——哪块发的请求，译文就贴哪块，**对位不依赖模型**，
 *    连 qwen-mt 这类翻译专用模型也能用（单条 user 协议）。
 *  - 结构化命中率过低 / 请求出错 → **降级为"只补没拿到的块"**，而不是回退底部面板。
 *
 * 其它工程约束：并发上限 3（防 429）、相同文本去重/缓存、阅读序优先、关窗即取消。
 */
object ImmersiveCoordinator {

    private val MARKER = Regex("⟦\\s*(\\d+)\\s*⟧")
    private val MARKER_FALLBACK = Regex("[【〔\\[（(]\\s*(\\d+)\\s*[】〕\\]）)]")
    private const val MIN_HIT_RATIO = 0.6f
    private const val MAX_CONCURRENT = 3

    private var overlay: ImmersiveOverlay? = null
    private var engine: TranslateEngine? = null
    private var call: Call? = null
    private val blockCalls = HashSet<Call>()
    private val dedupe = HashMap<String, String>()
    private var seq = 0
    private val handler = Handler(Looper.getMainLooper())
    private val raw = StringBuilder()
    private var appContext: Context? = null
    private var basePkg: String? = null
    private var baseWinId = -1
    private var startedAt = 0L
    private var rendered = 0

    var active = false
        private set

    val overlayVisible: Boolean get() = overlay?.visible == true

    fun start(context: Context, pkg: String?, items: List<ImmersiveItem>, winId: Int = -1) {
        if (items.isEmpty()) return
        val app = context.applicationContext
        appContext = app
        cancelQuiet()
        raw.setLength(0)
        rendered = 0
        dedupe.clear()
        if (engine == null) engine = TranslateEngine(SettingsStore(app))

        val ov = overlay ?: ImmersiveOverlay(app).also { overlay = it }
        ov.onClose = { close() }
        ov.clearViews()
        ov.setItems(items)
        if (!ov.ensure()) {
            DiagLog.log("imm", "叠层 addView 失败 → 放弃（不置 active，球照常可用）")
            android.widget.Toast.makeText(app, "沉浸式叠层挂不上（悬浮窗权限？）", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        active = true
        basePkg = pkg
        baseWinId = winId
        startedAt = android.os.SystemClock.elapsedRealtime()
        handler.postDelayed({
            if (active && !ov.visible) {
                DiagLog.log("imm", "叠层未 attach → 复位 active")
                close()
            }
        }, 2500)

        val isMt = SettingsStore(app).model.startsWith("qwen-mt")
        DiagLog.log("imm", "coordinator.start 条目=${items.size} MT=$isMt")
        if (isMt) {
            // MT 模型不支持结构化 → 直接逐块
            runBlocks(app, items, items.map { it.index })
        } else {
            runStructured(app, items)
        }
    }

    // ---------------- 路径一：结构化单请求 ----------------

    private fun runStructured(app: Context, items: List<ImmersiveItem>) {
        val sb = StringBuilder()
        items.forEach { sb.append('⟦').append(it.index).append('⟧').append(it.original).append('\n') }
        val content = sb.toString().trimEnd()
        val ov = overlay ?: return
        val mySeq = ++seq
        var flushPosted = false
        val alive = java.util.concurrent.atomic.AtomicBoolean(false)
        val flush = Runnable {
            flushPosted = false
            if (mySeq != seq) return@Runnable
            val m = parseNumbered(synchronized(raw) { raw.toString() }, items.size)
            ov.render(m)
            rendered = items.count { !m[it.index].isNullOrBlank() }
        }
        val watchdog = Runnable {
            if (mySeq == seq && !alive.get()) {
                DiagLog.log("imm", "结构化 15s 无任何返回 → 降级逐块")
                seq++                       // 先失效：否则 cancel 触发的 onDone 会再降级一次（双跑）
                call?.cancel()
                call = null
                handler.post { runBlocks(app, items, items.map { it.index }) }
            }
        }
        handler.postDelayed(watchdog, 15000)

        call = engine!!.translateImmersive(
            content,
            onDelta = { d ->
                synchronized(raw) { raw.append(d) }
                if (!flushPosted) {
                    flushPosted = true
                    handler.postDelayed(flush, 50)
                }
            },
            onAlive = { alive.set(true) },
            onDone = { err ->
                handler.post {
                    handler.removeCallbacks(watchdog)
                    if (mySeq != seq) return@post
                    if (flushPosted) {
                        handler.removeCallbacks(flush)
                        flush.run()
                    }
                    call = null
                    val map = parseNumbered(synchronized(raw) { raw.toString() }, items.size)
                    val missing = items.filter { map[it.index].isNullOrBlank() }.map { it.index }
                    val hit = items.size - missing.size
                    DiagLog.log("imm", "结构化结束 err=${err?.message} 命中=$hit/${items.size}")
                    if (missing.isEmpty()) return@post
                    if (hit.toFloat() / items.size >= MIN_HIT_RATIO && err == null) {
                        // 大部分拿到了，只补缺的
                        DiagLog.log("imm", "仅补缺块 ${missing.size} 个")
                        runBlocks(app, items, missing)
                    } else {
                        DiagLog.log("imm", "命中不足/出错 → 全部降级逐块")
                        runBlocks(app, items, items.map { it.index })
                    }
                }
            },
            onCallCreated = { c -> if (mySeq == seq) call = c }
        )
    }

    // ---------------- 路径二：逐块独立请求（对位不依赖模型） ----------------

    private fun runBlocks(app: Context, items: List<ImmersiveItem>, todoIn: List<Int>) {
        val ov = overlay ?: return
        val mySeq = seq
        val byIndex = items.associateBy { it.index }
        // 相同文本合并 → 同屏重复块只发一次请求
        val groups = LinkedHashMap<String, MutableList<Int>>()
        for (i in todoIn) {
            val it0 = byIndex[i] ?: continue
            groups.getOrPut(norm(it0.original)) { mutableListOf() }.add(i)
        }
        val queue = ArrayDeque<Pair<String, MutableList<Int>>>()
        groups.forEach { (k, v) -> queue.add(k to v) }
        var active = 0
        DiagLog.log("imm", "逐块开始：${todoIn.size} 块 → ${queue.size} 组（并发 $MAX_CONCURRENT）")

        fun pump() {
            while (active < MAX_CONCURRENT && queue.isNotEmpty()) {
                val (key, idxs) = queue.removeFirst()
                val sample = byIndex[idxs.first()] ?: continue
                val cached = dedupe[key]
                if (cached != null) {
                    idxs.forEach { ov.render(mapOf(it to cached)) }
                    rendered += idxs.size
                    continue
                }
                active++
                val sb = StringBuilder()
                var lastFlush = 0L
                engine!!.translateBlock(
                    sample.original,
                    onDelta = { d ->
                        synchronized(sb) { sb.append(d) }
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastFlush >= 60L) {
                            lastFlush = now
                            val t = stripMarkers(synchronized(sb) { sb.toString() }).trim()
                            if (t.isNotEmpty()) handler.post {
                                // ⚠️ 必须回主线程：OkHttp 线程不能碰 View（否则必崩）
                                if (mySeq == seq) idxs.forEach { ov.render(mapOf(it to t)) }
                            }
                        }
                    },
                    onAlive = {},
                    onDone = { err ->
                        handler.post {
                            if (mySeq != seq) return@post
                            active--
                            val t = stripMarkers(synchronized(sb) { sb.toString() }).trim()
                            if (err == null && t.isNotEmpty()) {
                                dedupe[key] = t
                                idxs.forEach { ov.render(mapOf(it to t)) }
                                rendered += idxs.size
                            } else if (err != null) {
                                DiagLog.log("imm", "块组失败：${err.message?.take(60)}")
                            }
                            pump()
                            if (active == 0 && queue.isEmpty()) {
                                DiagLog.log("imm", "逐块结束：渲染 $rendered/${items.size}")
                                if (rendered == 0) {
                                    // 逐块也全灭 → 最后兜底：转普通面板翻译（至少给用户一个结果）
                                    val text = items.joinToString("\n") { it.original }
                                    val ctx = appContext
                                    close()
                                    if (ctx != null) TranslateCoordinator.startTranslate(ctx, text)
                                }
                            }
                        }
                    },
                    onCallCreated = { c -> synchronized(blockCalls) { blockCalls.add(c) } }
                )
            }
        }
        pump()
    }

    // ---------------- 收尾 / 生命周期 ----------------

    private fun fallback(items: List<ImmersiveItem>, status: String) {
        DiagLog.log("imm", "回退底部面板：$status")
        val ctx = appContext ?: return
        val rawText = synchronized(raw) { raw.toString() }
        val map = parseNumbered(rawText, items.size)
        close()
        val text = if (map.isNotEmpty())
            items.joinToString("\n") { map[it.index]?.takeIf { s -> s.isNotBlank() } ?: it.original }
        else
            stripMarkers(rawText).trim()
        if (text.isBlank()) return
        try {
            val panel = TranslateCoordinator.overlay(ctx)
            panel.begin(reset = true, status = status)
            panel.append(text)
            panel.finish(null)
        } catch (_: Exception) {
        }
    }

    fun onWindowChanged(pkg: String?, winId: Int = -1) {
        if (!active) return
        val self = appContext?.packageName
        if (pkg == null || self == null || pkg == self) return
        if (android.os.SystemClock.elapsedRealtime() - startedAt < 1500L) return
        val base = basePkg ?: return
        if (pkg != base) {
            DiagLog.log("imm", "换 App → 关闭：pkg=$pkg base=$base")
            close()
        }
        // 注：同 App 内的窗口/内容变化**一律不关**——交给"用户触摸"那条规则（轻响 2026-10-15）
    }

    fun close() {
        DiagLog.log("imm", "close()")
        seq++
        call?.cancel()
        call = null
        synchronized(blockCalls) {
            blockCalls.forEach { try { it.cancel() } catch (_: Exception) {} }
            blockCalls.clear()
        }
        overlay?.close()
        active = false
        basePkg = null
        raw.setLength(0)
    }

    private fun cancelQuiet() {
        seq++
        call?.cancel()
        call = null
        synchronized(blockCalls) {
            blockCalls.forEach { try { it.cancel() } catch (_: Exception) {} }
            blockCalls.clear()
        }
        overlay?.close()
        active = false
    }

    // ---------------- 工具 ----------------

    private fun norm(s: String) = s.trim().lowercase().replace("\\s+".toRegex(), " ")

    private fun stripMarkers(s: String) = s.replace(MARKER, "").trim()

    private fun parseNumbered(s: String, expected: Int = 0): Map<Int, String> {
        val out = mutableMapOf<Int, String>()
        var ms = MARKER.findAll(s).toList()
        if (ms.isEmpty()) {
            val fb = MARKER_FALLBACK.findAll(s).toList()
            if (fb.size >= maxOf(3, expected / 2)) ms = fb
        }
        for (i in ms.indices) {
            val n = ms[i].groupValues[1].toIntOrNull() ?: continue
            val start = ms[i].range.last + 1
            val end = if (i + 1 < ms.size) ms[i + 1].range.first else s.length
            if (end < start) continue
            out[n] = s.substring(start, end).trim()
        }
        return out
    }
}
