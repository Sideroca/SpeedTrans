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
 *   这是系统限制，任何工具都无法越过。
 */
object TextCollector {

    private const val MAX_NODES = 800
    private const val MAX_DEPTH = 80

    fun collect(root: AccessibilityNodeInfo): String {
        val found = ArrayList<RectText>()
        try {
            dfs(root, 0, found)
        } catch (_: Exception) {
        }
        if (found.isEmpty()) return ""

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
        return out.joinToString("\n")
    }

    private fun dfs(node: AccessibilityNodeInfo, depth: Int, out: ArrayList<RectText>) {
        if (depth > MAX_DEPTH || out.size > MAX_NODES) return
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val t = when {
            !text.isNullOrBlank() -> text
            !desc.isNullOrBlank() -> desc
            else -> null
        }
        if (t != null && node.isVisibleToUser) {
            val r = Rect()
            node.getBoundsInScreen(r)
            if (r.width() > 0 && r.height() > 0 && r.top >= 0) {
                out.add(RectText(t, r))
            }
        }
        for (i in 0 until node.childCount) {
            val c = try {
                node.getChild(i)
            } catch (_: Exception) {
                continue
            } ?: continue
            try {
                dfs(c, depth + 1, out)
            } catch (_: Exception) {
            }
        }
    }

    private class RectText(val text: String, val rect: Rect)
}
