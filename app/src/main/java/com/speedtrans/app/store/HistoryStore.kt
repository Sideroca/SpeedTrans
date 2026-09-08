package com.speedtrans.app.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 翻译历史：纯本地 JSON 文件（filesDir/history.json），最多 50 条，FIFO。
 * 零权限、零依赖；入口在主界面（悬浮页空间留给极速）。
 */
object HistoryStore {

    private const val MAX = 50

    data class Entry(val time: Long, val task: String, val source: String, val text: String)

    private fun file(context: Context) = File(context.filesDir, "history.json")

    fun load(context: Context): List<Entry> = try {
        val arr = JSONArray(file(context).readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(o.optLong("t"), o.optString("task", "翻译"), o.optString("src", ""), o.optString("text", ""))
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** 成功翻译后追加一条；原文只存摘要，译文全量 */
    fun append(context: Context, task: String, source: String, text: String) {
        if (text.isBlank()) return
        val list = ArrayDeque(load(context))
        list.addFirst(Entry(System.currentTimeMillis(), task, source.take(80), text))
        while (list.size > MAX) list.removeLast()
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("t", e.time)
                    .put("task", e.task)
                    .put("src", e.source)
                    .put("text", e.text)
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
