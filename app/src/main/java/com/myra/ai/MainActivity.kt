package com.myra.ai

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.myra.ai.accessibility.PhoneControlManager
import com.myra.ai.ai.AiProviderManager
import com.myra.ai.ai.PhoneActionExecutor
import com.myra.ai.data.SecureStorage
import com.myra.ai.notification.TaskNotificationManager
import com.myra.ai.notification.TaskStopReceiver
import com.myra.ai.ui.screens.ChatMessage
import com.myra.ai.ui.screens.HomeScreen
import com.myra.ai.ui.screens.PermissionsScreen
import com.myra.ai.ui.screens.SettingsScreen
import com.myra.ai.ui.theme.MyraTheme
import com.myra.ai.voice.VoiceController
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var secureStorage: SecureStorage
    private lateinit var voiceController: VoiceController
    private lateinit var aiProviderManager: AiProviderManager
    private lateinit var phoneControlManager: PhoneControlManager
    private lateinit var taskNotificationManager: TaskNotificationManager

    private var currentTaskJob: Job? = null
    private var isTaskRunningState = mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceController.startListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        secureStorage = SecureStorage(this)
        voiceController = VoiceController(this, secureStorage)
        aiProviderManager = AiProviderManager(secureStorage)
        phoneControlManager = PhoneControlManager(this)
        taskNotificationManager = TaskNotificationManager(this)

        TaskStopReceiver.onStopTaskRequested = {
            stopCurrentTask()
        }

        setContent {
            MyraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf("home") }
                    val isListening by voiceController.isListening.collectAsState()
                    val isSpeaking by voiceController.isSpeaking.collectAsState()
                    val isTaskRunning by remember { isTaskRunningState }

                    val chatMessages = remember { mutableStateListOf<ChatMessage>() }

                    // Speech recognition result handler
                    LaunchedEffect(Unit) {
                        voiceController.onSpeechResultListener = { spokenText ->
                            chatMessages.add(ChatMessage("User", spokenText))
                            processUserPrompt(spokenText, chatMessages)
                        }
                    }

                    when (currentScreen) {
                        "settings" -> {
                            SettingsScreen(
                                secureStorage = secureStorage,
                                onBack = { currentScreen = "home" },
                                onOpenPermissions = { currentScreen = "permissions" }
                            )
                        }
                        "permissions" -> {
                            PermissionsScreen(
                                onBack = { currentScreen = "settings" }
                            )
                        }
                        else -> {
                            HomeScreen(
                                isListening = isListening,
                                isSpeaking = isSpeaking,
                                isTaskRunning = isTaskRunning,
                                onStartListening = {
                                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onStopListening = { voiceController.stopListening() },
                                onStopTask = { stopCurrentTask() },
                                onSendMessage = { text ->
                                    chatMessages.add(ChatMessage("User", text))
                                    processUserPrompt(text, chatMessages)
                                },
                                onOpenSettings = { currentScreen = "settings" },
                                chatMessages = chatMessages
                            )
                        }
                    }
                }
            }
        }
    }

    private fun processUserPrompt(prompt: String, chatMessages: MutableList<ChatMessage>) {
        currentTaskJob?.cancel()
        currentTaskJob = lifecycleScope.launch {
            isTaskRunningState.value = true
            taskNotificationManager.showTaskRunningNotification("Processing command: $prompt")

            val result = aiProviderManager.generateText(prompt, PhoneActionExecutor.SYSTEM_PROMPT)

            result.onSuccess { reply ->
                val action = PhoneActionExecutor.parseAction(reply)
                val execResult = PhoneActionExecutor.executeAction(action, phoneControlManager)

                execResult.onSuccess { resultMessage ->
                    chatMessages.add(ChatMessage("Myra", resultMessage))
                    voiceController.speak(resultMessage)
                }.onFailure { err ->
                    val errorMsg = err.localizedMessage ?: "Action execution failed."
                    chatMessages.add(ChatMessage("Myra", "Error: $errorMsg", isError = true))
                    voiceController.speak("Error: $errorMsg")
                }
            }.onFailure { err ->
                val errorMsg = err.localizedMessage ?: "Unknown error"
                chatMessages.add(ChatMessage("Myra", "Error: $errorMsg", isError = true))
                voiceController.speak("Error: $errorMsg")
            }

            isTaskRunningState.value = false
            taskNotificationManager.clearNotification()
        }
    }

    private fun stopCurrentTask() {
        currentTaskJob?.cancel()
        currentTaskJob = null
        isTaskRunningState.value = false
        taskNotificationManager.clearNotification()
        voiceController.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        TaskStopReceiver.onStopTaskRequested = null
        voiceController.destroy()
    }
}
