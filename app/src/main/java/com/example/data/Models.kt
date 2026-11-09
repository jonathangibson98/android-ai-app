package com.example.data

data class ChatMessage(
    val role: String,
    val content: String
)

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val contextWindow: String,
    val size: String,
    val isLocal: Boolean = true
)

data class LogEntry(
    val id: String,
    val timestamp: Long,
    val method: String,
    val path: String,
    val clientIp: String,
    val statusCode: Int,
    val durationMs: Long,
    val model: String? = null,
    val isStream: Boolean = false,
    val requestSnippet: String = "",
    val responseSnippet: String = "",
    val fullHeaders: Map<String, String> = emptyMap(),
    val fullRequestBody: String = "",
    val fullResponseBody: String = ""
)

data class ServerStats(
    val uptimeSeconds: Long = 0,
    val totalRequests: Long = 0,
    val successRequests: Long = 0,
    val errorRequests: Long = 0,
    val totalTokensGenerated: Long = 0,
    val activeConnections: Int = 0
)

data class ServerStatus(
    val isRunning: Boolean = false,
    val port: Int = 8080,
    val ipAddress: String = "127.0.0.1",
    val errorMessage: String? = null,
    val startedAt: Long = 0L
)

data class ServerSettings(
    val port: Int = 8080,
    val requireAuth: Boolean = false,
    val apiKey: String = "sk-android-ai-server",
    val customSystemPrompt: String = "",
    val defaultModel: String = "local-device-ai",
    val upstreamGeminiKey: String = "",
    val keepAwake: Boolean = true,
    val allowCors: Boolean = true
)
