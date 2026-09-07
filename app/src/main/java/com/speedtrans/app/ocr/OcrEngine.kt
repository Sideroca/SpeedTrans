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
 * - 启用的识别器并行执行，总耗时 ≈ 最慢一个
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
        val recs = langs.mapNotNull { lang ->
            try {
                recognizer(context, lang)
            } catch (_: Exception) {
                null
            }
        }
        if (recs.isEmpty()) {
            onFail(null)
            return
        }

        val lines = ArrayList<Text.Line>()
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

        recs.forEach { rec ->
            rec.process(image)
                .addOnSuccessListener { vision ->
                    synchronized(lock) {
                        vision.textBlocks.forEach { block ->
                            block.lines.forEach { lines.add(it) }
                        }
                    }
                    onOneSettled(true, null)
                }
                .addOnFailureListener { e ->
                    onOneSettled(false, e)
                }
        }
    }

    /** 剔除文本层区域内的行 → 按位置排序 → 同行合并 */
    private fun merge(raw: List<Text.Line>, exclude: List<Rect>): String {
        val kept = raw.filter { line ->
            val b = line.boundingBox ?: return@filter true
            exclude.none { e -> e.left < b.right && b.left < e.right && e.top < b.bottom && b.top < e.bottom }
        }.sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))

        val sb = StringBuilder()
        var lastTop = Int.MIN_VALUE
        var lastH = 0
        for (l in kept) {
            val t = l.text.trim()
            if (t.isEmpty()) continue
            val top = l.boundingBox?.top ?: 0
            val sameRow = sb.isNotEmpty() && abs(top - lastTop) <= max(10, lastH / 2)
            if (sameRow) sb.append(' ').append(t)
            else {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(t)
                lastTop = top
                lastH = l.boundingBox?.height() ?: 0
            }
        }
        return sb.toString().trim()
    }
}
