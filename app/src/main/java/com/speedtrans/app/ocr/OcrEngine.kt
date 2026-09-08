package com.speedtrans.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.speedtrans.app.store.SettingsStore
import kotlin.math.abs
import kotlin.math.max

/**
 * 端侧 OCR 引擎（ML Kit bundled：模型打包在 APK 内，本地识别、不上传）。
 * - 五个文字体系模型全部打包（拉丁/中文/日文/韩文/天城文），设置页勾选启用
 * - 启用的识别器并行执行，总耗时 ≈ 最慢一个；跨识别器去重（框重叠≥50% 或同文，按 中文>日文>韩文>拉丁 留一份）
 * - excludeRects：无障碍文本层坐标——落在其中的识别行被剔除，
 *   使 OCR 结果只包含"像素层内容"（游戏/图片/视频字幕），与文本路零重复
 */
object OcrEngine {

    private val recognizers = HashMap<String, TextRecognizer>()

    private fun recognizer(context: Context, lang: String): TextRecognizer =
        synchronized(recognizers) {
            recognizers.getOrPut(lang) {
                when (lang) {
                    "chinese" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                    "japanese" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                    "korean" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                    "devanagari" -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
                    else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                }
            }
        }

    /**
     * 识别截屏中的文字（回调在主线程）。
     * @param excludeRects 文本层节点坐标，其内的识别行将被剔除
     */
    /** 预热：空图跑一遍启用语言的识别器，提前完成模型加载（消除首次识图冷启动） */
    fun warmUp(context: Context) {
        val tiny = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val image = InputImage.fromBitmap(tiny, 0)
        SettingsStore(context).ocrLanguages.forEach { lang ->
            try {
                recognizer(context, lang).process(image)
            } catch (_: Exception) {
            }
        }
    }

    fun recognize(
        context: Context,
        bitmap: Bitmap,
        excludeRects: List<Rect>,
        onResult: (String) -> Unit,
        onFail: (Exception?) -> Unit
    ) {
        val langs = SettingsStore(context).ocrLanguages
        if (langs.isEmpty()) {
            onFail(null)
            return
        }
        val image = InputImage.fromBitmap(bitmap, 0)
        val tagged = langs.mapNotNull { lang ->
            try {
                recognizer(context, lang)?.let { lang to it }
            } catch (_: Exception) {
                null
            }
        }
        if (tagged.isEmpty()) {
            onFail(null)
            return
        }

        val lines = ArrayList<OcrLine>()
        val total = tagged.size
        var settled = 0
        var success = 0
        var lastError: Exception? = null
        val lock = Any()

        fun onOneSettled(ok: Boolean, e: Exception?) {
            val allDone: Boolean
            var ok2 = ok
            var err: Exception? = e
            synchronized(lock) {
                settled++
                if (ok) success++
                if (e != null) lastError = e
                allDone = settled >= total
                ok2 = ok
                err = lastError
            }
            if (!allDone) return
            if (success == 0) {
                onFail(err)
            } else {
                val snapshot = synchronized(lock) { ArrayList(lines) }
                onResult(merge(snapshot, excludeRects))
            }
        }

        tagged.forEach { (lang, rec) ->
            rec.process(image)
                .addOnSuccessListener { vision ->
                    val prio = priority(lang)
                    synchronized(lock) {
                        vision.textBlocks.forEach { block ->
                            block.lines.forEach { lines.add(OcrLine(it, prio)) }
                        }
                    }
                    onOneSettled(true, null)
                }
                .addOnFailureListener { e ->
                    onOneSettled(false, e)
                }
        }
    }

    /** 带识别器优先级的识别行 */
    private data class OcrLine(val line: Text.Line, val prio: Int)

    /** 识别器优先级：中文 > 日文 > 韩文 > 拉丁（多模型同框争用时的仲裁依据） */
    private fun priority(lang: String) = when (lang) {
        "chinese" -> 0
        "japanese" -> 1
        "korean" -> 2
        else -> 3
    }

    /** 归一化文本：去空格（含全角）、拉丁小写——跨识别器判重用 */
    private fun norm(s: String) = s.replace(" ", "").replace("\u3000", "").lowercase()

    /** 两框交并比 IoU */
    private fun iou(a: Rect, b: Rect): Float {
        val ix = maxOf(0, minOf(a.right, b.right) - maxOf(a.left, b.left))
        val iy = maxOf(0, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))
        val inter = ix.toLong() * iy
        if (inter <= 0L) return 0f
        val union = a.width().toLong() * a.height() + b.width().toLong() * b.height() - inter
        return if (union <= 0L) 0f else inter.toFloat() / union
    }

    /** 跨识别器去重 → 剔除文本层区域内的行 → 按位置排序 → 同行合并 */
    private fun merge(raw: List<OcrLine>, exclude: List<Rect>): String {
        // 1) 跨识别器去重：优先级高者先入列；后来者若框重叠≥50% 或归一化文本相同 → 判为重复
        val kept = ArrayList<OcrLine>()
        val boxes = ArrayList<Rect>()
        val texts = ArrayList<String>()
        raw.sortedBy { it.prio }.forEach { l ->
            val box = l.line.boundingBox
            val t = norm(l.line.text)
            val dup = (box != null && boxes.any { iou(it, box) >= 0.5f }) ||
                    (t.isNotEmpty() && texts.contains(t))
            if (!dup) {
                kept.add(l)
                if (box != null) boxes.add(box)
                if (t.isNotEmpty()) texts.add(t)
            }
        }
        // 2) 剔除文本层区域内的行（与无障碍文本路零重复）
        val survivors = kept.filter { l ->
            val b = l.line.boundingBox ?: return@filter true
            exclude.none { e -> e.left < b.right && b.left < e.right && e.top < b.bottom && b.top < e.bottom }
        }
        // 3) 按位置排序 → 同行合并
        val ordered = survivors.sortedWith(
            compareBy({ it.line.boundingBox?.top ?: 0 }, { it.line.boundingBox?.left ?: 0 })
        )
        val sb = StringBuilder()
        var lastTop = Int.MIN_VALUE
        var lastH = 0
        for (l in ordered) {
            val t = l.line.text.trim()
            if (t.isEmpty()) continue
            val top = l.line.boundingBox?.top ?: 0
            val sameRow = sb.isNotEmpty() && abs(top - lastTop) <= max(10, lastH / 2)
            if (sameRow) sb.append(' ').append(t)
            else {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(t)
                lastTop = top
                lastH = l.line.boundingBox?.height() ?: 0
            }
        }
        return sb.toString().trim()
    }
}
