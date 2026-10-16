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
        // 注意：这里**不能** tiny.recycle()——process() 是异步的，位图可能在后台线程才被读。
        // 16×16 = 1KB，回收零收益、风险实在，索性留着。
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

    /**
     * 沉浸式：识别并返回"**逐行 + 坐标**"（位图坐标），供译文原位贴合。
     * 与 recognize() 的区别：**不做同行合并**，保留每行自己的 boundingBox。
     */
    fun recognizeLines(
        context: Context,
        bitmap: Bitmap,
        excludeRects: List<Rect>,
        onResult: (List<Line>) -> Unit,
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
            var err: Exception? = e
            synchronized(lock) {
                settled++
                if (ok) success++
                if (e != null) lastError = e
                allDone = settled >= total
                err = lastError
            }
            if (!allDone) return
            if (success == 0) {
                onFail(err)
            } else {
                val snapshot = synchronized(lock) { ArrayList(lines) }
                onResult(mergeLines(snapshot, excludeRects))
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

    /** 带屏幕坐标的识别行（box 为**位图坐标**，可能为 null） */
    data class Line(val text: String, val box: Rect?)

    /** 跨识别器去重 → 剔除文本层区域 → 按位置排序 → **逐行**返回（不与同行合并） */
    private fun mergeLines(raw: List<OcrLine>, exclude: List<Rect>): List<Line> {
        // 1) 跨识别器去重（优先级高者先入；框重叠≥50% 或同文判重）
        val kept = ArrayList<OcrLine>()
        val boxes = ArrayList<Rect>()
        val texts = ArrayList<String>()
        raw.sortedBy { it.prio }.forEach { l ->
            val box = l.line.boundingBox
            val t = norm(l.line.text)
            // 2026-10-15：网页常有"叠字/描边字"被识别成多个错位框 → 阈值放到 0.3，
            // 并加"包含式"判重（一段是另一段的子串），否则同一句会画出好几份
            val dup = (box != null && boxes.any { iou(it, box) >= 0.3f }) ||
                    (t.isNotEmpty() && texts.any { it.contains(t) || t.contains(it) })
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
        // 3) 按位置排序 → 逐行返回
        return survivors
            .sortedWith(compareBy({ it.line.boundingBox?.top ?: 0 }, { it.line.boundingBox?.left ?: 0 }))
            .mapNotNull { l ->
                val t = l.line.text.trim()
                if (t.isEmpty()) null else Line(t, l.line.boundingBox)
            }
    }

    /** 沉浸式 · 一个"块"（≈段落/标题/按钮行）：整段文本 + 外接矩形 + 行高（供字号推断） */
    data class Block(val text: String, val rect: Rect?, val lineHeightPx: Int)

    /**
     * 沉浸式：按 **ML Kit TextBlock（段落级）** 识别。
     * 相比逐行：块数少得多 → 结构化对位更稳、译文读起来是"一段话"而不是碎片。
     */
    fun recognizeBlocks(
        context: Context,
        bitmap: Bitmap,
        excludeRects: List<Rect>,
        onResult: (List<Block>) -> Unit,
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

        val blocks = ArrayList<OcrBlock>()
        val total = tagged.size
        var settled = 0
        var success = 0
        var lastError: Exception? = null
        val lock = Any()

        fun onOneSettled(ok: Boolean, e: Exception?) {
            val allDone: Boolean
            var err: Exception? = e
            synchronized(lock) {
                settled++
                if (ok) success++
                if (e != null) lastError = e
                allDone = settled >= total
                err = lastError
            }
            if (!allDone) return
            if (success == 0) {
                onFail(err)
            } else {
                val snapshot = synchronized(lock) { ArrayList(blocks) }
                onResult(mergeBlocks(snapshot, excludeRects))
            }
        }

        tagged.forEach { (lang, rec) ->
            rec.process(image)
                .addOnSuccessListener { vision ->
                    val prio = priority(lang)
                    synchronized(lock) {
                        vision.textBlocks.forEach { blocks.add(OcrBlock(it, prio)) }
                    }
                    onOneSettled(true, null)
                }
                .addOnFailureListener { e ->
                    onOneSettled(false, e)
                }
        }
    }

    /** 带识别器优先级的识别块 */
    private data class OcrBlock(val block: Text.TextBlock, val prio: Int)

    /** 跨识别器去重（同文或框重叠≥50%）→ 剔除系统栏/文本层区域 → 排序 → 合并为 Block */
    private fun mergeBlocks(raw: List<OcrBlock>, exclude: List<Rect>): List<Block> {
        val kept = ArrayList<OcrBlock>()
        val boxes = ArrayList<Rect>()
        val texts = ArrayList<String>()
        raw.sortedBy { it.prio }.forEach { b ->
            val box = b.block.boundingBox
            val t = norm(joinLines(b.block.lines))
            // 2026-10-15：网页常有"叠字/描边字"被识别成多个错位框 → 阈值放到 0.3，
            // 并加"包含式"判重（一段是另一段的子串），否则同一句会画出好几份
            val dup = (box != null && boxes.any { iou(it, box) >= 0.3f }) ||
                    (t.isNotEmpty() && texts.any { it.contains(t) || t.contains(it) })
            if (!dup) {
                kept.add(b)
                if (box != null) boxes.add(box)
                if (t.isNotEmpty()) texts.add(t)
            }
        }
        // 剔除落在系统栏/文本层区域内的块（以"块中心点"判定，避免误伤跨界大块）
        val survivors = kept.filter { b ->
            val box = b.block.boundingBox ?: return@filter true
            val cx = (box.left + box.right) / 2
            val cy = (box.top + box.bottom) / 2
            exclude.none { e -> cx in e.left..e.right && cy in e.top..e.bottom }
        }
        return survivors
            .sortedWith(compareBy({ it.block.boundingBox?.top ?: 0 }, { it.block.boundingBox?.left ?: 0 }))
            .let { list ->
                var dropConf = 0
                var dropGeom = 0
                val out = ArrayList<Block>()
                for (b in list) {
                    val t = joinLines(b.block.lines).trim()
                    if (t.isEmpty()) continue
                    // 置信度（0 = 该识别器不提供 → 不判；阈值 0.45，CJK 普遍偏低，别用 0.55 整屏误杀）
                    val confs = b.block.lines.map { it.confidence }.filter { it > 0.01f }
                    val conf = if (confs.isEmpty()) 0f else confs.sum() / confs.size
                    if (conf > 0.01f && conf < 0.45f) {
                        dropConf++; continue
                    }
                    if (!keepText(t, b.block.boundingBox)) {
                        dropGeom++; continue
                    }
                    val hs = b.block.lines.mapNotNull { it.boundingBox?.height() }.filter { it > 0 }.sorted()
                    val lh = if (hs.isEmpty()) 0 else hs[hs.size / 2]
                    out.add(Block(t, b.block.boundingBox, lh))
                }
                if (dropConf + dropGeom > 0)
                    com.speedtrans.app.util.DiagLog.log("ocr", "沉浸式过滤：低置信=$dropConf 几何=$dropGeom 保留=${out.size}")
                out
            }
    }

    /** 块内多行合并为一段话：CJK 相邻不加空格，拉丁词间加空格 */
    private fun joinLines(lines: List<Text.Line>): String {
        val sb = StringBuilder()
        for (l in lines) {
            val t = l.text.trim()
            if (t.isEmpty()) continue
            if (sb.isNotEmpty()) {
                val prev = sb.last()
                val cur = t.first()
                if (!isCjk(prev) && !isCjk(cur)) sb.append(' ')
            }
            sb.append(t)
        }
        return sb.toString()
    }

    private fun isCjk(c: Char): Boolean = try {
        when (Character.UnicodeBlock.of(c)) {
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION,
            Character.UnicodeBlock.HIRAGANA,
            Character.UnicodeBlock.KATAKANA,
            Character.UnicodeBlock.HANGUL_SYLLABLES,
            Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS -> true
            else -> false
        }
    } catch (_: Exception) {
        false
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

    /**
     * 乱码/图标过滤（2026-10-14 起，2026-10-15 加强）。
     *
     * ML Kit 会把**图标**当文字（实测：≡ 菜单 →「三」、✕ →「X」、▽ →「V」…），
     * 光按字符类型判不出来 —— 故三条组合：
     *  ① looksLikeText：有字母/汉字，且"意义字符"占比 ≥0.6；
     *  ② hasLetter：至少一个**字母或汉字**（纯数字/纯标点 → 无需翻译，丢）；
     *  ③ isIconLike：**短 + 近方形** 视为图标字形（单个字符一律丢；≤3 字符且宽高比 0.6~1.6 也丢）。
     */
    /** 沉浸式专用：几何 + 字符类型三重把关（仅识图路径**不用**这些，避免误杀正常小标签） */
    private fun keepText(t: String, box: Rect?): Boolean {
        if (t.isEmpty()) return false
        if (!looksLikeText(t)) return false
        if (!hasLetter(t)) return false
        if (isIconLike(t, box)) return false
        if (isThinStrip(box)) return false
        return true
    }

    /** 细长竖条（宽/高 < 0.45）→ 分隔线 / 装饰条 / 图标，不是正文 */
    private fun isThinStrip(box: Rect?): Boolean {
        if (box == null) return false
        val h = box.height()
        if (h <= 0) return false
        return box.width().toFloat() / h < 0.45f
    }

    private fun hasLetter(s: String): Boolean {
        for (c in s) if (c.isLetter()) return true
        return false
    }

    /** 图标字形判定：短 + 近方形（文字行通常"宽＞高"，图标点阵才近方形） */
    private fun isIconLike(t: String, box: Rect?): Boolean {
        if (t.length <= 1) return true          // 单字符：图形/编号，基本不是正文
        if (box == null) return false
        val w = box.width()
        val h = box.height()
        if (h <= 0) return false
        val ar = w.toFloat() / h
        return t.length <= 3 && ar >= 0.6f && ar <= 1.6f
    }

    private fun looksLikeText(s: String): Boolean {
        if (s.isEmpty()) return false
        var meaningful = 0
        var letterish = 0
        var total = 0
        for (c in s) {
            total++
            when {
                c.isLetterOrDigit() -> {
                    meaningful++; letterish++
                }
                c.isWhitespace() -> meaningful++
                c in ".,!?:;'\"-()[]/\\%$¥€£+_=@#&*·。，、！？：；（）【】《》“”‘’…—～" -> meaningful++
                else -> {
                }
            }
        }
        if (total == 0 || letterish == 0) return false
        return meaningful.toFloat() / total >= 0.6f
    }

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
            // 2026-10-15：网页常有"叠字/描边字"被识别成多个错位框 → 阈值放到 0.3，
            // 并加"包含式"判重（一段是另一段的子串），否则同一句会画出好几份
            val dup = (box != null && boxes.any { iou(it, box) >= 0.3f }) ||
                    (t.isNotEmpty() && texts.any { it.contains(t) || t.contains(it) })
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
            if (t.isEmpty() || !looksLikeText(t)) continue   // 仅识图路径：只滤"符号串"，不做几何/置信度误杀
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
