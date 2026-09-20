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
import com.myra.ai.ai.AiProviderManager
import com.myra.ai.data.SecureStorage
import com.myra.ai.ui.screens.ChatMessage
import com.myra.ai.ui.screens.HomeScreen
import com.myra.ai.ui.screens.SettingsScreen
import com.myra.ai.ui.theme.MyraTheme
import com.myra.ai.voice.VoiceController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var secureStorage: SecureStorage
    private lateinit var voiceController: VoiceController
    private lateinit var aiProviderManager: AiProviderManager

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

        setContent {
            MyraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf("home") }
                    val isListening by voiceController.isListening.collectAsState()
                    val isSpeaking by voiceController.isSpeaking.collectAsState()

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
                                onBack = { currentScreen = "home" }
                            )
                        }
                        else -> {
                            HomeScreen(
                                isListening = isListening,
                                isSpeaking = isSpeaking,
                                onStartListening = {
                                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onStopListening = { voiceController.stopListening() },
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
        lifecycleScope.launch {
            val result = aiProviderManager.generateText(prompt)
            result.onSuccess { reply ->
                chatMessages.add(ChatMessage("Myra", reply))
                voiceController.speak(reply)
            }.onFailure { err ->
                val errorMsg = err.localizedMessage ?: "Unknown error"
                chatMessages.add(ChatMessage("Myra", "Error: $errorMsg", isError = true))
                voiceController.speak("Error: $errorMsg")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceController.destroy()
    }
}
