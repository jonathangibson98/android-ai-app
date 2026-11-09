package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AiServerViewModel
import com.example.ui.screens.ApiGuideScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.PlaygroundScreen
import com.example.ui.screens.ServerDashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.MintEmerald
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                AiServerApp()
            }
        }
    }
}

enum class NavTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tag: String) {
    CONTROL("Control", Icons.Default.Dns, "nav_tab_control"),
    PLAYGROUND("Playground", Icons.Default.AutoAwesome, "nav_tab_playground"),
    LOGS("Logs", Icons.Default.Terminal, "nav_tab_logs"),
    GUIDE("Guide", Icons.Default.Code, "nav_tab_guide"),
    SETTINGS("Settings", Icons.Default.Tune, "nav_tab_settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiServerApp(
    viewModel: AiServerViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val status by viewModel.serverStatus.collectAsState()
    val stats by viewModel.serverStats.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val networkIps by viewModel.networkIps.collectAsState()
    val playgroundMessages by viewModel.playgroundMessages.collectAsState()
    val playgroundLoading by viewModel.playgroundLoading.collectAsState()
    val playgroundModel by viewModel.playgroundModel.collectAsState()
    val selectedLog by viewModel.selectedLogForDetail.collectAsState()

    // Request notification permission on Android 13+ for background foreground service
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        viewModel.initNetwork(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = DarkBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (status.isRunning) MintEmerald.copy(alpha = 0.2f) else RoseError.copy(alpha = 0.2f),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (status.isRunning) MintEmerald else RoseError)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI SERVER",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = TextPrimary,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = ":${status.port}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = CyanNeon,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                NavTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 10.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanNeon,
                            selectedTextColor = CyanNeon,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextMuted,
                            indicatorColor = DarkSurfaceElevated
                        ),
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                label = "tab_animation"
            ) { targetTab ->
                when (targetTab) {
                    0 -> ServerDashboardScreen(
                        status = status,
                        stats = stats,
                        recentLogs = logs,
                        onToggleServer = {
                            if (status.isRunning) {
                                viewModel.stopServer(context)
                            } else {
                                viewModel.startServer(context)
                            }
                        },
                        onRefreshIp = { viewModel.initNetwork(context) },
                        onNavigateToLogs = { selectedTab = 2 },
                        onNavigateToGuide = { selectedTab = 3 },
                        onNavigateToPlayground = { selectedTab = 1 },
                        onSelectLog = { log ->
                            viewModel.selectLogForDetail(log)
                            selectedTab = 2
                        }
                    )
                    1 -> PlaygroundScreen(
                        messages = playgroundMessages,
                        isLoading = playgroundLoading,
                        selectedModel = playgroundModel,
                        onModelChange = { viewModel.setPlaygroundModel(it) },
                        onSendPrompt = { viewModel.sendPlaygroundPrompt(it) },
                        onClearHistory = { viewModel.clearPlayground() }
                    )
                    2 -> LogsScreen(
                        logs = logs,
                        selectedLog = selectedLog,
                        onSelectLog = { viewModel.selectLogForDetail(it) },
                        onClearLogs = { viewModel.clearLogs() }
                    )
                    3 -> ApiGuideScreen(
                        curlSnippet = viewModel.getCurlSnippet(),
                        pythonSnippet = viewModel.getPythonSnippet(),
                        jsSnippet = viewModel.getJsSnippet(),
                        serverUrl = viewModel.getEndpointUrl()
                    )
                    4 -> SettingsScreen(
                        settings = settings,
                        isServerRunning = status.isRunning,
                        networkIps = networkIps,
                        onUpdateSettings = { viewModel.updateSettings(it) }
                    )
                }
            }
        }
    }
}
