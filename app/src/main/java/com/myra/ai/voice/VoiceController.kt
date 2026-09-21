package com.myra.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.myra.ai.data.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

data class VoiceInfo(
    val name: String,
    val localeTag: String,
    val language: String
)

class VoiceController(
    private val context: Context,
    private val secureStorage: SecureStorage
) : TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _spokenText = MutableStateFlow("")
    val spokenText: StateFlow<String> = _spokenText

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    var isLiveMode: Boolean = false
        private set

    private var silenceRunnable: Runnable? = null
    var onSilenceTimeoutListener: (() -> Unit)? = null

    var onSpeechResultListener: ((String) -> Unit)? = null

    fun setLiveMode(enabled: Boolean) {
        isLiveMode = enabled
        if (enabled) {
            resetSilenceTimer()
        } else {
            cancelSilenceTimer()
            stopListening()
            stopSpeaking()
        }
    }

    private fun resetSilenceTimer() {
        cancelSilenceTimer()
        if (!isLiveMode) return
        silenceRunnable = Runnable {
            if (isLiveMode) {
                setLiveMode(false)
                onSilenceTimeoutListener?.invoke()
            }
        }
        mainHandler.postDelayed(silenceRunnable!!, 120000L) // 2 minutes auto-stop
    }

    private fun cancelSilenceTimer() {
        silenceRunnable?.let { mainHandler.removeCallbacks(it) }
        silenceRunnable = null
    }

    init {
        initSpeechRecognizer()
        textToSpeech = TextToSpeech(context, this)
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        if (isLiveMode) {
                            mainHandler.postDelayed({
                                if (isLiveMode) startListening()
                            }, 1000L)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            _spokenText.value = text
                            if (isLiveMode) {
                                resetSilenceTimer()
                            }
                            onSpeechResultListener?.invoke(text)
                        } else if (isLiveMode) {
                            mainHandler.postDelayed({
                                if (isLiveMode) startListening()
                            }, 500L)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            _spokenText.value = matches[0]
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    fun startListening() {
        if (speechRecognizer == null) initSpeechRecognizer()

        val langCode = secureStorage.getLanguage()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            _isListening.value = false
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            // Ignore
        }
        _isListening.value = false
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    if (isLiveMode) {
                        mainHandler.postDelayed({
                            if (isLiveMode) startListening()
                        }, 400L)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }
            })
            updateTtsLanguage()
        }
    }

    fun getAvailableVoices(): List<VoiceInfo> {
        if (!isTtsReady) return emptyList()
        val voices = textToSpeech?.voices ?: return emptyList()
        return voices.map { voice ->
            val lang = voice.locale?.language ?: "en"
            VoiceInfo(
                name = voice.name,
                localeTag = voice.locale?.toLanguageTag() ?: "en-US",
                language = lang
            )
        }
    }

    fun applyTtsSettings() {
        if (!isTtsReady) return
        val pitch = secureStorage.getPitch()
        val rate = secureStorage.getSpeechRate()
        val selectedVoiceName = secureStorage.getSelectedVoice()

        textToSpeech?.setPitch(pitch)
        textToSpeech?.setSpeechRate(rate)

        if (selectedVoiceName.isNotBlank()) {
            val voice = textToSpeech?.voices?.find { it.name == selectedVoiceName }
            if (voice != null) {
                textToSpeech?.voice = voice
            }
        }
    }

    fun previewVoice(voiceName: String, sampleText: String, pitch: Float, rate: Float) {
        if (!isTtsReady) return
        textToSpeech?.setPitch(pitch)
        textToSpeech?.setSpeechRate(rate)
        val voice = textToSpeech?.voices?.find { it.name == voiceName }
        if (voice != null) {
            textToSpeech?.voice = voice
        }
        val utteranceId = "myra_preview_${System.currentTimeMillis()}"
        textToSpeech?.speak(sampleText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun updateTtsLanguage() {
        if (!isTtsReady) return
        val langTag = secureStorage.getLanguage()
        val locale = when (langTag) {
            "ur-PK" -> Locale("ur", "PK")
            "hi-IN" -> Locale("hi", "IN")
            else -> Locale.US
        }
        textToSpeech?.language = locale
        applyTtsSettings()
    }

    fun speak(text: String) {
        if (!isTtsReady) return
        updateTtsLanguage()
        applyTtsSettings()
        val utteranceId = "myra_tts_${System.currentTimeMillis()}"
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        if (isTtsReady) {
            textToSpeech?.stop()
            _isSpeaking.value = false
        }
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
