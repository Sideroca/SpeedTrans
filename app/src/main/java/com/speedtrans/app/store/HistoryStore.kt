package com.speedtrans.app.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 翻译历史：纯本地 JSON 文件（filesDir/history.json），最多 200 条，FIFO。
 * 增量翻译（新原文延续上一条）会替换上一条，避免刷屏。
 * 零权限、零依赖；入口在主界面（悬浮页空间留给极速）。
 */
object HistoryStore {

    /** 条数上限：200 条约 100~200KB JSON，重写开销可忽略 */
    private const val MAX = 200

    /** 增量合并窗口：同任务 + 新原文延续上一条 + 10 分钟内 → 视为同一次翻译 */
    private const val MERGE_WINDOW_MS = 10 * 60 * 1000L

    /**
     * @param source 原文摘要（只存前 80 字，控体积）
     * @param srcLen **完整原文**长度——延续判据要用它，不能拿摘要长度凑数
     */
    data class Entry(
        val time: Long, val task: String, val source: String, val text: String,
        val srcLen: Int = 0
    )

    private fun file(context: Context) = File(context.filesDir, "history.json")

    /**
     * 读-改-写必须**串行**（并发 append 会互相覆盖），而且整份开销要**挪出主线程**。
     *
     * 2026-10-01：`append()` 的调用点在 `TranslateCoordinator` 的主线程回调里，而它做的是
     * "读整份 JSON + 重建 + 写回整份 JSON" —— 条目短时无感（注释原来按 200 条 100~200KB 估的），
     * 但**单字翻译的译文很长**时，文件轻松到 1~2MB，等于每次翻译成功都在主线程干几百毫秒。
     * 首字 1~2 秒是生命线，这个开销不该压在它旁边。
     */
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "st-history").apply { isDaemon = true }
    }

    fun load(context: Context): List<Entry> = try {
        val arr = JSONArray(file(context).readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val src = o.optString("src", "")
            // 老数据没有 srcLen → 退回摘要长度（等价于改动前的行为，不会误判）
            Entry(o.optLong("t"), o.optString("task", "翻译"), src, o.optString("text", ""),
                o.optInt("srcLen", src.length))
        }
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * 成功翻译后追加一条；增量翻译（新原文延续上一条）替换上一条。原文只存摘要，译文全量。
     * **立即返回**，真正的读改写丢给后台单线程（见 [io]）。
     */
    fun append(context: Context, task: String, source: String, text: String) {
        if (text.isBlank()) return
        val app = context.applicationContext
        io.execute { appendNow(app, task, source, text) }
    }

    private fun appendNow(context: Context, task: String, source: String, text: String) {
        val list = ArrayDeque(load(context))
        val now = System.currentTimeMillis()
        val last = list.firstOrNull()
        if (last != null &&
            last.task == task &&
            now - last.time < MERGE_WINDOW_MS &&
            source.length > last.srcLen &&        // 比的是"完整原文长度"，不是被 take(80) 截断的摘要
            source.startsWith(last.source)
        ) {
            list.removeFirst()   // 同一次翻译的延续 → 合并（保留最新）
        }
        list.addFirst(Entry(now, task, source.take(80), text, source.length))
        while (list.size > MAX) list.removeLast()
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("t", e.time)
                    .put("task", e.task)
                    .put("src", e.source)
                    .put("text", e.text)
                    .put("srcLen", e.srcLen)
            )
        }
        try {
            file(context).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    /** 清空。同样走 [io] 串行——否则"清空"和正在排队的 append 会打架（清完又被写回来） */
    fun clear(context: Context) {
        val app = context.applicationContext
        io.execute {
            try {
                file(app).delete()
            } catch (_: Exception) {
            }
        }
    }
}
