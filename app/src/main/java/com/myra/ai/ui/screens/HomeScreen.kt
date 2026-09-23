package com.myra.ai.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myra.ai.R
import com.myra.ai.ai.AgentStatus
import com.myra.ai.ai.AgentTask
import com.myra.ai.ai.AgentType
import com.myra.ai.ui.components.FlowingEdgeLightingContainer
import com.myra.ai.ui.components.GlowingOrbCenterpiece
import com.myra.ai.ui.theme.*

data class ChatMessage(
    val sender: String, // "User" or "Myra"
    val text: String,
    val isError: Boolean = false,
    val providerInfo: String? = null,
    val id: String = java.util.UUID.randomUUID().toString()
)

data class ActionConfirmation(
    val actionType: String, // "Call", "SMS", "WhatsApp", "Social Post"
    val recipient: String, // Target contact or Platform
    val textMessage: String? = null,
    val platform: String? = null,
    val caption: String? = null,
    val hashtags: String? = null,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppStructure(
    isListening: Boolean,
    isSpeaking: Boolean,
    isTaskRunning: Boolean = false,
    isWatchingVideo: Boolean = false,
    centerpieceStyle: String = "orb",
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onStopTask: () -> Unit = {},
    onStopWatching: () -> Unit = {},
    onSendMessage: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenCodeMode: () -> Unit = {},
    chatMessages: List<ChatMessage>,
    pendingConfirmation: ActionConfirmation? = null,
    agentTasks: List<AgentTask> = emptyList(),
    onCancelAgentTask: (String) -> Unit = {},
    onStopAllAgents: () -> Unit = {},
    currentScreen: String,
    onNavigate: (String) -> Unit,
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    FlowingEdgeLightingContainer(
        isListening = isListening,
        isSpeaking = isSpeaking
    ) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                // Drawer Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.myra_avatar),
                        contentDescription = "Myra Logo",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                    )
                    Column {
                        Text(
                            "Myra AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Multimodal Assistant",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Drawer Navigation Items
                val drawerItems = listOf(
                    Triple("home", "Home", Icons.Default.Home),
                    Triple("chat", "Chat", Icons.AutoMirrored.Filled.Comment),
                    Triple("agents", "Agents", Icons.Default.SmartToy),
                    Triple("task_log", "Task Log", Icons.Default.History),
                    Triple("code_mode", "Website / Coding", Icons.Default.Code),
                    Triple("guide", "Guide Mode", Icons.Default.Explore),
                    Triple("diagnostics", "Diagnostics", Icons.Default.BugReport),
                    Triple("settings", "Settings", Icons.Default.Settings),
                    Triple("permissions", "Permissions", Icons.Default.Security)
                )

                drawerItems.forEach { (route, label, icon) ->
                    NavigationDrawerItem(
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontWeight = FontWeight.SemiBold) },
                        selected = currentScreen == route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigate(route)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Theme Switch Row in Drawer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Theme",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            if (isDarkTheme) "Dark Theme" else "Light Theme",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = onToggleDarkTheme
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (currentScreen) {
                                "chat" -> "Chat with Myra"
                                "agents" -> "Agent Orchestrator"
                                "task_log" -> "Task Execution Log"
                                "code_mode" -> "Website & App Coder"
                                "diagnostics" -> "Diagnostics"
                                "settings" -> "Settings"
                                "permissions" -> "Permissions"
                                else -> "Myra AI"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Navigation Menu")
                        }
                    },
                    actions = {
                        val clipboardManager = LocalClipboardManager.current
                        if (chatMessages.isNotEmpty()) {
                            IconButton(onClick = {
                                val fullChatText = chatMessages.joinToString("\n\n") { "${it.sender}: ${it.text}" }
                                clipboardManager.setText(AnnotatedString(fullChatText))
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Chat")
                            }
                        }
                        IconButton(onClick = { onToggleDarkTheme(!isDarkTheme) }) {
                            Icon(
                                if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme"
                            )
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    val bottomNavItems = listOf(
                        Triple("home", "Home", Icons.Default.Home),
                        Triple("chat", "Chat", Icons.AutoMirrored.Filled.Comment),
                        Triple("agents", "Agents", Icons.Default.SmartToy),
                        Triple("settings", "Settings", Icons.Default.Settings)
                    )

                    bottomNavItems.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = currentScreen == route || (currentScreen !in listOf("chat", "agents", "settings", "permissions", "code_mode") && route == "home"),
                            onClick = { onNavigate(route) }
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
                when (currentScreen) {
                    "chat" -> ChatTabContent(
                        chatMessages = chatMessages,
                        onSendMessage = onSendMessage,
                        isListening = isListening,
                        isTaskRunning = isTaskRunning,
                        onStartListening = onStartListening,
                        onStopListening = onStopListening
                    )
                    "agents" -> AgentsTabContent(
                        agentTasks = agentTasks,
                        isTaskRunning = isTaskRunning,
                        onCancelAgentTask = onCancelAgentTask,
                        onStopAllAgents = onStopAllAgents,
                        onStopTask = onStopTask
                    )
                    else -> HomeTabContent(
                        isListening = isListening,
                        isSpeaking = isSpeaking,
                        isTaskRunning = isTaskRunning,
                        isWatchingVideo = isWatchingVideo,
                        centerpieceStyle = centerpieceStyle,
                        onStartListening = onStartListening,
                        onStopListening = onStopListening,
                        onStopTask = onStopTask,
                        onStopWatching = onStopWatching,
                        onSendMessage = onSendMessage,
                        chatMessages = chatMessages,
                        pendingConfirmation = pendingConfirmation,
                        agentTasks = agentTasks,
                        onCancelAgentTask = onCancelAgentTask,
                        onStopAllAgents = onStopAllAgents,
                        onOpenCodeMode = onOpenCodeMode
                    )
                }
            }
        }
    }
    }
}

