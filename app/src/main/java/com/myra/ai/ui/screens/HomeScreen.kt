package com.myra.ai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myra.ai.R
import com.myra.ai.ai.AgentStatus
import com.myra.ai.ai.AgentTask
import com.myra.ai.ai.AgentType

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
fun HomeScreen(
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
    onOpenCodeMode: () -> Unit = {},
    chatMessages: List<ChatMessage>,
    pendingConfirmation: ActionConfirmation? = null,
    agentTasks: List<AgentTask> = emptyList(),
    onCancelAgentTask: (String) -> Unit = {},
    onStopAllAgents: () -> Unit = {}
) {
    var textInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_myra_logo),
                            contentDescription = "Myra Logo",
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            "Myra AI",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    val clipboardManager = LocalClipboardManager.current
                    if (chatMessages.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                val fullChatText = chatMessages.joinToString("\n\n") { "${it.sender}: ${it.text}" }
                                clipboardManager.setText(AnnotatedString(fullChatText))
                            }
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy Chat",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy chat", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    IconButton(onClick = onOpenCodeMode) {
                        Icon(Icons.Default.Code, contentDescription = "Code Mode")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // "Myra is watching" Indicator Banner when watching video
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

            // Live Agent Status Cards & Stop-All Button
            val activeAgents = agentTasks.filter { it.status == AgentStatus.RUNNING || it.status == AgentStatus.QUEUED }
            if (activeAgents.isNotEmpty() || isTaskRunning) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Live Agent Orchestrator (${activeAgents.size}/3 Active)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Button(
                                onClick = {
                                    onStopTask()
                                    onStopAllAgents()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop All",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop All", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (task in activeAgents) {
                                AgentCardItem(
                                    task = task,
                                    onCancel = { onCancelAgentTask(task.id) }
                                )
                            }
                        }
                    }
                }
            }

            // Chat Messages List
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

            // Big Microphone Button & Voice Status
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val micGradient = if (isListening) {
                    listOf(Color(0xFFFF5252), Color(0xFFFF1744))
                } else {
                    listOf(Color(0xFF7C4DFF), Color(0xFF18FFFF))
                }

                IconButton(
                    onClick = {
                        if (isListening) onStopListening() else onStartListening()
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(micGradient))
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice Command",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            if (isListening) {
                Text(
                    "Listening... (Urdu, Hindi, English)",
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

            // Pre-Post / Pre-Action Confirmation Dialog
            pendingConfirmation?.let { conf ->
                AlertDialog(
                    onDismissRequest = { conf.onCancel() },
                    title = { Text("Confirm ${conf.actionType}") },
                    text = {
                        Column {
                            if (!conf.platform.isNullOrBlank()) {
                                Text("Platform: ${conf.platform}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(6.dp))
                            } else {
                                Text("Recipient/Target: ${conf.recipient}", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            if (!conf.caption.isNullOrBlank()) {
                                Text("Caption:", fontWeight = FontWeight.SemiBold)
                                Text(conf.caption, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            if (!conf.hashtags.isNullOrBlank()) {
                                Text("Hashtags:", fontWeight = FontWeight.SemiBold)
                                Text(conf.hashtags, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            conf.textMessage?.let { txt ->
                                if (txt.isNotBlank() && conf.caption.isNullOrBlank()) {
                                    Text("Details: $txt")
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Review details above before final post/action confirmation.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    },
                    confirmButton = {
                        Button(onClick = { conf.onConfirm() }) {
                            Text("Confirm & Post")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { conf.onCancel() }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Text Input Command Bar
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
                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
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
                .padding(10.dp),
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
                Spacer(modifier = Modifier.height(2.dp))
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
