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
 * 职责：增量判断、缓存秒回、流式渲染、取消旧请求。
 */
object TranslateCoordinator {

    private var overlay: ResultOverlay? = null
    private var lastSource = ""
    private var lastTranslation = ""
    private var currentCall: Call? = null
    private var engine: TranslateEngine? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun ensureInit(context: Context) {
        if (engine == null) {
            engine = TranslateEngine(SettingsStore(context.applicationContext))
        }
    }

    fun overlay(context: Context): ResultOverlay =
        overlay ?: ResultOverlay(context.applicationContext).also { overlay = it }

    fun closeOverlay() {
        overlay?.close()
    }

    val overlayVisible: Boolean get() = overlay?.visible == true

    /** 立刻取消进行中的翻译请求（流式回调占用主线程，关面板前先取消） */
    fun cancelActive() {
        currentCall?.cancel()
        currentCall = null
    }

    fun startTranslate(context: Context, rawText: String) {
        ensureInit(context)
        val ov = overlay(context)
        ov.ensure()

        val text = rawText
        if (text.length < 2) {
            ov.showStatus("⚠️ 没有抓到屏幕文字")
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

        // 3) 取消旧请求，立刻发新的
        currentCall?.cancel()
        ov.begin(
            reset = !incremental,
            status = if (incremental) "⚡ 增量 ${segment.length} 字 · 翻译中…"
            else "⚡ 原文 ${text.length} 字 · 翻译中…"
        )

        currentCall = engine!!.translate(
            segment,
            isContinuation = incremental,
            onDelta = { d -> mainHandler.post { ov.append(d) } },
            onDone = { err ->
                mainHandler.post {
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
    }

    fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("translation", text))
    }
}
