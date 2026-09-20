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
import com.myra.ai.accessibility.MyraAccessibilityService
import com.myra.ai.accessibility.PhoneControlManager
import com.myra.ai.ai.AiProviderManager
import com.myra.ai.ai.PhoneActionExecutor
import com.myra.ai.ai.WatchVideoManager
import com.myra.ai.data.SecureStorage
import com.myra.ai.notification.TaskNotificationManager
import com.myra.ai.notification.TaskStopReceiver
import com.myra.ai.ai.ActionType
import com.myra.ai.ai.SystemAction
import com.myra.ai.ui.screens.ActionConfirmation
import com.myra.ai.ui.screens.ChatMessage
import com.myra.ai.ui.screens.HomeScreen
import com.myra.ai.ui.screens.PermissionsScreen
import com.myra.ai.ui.screens.SettingsScreen
import com.myra.ai.ui.theme.MyraTheme
import com.myra.ai.voice.VoiceController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity : ComponentActivity() {

    private lateinit var secureStorage: SecureStorage
    private lateinit var voiceController: VoiceController
    private lateinit var aiProviderManager: AiProviderManager
    private lateinit var phoneControlManager: PhoneControlManager
    private lateinit var taskNotificationManager: TaskNotificationManager
    private lateinit var watchVideoManager: WatchVideoManager

    private var currentTaskJob: Job? = null
    private var isTaskRunningState = mutableStateOf(false)
    private var pendingConfirmationState = mutableStateOf<ActionConfirmation?>(null)

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
        watchVideoManager = WatchVideoManager(aiProviderManager)

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
                    val isWatchingVideo by watchVideoManager.isWatching.collectAsState()
                    val pendingConfirmation by remember { pendingConfirmationState }

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
                                isWatchingVideo = isWatchingVideo,
                                onStartListening = {
                                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onStopListening = { voiceController.stopListening() },
                                onStopTask = { stopCurrentTask() },
                                onStopWatching = { watchVideoManager.stopWatching() },
                                onSendMessage = { text ->
                                    chatMessages.add(ChatMessage("User", text))
                                    processUserPrompt(text, chatMessages)
                                },
                                onOpenSettings = { currentScreen = "settings" },
                                chatMessages = chatMessages,
                                pendingConfirmation = pendingConfirmation
                            )
                        }
                    }
                }
            }
        }
    }

    private fun processUserPrompt(prompt: String, chatMessages: MutableList<ChatMessage>) {
        currentTaskJob?.cancel()
        pendingConfirmationState.value = null

        val trimmedPrompt = prompt.trim()

        // Handle direct stop command
        if (trimmedPrompt.equals("stop", ignoreCase = true)) {
            stopCurrentTask()
            MyraAccessibilityService.getInstance()?.clearGuideHighlight()
            chatMessages.add(ChatMessage("Myra", "Task, video watching and guide overlay stopped."))
            voiceController.speak("Task stopped.")
            return
        }

        // Handle Guide mode ("guide me to...", "show me where to tap")
        if (trimmedPrompt.contains("guide", ignoreCase = true) ||
            trimmedPrompt.contains("show me where to tap", ignoreCase = true) ||
            trimmedPrompt.contains("where to click", ignoreCase = true)
        ) {
            currentTaskJob = lifecycleScope.launch {
                isTaskRunningState.value = true
                taskNotificationManager.showTaskRunningNotification("Analyzing screen for Guide Mode...")

                val service = MyraAccessibilityService.getInstance()
                if (service == null) {
                    val errMsg = "Accessibility service is disabled. Enable Myra in Accessibility Settings."
                    chatMessages.add(ChatMessage("Myra", errMsg, isError = true))
                    voiceController.speak(errMsg)
                } else {
                    val bitmap = kotlin.coroutines.suspendCoroutine { continuation ->
                        service.captureScreenshot { bmp -> continuation.resume(bmp) }
                    }
                    val treeText = service.dumpNodeTreeText()
                    val guideSystemPrompt = """
                        You are Myra in Guide Mode ("show me where to tap").
                        Identify the single target element on screen that the user should tap next to fulfill their request: "$prompt".
                        Respond in this exact format:
                        TARGET: <exact element text or description on screen>
                        INSTRUCTION: <short spoken/written instruction, e.g. "Yahan click karo">
                    """.trimIndent()

                    val guideResult = aiProviderManager.describeScreen(bitmap, treeText, guideSystemPrompt)
                    guideResult.onSuccess { responseText ->
                        var targetText = ""
                        var instruction = responseText

                        val lines = responseText.lines()
                        for (line in lines) {
                            if (line.startsWith("TARGET:", ignoreCase = true)) {
                                targetText = line.substringAfter(":").trim()
                            } else if (line.startsWith("INSTRUCTION:", ignoreCase = true)) {
                                instruction = line.substringAfter(":").trim()
                            }
                        }

                        if (targetText.isBlank()) {
                            targetText = prompt.replace("guide me to", "", ignoreCase = true).trim()
                        }

                        val highlighted = service.showGuideHighlight(targetText, instruction)
                        if (highlighted) {
                            val msgText = "Guide: $instruction (Marked '$targetText')"
                            chatMessages.add(ChatMessage("Myra", msgText))
                            voiceController.speak(instruction)
                        } else {
                            val msgText = "Could not locate '$targetText' on screen to mark. Instruction: $instruction"
                            chatMessages.add(ChatMessage("Myra", msgText))
                            voiceController.speak(instruction)
                        }
                    }.onFailure { err ->
                        val errText = "Guide mode failed: ${err.localizedMessage ?: err.message}"
                        chatMessages.add(ChatMessage("Myra", errText, isError = true))
                        voiceController.speak(errText)
                    }
                }

                isTaskRunningState.value = false
                taskNotificationManager.clearNotification()
            }
            return
        }

        // Handle "Watch video" mode command
        if (trimmedPrompt.contains("watch video", ignoreCase = true) || trimmedPrompt.contains("watch this video", ignoreCase = true)) {
            chatMessages.add(ChatMessage("Myra", "Started Watch Video mode. Myra is watching your screen..."))
            voiceController.speak("Started Watch Video mode. Myra is watching your screen.")
            watchVideoManager.startWatching(lifecycleScope) { summary ->
                chatMessages.add(ChatMessage("Myra", summary))
                voiceController.speak(summary)
            }
            return
        }

        // Handle direct screen description / screenshot analysis
        if (trimmedPrompt.contains("describe screen", ignoreCase = true) ||
            trimmedPrompt.contains("what is on screen", ignoreCase = true) ||
            trimmedPrompt.contains("screenshot", ignoreCase = true)
        ) {
            currentTaskJob = lifecycleScope.launch {
                isTaskRunningState.value = true
                taskNotificationManager.showTaskRunningNotification("Analyzing screen...")

                val service = MyraAccessibilityService.getInstance()
                if (service == null) {
                    val errMsg = "Accessibility service is disabled. Enable Myra in Accessibility Settings."
                    chatMessages.add(ChatMessage("Myra", errMsg, isError = true))
                    voiceController.speak(errMsg)
                } else {
                    val bitmap = kotlin.coroutines.suspendCoroutine { continuation ->
                        service.captureScreenshot { bmp -> continuation.resume(bmp) }
                    }
                    val treeText = service.dumpNodeTreeText()
                    val descResult = aiProviderManager.describeScreen(bitmap, treeText, "Describe what is on screen in detail and explain key elements.")
                    descResult.onSuccess { desc ->
                        chatMessages.add(ChatMessage("Myra", desc))
                        voiceController.speak(desc)
                    }.onFailure { err ->
                        val errText = "Screen analysis failed: ${err.localizedMessage ?: err.message}"
                        chatMessages.add(ChatMessage("Myra", errText, isError = true))
                        voiceController.speak(errText)
                    }
                }

                isTaskRunningState.value = false
                taskNotificationManager.clearNotification()
            }
            return
        }

        // Check if command can be parsed on-device without calling AI
        val localAction = com.myra.ai.ai.CommandParser.parseCommand(trimmedPrompt)
        if (localAction != null) {
            currentTaskJob = lifecycleScope.launch {
                isTaskRunningState.value = true
                taskNotificationManager.showTaskRunningNotification("Executing command: $trimmedPrompt")
                executeSingleActionWithConfirmation(localAction, chatMessages)
                isTaskRunningState.value = false
                taskNotificationManager.clearNotification()
            }
            return
        }

        currentTaskJob = lifecycleScope.launch {
            isTaskRunningState.value = true
            taskNotificationManager.showTaskRunningNotification("Processing command: $prompt")

            val result = aiProviderManager.generateText(prompt, PhoneActionExecutor.SYSTEM_PROMPT)

            result.onSuccess { reply ->
                val rootAction = PhoneActionExecutor.parseAction(reply)
                if (rootAction.type == ActionType.MULTI_STEP && !rootAction.steps.isNullOrEmpty()) {
                    executeMultiStepTask(rootAction.steps, chatMessages)
                } else {
                    executeSingleActionWithConfirmation(rootAction, chatMessages)
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

    private suspend fun executeSingleActionWithConfirmation(
        action: SystemAction,
        chatMessages: MutableList<ChatMessage>
    ): Boolean {
        val requiresConfirmation = action.type == ActionType.CALL ||
                action.type == ActionType.SEND_SMS ||
                action.type == ActionType.WHATSAPP ||
                action.type == ActionType.POST_SOCIAL_MEDIA

        if (requiresConfirmation) {
            val actionTypeName = when (action.type) {
                ActionType.CALL -> "Call"
                ActionType.SEND_SMS -> "SMS"
                ActionType.WHATSAPP -> "WhatsApp Message"
                ActionType.POST_SOCIAL_MEDIA -> "Social Media Post"
                else -> action.type.name
            }
            val recipient = action.recipient ?: action.platform ?: action.target ?: "Unknown"
            val textMsg = action.textToType ?: action.target

            val confirmed = suspendCoroutine<Boolean> { continuation ->
                pendingConfirmationState.value = ActionConfirmation(
                    actionType = actionTypeName,
                    recipient = recipient,
                    textMessage = textMsg,
                    platform = action.platform,
                    caption = action.caption,
                    hashtags = action.hashtags,
                    onConfirm = {
                        pendingConfirmationState.value = null
                        continuation.resume(true)
                    },
                    onCancel = {
                        pendingConfirmationState.value = null
                        continuation.resume(false)
                    }
                )
            }

            if (!confirmed) {
                val cancelMsg = "Cancelled $actionTypeName for $recipient."
                chatMessages.add(ChatMessage("Myra", cancelMsg))
                voiceController.speak(cancelMsg)
                return false
            }
        }

        val execResult = PhoneActionExecutor.executeAction(action, phoneControlManager)
        return execResult.fold(
            onSuccess = { resultMessage ->
                chatMessages.add(ChatMessage("Myra", resultMessage))
                voiceController.speak(resultMessage)
                true
            },
            onFailure = { err ->
                val errorMsg = err.localizedMessage ?: "Action execution failed."
                chatMessages.add(ChatMessage("Myra", "Error: $errorMsg", isError = true))
                voiceController.speak("Error: $errorMsg")
                false
            }
        )
    }

    private suspend fun executeMultiStepTask(
        steps: List<SystemAction>,
        chatMessages: MutableList<ChatMessage>
    ) {
        val total = steps.size
        for ((index, step) in steps.withIndex()) {
            val stepNum = index + 1
            val stepDesc = step.message ?: "Executing step $stepNum: ${step.type.name}"
            chatMessages.add(ChatMessage("Myra", "Step $stepNum/$total: $stepDesc"))

            val success = executeSingleActionWithConfirmation(step, chatMessages)

            if (!success) {
                val failMsg = "Multi-step task stopped. Step $stepNum of $total (${step.type.name}) failed or was cancelled."
                chatMessages.add(ChatMessage("Myra", failMsg, isError = true))
                voiceController.speak(failMsg)
                return
            }

            // Wait for screen to load/settle between steps
            if (index < total - 1) {
                delay(1500L)
            }
        }

        val completionMsg = "All $total steps completed successfully."
        chatMessages.add(ChatMessage("Myra", completionMsg))
        voiceController.speak(completionMsg)
    }

    private fun stopCurrentTask() {
        currentTaskJob?.cancel()
        currentTaskJob = null
        pendingConfirmationState.value = null
        isTaskRunningState.value = false
        watchVideoManager.stopWatching()
        MyraAccessibilityService.getInstance()?.clearGuideHighlight()
        taskNotificationManager.clearNotification()
        voiceController.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        TaskStopReceiver.onStopTaskRequested = null
        voiceController.destroy()
    }
}
