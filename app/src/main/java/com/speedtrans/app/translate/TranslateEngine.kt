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
 */
class TranslateEngine(private val store: SettingsStore) {

    companion object {
        private const val DEFAULT_SYS_PROMPT =
            "You are a fast translation engine. Translate the user's text into Simplified Chinese. " +
                    "Output ONLY the Chinese translation. Preserve line breaks. " +
                    "Keep code, URLs and proper nouns unchanged. No notes, no explanations."
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // 流式读取不设超时
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun translate(text: String, onDelta: (String) -> Unit, onDone: (Throwable?) -> Unit): Call {
        val isMtModel = store.model.startsWith("qwen-mt")
        val isDashScope =
            store.baseUrl.contains("dashscope") || store.baseUrl.contains("aliyun")

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
                    put(JSONObject().put("role", "user").put("content", text))
                })
            } else {
                // qwen3 系列思考型模型默认先思考，强制关闭以获得最快首字
                if (isDashScope) put("enable_thinking", false)
                put("messages", JSONArray().apply {
                    put(
                        JSONObject().put("role", "system")
                            .put("content", store.customPrompt.ifBlank { DEFAULT_SYS_PROMPT })
                    )
                    put(JSONObject().put("role", "user").put("content", text))
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
