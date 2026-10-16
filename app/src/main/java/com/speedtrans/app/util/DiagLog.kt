package com.speedtrans.app.util

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 轻量诊断日志（内存环形缓冲，2026-10-15）。
 *
 * 用途：手机端排查 —— **长按设置页标题**即可弹出（内存里最近 400 条）。
 * 只记"关键步骤 / 计数 / 异常"，不记原文，不落盘（隐私无碍）。
 */
object DiagLog {

    private const val MAX = 400
    private val buf = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(tag: String, msg: String) {
        if (buf.size >= MAX) buf.removeFirst()
        buf.addLast("${fmt.format(Date())} [$tag] $msg")
    }

    @Synchronized
    fun dump(): String = buf.joinToString("\n")

    @Synchronized
    fun clear() {
        buf.clear()
    }

    @Synchronized
    fun size(): Int = buf.size
}
