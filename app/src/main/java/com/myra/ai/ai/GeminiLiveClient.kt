package com.myra.ai.ai

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveClient(
    private val apiKey: String,
    private val modelName: String = "gemini-2.0-flash-exp",
    private val systemInstructionText: String? = null,
    private val listener: LiveClientListener
) {

    interface LiveClientListener {
        fun onSessionStarted()
        fun onAudioDataReceived(pcmData: ByteArray)
        fun onInputTranscription(text: String)
        fun onOutputTranscription(text: String)
        fun onInterrupted()
        fun onError(error: String)
        fun onSessionClosed(reason: String)
    }

    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    @Volatile var isConnected: Boolean = false
        private set

    fun connect() {
        if (apiKey.isBlank()) {
            listener.onError("Gemini API key is missing.")
            return
        }

        disconnect()

        val wsUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"

        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client?.newWebSocket(request, createWebSocketListener())
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                sendSetupConfig(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                val errMsg = t.localizedMessage ?: t.message ?: "WebSocket connection failed"
                listener.onError(errMsg)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                listener.onSessionClosed("Closing ($code): $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                listener.onSessionClosed("Closed ($code): $reason")
            }
        }
    }

    private fun sendSetupConfig(ws: WebSocket) {
        try {
            val setupObj = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", if (modelName.startsWith("models/")) modelName else "models/$modelName")
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply { put("AUDIO") })
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", "Kore")
                                })
                            })
                        })
                    })
                    if (!systemInstructionText.isNullOrBlank()) {
                        put("systemInstruction", JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", systemInstructionText)
                                })
                            })
                        })
                    }
                    put("inputAudioTranscription", JSONObject())
                    put("outputAudioTranscription", JSONObject())
                })
            }

            ws.send(setupObj.toString())
        } catch (e: Exception) {
            listener.onError("Failed to send setup config: ${e.message}")
        }
    }

    fun sendAudioChunk(pcmData: ByteArray) {
        if (!isConnected) return
        val ws = webSocket ?: return

        try {
            val base64Data = Base64.encodeToString(pcmData, Base64.NO_WRAP)
            val jsonMsg = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Data)
                        })
                    })
                })
            }
            ws.send(jsonMsg.toString())
        } catch (e: Exception) {
            // Ignore small stream send failures
        }
    }

    fun sendTextMessage(text: String) {
        if (!isConnected) return
        val ws = webSocket ?: return

        try {
            val jsonMsg = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("text", text)
                })
            }
            ws.send(jsonMsg.toString())
        } catch (e: Exception) {
            listener.onError("Failed to send text message: ${e.message}")
        }
    }

    private fun handleServerMessage(jsonString: String) {
        try {
            val json = JSONObject(jsonString)

            if (json.has("setupComplete")) {
                listener.onSessionStarted()
                return
            }

            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")

                if (serverContent.optBoolean("interrupted", false)) {
                    listener.onInterrupted()
                }

                if (serverContent.has("inputTranscription")) {
                    val inputTrans = serverContent.getJSONObject("inputTranscription")
                    val text = inputTrans.optString("text", "")
                    if (text.isNotBlank()) {
                        listener.onInputTranscription(text)
                    }
                }

                if (serverContent.has("outputTranscription")) {
                    val outputTrans = serverContent.getJSONObject("outputTranscription")
                    val text = outputTrans.optString("text", "")
                    if (text.isNotBlank()) {
                        listener.onOutputTranscription(text)
                    }
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val mimeType = inlineData.optString("mimeType", "")
                                val b64Data = inlineData.optString("data", "")
                                if (b64Data.isNotBlank() && (mimeType.contains("audio") || mimeType.isBlank())) {
                                    val audioBytes = Base64.decode(b64Data, Base64.DEFAULT)
                                    if (audioBytes.isNotEmpty()) {
                                        listener.onAudioDataReceived(audioBytes)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (json.has("goAway")) {
                listener.onSessionClosed("Server issued GoAway notice.")
            }
        } catch (e: Exception) {
            // Ignore parse errors on minor frames
        }
    }

    fun disconnect() {
        isConnected = false
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
            // Ignore
        }
        webSocket = null
        try {
            client?.dispatcher?.executorService?.shutdown()
            client?.connectionPool?.evictAll()
        } catch (e: Exception) {
            // Ignore
        }
        client = null
    }
}
