package com.speedtrans.app.ui

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

/**
 * 包含式匹配的下拉适配器：输入任意片段即可命中（浏览器式联想），
 * 不受 ArrayAdapter 默认「前缀过滤」的限制（踩坑速查 #4）。
 *
 * 2026-10-01 从 `SettingsActivity.kt` 尾部**原样搬出**（拆 SettingsActivity 第 1 步），逻辑一行未动。
 * （历史上这里出过一次「同文件重复声明」的编译阻断，见《闪译-兼容性审计》：P0-1 ContainsAdapter 重名）
 */
internal class ContainsAdapter(
    context: Context,
    private val originals: List<String>,
    private val keys: List<String> = originals
) : ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, originals) {

    private var shown: List<String> = originals

    override fun getCount() = shown.size

    override fun getItem(position: Int): String = shown[position]

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): Filter.FilterResults {
            val q = (constraint?.toString() ?: "").trim()
            val list = if (q.isEmpty()) originals else originals.indices
                .filter { i ->
                    originals[i].contains(q, true) ||
                            (i < keys.size && keys[i].contains(q, true))
                }
                .map { originals[it] }
            return Filter.FilterResults().apply { values = list; count = list.size }
        }

        override fun publishResults(constraint: CharSequence?, results: Filter.FilterResults) {
            shown = results.values as? List<String> ?: originals
            if (shown.isNotEmpty()) notifyDataSetChanged() else notifyDataSetInvalidated()
        }
    }
}
