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

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        // 读超时 = 两次数据之间的最大间隔（非总时长）。60s 无新数据视为连接死亡，
        // 防止"翻译中"永久悬挂（此前为 0 = 永不超时）
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 连接测试：非流式小请求，快速验证地址/Key/模型三项是否正确。
     * 返回人性化结果信息（含常见 404/401 的排查提示）。
     */
    fun testConnection(onResult: (String) -> Unit): Call {
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
        val req = Request.Builder()
            .url(store.baseUrl)
            .header("Authorization", "Bearer ${store.apiKey}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

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
        onDelta: (String) -> Unit,
        onDone: (Throwable?) -> Unit
    ): Call {
        val isMtModel = store.model.startsWith("qwen-mt")
        val isDashScope =
            store.baseUrl.contains("dashscope") || store.baseUrl.contains("aliyun")
        val lvl = store.thinkingLevel

        // 续段标记：让模型知道这是长文本的延续，所有规则对本段同样生效
        // 防呆设计（用户钦定保留）：留空 = 内置极速翻译词；填写任意内容 = 完全以用户为准
        val userContent = if (isContinuation && !isMtModel) "【续段】$text" else text
        val systemPrompt = store.customPrompt.ifBlank { SettingsStore.DEFAULT_SYS_PROMPT } +
                if (isContinuation && !isMtModel)
                    "\n(Note: the user message is a continuation segment of previously submitted content. ALL the same rules apply to this segment as well.)"
                else ""

        val body = JSONObject().apply {
            put("model", store.model)
            put("stream", true)
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
                    u.contains("bigmodel.cn", true) ->
                        put("thinking", JSONObject().put("type", if (lvl == "off") "disabled" else "enabled"))
                    u.contains("deepseek.com", true) -> when (lvl) {
                        "low" -> {
                            put("thinking", JSONObject().put("type", "enabled"))
                            put("reasoning_effort", "low")
                        }
                        "high" -> {
                            put("thinking", JSONObject().put("type", "enabled"))
                            put("reasoning_effort", "high")
                        }
                        else -> put("thinking", JSONObject().put("type", "disabled"))
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

    private fun parseDelta(data: String): String = try {
        val choices = JSONObject(data).optJSONArray("choices") ?: return ""
        val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return ""
        delta.optString("content", "")
    } catch (_: Exception) {
        ""
    }
}
