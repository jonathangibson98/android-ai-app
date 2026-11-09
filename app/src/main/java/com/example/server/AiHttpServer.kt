package com.example.server

import android.os.Build
import com.example.data.ChatMessage
import com.example.data.LogEntry
import com.example.data.ServerSettings
import com.example.data.ServerStats
import com.example.engine.GeminiProxyEngine
import com.example.engine.LocalDeviceAiEngine
import com.example.engine.ModelCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class AiHttpServer(
    private val scope: CoroutineScope
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var startTimeMillis: Long = 0L

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _serverStats = MutableStateFlow(ServerStats())
    val serverStats: StateFlow<ServerStats> = _serverStats.asStateFlow()

    private val _logsFlow = MutableSharedFlow<LogEntry>(replay = 50)
    val logsFlow: SharedFlow<LogEntry> = _logsFlow.asSharedFlow()

    private val activeConnections = AtomicInteger(0)
    private var totalReqCount: Long = 0
    private var totalSuccessCount: Long = 0
    private var totalErrorCount: Long = 0
    private var totalTokensCount: Long = 0

    var currentSettings = ServerSettings()

    fun start(settings: ServerSettings, onStarted: (Boolean, String?) -> Unit) {
        if (_isRunning.value) {
            onStarted(true, null)
            return
        }
        currentSettings = settings

        try {
            val socket = ServerSocket(settings.port)
            serverSocket = socket
            _isRunning.value = true
            startTimeMillis = System.currentTimeMillis()

            serverJob = scope.launch(Dispatchers.IO) {
                onStarted(true, null)
                while (isActive && !socket.isClosed) {
                    try {
                        val clientSocket = socket.accept()
                        launch(Dispatchers.IO) {
                            handleClient(clientSocket)
                        }
                    } catch (e: SocketException) {
                        break
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            }
        } catch (e: Exception) {
            _isRunning.value = false
            onStarted(false, e.localizedMessage ?: "Failed to bind to port ${settings.port}")
        }
    }

    fun stop() {
        _isRunning.value = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
        serverSocket = null
        serverJob = null
    }

    private suspend fun handleClient(clientSocket: Socket) {
        val clientIp = clientSocket.inetAddress?.hostAddress ?: "Unknown"
        activeConnections.incrementAndGet()
        updateStats()

        val startTime = System.currentTimeMillis()
        var statusCode = 200
        var method = "GET"
        var path = "/"
        var requestBody = ""
        var responseBody = ""
        var modelUsed: String? = null
        var isStream = false
        val headers = mutableMapOf<String, String>()

        try {
            clientSocket.soTimeout = 30000
            val input = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val output = clientSocket.getOutputStream()

            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size >= 2) {
                method = parts[0].uppercase()
                path = parts[1].split("?")[0]
            }

            // Parse headers
            var line: String?
            while (input.readLine().also { line = it } != null) {
                if (line!!.isEmpty()) break
                val colonIdx = line!!.indexOf(':')
                if (colonIdx > 0) {
                    val k = line!!.substring(0, colonIdx).trim().lowercase()
                    val v = line!!.substring(colonIdx + 1).trim()
                    headers[k] = v
                }
            }

            // Parse body if present
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            if (contentLength > 0) {
                val charArray = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = input.read(charArray, read, contentLength - read)
                    if (r == -1) break
                    read += r
                }
                requestBody = String(charArray, 0, read)
            }

            // Handle CORS Preflight
            if (method == "OPTIONS") {
                sendCorsPreflight(output)
                return
            }

            // Check Authentication
            if (currentSettings.requireAuth && path.startsWith("/v1")) {
                val authHeader = headers["authorization"] ?: ""
                val expectedKey = currentSettings.apiKey
                if (!authHeader.equals("Bearer $expectedKey", ignoreCase = true)) {
                    statusCode = 401
                    responseBody = JSONObject()
                        .put("error", JSONObject()
                            .put("message", "Incorrect or missing API key. Please pass 'Authorization: Bearer <your-key>'")
                            .put("type", "invalid_request_error")
                            .put("code", "invalid_api_key")
                        ).toString()
                    sendJsonResponse(output, 401, responseBody)
                    recordLog(method, path, clientIp, statusCode, System.currentTimeMillis() - startTime,
                        null, false, requestBody, responseBody, headers)
                    return
                }
            }

            // Router
            when {
                // Embedded Web Dashboard
                path == "/" || path == "/index.html" -> {
                    val html = buildWebDashboardHtml()
                    sendHtmlResponse(output, 200, html)
                    responseBody = "[HTML Dashboard Served]"
                }

                // Health status endpoint
                path == "/health" || path == "/v1/health" -> {
                    val uptimeSec = (System.currentTimeMillis() - startTimeMillis) / 1000
                    val healthObj = JSONObject()
                        .put("status", "healthy")
                        .put("server", "Android Local AI Server")
                        .put("uptime_seconds", uptimeSec)
                        .put("total_requests", totalReqCount + 1)
                        .put("active_connections", activeConnections.get())
                        .put("device_model", Build.MODEL)
                        .put("android_version", Build.VERSION.RELEASE)
                    responseBody = healthObj.toString(2)
                    sendJsonResponse(output, 200, responseBody)
                }

                // Models list
                path == "/v1/models" || path == "/models" -> {
                    val modelsArray = JSONArray()
                    for (m in ModelCatalog.defaultModels) {
                        modelsArray.put(JSONObject()
                            .put("id", m.id)
                            .put("object", "model")
                            .put("created", 1700000000)
                            .put("owned_by", if (m.isLocal) "android-local" else "cloud-proxy")
                            .put("permission", JSONArray())
                            .put("root", m.id)
                        )
                    }
                    val json = JSONObject()
                        .put("object", "list")
                        .put("data", modelsArray)
                    responseBody = json.toString(2)
                    sendJsonResponse(output, 200, responseBody)
                }

                // Chat Completions (OpenAI Compatible)
                path == "/v1/chat/completions" || path == "/chat/completions" -> {
                    if (method != "POST") {
                        statusCode = 405
                        responseBody = "{\"error\": \"Method not allowed. Use POST.\"}"
                        sendJsonResponse(output, 405, responseBody)
                    } else {
                        val parsed = parseChatRequest(requestBody)
                        val model = parsed.first
                        val messages = parsed.second
                        val stream = parsed.third
                        modelUsed = model
                        isStream = stream

                        if (stream) {
                            sendSseHeader(output)
                            val tokensBuilder = StringBuilder()
                            val streamId = "chatcmpl-${UUID.randomUUID()}"
                            val created = System.currentTimeMillis() / 1000

                            val tokenFlow = if (model.contains("gemini", ignoreCase = true) && currentSettings.upstreamGeminiKey.isNotBlank()) {
                                GeminiProxyEngine.generateStream(messages, currentSettings.upstreamGeminiKey, model)
                            } else {
                                LocalDeviceAiEngine.generateStream(messages, model, currentSettings.customSystemPrompt)
                            }

                            tokenFlow.collect { token ->
                                tokensBuilder.append(token)
                                totalTokensCount++
                                sendSseChunk(output, streamId, created, model, token)
                            }
                            sendSseDone(output)
                            responseBody = tokensBuilder.toString()
                        } else {
                            val replyText = if (model.contains("gemini", ignoreCase = true) && currentSettings.upstreamGeminiKey.isNotBlank()) {
                                GeminiProxyEngine.generate(messages, currentSettings.upstreamGeminiKey, model)
                            } else {
                                LocalDeviceAiEngine.generate(messages, model, currentSettings.customSystemPrompt)
                            }

                            val completionTokens = replyText.split(" ").size
                            val promptTokens = messages.sumOf { it.content.split(" ").size }
                            totalTokensCount += completionTokens

                            val respJson = JSONObject()
                                .put("id", "chatcmpl-${UUID.randomUUID()}")
                                .put("object", "chat.completion")
                                .put("created", System.currentTimeMillis() / 1000)
                                .put("model", model)
                                .put("choices", JSONArray().put(JSONObject()
                                    .put("index", 0)
                                    .put("message", JSONObject()
                                        .put("role", "assistant")
                                        .put("content", replyText)
                                    )
                                    .put("finish_reason", "stop")
                                ))
                                .put("usage", JSONObject()
                                    .put("prompt_tokens", promptTokens)
                                    .put("completion_tokens", completionTokens)
                                    .put("total_tokens", promptTokens + completionTokens)
                                )
                            responseBody = respJson.toString(2)
                            sendJsonResponse(output, 200, responseBody)
                        }
                    }
                }

                // Text Completions
                path == "/v1/completions" -> {
                    val prompt = try {
                        JSONObject(requestBody).optString("prompt", "Hello")
                    } catch (_: Exception) { "Hello" }
                    val reply = LocalDeviceAiEngine.generate(listOf(ChatMessage("user", prompt)), "local-device-ai")
                    val respJson = JSONObject()
                        .put("id", "cmpl-${UUID.randomUUID()}")
                        .put("object", "text_completion")
                        .put("choices", JSONArray().put(JSONObject()
                            .put("text", reply)
                            .put("index", 0)
                            .put("finish_reason", "stop")
                        ))
                    responseBody = respJson.toString(2)
                    sendJsonResponse(output, 200, responseBody)
                }

                // Embeddings (1536-dimensional mock vector)
                path == "/v1/embeddings" -> {
                    val embeddingArray = JSONArray()
                    for (i in 0 until 64) {
                        embeddingArray.put(kotlin.random.Random.nextDouble(-0.1, 0.1))
                    }
                    val data = JSONArray().put(JSONObject()
                        .put("object", "embedding")
                        .put("index", 0)
                        .put("embedding", embeddingArray)
                    )
                    val respJson = JSONObject()
                        .put("object", "list")
                        .put("data", data)
                        .put("model", "text-embedding-ada-002")
                    responseBody = respJson.toString()
                    sendJsonResponse(output, 200, responseBody)
                }

                else -> {
                    statusCode = 404
                    responseBody = JSONObject()
                        .put("error", JSONObject()
                            .put("message", "Route not found: $path. Supported routes: /v1/models, /v1/chat/completions, /health")
                            .put("type", "invalid_request_error")
                        ).toString()
                    sendJsonResponse(output, 404, responseBody)
                }
            }
        } catch (e: Exception) {
            statusCode = 500
            responseBody = JSONObject()
                .put("error", JSONObject()
                    .put("message", "Internal server error: ${e.message}")
                    .put("type", "api_error")
                ).toString()
            try {
                sendJsonResponse(clientSocket.getOutputStream(), 500, responseBody)
            } catch (_: Exception) {}
        } finally {
            activeConnections.decrementAndGet()
            val duration = System.currentTimeMillis() - startTime
            if (statusCode < 400) totalSuccessCount++ else totalErrorCount++
            totalReqCount++
            updateStats()

            recordLog(
                method = method,
                path = path,
                clientIp = clientIp,
                statusCode = statusCode,
                durationMs = duration,
                model = modelUsed,
                isStream = isStream,
                requestBody = requestBody,
                responseBody = responseBody,
                headers = headers
            )

            try {
                clientSocket.close()
            } catch (_: Exception) {}
        }
    }

    private fun parseChatRequest(body: String): Triple<String, List<ChatMessage>, Boolean> {
        return try {
            val json = JSONObject(body)
            val model = json.optString("model", "local-device-ai")
            val stream = json.optBoolean("stream", false)
            val msgs = mutableListOf<ChatMessage>()
            val arr = json.optJSONArray("messages")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    msgs.add(ChatMessage(
                        role = obj.optString("role", "user"),
                        content = obj.optString("content", "")
                    ))
                }
            }
            if (msgs.isEmpty()) msgs.add(ChatMessage("user", "Hello"))
            Triple(model, msgs, stream)
        } catch (e: Exception) {
            Triple("local-device-ai", listOf(ChatMessage("user", "Hello")), false)
        }
    }

    private fun sendJsonResponse(out: OutputStream, code: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val statusText = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Error"
        }
        val header = "HTTP/1.1 $code $statusText\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type, Authorization\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun sendHtmlResponse(out: OutputStream, code: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $code OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun sendCorsPreflight(out: OutputStream) {
        val header = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With\r\n" +
                "Access-Control-Max-Age: 86400\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun sendSseHeader(out: OutputStream) {
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/event-stream; charset=utf-8\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Connection: keep-alive\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: Content-Type, Authorization\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun sendSseChunk(out: OutputStream, id: String, created: Long, model: String, delta: String) {
        val chunkObj = JSONObject()
            .put("id", id)
            .put("object", "chat.completion.chunk")
            .put("created", created)
            .put("model", model)
            .put("choices", JSONArray().put(JSONObject()
                .put("index", 0)
                .put("delta", JSONObject().put("content", delta))
                .put("finish_reason", JSONObject.NULL)
            ))
        val line = "data: ${chunkObj}\n\n"
        out.write(line.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun sendSseDone(out: OutputStream) {
        out.write("data: [DONE]\n\n".toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private suspend fun recordLog(
        method: String,
        path: String,
        clientIp: String,
        statusCode: Int,
        durationMs: Long,
        model: String?,
        isStream: Boolean,
        requestBody: String,
        responseBody: String,
        headers: Map<String, String>
    ) {
        val reqSnippet = if (requestBody.length > 200) requestBody.take(200) + "..." else requestBody
        val respSnippet = if (responseBody.length > 200) responseBody.take(200) + "..." else responseBody

        val entry = LogEntry(
            id = UUID.randomUUID().toString().take(8),
            timestamp = System.currentTimeMillis(),
            method = method,
            path = path,
            clientIp = clientIp,
            statusCode = statusCode,
            durationMs = durationMs,
            model = model,
            isStream = isStream,
            requestSnippet = reqSnippet,
            responseSnippet = respSnippet,
            fullHeaders = headers,
            fullRequestBody = requestBody,
            fullResponseBody = responseBody
        )
        _logsFlow.emit(entry)
    }

    private fun updateStats() {
        val uptime = if (_isRunning.value && startTimeMillis > 0) {
            (System.currentTimeMillis() - startTimeMillis) / 1000
        } else 0L

        _serverStats.value = ServerStats(
            uptimeSeconds = uptime,
            totalRequests = totalReqCount,
            successRequests = totalSuccessCount,
            errorRequests = totalErrorCount,
            totalTokensGenerated = totalTokensCount,
            activeConnections = activeConnections.get()
        )
    }

    private fun buildWebDashboardHtml(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Android AI Server Dashboard</title>
    <style>
        :root {
            --bg: #090d16;
            --surface: #111827;
            --surface-elevated: #1f2937;
            --cyan: #00e5ff;
            --emerald: #00e676;
            --text: #f3f4f6;
            --text-dim: #9ca3af;
            --border: #374151;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, monospace;
            background: var(--bg);
            color: var(--text);
            margin: 0;
            padding: 24px;
        }
        .container { max-width: 900px; margin: 0 auto; }
        .header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; border-bottom: 1px solid var(--border); padding-bottom: 16px; }
        .badge { background: rgba(0, 230, 118, 0.15); color: var(--emerald); border: 1px solid var(--emerald); padding: 4px 12px; border-radius: 9999px; font-size: 13px; font-weight: 600; }
        .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: 24px; }
        .card { background: var(--surface); border: 1px solid var(--border); border-radius: 12px; padding: 20px; }
        .card h4 { margin: 0 0 8px 0; color: var(--text-dim); font-size: 13px; text-transform: uppercase; }
        .card .value { font-size: 26px; font-weight: 700; color: var(--cyan); }
        .code-block { background: #000; border: 1px solid var(--border); border-radius: 8px; padding: 16px; font-family: monospace; font-size: 13px; overflow-x: auto; color: #a7f3d0; margin-top: 12px; }
        .btn { background: var(--cyan); color: #000; border: none; padding: 8px 16px; border-radius: 6px; font-weight: 600; cursor: pointer; }
        .btn:hover { opacity: 0.9; }
        textarea { width: 100%; background: #000; border: 1px solid var(--border); color: #fff; border-radius: 6px; padding: 10px; font-family: monospace; box-sizing: border-box; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div>
                <h1 style="margin: 0; font-size: 24px;">🤖 Android AI Server</h1>
                <p style="margin: 4px 0 0 0; color: var(--text-dim); font-size: 14px;">OpenAI-Compatible REST API hosted directly on your Android phone</p>
            </div>
            <span class="badge">● ONLINE & SERVING</span>
        </div>

        <div class="grid">
            <div class="card">
                <h4>Host Device</h4>
                <div class="value" style="font-size: 20px;">${Build.MODEL}</div>
            </div>
            <div class="card">
                <h4>Endpoint URL</h4>
                <div class="value" style="font-size: 20px;">:${currentSettings.port}/v1</div>
            </div>
            <div class="card">
                <h4>Total Requests</h4>
                <div class="value">$totalReqCount</div>
            </div>
            <div class="card">
                <h4>Tokens Served</h4>
                <div class="value">$totalTokensCount</div>
            </div>
        </div>

        <div class="card" style="margin-bottom: 24px;">
            <h3 style="margin-top: 0;">🚀 Quick cURL Test (Run from Laptop Terminal)</h3>
            <div class="code-block">curl -X POST "http://$(location.host)/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "local-device-ai",
    "messages": [{"role": "user", "content": "Explain quantum computing in one sentence"}]
  }'</div>
        </div>

        <div class="card">
            <h3 style="margin-top: 0;">⚡ Live Browser Prompt Tester</h3>
            <textarea id="promptInput" rows="3" placeholder="Enter prompt to send directly to this Android device...">Hello! What models do you offer?</textarea>
            <div style="margin-top: 12px; display: flex; gap: 10px; align-items: center;">
                <button class="btn" onclick="sendPrompt()">Send Prompt to Phone</button>
                <span id="loading" style="display: none; color: var(--cyan);">Thinking on Android...</span>
            </div>
            <div id="outputContainer" style="display: none; margin-top: 16px;">
                <h4 style="color: var(--text-dim); margin-bottom: 6px;">PHONE RESPONSE:</h4>
                <div id="output" class="code-block" style="color: #fff; white-space: pre-wrap;"></div>
            </div>
        </div>
    </div>

    <script>
        async function sendPrompt() {
            const prompt = document.getElementById('promptInput').value;
            const loading = document.getElementById('loading');
            const outputContainer = document.getElementById('outputContainer');
            const output = document.getElementById('output');

            loading.style.display = 'inline';
            outputContainer.style.display = 'none';

            try {
                const res = await fetch('/v1/chat/completions', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        model: 'local-device-ai',
                        messages: [{ role: 'user', content: prompt }]
                    })
                });
                const data = await res.json();
                output.textContent = data.choices ? data.choices[0].message.content : JSON.stringify(data, null, 2);
                outputContainer.style.display = 'block';
            } catch (err) {
                output.textContent = 'Error: ' + err.message;
                outputContainer.style.display = 'block';
            } finally {
                loading.style.display = 'none';
            }
        }
    </script>
</body>
</html>
        """.trimIndent()
    }
}
