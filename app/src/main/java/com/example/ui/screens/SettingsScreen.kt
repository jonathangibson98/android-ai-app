package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ServerSettings
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.MintEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent
import java.util.UUID

@Composable
fun SettingsScreen(
    settings: ServerSettings,
    isServerRunning: Boolean,
    networkIps: List<Pair<String, String>>,
    onUpdateSettings: (ServerSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var portText by remember(settings.port) { mutableStateOf(settings.port.toString()) }
    var apiKeyText by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var systemPromptText by remember(settings.customSystemPrompt) { mutableStateOf(settings.customSystemPrompt) }
    var geminiKeyText by remember(settings.upstreamGeminiKey) { mutableStateOf(settings.upstreamGeminiKey) }

    val portPresets = listOf(8080, 5000, 11434, 8000, 3000)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Network Interfaces Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lan, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DETECTED NETWORK INTERFACES", fontWeight = FontWeight.Bold, color = CyanNeon, fontSize = 12.sp, letterSpacing = 1.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    networkIps.forEach { (name, ip) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    copyToClipboard(context, "IP Address", ip)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(name, fontSize = 12.sp, color = TextSecondary)
                                    Text(ip, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy IP", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Port Configuration
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("HTTP SERVER PORT", fontWeight = FontWeight.Bold, color = CyanNeon, fontSize = 12.sp, letterSpacing = 1.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isServerRunning) "⚠️ Stop server on Control tab to change port" else "Select or enter custom port:",
                        color = if (isServerRunning) VioletAccent else TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(portPresets) { p ->
                            val selected = p == settings.port
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) CyanNeon else DarkSurfaceElevated,
                                modifier = Modifier.clickable(enabled = !isServerRunning) {
                                    portText = p.toString()
                                    onUpdateSettings(settings.copy(port = p))
                                }
                            ) {
                                Text(
                                    text = if (p == 11434) "11434 (Ollama)" else "$p",
                                    color = if (selected) Color.Black else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = portText,
                        onValueChange = {
                            portText = it
                            val intVal = it.toIntOrNull()
                            if (intVal != null && intVal in 1024..65535) {
                                onUpdateSettings(settings.copy(port = intVal))
                            }
                        },
                        enabled = !isServerRunning,
                        label = { Text("Custom Port Number (1024 - 65535)") },
                        modifier = Modifier.fillMaxWidth().testTag("port_input_field"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                        ),
                        singleLine = true
                    )
                }
            }
        }

        // Authentication Settings
        item {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MintEmerald, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("REQUIRE API KEY AUTH", fontWeight = FontWeight.Bold, color = MintEmerald, fontSize = 12.sp, letterSpacing = 1.sp)
                        }
                        Switch(
                            checked = settings.requireAuth,
                            onCheckedChange = { onUpdateSettings(settings.copy(requireAuth = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = MintEmerald, checkedTrackColor = MintEmerald.copy(alpha = 0.3f)),
                            modifier = Modifier.testTag("require_auth_switch")
                        )
                    }

                    if (settings.requireAuth) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Clients must provide header: 'Authorization: Bearer <key>'",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = apiKeyText,
                            onValueChange = {
                                apiKeyText = it
                                onUpdateSettings(settings.copy(apiKey = it))
                            },
                            label = { Text("Server API Key") },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val newKey = "sk-" + UUID.randomUUID().toString().replace("-", "").take(24)
                                    apiKeyText = newKey
                                    onUpdateSettings(settings.copy(apiKey = newKey))
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Generate new key", tint = CyanNeon)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("api_key_input_field"),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MintEmerald,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        }

        // Background Execution & WakeLock
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("BACKGROUND SERVICE & WAKELOCK", fontWeight = FontWeight.Bold, color = CyanNeon, fontSize = 12.sp, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Keep server alive when screen is off or app is closed via Foreground Service",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = settings.keepAwake,
                        onCheckedChange = { onUpdateSettings(settings.copy(keepAwake = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyanNeon, checkedTrackColor = CyanNeon.copy(alpha = 0.3f)),
                        modifier = Modifier.testTag("keep_awake_switch")
                    )
                }
            }
        }

        // Optional Upstream Gemini Proxy Key
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = VioletAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("UPSTREAM GEMINI API KEY (OPTIONAL)", fontWeight = FontWeight.Bold, color = VioletAccent, fontSize = 12.sp, letterSpacing = 1.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "When provided, enables high-intelligence Gemini 2.5 Flash cloud models over this Android proxy.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = geminiKeyText,
                        onValueChange = {
                            geminiKeyText = it
                            onUpdateSettings(settings.copy(upstreamGeminiKey = it))
                        },
                        placeholder = { Text("AIzaSy...", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("gemini_key_input_field"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VioletAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                        ),
                        singleLine = true
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}
