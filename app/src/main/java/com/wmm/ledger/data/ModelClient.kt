package com.wmm.ledger.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

enum class Provider(val title: String, val host: String, val path: String, val model: String) {
    DEEPSEEK("DeepSeek", "api.deepseek.com", "/chat/completions", "deepseek-flash"),
    MINIMAX("MiniMax 国内站", "api.minimax.cn", "/v1/chat/completions", "MiniMax-M3.1-Flash");

    val endpoint get() = "https://$host$path"
}

class LedgerError(val display: String) : Exception()

internal object ModelProtocol {
    fun chatRequest(provider: Provider, system: String, user: String, json: Boolean, maxTokens: Int = if (json) 1800 else 2000): JSONObject = JSONObject()
        .put("model", provider.model).put("stream", false).put("max_tokens", maxTokens)
        .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user)))
        .apply {
            if (provider == Provider.DEEPSEEK) {
                put("thinking", JSONObject().put("type", "disabled"))
                if (json) put("response_format", JSONObject().put("type", "json_object"))
            } else put("reasoning_effort", "low")
        }

    fun connectionRequest(provider: Provider): JSONObject = JSONObject().apply {
        put("model", provider.model)
        put("stream", false)
        put("messages", JSONArray()
            .put(JSONObject().put("role", "system").put("content", "请仅回复：连接成功。"))
            .put(JSONObject().put("role", "user").put("content", "测试连接")))
        // 两家的连接测试沿用各自支持的长度与思考参数，不携带账本数据。
        when (provider) {
            Provider.DEEPSEEK -> {
                put("thinking", JSONObject().put("type", "disabled"))
                put("max_tokens", 800)
            }
            Provider.MINIMAX -> {
                put("reasoning_effort", "low")
                put("max_completion_tokens", 1024)
            }
        }
    }

    internal fun readReply(status: Int, response: String): String {
        if (status !in 200..299) throw when (status) {
            401, 403 -> LedgerError("API Key 无效或没有权限，请检查模型设置。")
            402 -> LedgerError("模型账户余额不足或额度已用完。")
            429 -> LedgerError("模型服务请求过于频繁，请稍后重试。")
            in 500..599 -> LedgerError("模型服务暂时不可用，请稍后重试。")
            else -> LedgerError("模型服务拒绝了请求（$status）。")
        }
        val json = runCatching { JSONObject(response) }.getOrElse {
            throw LedgerError("模型返回无法读取，请稍后重试。")
        }
        when (json.optJSONObject("base_resp")?.optInt("status_code", 0) ?: 0) {
            0 -> Unit
            1008 -> throw LedgerError("MiniMax 余额不足或额度已用完。")
            1002, 1039 -> throw LedgerError("MiniMax 请求受限，请稍后重试。")
            1004, 2049 -> throw LedgerError("MiniMax API Key 无效或没有权限。")
            else -> throw LedgerError("MiniMax 暂时未能完成请求，请检查账户状态后重试。")
        }
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
        val message = choice?.optJSONObject("message")
        val content = (message?.opt("content") as? String)?.trim()
        if (content.isNullOrEmpty() || choice.optString("finish_reason") == "length") {
            throw LedgerError("模型没有返回完整内容，请重试。")
        }
        return content
    }
}

class ModelClient {
    fun testConnection(provider: Provider, key: String): String =
        send(provider, key, ModelProtocol.connectionRequest(provider))

    internal fun send(provider: Provider, key: String, request: JSONObject, readTimeout: Int = 60_000): String {
        try {
            val connection = URL(provider.endpoint).openConnection() as HttpsURLConnection
            try {
                // 禁止跳转，Key 和用户授权发送的数据只发往固定的官方主机。
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 12_000
                connection.readTimeout = readTimeout
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $key")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val body = request.toString().toByteArray()
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
                val status = connection.responseCode
                val response = if (status in 200..299) {
                    connection.inputStream.use { it.readBounded(1_048_576).decodeToString() }
                } else ""
                return ModelProtocol.readReply(status, response)
            } finally {
                connection.disconnect()
            }
        } catch (_: SocketTimeoutException) {
            throw LedgerError("模型请求超时，可以稍后重试。")
        } catch (_: IOException) {
            throw LedgerError("无法连接模型服务，请检查网络后重试。")
        }
    }
}
