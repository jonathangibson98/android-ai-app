package com.example.engine

import com.example.data.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiProxyEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generate(
        messages: List<ChatMessage>,
        apiKey: String,
        modelName: String = "gemini-2.5-flash"
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext LocalDeviceAiEngine.generate(messages, modelName)
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val jsonPayload = buildGeminiPayload(messages)

            val body = jsonPayload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext "Gemini Proxy Error (${response.code}): $responseBody\nFalling back to local AI.\n\n" +
                        LocalDeviceAiEngine.generate(messages, modelName)
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text")

            text ?: "No response generated from Gemini upstream."
        } catch (e: Exception) {
            "Proxy Connection Error: ${e.message}\n\nFalling back to on-device engine:\n" +
                    LocalDeviceAiEngine.generate(messages, modelName)
        }
    }

    fun generateStream(
        messages: List<ChatMessage>,
        apiKey: String,
        modelName: String = "gemini-2.5-flash"
    ): Flow<String> = flow {
        if (apiKey.isBlank()) {
            LocalDeviceAiEngine.generateStream(messages, modelName).collect { emit(it) }
            return@flow
        }

        val text = generate(messages, apiKey, modelName)
        val words = text.split(" ")
        for (i in words.indices) {
            val word = words[i]
            val chunk = if (i == 0) word else " $word"
            emit(chunk)
            kotlinx.coroutines.delay((10..30).random().toLong())
        }
    }

    private fun buildGeminiPayload(messages: List<ChatMessage>): JSONObject {
        val contentsArray = JSONArray()
        for (msg in messages) {
            val role = if (msg.role == "assistant") "model" else "user"
            val partObj = JSONObject().put("text", msg.content)
            val partsArray = JSONArray().put(partObj)
            val contentObj = JSONObject()
                .put("role", role)
                .put("parts", partsArray)
            contentsArray.put(contentObj)
        }
        return JSONObject().put("contents", contentsArray)
    }
}
