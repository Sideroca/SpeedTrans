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

    /** 成功翻译后追加一条；增量翻译（新原文延续上一条）替换上一条。原文只存摘要，译文全量 */
    fun append(context: Context, task: String, source: String, text: String) {
        if (text.isBlank()) return
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

    fun clear(context: Context) {
        try {
            file(context).delete()
        } catch (_: Exception) {
        }
    }
}
