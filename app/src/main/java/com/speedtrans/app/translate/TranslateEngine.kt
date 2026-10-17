package com.speedtrans.app.translate

import com.speedtrans.app.store.SettingsStore
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容流式翻译引擎。
 * - qwen-mt-* ：翻译特化协议（单条 user 消息 + translation_options）
 * - 其他模型  ：标准协议；DashScope 域名自动关闭 qwen3 思考模式（enable_thinking=false）
 * - 续段模式  ：增量翻译时带【续段】标记 + 规则重申，保证自定义规则对每个片段持续生效
 */
class TranslateEngine(private val store: SettingsStore) {

    companion object {
        /**
         * 全进程共用一个 OkHttpClient。
         * 否则每 new 一个 TranslateEngine 就是一套连接池 + 线程池：
         * 设置页每点一次「测试连接」建一套、BallService 每次连上也建一套，白吃内存与 fd。
         */
        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                // 读超时 = 两次数据之间的最大间隔（非总时长）。60s 无新数据视为连接死亡，
                // 防止"翻译中"永久悬挂（此前为 0 = 永不超时）
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * 连接预热：提前完成 DNS 解析 / TCP 握手 / TLS（HEAD 请求，忽略状态码）。
     * 首次点球可省 200~600ms 建连时间；连接由 OkHttp 连接池复用给后续请求。
     */
    fun warmUp() {
        try {
            if (store.baseUrl.isBlank()) return
            val req = Request.Builder().url(store.baseUrl).head().build()
            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                }

                override fun onResponse(call: Call, response: Response) {
                    response.close()
                }
            })
        } catch (_: Exception) {
        }
    }

    /**
     * 连接测试：非流式小请求，快速验证地址/Key/模型三项是否正确。
     * 返回人性化结果信息（含常见 404/401 的排查提示）。
     *
     * @return 发起的请求；地址非法等**无法发起**的情况返回 null（同时用 onResult 给出提示）。
     *         （此前这里直接 `url(baseUrl)`：用户手打的地址不带 http/https 会抛
     *         IllegalArgumentException，而调用方没有 try → 直接闪退。）
     */
    fun testConnection(onResult: (String) -> Unit): Call? {
        if (!store.baseUrl.startsWith("http://") && !store.baseUrl.startsWith("https://")) {
            val shown = store.baseUrl.ifBlank { "空" }.take(60)
            onResult("❌ 地址要以 http:// 或 https:// 开头\n（当前：$shown）")
            return null
        }
        val isMtModel = store.model.startsWith("qwen-mt")
        val body = JSONObject().apply {
            put("model", store.model)
            put("stream", false)
            put("max_tokens", 8)
            if (isMtModel) {
                put(
                    "translation_options",
                    JSONObject().put("source_lang", "auto").put("target_lang", "Chinese")
                )
            }
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "user").put("content", "hi"))
            })
        }
        // 兜底：形如 "https://" 的空壳地址等仍可能让 OkHttp 抛错——绝不把异常泄到调用方主线程
        val req = try {
            Request.Builder()
                .url(store.baseUrl)
                .header("Authorization", "Bearer ${store.apiKey}")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
        } catch (e: Exception) {
            onResult("❌ 地址无法解析：${e.message?.take(80) ?: "格式不对"}")
            return null
        }

        val start = android.os.SystemClock.elapsedRealtime()
        val call = client.newCall(req)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult("❌ 无法连接：${e.message ?: "网络错误"}\n（检查网络 / 地址拼写）")
            }

            override fun onResponse(call: Call, resp: Response) {
                resp.use { r ->
                    val ms = android.os.SystemClock.elapsedRealtime() - start
                    when {
                        r.isSuccessful -> {
                            onResult("✅ 连接成功 · ${ms}ms · 模型 ${store.model}")
                        }
                        r.code == 404 -> {
                            val hint = if (store.model.startsWith("qwen3") || store.model.startsWith("qwen-mt"))
                                "模型名可能拼错（检查大小写/空格），或地址缺 /chat/completions 尾巴"
                            else "地址可能缺 /chat/completions 尾巴，或模型名不存在"
                            onResult("❌ 404 未找到：$hint")
                        }
                        r.code == 401 -> onResult("❌ 401 鉴权失败：API Key 无效或已过期")
                        r.code == 400 -> {
                            val detail = try {
                                r.body?.string()?.take(200)
                            } catch (_: Exception) {
                                ""
                            }
                            onResult("❌ 400 参数错误：$detail")
                        }
                        else -> {
                            val detail = try {
                                r.body?.string()?.take(200)
                            } catch (_: Exception) {
                                ""
                            }
                            onResult("❌ HTTP ${r.code}：$detail")
                        }
                    }
                }
            }
        })
        return call
    }

    fun translate(
        text: String,
        isContinuation: Boolean = false,
        /** 续段时只带上一段译文的末尾，帮助句子衔接；绝不重传原文前缀 */
        contextTranslation: String = "",
        onDelta: (String) -> Unit,
        onDone: (Throwable?) -> Unit,
        includeMax: Boolean = true,
        /**
         * 每次真正发起请求时回调（含下方 400 自动重试另起的那一个 Call）。
         * 编排层靠它把"当前请求"始终攥在手里——否则取消只能取消到已结束的外层请求，
         * 重试出来的那个会在后台继续跑到自然结束。
         */
        onCallCreated: ((Call) -> Unit)? = null
    ): Call {
        val isMtModel = store.model.startsWith("qwen-mt")
        val isDashScope =
            store.baseUrl.contains("dashscope") || store.baseUrl.contains("aliyun")
        val lvl = store.thinkingLevel

        // 续段标记：让模型知道这是长文本的延续，所有规则对本段同样生效
        // 防呆设计（用户钦定保留）：留空 = 内置极速翻译词；填写任意内容 = 完全以用户为准
        // 附加模块：内置提示词模式下把"翻译要求"随用户消息一起提交（附在结尾）——
        // 降低"不翻译/不服从指令"概率；用户自定义了提示词则完全不加（与系统提示词同规则）
        val appendix = if (store.customPrompt.isBlank() && !isMtModel)
            "\n\n" + SettingsStore.DEFAULT_USER_APPENDIX else ""
        val userContent = (if (isContinuation && !isMtModel) "【续段】$text" else text) + appendix
        val contextHint = if (isContinuation && !isMtModel && contextTranslation.isNotBlank())
            "\n【上一段译文末尾，仅供衔接参考，不要重复输出】" +
                    contextTranslation.takeLast(160).trim()
        else ""
        val systemPrompt = store.customPrompt.ifBlank { SettingsStore.DEFAULT_SYS_PROMPT } +
                (if (isContinuation && !isMtModel)
                    "\n（注意：本条是此前内容的延续片段，所有规则对本段同样生效，输出仍然是简体中文。）"
                else "") +
                contextHint

        val body = JSONObject().apply {
            put("model", store.model)
            put("stream", true)
            // 高级参数（可留空/置 0 关闭）：老模型兼容用 max_tokens，OpenAI 新系用 max_completion_tokens
            val mt = store.maxTokens
            if (includeMax && mt > 0) {
                if (store.baseUrl.contains("openai.com", true)) put("max_completion_tokens", mt)
                else put("max_tokens", mt)
            }
            val tp = store.temperature
            if (tp >= 0f) put("temperature", tp.toDouble())
            if (isMtModel) {
                // 千问翻译特化模型：仅单条 user 消息，配置经 translation_options
                put(
                    "translation_options",
                    JSONObject()
                        .put("source_lang", "auto")
                        .put("target_lang", "Chinese")
                        .apply {
                            // 自定义提示词 → 领域提示（MT 模型专用通道）
                            if (store.customPrompt.isNotEmpty()) put("domains", store.customPrompt)
                        }
                )
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "user").put("content", userContent))
                })
            } else {
                // 思考档位：按域名映射为各家真实参数（门控注入，自定义接口零影响）
                // 默认最快档 off，符合极速红线；qwen-mt 特化协议无思考概念，不进入本分支
                val u = store.baseUrl
                when {
                    isDashScope ->
                        put("enable_thinking", lvl != "off")
                    u.contains("volces.com", true) ->
                        put("thinking", JSONObject().put("type", when (lvl) {
                            "auto" -> "auto"
                            "on" -> "enabled"
                            else -> "disabled"
                        }))
                    u.contains("bigmodel.cn", true) -> {
                        // glm-5 系始终思考（仅支持 enabled）：选"关"时降级为最低强度，避免直接报错（容错）
                        val glm5 = store.model.startsWith("glm-5")
                        if (lvl == "off" && !glm5) {
                            put("thinking", JSONObject().put("type", "disabled"))
                        } else {
                            put("thinking", JSONObject().put("type", "enabled"))
                            val eff = when (lvl) {
                                "low", "high", "max" -> lvl
                                "off" -> "low"
                                else -> null
                            }
                            if (eff != null) put("reasoning_effort", eff)
                        }
                    }
                    u.contains("deepseek.com", true) -> when (lvl) {
                        "low", "high", "max" -> {
                            put("thinking", JSONObject().put("type", "enabled"))
                            put("reasoning_effort", lvl)
                        }
                        "off" -> put("thinking", JSONObject().put("type", "disabled"))
                        // 其他未知档位：不发思考参数（容错，交给服务端默认）
                    }
                    u.contains("moonshot.cn", true) ->
                        put("thinking", JSONObject().put("type", if (lvl == "off") "disabled" else "enabled"))
                    u.contains("openai.com", true) ->
                        put("reasoning_effort", when (lvl) {
                            "min" -> "minimal"
                            "max" -> "high"
                            else -> "medium"
                        })
                }
                put("messages", JSONArray().apply {
                    // 提示词为空时不发送 system 消息（用户可完全自定义/删除）
                    if (systemPrompt.isNotBlank())
                        put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userContent))
                })
            }
        }

        val req = Request.Builder()
            .url(store.baseUrl)
            .header("Authorization", "Bearer ${store.apiKey}")
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(req)
        onCallCreated?.invoke(call)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onDone(if (call.isCanceled()) null else e)
            }

            override fun onResponse(call: Call, resp: Response) {
                resp.use { r ->
                    if (!r.isSuccessful) {
                        val detail = try {
                            r.body?.string()?.take(300)
                        } catch (_: Exception) {
                            ""
                        }
                        // 容错：max_tokens 超过服务端上限（各家 8k/16k/32k 不等）被 400 拒绝时，
                        // 自动去掉该参数重试一次，保证"绝不会因为一个可选参数把翻译打死"
                        if (includeMax && store.maxTokens > 0 && r.code == 400) {
                            // 把 onCallCreated 一并透传：重试另起的那一个 Call 也要能被编排层取消到
                            translate(
                                text, isContinuation, contextTranslation, onDelta, onDone,
                                includeMax = false, onCallCreated = onCallCreated
                            )
                            return
                        }
                        onDone(IOException("HTTP ${r.code} $detail"))
                        return
                    }
                    try {
                        val src = r.body!!.source()
                        while (!src.exhausted()) {
                            val line = src.readUtf8Line() ?: break
                            if (call.isCanceled()) break
                            if (!line.startsWith("data:")) continue
                            val data = line.substring(5).trim()
                            if (data == "[DONE]") break
                            val delta = parseDelta(data)
                            if (delta.isNotEmpty()) onDelta(delta)
                        }
                        onDone(null)
                    } catch (e: Exception) {
                        onDone(if (call.isCanceled()) null else e)
                    }
                }
            }
        })
        return call
    }

    /** 沉浸式（结构化）：一次请求 + ⟦n⟧ 逐块；失败由编排层降级逐块 */
    fun translateImmersive(
        userContent: String,
        onDelta: (String) -> Unit,
        onAlive: () -> Unit = {},
        onDone: (Throwable?) -> Unit,
        onCallCreated: ((Call) -> Unit)? = null
    ): Call = startRequest(true, userContent, onDelta, onAlive, onDone, onCallCreated, 0)

    /** 沉浸式（逐块）：单条 user 消息 → 对位天然成立、不依赖模型行为（MT 模型也能用） */
    fun translateBlock(
        text: String,
        onDelta: (String) -> Unit,
        onAlive: () -> Unit = {},
        onDone: (Throwable?) -> Unit,
        onCallCreated: ((Call) -> Unit)? = null
    ): Call = startRequest(false, text, onDelta, onAlive, onDone, onCallCreated, 0)

    /**
     * 统一发起：level 0=完整；1=去掉 max_tokens；2=最简（只留 model/stream/messages）。
     * 主要应对"某个可选参数被服务端 400"——逐级退让而不是一次打死。
     */
    private fun startRequest(
        structured: Boolean,
        userContent: String,
        onDelta: (String) -> Unit,
        onAlive: () -> Unit,
        onDone: (Throwable?) -> Unit,
        onCallCreated: ((Call) -> Unit)?,
        level: Int
    ): Call {
        val body = buildBody(structured, userContent, level)
        val req = try {
            val b = Request.Builder()
                .url(store.baseUrl)
                .header("Authorization", "Bearer ${store.apiKey}")
                .header("Accept", "text/event-stream")
            // Azure OpenAI 用 api-key 头
            if (store.baseUrl.contains("azure", true)) b.header("api-key", store.apiKey)
            b.post(body.toString().toRequestBody("application/json".toMediaType())).build()
        } catch (e: Exception) {
            onDone(e)
            return client.newCall(Request.Builder().url("http://127.0.0.1/").build())
        }
        val call = client.newCall(req)
        onCallCreated?.invoke(call)
        enqueueStream(call, onDelta, onAlive, onDone, level) { next ->
            startRequest(structured, userContent, onDelta, onAlive, onDone, onCallCreated, next)
        }
        return call
    }

    /** level 0=全量；1=去 max_tokens；2=最简 */
    private fun buildBody(structured: Boolean, userContent: String, level: Int): JSONObject {
        val isMt = store.model.startsWith("qwen-mt")
        return JSONObject().apply {
            put("model", store.model)
            put("stream", true)
            if (level == 0) {
                val tp = store.temperature
                if (tp >= 0f) put("temperature", tp.toDouble())
                val mt = store.maxTokens
                if (mt > 0) {
                    if (store.baseUrl.contains("openai.com", true)) put("max_completion_tokens", mt)
                    else put("max_tokens", mt)
                }
            }
            if (isMt) {
                put(
                    "translation_options",
                    JSONObject().put("source_lang", "auto").put("target_lang", "Chinese")
                )
            } else if (level == 0) {
                // 只在**已知**服务商发思考参数；自建 vLLM/ollama 乱发会 400
                val u = store.baseUrl
                when {
                    u.contains("dashscope", true) || u.contains("aliyun", true) ->
                        put("enable_thinking", false)
                    u.contains("volces.com", true) ->
                        put("thinking", JSONObject().put("type", "disabled"))
                    u.contains("bigmodel.cn", true) -> {
                        if (!store.model.startsWith("glm-5"))
                            put("thinking", JSONObject().put("type", "disabled"))
                    }
                    u.contains("deepseek.com", true) ->
                        put("thinking", JSONObject().put("type", "disabled"))
                    u.contains("moonshot.cn", true) ->
                        put("thinking", JSONObject().put("type", "disabled"))
                    u.contains("openai.com", true) ->
                        put("reasoning_effort", "minimal")
                }
            }
            val sys = if (structured)
                store.immersivePrompt.ifBlank { SettingsStore.DEFAULT_IMMERSIVE_PROMPT }
            else
                store.immersivePrompt.ifBlank { SettingsStore.DEFAULT_BLOCK_PROMPT }
            put("messages", JSONArray().apply {
                if (!isMt && sys.isNotBlank())
                    put(JSONObject().put("role", "system").put("content", sys))
                put(JSONObject().put("role", "user").put("content", userContent))
            })
        }
    }

    /**
     * 读流：多协议容错 + 400 逐级退让 + 非流式兜底。
     * onAlive：每收到一条有内容的行就回调（思考型模型只出 reasoning 时不算挂起）。
     */
    private fun enqueueStream(
        call: Call,
        onDelta: (String) -> Unit,
        onAlive: () -> Unit,
        onDone: (Throwable?) -> Unit,
        level: Int,
        resend: (Int) -> Unit
    ) {
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onDone(if (call.isCanceled()) null else e)
            }

            override fun onResponse(call: Call, resp: Response) {
                resp.use { r ->
                    com.speedtrans.app.util.DiagLog.log("http", "响应 code=${r.code} level=$level")
                    if (!r.isSuccessful) {
                        val detail = try {
                            r.body?.string()?.take(300)
                        } catch (_: Exception) {
                            ""
                        }
                        com.speedtrans.app.util.DiagLog.log("http", "非 2xx：${detail?.take(120) ?: ""}")
                        if (r.code == 400 && level < 2) {
                            com.speedtrans.app.util.DiagLog.log("http", "400 → 退让到 level ${level + 1} 重试")
                            resend(level + 1)
                            return
                        }
                        onDone(IOException(friendly(r.code) + " " + (detail ?: "")))
                        return
                    }
                    try {
                        val src = r.body!!.source()
                        var sawStream = false
                        val whole = StringBuilder()
                        var newlinePending = false
                        while (!src.exhausted()) {
                            val rawLine = src.readUtf8Line() ?: break
                            if (call.isCanceled()) break
                            var data: String? = null
                            val t = rawLine.trim()
                            when {
                                rawLine.startsWith("data:") -> data = rawLine.substring(5).trim()
                                // NDJSON（ollama /api/chat 等）：整行就是 JSON
                                t.startsWith("{") && t.endsWith("}") -> data = t
                                else -> {
                                    if (!sawStream) {
                                        if (newlinePending) whole.append('\n')
                                        whole.append(rawLine)
                                        newlinePending = true
                                    }
                                    continue
                                }
                            }
                            sawStream = true
                            onAlive()
                            if (data == "[DONE]") break
                            val d = parseDelta(data)
                            if (d.isNotEmpty()) onDelta(d)
                        }
                        if (!sawStream) {
                            val f = parseFull(whole.toString())
                            com.speedtrans.app.util.DiagLog.log("http", "非流式兜底 解析=${f.length}字（${whole.length}B）")
                            if (f.isNotEmpty()) onDelta(f)
                        }
                        onDone(null)
                    } catch (e: Exception) {
                        onDone(if (call.isCanceled()) null else e)
                    }
                }
            }
        })
    }

    private fun friendly(code: Int): String = when (code) {
        401 -> "401 鉴权失败：API Key 无效或过期"
        403 -> "403 被拒绝：Key 无该模型权限？"
        404 -> "404 未找到：地址缺 /chat/completions 尾巴，或模型名不存在"
        429 -> "429 太频繁：被限流，稍后再试"
        in 500..599 -> "HTTP $code 服务端故障"
        else -> "HTTP $code"
    }

    /** 宽容解析：OpenAI / ollama / Gemini */
    private fun parseDelta(data: String): String {
        return try {
            val o = JSONObject(data)
            val ch = o.optJSONArray("choices")
            if (ch != null && ch.length() > 0) {
                val c0 = ch.optJSONObject(0) ?: return ""
                val a = c0.optJSONObject("delta")?.optString("content", "") ?: ""
                if (a.isNotEmpty()) return a
                val b = c0.optJSONObject("message")?.optString("content", "") ?: ""
                if (b.isNotEmpty()) return b
                val t = c0.optString("text", "")
                if (t.isNotEmpty()) return t
                return ""
            }
            val resp = o.optString("response", "")           // ollama /api/generate
            if (resp.isNotEmpty()) return resp
            val msg = o.optJSONObject("message")?.optString("content", "") ?: ""
            if (msg.isNotEmpty()) return msg                  // ollama /api/chat
            return geminiText(o)                              // Gemini
        } catch (_: Exception) {
            ""
        }
    }

    /** Gemini：candidates[0].content.parts[*].text 全拼 */
    private fun geminiText(o: JSONObject): String {
        return try {
            val cand = o.optJSONArray("candidates") ?: return ""
            if (cand.length() == 0) return ""
            val parts = cand.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.optJSONObject(i)?.optString("text", "") ?: "")
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    /** 非流式整段 JSON 兜底：OpenAI / ollama / Gemini */
    private fun parseFull(s: String): String {
        return try {
            val o = JSONObject(s)
            val ch = o.optJSONArray("choices")
            if (ch != null && ch.length() > 0) {
                val m = ch.optJSONObject(0)?.optJSONObject("message")?.optString("content", "") ?: ""
                if (m.isNotEmpty()) return m
                val t = ch.optJSONObject(0)?.optString("text", "") ?: ""
                if (t.isNotEmpty()) return t
            }
            val resp = o.optString("response", "")
            if (resp.isNotEmpty()) return resp
            val msg = o.optJSONObject("message")?.optString("content", "") ?: ""
            if (msg.isNotEmpty()) return msg
            geminiText(o)
        } catch (_: Exception) {
            ""
        }
    }
}
