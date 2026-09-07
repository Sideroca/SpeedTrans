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
 * 每次调用 = 一次独立请求（无会话状态），整段文字一次性发送，SSE 流式返回。
 */
class TranslateEngine(private val store: SettingsStore) {

    companion object {
        private const val SYS_PROMPT =
            "You are a fast translation engine. Translate the user's text into Simplified Chinese. " +
                    "Output ONLY the Chinese translation. Preserve line breaks. " +
                    "Keep code, URLs and proper nouns unchanged. No notes, no explanations."
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // 流式读取不设超时
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 发起流式翻译。
     * @param onDelta 每收到一小段译文回调一次（在 OkHttp 工作线程，调用方需自行切线程）
     * @param onDone 结束回调，err == null 表示成功完成
     * @return Call 句柄，可用于取消
     */
    fun translate(text: String, onDelta: (String) -> Unit, onDone: (Throwable?) -> Unit): Call {
        // 千问翻译特化模型（qwen-mt-*）：仅支持单条 user 消息，语种经 translation_options 配置
        val isMtModel = store.model.startsWith("qwen-mt")
        val body = JSONObject().apply {
            put("model", store.model)
            put("stream", true)
            if (isMtModel) {
                put(
                    "translation_options",
                    JSONObject()
                        .put("source_lang", "auto")
                        .put("target_lang", "Chinese")
                )
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "user").put("content", text))
                })
            } else {
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", SYS_PROMPT))
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
