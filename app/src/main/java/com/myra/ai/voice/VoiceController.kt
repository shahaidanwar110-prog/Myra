package com.myra.ai.voice

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import com.myra.ai.data.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
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
    private var mediaPlayer: MediaPlayer? = null
    private var isTtsReady = false
    private var activeLastUtteranceId: String = ""
    private val geminiTtsTimestamps = mutableListOf<Long>()

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

    // Gemini Live Session Management
    private var geminiLiveClient: com.myra.ai.ai.GeminiLiveClient? = null
    private var liveAudioEngine: LiveAudioEngine? = null

    private val _isGeminiLiveActive = MutableStateFlow(false)
    val isGeminiLiveActive: StateFlow<Boolean> = _isGeminiLiveActive

    var onGeminiLiveUserTranscript: ((String) -> Unit)? = null
    var onGeminiLiveModelTranscript: ((String) -> Unit)? = null
    var onGeminiLiveError: ((String) -> Unit)? = null

    fun startGeminiLiveSession(scope: CoroutineScope) {
        val apiKey = secureStorage.getApiKey(SecureStorage.PROVIDER_GEMINI)
        if (apiKey.isBlank()) {
            val err = "Gemini API key is missing in Settings. Cannot start Gemini Live session."
            com.myra.ai.util.DiagnosticsHelper.lastError = err
            onGeminiLiveError?.invoke(err)
            return
        }

        stopGeminiLiveSession()
        stopListening()
        stopSpeaking()

        val audioEngine = LiveAudioEngine(context, scope)
        liveAudioEngine = audioEngine

        val systemPrompt = com.myra.ai.ai.PersonalityPromptBuilder.buildSystemPrompt(secureStorage)

        val liveClient = com.myra.ai.ai.GeminiLiveClient(
            apiKey = apiKey,
            modelName = "gemini-2.0-flash-exp",
            systemInstructionText = systemPrompt,
            listener = object : com.myra.ai.ai.GeminiLiveClient.LiveClientListener {
                override fun onSessionStarted() {
                    _isGeminiLiveActive.value = true
                    resetSilenceTimer()
                    audioEngine.startRecording()
                }

                override fun onAudioDataReceived(pcmData: ByteArray) {
                    audioEngine.playPcmChunk(pcmData)
                }

                override fun onInputTranscription(text: String) {
                    resetSilenceTimer()
                    onGeminiLiveUserTranscript?.invoke(text)
                }

                override fun onOutputTranscription(text: String) {
                    resetSilenceTimer()
                    onGeminiLiveModelTranscript?.invoke(text)
                }

                override fun onInterrupted() {
                    audioEngine.clearPlaybackQueue()
                }

                override fun onError(error: String) {
                    com.myra.ai.util.DiagnosticsHelper.lastError = "Gemini Live Error: $error"
                    com.myra.ai.util.EventLogger.logEvent(
                        eventType = "GEMINI_LIVE_ERROR",
                        tag = "GeminiLive",
                        message = error,
                        status = "ERROR"
                    )
                    stopGeminiLiveSession()
                    onGeminiLiveError?.invoke(error)
                }

                override fun onSessionClosed(reason: String) {
                    _isGeminiLiveActive.value = false
                    audioEngine.stopAll()
                }
            }
        )

        geminiLiveClient = liveClient

        audioEngine.onAudioChunkCaptured = { pcmChunk ->
            liveClient.sendAudioChunk(pcmChunk)
        }

        // Sync playback state with isSpeaking flow
        scope.launch {
            audioEngine.isPlaying.collect { playing ->
                _isSpeaking.value = playing
                audioEngine.isMuted = playing
                _isListening.value = audioEngine.isRecording.value && !playing
            }
        }

        liveClient.connect()
    }

    fun stopGeminiLiveSession() {
        _isGeminiLiveActive.value = false
        geminiLiveClient?.disconnect()
        geminiLiveClient = null
        liveAudioEngine?.stopAll()
        liveAudioEngine = null
        _isListening.value = false
        _isSpeaking.value = false
        cancelSilenceTimer()
    }

    fun setLiveMode(enabled: Boolean) {
        isLiveMode = enabled
        if (enabled) {
            resetSilenceTimer()
        } else {
            cancelSilenceTimer()
            stopGeminiLiveSession()
            stopListening()
            stopSpeaking()
        }
    }

    private fun resetSilenceTimer() {
        cancelSilenceTimer()
        if (!isLiveMode) return
        val timeoutMins = secureStorage.getSilenceAutoStopMinutes()
        val timeoutMs = timeoutMins * 60 * 1000L
        silenceRunnable = Runnable {
            if (isLiveMode) {
                setLiveMode(false)
                onSilenceTimeoutListener?.invoke()
            }
        }
        mainHandler.postDelayed(silenceRunnable!!, timeoutMs)
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
                                if (isLiveMode && !_isSpeaking.value) startListening()
                            }, 1000L)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim() ?: ""
                        if (text.isNotBlank()) {
                            _spokenText.value = text
                            if (isLiveMode) {
                                resetSilenceTimer()
                            }
                            // Interruption shortcut: if user says stop/cancel, immediately stop TTS
                            val cleanText = text.lowercase()
                            if (cleanText == "stop" || cleanText == "cancel" || cleanText == "shut up" || cleanText == "be quiet" || cleanText == "stop listening") {
                                stopSpeaking()
                            }
                            // Pause speech recognizer while processing user input and until TTS completes
                            stopListening()
                            onSpeechResultListener?.invoke(text)
                        } else if (isLiveMode) {
                            mainHandler.postDelayed({
                                if (isLiveMode && !_isSpeaking.value) startListening()
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
        if (_isSpeaking.value) {
            _isListening.value = false
            return
        }

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
                    mainHandler.post { stopListening() }
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == activeLastUtteranceId || utteranceId?.startsWith("myra_preview") == true) {
                        _isSpeaking.value = false
                        if (isLiveMode) {
                            mainHandler.postDelayed({
                                if (isLiveMode && !_isSpeaking.value) startListening()
                            }, 500L)
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == activeLastUtteranceId) {
                        _isSpeaking.value = false
                        if (isLiveMode) {
                            mainHandler.postDelayed({
                                if (isLiveMode && !_isSpeaking.value) startListening()
                            }, 500L)
                        }
                    }
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
        var locale = when (langTag) {
            "ur-PK" -> Locale("ur", "PK")
            "hi-IN" -> Locale("hi", "IN")
            else -> Locale.US
        }
        var res = textToSpeech?.setLanguage(locale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (langTag == "ur-PK") {
                locale = Locale("hi", "IN")
                res = textToSpeech?.setLanguage(locale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    locale = Locale.US
                    textToSpeech?.setLanguage(locale)
                }
            } else if (langTag == "hi-IN") {
                locale = Locale.US
                textToSpeech?.setLanguage(locale)
            }
        }

        // Prefer an Urdu or Hindi capable voice if available when language is ur-PK or hi-IN
        if (langTag == "ur-PK" || langTag == "hi-IN") {
            val voices = textToSpeech?.voices
            if (!voices.isNullOrEmpty()) {
                val urduVoice = voices.find { it.locale?.language == "ur" }
                val hindiVoice = voices.find { it.locale?.language == "hi" }
                val preferredVoice = urduVoice ?: hindiVoice
                if (preferredVoice != null) {
                    textToSpeech?.voice = preferredVoice
                }
            }
        }

        applyTtsSettings()
    }

    fun normalizeTextForSpeech(input: String): String {
        if (input.isBlank()) return input

        var normalized = input

        // 1. Common Urdu/Hinglish mispronunciation fixes
        normalized = normalized.replace(Regex("(?i)\\btayyar\\b"), "tayaar")
        normalized = normalized.replace(Regex("(?i)\\btyar\\b"), "tayaar")
        normalized = normalized.replace(Regex("(?i)\\bshukriya\\b"), "shuk-ri-ya")
        normalized = normalized.replace(Regex("(?i)\\bkhushamdeed\\b"), "khush-am-deed")

        // 2. Convert digits in Urdu/Hindi/Hinglish context to clear words for natural pronunciation
        val langTag = secureStorage.getLanguage()
        if (langTag == "ur-PK" || langTag == "hi-IN") {
            normalized = normalized.replace(Regex("\\b0\\b"), "zero")
            normalized = normalized.replace(Regex("\\b1\\b"), "ek")
            normalized = normalized.replace(Regex("\\b2\\b"), "do")
            normalized = normalized.replace(Regex("\\b3\\b"), "teen")
            normalized = normalized.replace(Regex("\\b4\\b"), "chaar")
            normalized = normalized.replace(Regex("\\b5\\b"), "paanch")
            normalized = normalized.replace(Regex("\\b6\\b"), "chhey")
            normalized = normalized.replace(Regex("\\b7\\b"), "saat")
            normalized = normalized.replace(Regex("\\b8\\b"), "aath")
            normalized = normalized.replace(Regex("\\b9\\b"), "nau")
            normalized = normalized.replace(Regex("\\b10\\b"), "das")
        }

        return normalized
    }

    fun speak(text: String) {
        if (!isTtsReady || text.isBlank()) return
        stopListening()
        updateTtsLanguage()
        applyTtsSettings()

        val normalized = normalizeTextForSpeech(text)
        val chunks = normalized.split(Regex("(?<=[.!?\\n])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (chunks.isEmpty()) return

        val baseId = "myra_tts_${System.currentTimeMillis()}"
        var lastId = ""
        for ((index, chunk) in chunks.withIndex()) {
            val id = "${baseId}_$index"
            lastId = id
            textToSpeech?.speak(chunk, TextToSpeech.QUEUE_ADD, null, id)
        }
        activeLastUtteranceId = lastId
        _isSpeaking.value = true
    }

    fun getStyleInstruction(emotion: String?): String {
        return when (emotion?.lowercase()?.trim()) {
            "happy" -> "Say cheerfully and with joy:"
            "playful" -> "Say warmly and playfully:"
            "caring" -> "Say with deep warmth, care, and tenderness:"
            "excited" -> "Say with excitement and enthusiasm:"
            "sad" -> "Say gently with sympathy and warmth:"
            "shy" -> "Say softly, shyly, and warmly:"
            "teasing" -> "Say with playful teasing and amusement:"
            "calm" -> "Say calmly, softly, and soothingly:"
            else -> "Say warmly and naturally:"
        }
    }

    fun speakExpressiveOrFallback(text: String, emotion: String? = null, overrideVoice: String? = null) {
        if (text.isBlank()) return
        stopSpeaking()
        stopListening()

        val normalized = normalizeTextForSpeech(text)

        val isExpressiveEnabled = secureStorage.isExpressiveVoiceEnabled()
        val geminiKey = secureStorage.getApiKey(SecureStorage.PROVIDER_GEMINI)

        if (!isExpressiveEnabled) {
            com.myra.ai.util.DiagnosticsHelper.lastError = "Expressive Gemini Voice Mode disabled in Settings (using Phone TTS)."
            speak(normalized)
            return
        }

        if (geminiKey.isBlank()) {
            com.myra.ai.util.DiagnosticsHelper.lastError = "Expressive Gemini Voice Mode requires a Gemini API key in Settings."
            speak(normalized)
            return
        }

        val now = System.currentTimeMillis()
        synchronized(geminiTtsTimestamps) {
            geminiTtsTimestamps.removeAll { now - it >= 60_000L }
        }

        if (geminiTtsTimestamps.size >= 3) {
            com.myra.ai.util.DiagnosticsHelper.lastError = "Expressive Gemini Voice Mode rate limit reached (max 3 req/min). Falling back to Phone TTS."
            speak(normalized)
            return
        }

        synchronized(geminiTtsTimestamps) {
            geminiTtsTimestamps.add(now)
        }

        CoroutineScope(Dispatchers.IO).launch {
            val success = trySpeakGeminiTts(normalized, emotion, overrideVoice)
            if (!success) {
                withContext(Dispatchers.Main) {
                    speak(normalized)
                }
            }
        }
    }

    fun previewGeminiVoice(voiceName: String, sampleText: String) {
        speakExpressiveOrFallback(sampleText, emotion = "playful", overrideVoice = voiceName)
    }

    private suspend fun trySpeakGeminiTts(
        text: String,
        emotion: String?,
        overrideVoice: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val geminiKey = secureStorage.getApiKey(SecureStorage.PROVIDER_GEMINI)
            if (geminiKey.isBlank()) return@withContext false

            val ttsModel = secureStorage.getGeminiTtsModel().ifBlank { "gemini-3.1-flash-tts-preview" }
            val voiceName = overrideVoice ?: secureStorage.getGeminiVoice().ifBlank { "Kore" }

            val styleInstruction = getStyleInstruction(emotion)
            val promptText = "$styleInstruction $text"

            val urlStr = "https://generativelanguage.googleapis.com/v1beta/models/$ttsModel:generateContent?key=$geminiKey"

            val body = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", promptText) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply { put("AUDIO") })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", voiceName)
                            })
                        })
                    })
                })
            }

            val headers = mapOf("Content-Type" to "application/json")
            val (code, responseBody) = httpPost(urlStr, headers, body.toString())

            if (code !in 200..299 || responseBody.isNullOrBlank()) {
                com.myra.ai.util.DiagnosticsHelper.lastError = "Gemini TTS API request failed (HTTP $code). Falling back to Phone TTS."
                return@withContext false
            }

            val obj = JSONObject(responseBody)
            val candidates = obj.optJSONArray("candidates") ?: return@withContext false
            if (candidates.length() == 0) return@withContext false

            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content") ?: return@withContext false
            val parts = content.optJSONArray("parts") ?: return@withContext false
            if (parts.length() == 0) return@withContext false

            val part = parts.getJSONObject(0)
            val inlineData = part.optJSONObject("inlineData") ?: return@withContext false
            val base64Data = inlineData.optString("data", "")

            if (base64Data.isBlank()) return@withContext false

            val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val tempFile = File(context.cacheDir, "gemini_tts_${System.currentTimeMillis()}.mp3")
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            withContext(Dispatchers.Main) {
                playAudioFile(tempFile)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun playAudioFile(file: File) {
        stopListening()
        stopSpeaking()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _isSpeaking.value = false
                    try { file.delete() } catch (e: Exception) {}
                    if (isLiveMode) {
                        mainHandler.postDelayed({
                            if (isLiveMode && !_isSpeaking.value) startListening()
                        }, 400L)
                    }
                }
                setOnErrorListener { _, _, _ ->
                    _isSpeaking.value = false
                    try { file.delete() } catch (e: Exception) {}
                    if (isLiveMode) {
                        mainHandler.postDelayed({
                            if (isLiveMode && !_isSpeaking.value) startListening()
                        }, 400L)
                    }
                    true
                }
                start()
            }
            _isSpeaking.value = true
        } catch (e: Exception) {
            _isSpeaking.value = false
            try { file.delete() } catch (ex: Exception) {}
        }
    }

    private fun httpPost(urlString: String, headers: Map<String, String>, bodyJson: String): Pair<Int, String?> {
        return try {
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                connectTimeout = 15000
                readTimeout = 15000
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use { os ->
                os.write(bodyJson)
                os.flush()
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.let { BufferedReader(InputStreamReader(it, "UTF-8")).use { r -> r.readText() } }
            Pair(code, resp)
        } catch (e: Exception) {
            Pair(-1, null)
        }
    }

    fun stopSpeaking() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null

        if (isTtsReady) {
            textToSpeech?.stop()
            _isSpeaking.value = false
        }
    }

    fun destroy() {
        stopGeminiLiveSession()
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
