package com.myra.ai.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
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
import com.myra.ai.ui.theme.PrimaryPurple
import com.myra.ai.ui.theme.SecondaryCyan

data class ChatMessage(
    val sender: String, // "User" or "Myra"
    val text: String,
    val isError: Boolean = false
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
                        painter = painterResource(id = R.drawable.myra_logo),
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
                    Triple("code_mode", "Website / Coding", Icons.Default.Code),
                    Triple("guide", "Guide Mode", Icons.Default.CompassCalibration),
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
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (currentScreen) {
                                "chat" -> "Chat with Myra"
                                "agents" -> "Agent Orchestrator"
                                "code_mode" -> "Website & App Coder"
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

@Composable
fun HomeTabContent(
    isListening: Boolean,
    isSpeaking: Boolean,
    isTaskRunning: Boolean,
    isWatchingVideo: Boolean,
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

        // Top Greeting & Avatar Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text(
                text = "Hello, I'm Myra",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Large Avatar with Gradient Border
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(PrimaryPurple, SecondaryCyan)
                        )
                    )
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.myra_avatar),
                    contentDescription = "Myra Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Action Chips Row
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        val quickActions = listOf(
            "Open app" to "Open YouTube",
            "Message" to "Send WhatsApp message to John: Hello!",
            "Call" to "Call Mum",
            "Watch screen" to "Watch video on screen",
            "Guide me" to "Guide me to settings",
            "Post" to "Post on Twitter: Having a great day with Myra AI!"
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickActions) { (actionLabel, actionPrompt) ->
                SuggestionChip(
                    onClick = {
                        if (actionLabel == "Watch screen") {
                            onSendMessage("watch video")
                        } else if (actionLabel == "Guide me") {
                            onSendMessage("guide me on screen")
                        } else {
                            onSendMessage(actionPrompt)
                        }
                    },
                    label = {
                        Text(
                            actionLabel,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Agent Activity / Chat Stream Preview
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (chatMessages.isEmpty()) {
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
                items(chatMessages) { msg ->
                    ChatMessageItem(msg)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Large Mic Button with Purple to Cyan Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val micGradient = if (isListening) {
                listOf(Color(0xFFEF4444), Color(0xFFDC2626))
            } else {
                listOf(PrimaryPurple, SecondaryCyan)
            }

            IconButton(
                onClick = {
                    if (isListening) onStopListening() else onStartListening()
                },
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(micGradient))
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Voice Command",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        if (isListening) {
            Text(
                "Listening... (English, Urdu, Hindi)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else if (isSpeaking) {
            Text(
                "Myra is speaking...",
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
    onStartListening: () -> Unit,
    onStopListening: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chatMessages) { msg ->
                ChatMessageItem(msg)
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
fun ChatMessageItem(msg: ChatMessage) {
    val isUser = msg.sender == "User"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColor = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        msg.isError -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bgColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = msg.sender,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(msg.text))
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy message",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (msg.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