@Composable
fun HomeTabContent(
    isListening: Boolean,
    isSpeaking: Boolean,
    isTaskRunning: Boolean,
    isWatchingVideo: Boolean,
    centerpieceStyle: String = "orb",
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onStopTask: () -> Unit,
    onStopWatching: () -> Unit,
    onSendMessage: (String) -> Unit,
    chatMessages: List<ChatMessage>,
    pendingConfirmation: ActionConfirmation?,
    agentTasks: List<AgentTask>,
    onCancelAgentTask: (String) -> Unit,
    onStopAllAgents: () -> Unit,
    onOpenCodeMode: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // "Myra is watching" Banner
        if (isWatchingVideo) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Myra is watching",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Button(
                        onClick = onStopWatching,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Watching",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop", color = Color.White)
                    }
                }
            }
        }

        val haptic = LocalHapticFeedback.current
        val infiniteTransition = rememberInfiniteTransition(label = "mic_transition")

        // Top Greeting & Avatar Centerpiece Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Text(
                text = "Hello, I'm Myra",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = GoldPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3D Glowing Orb Centerpiece (about 60% screen width, pulsing while Myra speaks)
            GlowingOrbCenterpiece(
                isSpeaking = isSpeaking,
                centerpieceStyle = centerpieceStyle
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val listState = rememberLazyListState()

        LaunchedEffect(chatMessages.size, isTaskRunning) {
            val totalItems = chatMessages.size + (if (isTaskRunning) 1 else 0)
            if (totalItems > 0) {
                listState.animateScrollToItem(totalItems - 1)
            }
        }

        // Agent Activity / Chat Stream Preview
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (chatMessages.isEmpty() && !isTaskRunning) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = PrimaryPurple,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "How can I help you today?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Tap the mic below or type a command to open apps, control phone, watch screen, or generate code.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = chatMessages,
                    key = { msg -> msg.id }
                ) { msg ->
                    ChatMessageItem(msg)
                }

                if (isTaskRunning) {
                    item {
                        ThinkingIndicatorItem()
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Glowing Pulse Animation for Mic Button
        val micPulseScale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = if (isListening) 1.35f else 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (isListening) 600 else 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "micPulseScale"
        )

        val micGlowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = if (isListening) 0.85f else 0.45f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (isListening) 600 else 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "micGlowAlpha"
        )

        // Gemini Live Toggle & Standard Mic Button Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gemini Live Conversation Toggle Button
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSendMessage("toggle live mode")
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isListening) PrimaryPurple.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (isListening) SecondaryCyan else GoldPrimary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.GraphicEq,
                        contentDescription = "Gemini Live",
                        tint = if (isListening) SecondaryCyan else GoldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isListening) "End Live" else "Gemini Live",
                        fontWeight = FontWeight.Bold,
                        color = if (isListening) SecondaryCyan else GoldPrimary
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center
            ) {
                // Pulse outer glow halo behind mic button
                Box(
                    modifier = Modifier
                        .scale(micPulseScale)
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = if (isListening) listOf(
                                    ErrorRed.copy(alpha = micGlowAlpha),
                                    PrimaryPurple.copy(alpha = micGlowAlpha * 0.5f),
                                    Color.Transparent
                                ) else listOf(
                                    GoldPrimary.copy(alpha = micGlowAlpha),
                                    PrimaryPurple.copy(alpha = micGlowAlpha * 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                val micGradient = if (isListening) {
                    listOf(Color(0xFFEF4444), Color(0xFFB91C1C))
                } else {
                    listOf(GoldPrimary, PrimaryPurple)
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (isListening) onStopListening() else onStartListening()
                    },
                    modifier = Modifier
                        .size(68.dp)
                        .shadow(10.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(micGradient))
                        .border(1.5.dp, GoldLight.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice Command",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        if (isListening) {
            Text(
                "Listening continuous voice stream...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else if (isSpeaking) {
            Text(
                "Myra is speaking (Mic muted for echo prevention)...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Confirmation Dialog
        pendingConfirmation?.let { conf ->
            AlertDialog(
                onDismissRequest = { conf.onCancel() },
                title = { Text("Confirm ${conf.actionType}") },
                text = {
                    Column {
                        if (!conf.platform.isNullOrBlank()) {
                            Text("Platform: ${conf.platform}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("Target: ${conf.recipient}", fontWeight = FontWeight.Bold)
                        }
                        conf.textMessage?.let { txt ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Details: $txt")
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { conf.onConfirm() }) {
                        Text("Confirm & Execute")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { conf.onCancel() }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Text Field Input Bar with Send Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Ask Myra anything...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )

            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onSendMessage(textInput)
                        textInput = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.linearGradient(listOf(PrimaryPurple, SecondaryCyan)),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun ChatTabContent(
    chatMessages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    isListening: Boolean,
    isTaskRunning: Boolean = false,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(chatMessages.size, isTaskRunning) {
        val totalItems = chatMessages.size + (if (isTaskRunning) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = chatMessages,
                key = { msg -> msg.id }
            ) { msg ->
                ChatMessageItem(msg)
            }

            if (isTaskRunning) {
                item {
                    ThinkingIndicatorItem()
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { if (isListening) onStopListening() else onStartListening() },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isListening) Color(0xFFEF4444) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Mic",
                    tint = if (isListening) Color.White else MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Type message...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )

            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onSendMessage(textInput)
                        textInput = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.linearGradient(listOf(PrimaryPurple, SecondaryCyan)),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun AgentsTabContent(
    agentTasks: List<AgentTask>,
    isTaskRunning: Boolean,
    onCancelAgentTask: (String) -> Unit,
    onStopAllAgents: () -> Unit,
    onStopTask: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Multi-Agent Orchestrator",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = {
                    onStopTask()
                    onStopAllAgents()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Stop All")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val activeTasks = agentTasks.filter { it.status == AgentStatus.RUNNING || it.status == AgentStatus.QUEUED }

        if (activeTasks.isEmpty() && !isTaskRunning) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = PrimaryPurple
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No Agents Currently Active",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "When you request tasks (e.g. phone automation, code generation, content creation), agent status will appear here live.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(activeTasks) { task ->
                    AgentCardItem(task = task, onCancel = { onCancelAgentTask(task.id) })
                }
            }
        }
    }
}

@Composable
fun AgentCardItem(
    task: AgentTask,
    onCancel: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val badgeColor = when (task.type) {
                        AgentType.PHONE -> Color(0xFFFF9800)
                        AgentType.CONTENT -> Color(0xFF9C27B0)
                        AgentType.CODER -> Color(0xFF00BCD4)
                        AgentType.CHAT -> Color(0xFF4CAF50)
                    }
                    Surface(
                        color = badgeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = task.type.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${task.status.name}: ${task.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel Agent",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ThinkingIndicatorItem() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking_dots")
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1Alpha"
    )

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(initialOffsetY = { 20 })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .shadow(4.dp, RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.myra_avatar),
                        contentDescription = "Myra Orb Avatar",
                        modifier = Modifier.size(20.dp).clip(CircleShape)
                    )
                    Text(
                        text = "Myra is thinking...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary.copy(alpha = dot1Alpha)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(msg: ChatMessage) {
    val isUser = msg.sender == "User"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val clipboardManager = LocalClipboardManager.current

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(280)) + slideInVertically(initialOffsetY = { if (isUser) 18 else -18 })
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalAlignment = alignment
        ) {
            if (isUser) {
                // User Chat Bubble: Rich Violet Gradient Glass Bubble with Thin Gold Accents
                Box(
                    modifier = Modifier
                        .widthIn(max = 295.dp)
                        .shadow(6.dp, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 4.dp))
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 4.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(PrimaryPurpleVariant, PrimaryPurple, VioletAccent)
                            )
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(listOf(GoldPrimary.copy(alpha = 0.6f), VioletAccent.copy(alpha = 0.3f))),
                            RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 4.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "You",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = GoldLight
                            )
                            IconButton(
                                onClick = { clipboardManager.setText(AnnotatedString(msg.text)) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy message",
                                    tint = GoldLight.copy(alpha = 0.85f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                // Myra Response Bubble: Translucent Glass Card with Thin Gold or Violet Border
                Surface(
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 4.dp, bottomEnd = 22.dp),
                    color = if (msg.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (msg.isError) ErrorRed else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .widthIn(max = 295.dp)
                        .shadow(4.dp, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 4.dp, bottomEnd = 22.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.myra_avatar),
                                    contentDescription = "Myra",
                                    modifier = Modifier.size(18.dp).clip(CircleShape)
                                )
                                Text(
                                    text = "Myra",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (msg.isError) MaterialTheme.colorScheme.onErrorContainer else GoldPrimary
                                )
                            }
                            IconButton(
                                onClick = { clipboardManager.setText(AnnotatedString(msg.text)) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy message",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        SelectionContainer {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (msg.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (!msg.providerInfo.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = msg.providerInfo,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}
