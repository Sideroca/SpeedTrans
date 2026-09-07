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
        .readTimeout(0, TimeUnit.MILLISECONDS) // 流式读取不设超时
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

        // 续段标记：让模型知道这是长文本的延续，所有规则对本段同样生效
        val userContent = if (isContinuation && !isMtModel) "【续段】$text" else text
        // 提示词哲学：不内置兜底。空 = 不发送 system 消息（用户在设置页预填/自定义）
        val systemPrompt = store.customPrompt +
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
                // qwen3 系列思考型模型默认先思考，强制关闭以获得最快首字
                if (isDashScope) put("enable_thinking", false)
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
