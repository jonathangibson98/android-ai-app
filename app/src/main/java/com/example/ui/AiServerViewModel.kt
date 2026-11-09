package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatMessage
import com.example.data.LogEntry
import com.example.data.ModelInfo
import com.example.data.ServerSettings
import com.example.data.ServerStats
import com.example.data.ServerStatus
import com.example.engine.ModelCatalog
import com.example.server.AiHttpServer
import com.example.server.AiServerService
import com.example.server.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiServerViewModel : ViewModel() {

    private val httpServer = AiHttpServer(viewModelScope)

    private val _serverStatus = MutableStateFlow(ServerStatus())
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    val serverStats: StateFlow<ServerStats> = httpServer.serverStats

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _settings = MutableStateFlow(ServerSettings())
    val settings: StateFlow<ServerSettings> = _settings.asStateFlow()

    private val _networkIps = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val networkIps: StateFlow<List<Pair<String, String>>> = _networkIps.asStateFlow()

    // Playground Chat State
    private val _playgroundMessages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage("assistant", "Hello! I am connected directly to your local Android AI server. Send a prompt to test inference.")
    ))
    val playgroundMessages: StateFlow<List<ChatMessage>> = _playgroundMessages.asStateFlow()

    private val _playgroundLoading = MutableStateFlow(false)
    val playgroundLoading: StateFlow<Boolean> = _playgroundLoading.asStateFlow()

    private val _playgroundModel = MutableStateFlow("local-device-ai")
    val playgroundModel: StateFlow<String> = _playgroundModel.asStateFlow()

    private val _selectedLogForDetail = MutableStateFlow<LogEntry?>(null)
    val selectedLogForDetail: StateFlow<LogEntry?> = _selectedLogForDetail.asStateFlow()

    private val localClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        // Collect logs emitted from the server
        viewModelScope.launch {
            httpServer.logsFlow.collect { entry ->
                _logs.value = listOf(entry) + _logs.value.take(99)
            }
        }
    }

    fun initNetwork(context: Context) {
        val ip = NetworkUtils.getDeviceIpAddress(context)
        val allIps = NetworkUtils.getAllIpAddresses()
        _networkIps.value = allIps
        _serverStatus.value = _serverStatus.value.copy(ipAddress = ip)
    }

    fun startServer(context: Context) {
        val current = _settings.value
        val ip = NetworkUtils.getDeviceIpAddress(context)
        _networkIps.value = NetworkUtils.getAllIpAddresses()

        _serverStatus.value = _serverStatus.value.copy(
            port = current.port,
            ipAddress = ip,
            errorMessage = null
        )

        httpServer.start(current) { success, error ->
            if (success) {
                _serverStatus.value = _serverStatus.value.copy(
                    isRunning = true,
                    startedAt = System.currentTimeMillis(),
                    errorMessage = null
                )
                if (current.keepAwake) {
                    AiServerService.startService(context, ip, current.port)
                }
            } else {
                _serverStatus.value = _serverStatus.value.copy(
                    isRunning = false,
                    errorMessage = error
                )
            }
        }
    }

    fun stopServer(context: Context) {
        httpServer.stop()
        AiServerService.stopService(context)
        _serverStatus.value = _serverStatus.value.copy(
            isRunning = false,
            errorMessage = null
        )
    }

    fun updateSettings(newSettings: ServerSettings) {
        _settings.value = newSettings
        httpServer.currentSettings = newSettings
    }

    fun selectLogForDetail(entry: LogEntry?) {
        _selectedLogForDetail.value = entry
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun setPlaygroundModel(modelId: String) {
        _playgroundModel.value = modelId
    }

    fun sendPlaygroundPrompt(prompt: String) {
        if (prompt.isBlank() || _playgroundLoading.value) return

        val userMsg = ChatMessage("user", prompt.trim())
        val updatedList = _playgroundMessages.value + userMsg
        _playgroundMessages.value = updatedList
        _playgroundLoading.value = true

        viewModelScope.launch {
            val port = _serverStatus.value.port
            val model = _playgroundModel.value
            val isServerRunning = _serverStatus.value.isRunning

            if (!isServerRunning) {
                _playgroundMessages.value = updatedList + ChatMessage(
                    "assistant",
                    "⚠️ The AI Server is currently stopped. Please start the server from the Control tab first."
                )
                _playgroundLoading.value = false
                return@launch
            }

            try {
                val url = "http://127.0.0.1:$port/v1/chat/completions"
                val messagesJson = JSONArray()
                for (m in updatedList.takeLast(6)) {
                    messagesJson.put(JSONObject().put("role", m.role).put("content", m.content))
                }

                val payload = JSONObject()
                    .put("model", model)
                    .put("messages", messagesJson)
                    .put("temperature", 0.7)

                val reqBuilder = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))

                if (_settings.value.requireAuth) {
                    reqBuilder.header("Authorization", "Bearer ${_settings.value.apiKey}")
                }

                val response = localClient.newCall(reqBuilder.build()).execute()
                val respStr = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val jsonObj = JSONObject(respStr)
                    val choices = jsonObj.getJSONArray("choices")
                    val firstChoice = choices.getJSONObject(0)
                    val reply = firstChoice.getJSONObject("message").getString("content")
                    _playgroundMessages.value = updatedList + ChatMessage("assistant", reply)
                } else {
                    _playgroundMessages.value = updatedList + ChatMessage(
                        "assistant",
                        "Server Error (${response.code}): $respStr"
                    )
                }
            } catch (e: Exception) {
                _playgroundMessages.value = updatedList + ChatMessage(
                    "assistant",
                    "Connection failed: ${e.message}"
                )
            } finally {
                _playgroundLoading.value = false
            }
        }
    }

    fun clearPlayground() {
        _playgroundMessages.value = listOf(
            ChatMessage("assistant", "Chat history cleared. Send a prompt to test your local Android AI server.")
        )
    }

    fun getEndpointUrl(ip: String? = null): String {
        val host = ip ?: _serverStatus.value.ipAddress
        return "http://$host:${_serverStatus.value.port}/v1"
    }

    fun getCurlSnippet(prompt: String = "Explain relativity in 20 words"): String {
        val url = "${getEndpointUrl()}/chat/completions"
        val authHeader = if (_settings.value.requireAuth) " \\\n  -H \"Authorization: Bearer ${_settings.value.apiKey}\"" else ""
        return "curl -X POST \"$url\" \\\n" +
                "  -H \"Content-Type: application/json\"$authHeader \\\n" +
                "  -d '{\n" +
                "    \"model\": \"${_playgroundModel.value}\",\n" +
                "    \"messages\": [{\"role\": \"user\", \"content\": \"$prompt\"}]\n" +
                "  }'"
    }

    fun getPythonSnippet(): String {
        val auth = if (_settings.value.requireAuth) _settings.value.apiKey else "local-key"
        return """
from openai import OpenAI

client = OpenAI(
    base_url="${getEndpointUrl()}",
    api_key="$auth"
)

response = client.chat.completions.create(
    model="${_playgroundModel.value}",
    messages=[
        {"role": "system", "content": "You are an assistant running on Android."},
        {"role": "user", "content": "Hello from Python script!"}
    ]
)

print(response.choices[0].message.content)
        """.trimIndent()
    }

    fun getJsSnippet(): String {
        val auth = if (_settings.value.requireAuth) _settings.value.apiKey else "local-key"
        return """
import OpenAI from "openai";

const openai = new OpenAI({
  baseURL: "${getEndpointUrl()}",
  apiKey: "$auth"
});

async function main() {
  const completion = await openai.chat.completions.create({
    model: "${_playgroundModel.value}",
    messages: [{ role: "user", content: "Hello from Node.js!" }],
  });
  console.log(completion.choices[0].message.content);
}
main();
        """.trimIndent()
    }
}
