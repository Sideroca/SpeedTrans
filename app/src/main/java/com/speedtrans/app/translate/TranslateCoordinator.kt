package com.speedtrans.app.translate

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.speedtrans.app.overlay.ResultOverlay
import com.speedtrans.app.store.HistoryStore
import com.speedtrans.app.store.SettingsStore
import okhttp3.Call

/**
 * 翻译编排单例：悬浮球触发点（OCR 服务 / 无障碍服务）共用同一条翻译管线。
 * 职责：增量判断、缓存秒回、流式渲染、忙碌忽略（一次只跑一个请求）。
 */
object TranslateCoordinator {

    /** 累积模式：译文块之间的分隔线 */
    private const val BLOCK_SEP = "\n\n"   // 仅空行分隔：不再出现横线（用户钦定，识图/文本统一）

    /** 单例面板（持有 applicationContext，不泄漏 Activity）。lint 静态持有告警在此为误报 */
    @SuppressLint("StaticFieldLeak")
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

    private var lastAutoCloseAt = 0L

    /** 窗外触摸（原文区）触发：取消翻译 + 关面板 + 记时间戳（防球上 UP 变相复活连点重翻） */
    fun onOutsideTouch() {
        lastAutoCloseAt = SystemClock.elapsedRealtime()
        cancelActive()
        overlay?.close()
    }

    /** 面板刚被窗外触摸关闭（400ms 内）——悬浮球据此忽略收尾点击 */
    fun recentlyAutoClosed(): Boolean =
        SystemClock.elapsedRealtime() - lastAutoCloseAt < 400L

    val overlayVisible: Boolean get() = overlay?.visible == true

    /** 距面板边缘实时预览（设置页滑条拖动时调用） */
    fun liveEdgePadding(padDp: Int) {
        overlay?.applyEdgePadding(padDp)
    }

    /** 译文文字大小实时生效（设置页滑条拖动时调用） */
    fun liveTextSize(spSize: Int) {
        overlay?.applyTextSize(spSize)
    }

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
        // 关键：必须记录"本次点击之前"面板是否已经开着——ensure() 之后必然为 true，
        // 否则"面板关着→重新打开"会被误判成累积追加（横线bug）/误判成未变不回显（空白bug）
        val wasOpen = ov.visible
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

        val accumulate = SettingsStore(context.applicationContext).panelAccumulate

        // 1) 内容完全没变：0 请求直接回显
        if (text == lastSource && lastTranslation.isNotEmpty()) {
            // 累积模式下"面板本来就开着"：不动内容，只闪状态；面板是这次才打开的 → 照旧回显上次译文
            if (accumulate && wasOpen) {
                if (text.contains("内容过长已截断"))
                    ov.showStatus("⚡ 全文相同（原文超最大字数已截断）· 设置→悬浮可调大")
                else
                    ov.showStatus("⚡ 内容未变 · 未追加（译文累积已开）")
            } else {
                ov.showFinished(text.length, lastTranslation)
            }
            return
        }

        // 2) 增量翻译：内容在增长（模型思考持续输出等场景）
        val incremental = lastSource.isNotEmpty() &&
                text.length > lastSource.length &&
                text.startsWith(lastSource)
        val segment = if (incremental) text.substring(lastSource.length) else text

        // 2.5) 译文累积：面板开着 + 非增量 + 已有内容 → 不清空，追加新块
        val appendBlock = !incremental && accumulate && wasOpen && lastSource.isNotEmpty()

        // 3) 发起新请求（能走到这里必然无进行中请求）
        val mySeq = ++seq
        val beforeLen = if (appendBlock) ov.currentText().length else 0
        when {
            incremental -> ov.begin(reset = false, status = "⚡ 增量 ${segment.length} 字 · 翻译中…")
            appendBlock -> {
                ov.begin(reset = false, status = "⚡ 原文 ${text.length} 字 · 追加翻译中…")
                ov.append(BLOCK_SEP)
            }
            else -> ov.begin(reset = true, status = "⚡ 原文 ${text.length} 字 · 翻译中…")
        }

        currentCall = try {
            val t0 = android.os.SystemClock.elapsedRealtime()
            val buf = StringBuilder()
            var flushPosted = false
            var firstMs = 0L
            val flush = Runnable {
                flushPosted = false
                val s = synchronized(buf) {
                    val x = buf.toString(); buf.setLength(0); x
                }
                if (s.isNotEmpty() && mySeq == seq) {
                    if (firstMs == 0L) firstMs = android.os.SystemClock.elapsedRealtime() - t0
                    ov.append(s)
                }
            }
            engine!!.translate(
                segment,
                isContinuation = incremental,
                onDelta = { d ->
                    // 合批：高频 delta 先入缓冲，~30ms 一批上屏（大幅减少 TextView 重排 → 更快更顺）
                    synchronized(buf) { buf.append(d) }
                    if (!flushPosted) {
                        flushPosted = true
                        mainHandler.postDelayed(flush, 30)
                    }
                },
                onDone = { err ->
                    mainHandler.post {
                        if (mySeq != seq) return@post   // 迟到回调（请求已被取消）
                        if (flushPosted) {
                            mainHandler.removeCallbacks(flush)
                            flush.run()                 // 落地剩余缓冲（保证完整）
                        }
                        currentCall = null
                        if (err == null) {
                            lastSource = text
                            lastTranslation = ov.currentText()
                            // 累积模式：历史只存本次新增的那一块（否则每次都会把整页快照塞进历史）
                            val full = ov.currentText()
                            val histText = if (appendBlock && full.length > beforeLen)
                                full.substring(beforeLen).removePrefix(BLOCK_SEP).trim()
                            else full
                            HistoryStore.append(context.applicationContext, "翻译", text, histText)
                            ov.finish(null)
                            val outLen = ov.currentText().length
                            if (firstMs > 0L) {
                                val tip = if (text.length >= 300 && outLen < text.length * 0.12)
                                    " · ⚠️ 输出偏短（点球可重试）" else ""
                                ov.showStatus("✓ 完成 · 首字 ${"%.1f".format(firstMs / 1000f)}s · 点球继续$tip")
                            }
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
