package com.speedtrans.app.translate

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * 遍历无障碍节点树，按屏幕位置整理出可读文本。
 *
 * 特性：
 * - 长文本 TextView 节点天然携带全文（即使只有部分在屏内），因此
 *   LLM 思考内容 / 网页正文等场景会一次抓到整段文字；
 * - RecyclerView 虚拟化列表（Twitter/YouTube 时间线）只含有可见项，
 *   这是系统限制，任何工具都无法越过；
 * - collectWithRects 额外返回文本层坐标，供 OCR 剔除重复区域。
 */
object TextCollector {

    private const val MAX_NODES = 800
    private const val MAX_DEPTH = 80

    /** 抓取结果：文本 + 文本层节点坐标（供 OCR 剔除重复） */
    data class Collected(val text: String, val rects: List<Rect>)

    private class RectText(val text: String, val rect: Rect)

    fun collect(root: AccessibilityNodeInfo): String = collectWithRects(root).text

    /**
     * @param excludePackage 跳过该包名的节点子树。本应用的译文面板是可聚焦窗口，
     *                       可能成为活动窗口，不排除会把面板自身 UI 文字当原文抓走
     */
    fun collectWithRects(
        root: AccessibilityNodeInfo,
        excludePackage: String? = null,
        /** 只翻这个 y 以下（屏幕坐标）；默认 0 = 当前屏幕 + 下方，屏幕上方不要 */
        topLimit: Int = 0,
        /** 只翻到这个 y（默认不限）；普通模式传“当前屏 + 半屏”，避免把整篇文章全发出去 */
        bottomLimit: Int = Int.MAX_VALUE
    ): Collected {
        val found = ArrayList<RectText>()
        try {
            dfs(root, 0, found, excludePackage, topLimit, bottomLimit)
        } catch (_: Exception) {
        }
        if (found.isEmpty()) return Collected("", emptyList())

        // 去除同位置同文本的重复节点（父容器与子节点重复携带）
        val seen = HashSet<String>()
        val uniq = ArrayList<RectText>(found.size)
        for (rt in found) {
            val key = "${rt.text}@${rt.rect.left},${rt.rect.top},${rt.rect.width()}x${rt.rect.height()}"
            if (seen.add(key)) uniq.add(rt)
        }

        // 按屏幕位置排序：从上到下、从左到右
        uniq.sortWith(compareBy({ it.rect.top }, { it.rect.left }))

        // 同一视觉行内的碎片合并
        val lines = ArrayList<String>()
        val sb = StringBuilder()
        var lineTop = Int.MIN_VALUE
        var lineH = 0
        for (rt in uniq) {
            val t = rt.text.trim()
            if (t.isEmpty()) continue
            val sameLine = lines.isNotEmpty() && abs(rt.rect.top - lineTop) <= maxOf(8, lineH / 2)
            if (sameLine) {
                sb.append(' ').append(t)
            } else {
                if (sb.isNotEmpty()) {
                    lines.add(sb.toString())
                    sb.setLength(0)
                }
                sb.append(t)
                lineTop = rt.rect.top
                lineH = rt.rect.height()
            }
        }
        if (sb.isNotEmpty()) lines.add(sb.toString())

        // 清理空行与连续重复行
        val out = ArrayList<String>(lines.size)
        for (l in lines) {
            val t = l.trim()
            if (t.isEmpty()) continue
            if (out.isNotEmpty() && out.last() == t) continue
            out.add(t)
        }
        return Collected(out.joinToString("\n"), uniq.map { it.rect })
    }

    private fun dfs(
        node: AccessibilityNodeInfo,
        depth: Int,
        out: ArrayList<RectText>,
        excludePackage: String?,
        topLimit: Int,
        bottomLimit: Int
    ) {
        if (depth > MAX_DEPTH || out.size > MAX_NODES) return
        if (excludePackage != null && node.packageName?.toString() == excludePackage) return
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val raw = when {
            !text.isNullOrBlank() -> text
            !desc.isNullOrBlank() -> desc
            else -> null
        }
        if (raw != null && node.isVisibleToUser) {
            val r = Rect()
            node.getBoundsInScreen(r)
            // 只保留 [topLimit, bottomLimit) 的内容：屏幕上方不翻，屏幕下方只预翻半屏。
            if (r.width() > 0 && r.height() > 0 && r.bottom > topLimit && r.top < bottomLimit) {
                val t = if (r.top < topLimit || r.bottom > bottomLimit)
                    trimWindow(node, raw, r, topLimit, bottomLimit) else raw
                if (!t.isNullOrBlank()) out.add(RectText(t, r))
            }
        }
        for (i in 0 until node.childCount) {
            val c = try {
                node.getChild(i)
            } catch (_: Exception) {
                continue
            } ?: continue
            try {
                dfs(c, depth + 1, out, excludePackage, topLimit, bottomLimit)
            } catch (_: Exception) {
            }
        }
    }

    /** 节点跨出 [topLimit, bottomLimit) 时，用逐字矩形裁掉窗口外的字符；拿不到就按比例估。 */
    private fun trimWindow(
        node: AccessibilityNodeInfo,
        raw: String,
        r: Rect,
        topLimit: Int,
        bottomLimit: Int
    ): String? {
        try {
            val args = android.os.Bundle().apply {
                putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
                putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, raw.length)
            }
            if (node.refreshWithExtraData(
                    AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, args
                )
            ) {
                @Suppress("DEPRECATION")
                val arr = node.extras.getParcelableArray(
                    AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
                )
                if (arr != null && arr.size >= raw.length) {
                    var start = -1
                    var end = -1
                    for (i in 0 until raw.length) {
                        val rf = arr[i] as? android.graphics.RectF ?: continue
                        if (rf.bottom > topLimit && rf.top < bottomLimit) {
                            if (start < 0) start = i
                            end = i
                        }
                    }
                    if (start >= 0 && end >= start) {
                        return raw.substring(start, end + 1).trim()
                    }
                    return null
                }
            }
        } catch (_: Exception) {
        }
        val h = r.height()
        if (h <= 0) return raw
        val startRatio = ((topLimit - r.top).toFloat() / h).coerceIn(0f, 1f)
        val endRatio = ((bottomLimit - r.top).toFloat() / h).coerceIn(0f, 1f)
        val start = (raw.length * startRatio).toInt().coerceIn(0, raw.length)
        val end = (raw.length * endRatio).toInt().coerceIn(start, raw.length)
        if (end <= start) return null
        return raw.substring(start, end).trim()
    }
}
