package com.speedtrans.app.translate

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.speedtrans.app.overlay.ResultOverlay
import com.speedtrans.app.store.SettingsStore
import okhttp3.Call

/**
 * 翻译编排单例：悬浮球触发点（OCR 服务 / 无障碍服务）共用同一条翻译管线。
 * 职责：增量判断、缓存秒回、流式渲染、忙碌忽略（一次只跑一个请求）。
 */
object TranslateCoordinator {

    private var overlay: ResultOverlay? = null
    private var lastSource = ""
    private var lastTranslation = ""

    /** 进行中的请求；完成/失败/取消时置空 */
    private var currentCall: Call? = null

    /** 请求序号：每次发新请求或取消时推进，用于丢弃迟到回调 */
    private var seq = 0

    private var engine: TranslateEngine? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 是否有翻译进行中（悬浮球据此忽略连点） */
    val busy: Boolean get() = currentCall != null

    private fun ensureInit(context: Context) {
        if (engine == null) {
            engine = TranslateEngine(SettingsStore(context.applicationContext))
        }
    }

    fun overlay(context: Context): ResultOverlay =
        overlay ?: ResultOverlay(context.applicationContext).also { overlay = it }

    fun closeOverlay() {
        cancelActive()      // 关面板 = 停止翻译，避免流继续打到已关闭的面板
        overlay?.close()
    }

    val overlayVisible: Boolean get() = overlay?.visible == true

    /** 立刻取消进行中的翻译请求（流式回调占用主线程，关面板前先取消） */
    fun cancelActive() {
        seq++               // 使该请求的迟到回调全部失效
        currentCall?.cancel()
        currentCall = null
    }

    fun startTranslate(context: Context, rawText: String) {
        // 翻译进行中忽略新请求：连点视为未发生，一次只跑第一次的反应
        if (currentCall != null) return

        ensureInit(context)
        val ov = overlay(context)
        ov.ensure()

        val text = rawText
        if (text.length < 2) {
            ov.showStatus("⚠️ 没有抓到屏幕文字")
            return
        }

        if (!SettingsStore(context.applicationContext).isConfigured) {
            ov.showStatus("⚠️ 接口未配置：设置 → 🔌 接口")
            return
        }

        // 1) 内容完全没变：0 请求直接回显
        if (text == lastSource && lastTranslation.isNotEmpty()) {
            ov.showFinished(text.length, lastTranslation)
            return
        }

        // 2) 增量翻译：内容在增长（模型思考持续输出等场景）
        val incremental = lastSource.isNotEmpty() &&
                text.length > lastSource.length &&
                text.startsWith(lastSource)
        val segment = if (incremental) text.substring(lastSource.length) else text

        // 3) 发起新请求（能走到这里必然无进行中请求）
        val mySeq = ++seq
        ov.begin(
            reset = !incremental,
            status = if (incremental) "⚡ 增量 ${segment.length} 字 · 翻译中…"
            else "⚡ 原文 ${text.length} 字 · 翻译中…"
        )

        currentCall = try {
            engine!!.translate(
                segment,
                isContinuation = incremental,
                onDelta = { d -> mainHandler.post { if (mySeq == seq) ov.append(d) } },
                onDone = { err ->
                    mainHandler.post {
                        if (mySeq != seq) return@post   // 迟到回调（请求已被取消）
                        currentCall = null
                        if (err == null) {
                            lastSource = text
                            lastTranslation = ov.currentText()
                            ov.finish(null)
                        } else {
                            ov.finish(err)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            // 接口地址非法等同步异常：不闪退，面板直接报错
            ov.showStatus("✗ 无法发起请求：${e.message?.take(100) ?: "请检查接口地址"}")
            null
        }
    }

    fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("translation", text))
    }
}
