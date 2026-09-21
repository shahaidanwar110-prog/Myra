package com.myra.ai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myra.ai.accessibility.PhoneControlManager
import com.myra.ai.ai.ActionType
import com.myra.ai.ai.AiProviderManager
import com.myra.ai.ai.CommandParser
import com.myra.ai.ai.PhoneActionExecutor
import com.myra.ai.ai.SystemAction
import com.myra.ai.data.SecureStorage
import com.myra.ai.ui.screens.ChatMessage
import com.myra.ai.voice.VoiceController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ChatViewModel(
    private val secureStorage: SecureStorage,
    private val aiProviderManager: AiProviderManager,
    private val phoneControlManager: PhoneControlManager? = null,
    private val voiceController: VoiceController? = null,
    private val appDatabase: com.myra.ai.data.db.AppDatabase? = null
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private var activeTaskJob: kotlinx.coroutines.Job? = null

    fun stopCurrentTask() {
        activeTaskJob?.cancel()
        activeTaskJob = null
        voiceController?.stopSpeaking()
        _isThinking.value = false
        com.myra.ai.accessibility.AssistantOverlayManager.updateOverlayState(
            isListening = false,
            isThinking = false,
            isSpeaking = false
        )
    }

    fun getProviderInfo(): String {
        val provider = secureStorage.getActiveProvider()
        val model = secureStorage.getModel(provider)
        return "$provider ($model)"
    }

    fun addMessage(message: ChatMessage) {
        _messages.update { it + message }
    }

    fun setThinking(thinking: Boolean) {
        _isThinking.value = thinking
    }

    fun sendMessage(
        prompt: String,
        onConfirmationRequired: ((SystemAction, (Boolean) -> Unit) -> Unit)? = null
    ) {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isBlank()) return

        val lowerPrompt = trimmedPrompt.lowercase()
        if (lowerPrompt == "stop" || lowerPrompt == "cancel" || lowerPrompt == "stop task" || lowerPrompt == "shut up") {
            stopCurrentTask()
            addMessage(ChatMessage(sender = "User", text = trimmedPrompt))
            addMessage(ChatMessage(sender = "Myra", text = "Task stopped."))
            voiceController?.speak("Task stopped.")
            return
        }

        // Interrupt previous task if active
        activeTaskJob?.cancel()

        // Append user message
        addMessage(ChatMessage(sender = "User", text = trimmedPrompt))

        activeTaskJob = viewModelScope.launch {
            processPromptInternal(trimmedPrompt, onConfirmationRequired)
        }
    }

    private suspend fun processPromptInternal(
        trimmedPrompt: String,
        onConfirmationRequired: ((SystemAction, (Boolean) -> Unit) -> Unit)?
    ) {
        // 1. Check on-device command parser first
        val localAction = CommandParser.parseCommand(trimmedPrompt)
        if (localAction != null) {
            com.myra.ai.accessibility.AssistantOverlayManager.appendChatMessage("You: $trimmedPrompt")
            executeParsedAction(localAction, providerInfo = null, onConfirmationRequired = onConfirmationRequired)
            return
        }

        // 2. Not an on-device phone command -> Send to active AI provider as conversation
        _isThinking.value = true
        com.myra.ai.accessibility.AssistantOverlayManager.updateOverlayState(isListening = false, isThinking = true, isSpeaking = false)
        val providerInfoStr = getProviderInfo()

        aiProviderManager.onQuotaWaitListener = { notice ->
            addMessage(ChatMessage(sender = "Myra", text = notice, providerInfo = providerInfoStr))
            com.myra.ai.accessibility.AssistantOverlayManager.appendChatMessage("Myra: $notice")
        }

        val systemPrompt = com.myra.ai.ai.PersonalityPromptBuilder.buildSystemPrompt(secureStorage)
        val result = aiProviderManager.generateText(trimmedPrompt, systemPrompt)

        _isThinking.value = false
        com.myra.ai.accessibility.AssistantOverlayManager.updateOverlayState(isListening = false, isThinking = false, isSpeaking = false)

        result.fold(
            onSuccess = { reply ->
                val action = PhoneActionExecutor.parseAction(reply)
                if (action.type != ActionType.CHAT_RESPONSE && phoneControlManager != null) {
                    executeParsedAction(action, providerInfo = providerInfoStr, onConfirmationRequired = onConfirmationRequired)
                } else {
                    // Normal chat response or non-action text JSON
                    val replyText = if (!action.message.isNullOrBlank()) {
                        action.message
                    } else if (reply.isNotBlank()) {
                        reply.trim()
                    } else {
                        "No response received."
                    }
                    addMessage(ChatMessage(sender = "Myra", text = replyText, providerInfo = providerInfoStr))
                    speakSentenceBySentence(replyText)
                }
            },
            onFailure = { err ->
                val errorMsg = err.localizedMessage ?: err.message ?: "Unknown error"
                val fullErrorMsg = "Error: $errorMsg"
                addMessage(
                    ChatMessage(
                        sender = "Myra",
                        text = fullErrorMsg,
                        isError = true,
                        providerInfo = providerInfoStr
                    )
                )
                voiceController?.speak(fullErrorMsg)
            }
        )
    }

    private fun speakSentenceBySentence(text: String) {
        val sentences = text.split(Regex("(?<=[.!?\\n])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (sentences.isEmpty()) {
            voiceController?.speak(text)
            return
        }
        for (sentence in sentences) {
            voiceController?.speak(sentence)
        }
    }

    private suspend fun executeParsedAction(
        action: SystemAction,
        providerInfo: String?,
        onConfirmationRequired: ((SystemAction, (Boolean) -> Unit) -> Unit)?
    ) {
        if (phoneControlManager == null) {
            val msg = action.message ?: "Command parsed: ${action.type}"
            addMessage(ChatMessage(sender = "Myra", text = msg, providerInfo = providerInfo))
            return
        }

        if (action.type == ActionType.MULTI_STEP && !action.steps.isNullOrEmpty()) {
            executeMultiStepTask(action.steps, providerInfo, onConfirmationRequired)
            return
        }

        var actionToExecute = action
        if (action.type == ActionType.POST_SOCIAL_MEDIA && action.caption.isNullOrBlank()) {
            val screenTreeText = com.myra.ai.accessibility.MyraAccessibilityService.getInstance()?.dumpNodeTreeText() ?: ""
            val commentPrompt = "Read the screen caption/content below and write a short, friendly, fitting 1-sentence comment for this video:\n$screenTreeText"
            val generatedResult = aiProviderManager.generateText(commentPrompt)
            val commentText = generatedResult.getOrDefault("Awesome video! 🔥").trim()
            actionToExecute = action.copy(caption = commentText, message = "Drafted comment: \"$commentText\"")
        }

        val requiresConfirmation = actionToExecute.type == ActionType.CALL ||
                actionToExecute.type == ActionType.SEND_SMS ||
                actionToExecute.type == ActionType.WHATSAPP ||
                actionToExecute.type == ActionType.POST_SOCIAL_MEDIA

        if (requiresConfirmation && onConfirmationRequired != null) {
            if (actionToExecute.type == ActionType.POST_SOCIAL_MEDIA && !actionToExecute.caption.isNullOrBlank()) {
                val spokenPrompt = "I've drafted a comment: \"${actionToExecute.caption}\". Do you want me to post this on ${actionToExecute.platform ?: "Instagram"}?"
                addMessage(ChatMessage(sender = "Myra", text = spokenPrompt, providerInfo = providerInfo))
                voiceController?.speak(spokenPrompt)
            }

            val confirmed = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
                onConfirmationRequired(actionToExecute) { result ->
                    if (cont.isActive) cont.resume(result, null)
                }
            }

            if (!confirmed) {
                val cancelMsg = "Cancelled ${actionToExecute.type.name}."
                addMessage(ChatMessage(sender = "Myra", text = cancelMsg, providerInfo = providerInfo))
                voiceController?.speak(cancelMsg)
                return
            }
        }

        val targetStr = actionToExecute.target ?: actionToExecute.recipient ?: actionToExecute.caption ?: actionToExecute.textToType ?: "N/A"
        val execResult = PhoneActionExecutor.executeAction(actionToExecute, phoneControlManager, maxRetries = 2)

        val isSuccess = execResult.isSuccess
        val resultMsg = execResult.getOrElse { it.localizedMessage ?: "Failed" }
        val failureReason = if (isSuccess) null else execResult.exceptionOrNull()?.localizedMessage

        appDatabase?.taskDao()?.insertStepLog(
            com.myra.ai.data.db.TaskStepEntity(
                stepIndex = 1,
                action = action.type.name,
                target = targetStr,
                status = if (isSuccess) "SUCCESS" else "FAILED",
                resultMessage = resultMsg,
                failureReason = failureReason
            )
        )

        execResult.fold(
            onSuccess = { resultMessage ->
                addMessage(ChatMessage(sender = "Myra", text = resultMessage, providerInfo = providerInfo))
                com.myra.ai.accessibility.AssistantOverlayManager.appendChatMessage("Myra: $resultMessage")
                voiceController?.speak(resultMessage)
            },
            onFailure = { err ->
                val errorMsg = err.localizedMessage ?: err.message ?: "Action execution failed."
                val fullErrMsg = "Step Failed (${action.type.name}): $errorMsg"
                addMessage(
                    ChatMessage(
                        sender = "Myra",
                        text = fullErrMsg,
                        isError = true,
                        providerInfo = providerInfo
                    )
                )
                com.myra.ai.accessibility.AssistantOverlayManager.appendChatMessage("Myra Error: $errorMsg")
                voiceController?.speak("Error: $errorMsg")
            }
        )
    }

    private suspend fun executeMultiStepTask(
        steps: List<SystemAction>,
        providerInfo: String?,
        onConfirmationRequired: ((SystemAction, (Boolean) -> Unit) -> Unit)?
    ) {
        val total = steps.size
        for ((index, step) in steps.withIndex()) {
            if (!coroutineContext.isActive) break

            val stepNum = index + 1
            val stepDesc = step.message ?: "Step $stepNum: ${step.type.name}"
            addMessage(ChatMessage(sender = "Myra", text = "Step $stepNum/$total: $stepDesc", providerInfo = providerInfo))

            // Short spoken progress update for user
            val spokenProgress = "Step $stepNum: $stepDesc"
            com.myra.ai.accessibility.AssistantOverlayManager.appendChatMessage("Myra: $spokenProgress")
            voiceController?.speak(spokenProgress)

            val pcm = phoneControlManager ?: break
            val targetStr = step.target ?: step.recipient ?: step.textToType ?: "N/A"
            val execResult = PhoneActionExecutor.executeAction(step, pcm, maxRetries = 2)

            val isSuccess = execResult.isSuccess
            val resultMsg = execResult.getOrElse { it.localizedMessage ?: "Failed" }
            val failureReason = if (isSuccess) null else execResult.exceptionOrNull()?.localizedMessage

            appDatabase?.taskDao()?.insertStepLog(
                com.myra.ai.data.db.TaskStepEntity(
                    stepIndex = stepNum,
                    action = step.type.name,
                    target = targetStr,
                    status = if (isSuccess) "SUCCESS" else "FAILED",
                    resultMessage = resultMsg,
                    failureReason = failureReason
                )
            )

            if (!isSuccess) {
                val failReport = "Multi-step task stopped at step $stepNum of $total (${step.type.name}). Failure reason: ${failureReason ?: "Unknown error"}"
                addMessage(ChatMessage(sender = "Myra", text = failReport, isError = true, providerInfo = providerInfo))
                voiceController?.speak("Step $stepNum failed. $resultMsg")
                return
            }

            if (index < total - 1) {
                kotlinx.coroutines.delay(1200L)
            }
        }

        val completionMsg = "All $total steps completed successfully."
        addMessage(ChatMessage(sender = "Myra", text = completionMsg, providerInfo = providerInfo))
        voiceController?.speak(completionMsg)
    }
}
