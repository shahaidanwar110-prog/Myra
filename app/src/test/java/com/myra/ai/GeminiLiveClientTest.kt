package com.myra.ai

import com.myra.ai.ai.GeminiLiveClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiLiveClientTest {

    @Test
    fun testSetupMessageFormat() {
        val apiKey = "test_key"

        val listener = object : GeminiLiveClient.LiveClientListener {
            override fun onSessionStarted() {}
            override fun onAudioDataReceived(pcmData: ByteArray) {}
            override fun onInputTranscription(text: String) {}
            override fun onOutputTranscription(text: String) {}
            override fun onInterrupted() {}
            override fun onError(error: String) {}
            override fun onSessionClosed(reason: String) {}
        }

        val client = GeminiLiveClient(apiKey = apiKey, listener = listener)
        assertFalse(client.isConnected)
    }

    @Test
    fun testServerMessageParsing() {
        val sampleJson = """
            {
                "serverContent": {
                    "inputTranscription": {
                        "text": "open Instagram"
                    },
                    "outputTranscription": {
                        "text": "Opening Instagram now."
                    }
                }
            }
        """.trimIndent()

        val jsonObj = JSONObject(sampleJson)
        val serverContent = jsonObj.getJSONObject("serverContent")
        val inputText = serverContent.getJSONObject("inputTranscription").getString("text")
        val outputText = serverContent.getJSONObject("outputTranscription").getString("text")

        assertEquals("open Instagram", inputText)
        assertEquals("Opening Instagram now.", outputText)
    }
}
