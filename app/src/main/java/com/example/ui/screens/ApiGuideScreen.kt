package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CodeBackground
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.MintEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent

@Composable
fun ApiGuideScreen(
    curlSnippet: String,
    pythonSnippet: String,
    jsSnippet: String,
    serverUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("cURL", "Python", "Node.js", "OpenWebUI", "Endpoints")

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("api_guide_screen")
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkSurfaceElevated,
            contentColor = CyanNeon,
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = CyanNeon
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) CyanNeon else TextSecondary
                        )
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTab) {
                0 -> { // cURL
                    item {
                        GuideSection(
                            title = "cURL Terminal Request",
                            description = "Run this in terminal from any PC, Mac, or server on the same Wi-Fi network:",
                            snippet = curlSnippet,
                            onCopy = { copyToClipboard(context, "cURL command", curlSnippet) }
                        )
                    }
                    item {
                        GuideTipCard(
                            "Streaming Response",
                            "Add `\"stream\": true` to receive real-time Server-Sent Events (SSE) word by word."
                        )
                    }
                }
                1 -> { // Python
                    item {
                        GuideSection(
                            title = "Python (Official OpenAI SDK)",
                            description = "First run: `pip install openai`\nPoint `base_url` directly at this Android device:",
                            snippet = pythonSnippet,
                            onCopy = { copyToClipboard(context, "Python code", pythonSnippet) }
                        )
                    }
                    item {
                        GuideTipCard(
                            "Zero Code Changes",
                            "Because this server implements the standard OpenAI chat completions schema, any LangChain, LlamaIndex, or AutoGen script works out of the box!"
                        )
                    }
                }
                2 -> { // Node.js
                    item {
                        GuideSection(
                            title = "JavaScript / Node.js (OpenAI npm)",
                            description = "Use the official npm package `openai` or standard fetch:",
                            snippet = jsSnippet,
                            onCopy = { copyToClipboard(context, "JavaScript code", jsSnippet) }
                        )
                    }
                }
                3 -> { // OpenWebUI / Ollama
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Devices, contentDescription = null, tint = CyanNeon)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("OpenWebUI / LibreChat Integration", fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "1. In OpenWebUI Settings > Connections, add an OpenAI API connection:\n" +
                                    "   • Base URL: $serverUrl\n" +
                                    "   • API Key: local-key (or your configured key)\n\n" +
                                    "2. OpenWebUI will automatically fetch models from GET /v1/models\n\n" +
                                    "3. Start chatting seamlessly with your phone acting as the AI backend!",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
                4 -> { // Endpoints
                    item {
                        EndpointsReferenceCard(serverUrl)
                    }
                }
            }
        }
    }
}

@Composable
fun GuideSection(
    title: String,
    description: String,
    snippet: String,
    onCopy: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = CyanNeon, modifier = Modifier.size(18.dp))
                }
            }
            Text(description, color = TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = CodeBackground,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = snippet,
                    color = MintEmerald,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun GuideTipCard(title: String, description: String) {
    Surface(
        color = DarkSurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Code, contentDescription = null, tint = VioletAccent, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                Text(description, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
fun EndpointsReferenceCard(baseUrl: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("API ENDPOINTS REFERENCE", fontWeight = FontWeight.Bold, color = CyanNeon, fontSize = 12.sp, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(12.dp))

            EndpointRow("GET", "/", "Interactive web dashboard & browser tester")
            Spacer(modifier = Modifier.height(8.dp))
            EndpointRow("GET", "/health", "Server uptime, battery, memory telemetry")
            Spacer(modifier = Modifier.height(8.dp))
            EndpointRow("GET", "/v1/models", "Standard OpenAI models catalog")
            Spacer(modifier = Modifier.height(8.dp))
            EndpointRow("POST", "/v1/chat/completions", "Chat completions (streaming SSE & JSON)")
            Spacer(modifier = Modifier.height(8.dp))
            EndpointRow("POST", "/v1/completions", "Legacy text completion endpoint")
            Spacer(modifier = Modifier.height(8.dp))
            EndpointRow("POST", "/v1/embeddings", "Semantic vector embeddings (1536-dim)")
        }
    }
}

@Composable
fun EndpointRow(method: String, path: String, desc: String) {
    val methodColor = when (method) {
        "POST" -> MintEmerald
        "GET" -> CyanNeon
        else -> VioletAccent
    }
    Surface(
        color = DarkSurfaceElevated,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = methodColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = method,
                        color = methodColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(path, color = TextPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(desc, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}
