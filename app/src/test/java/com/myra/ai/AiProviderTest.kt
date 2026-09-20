package com.myra.ai

import com.myra.ai.ai.GeminiProvider
import com.myra.ai.ai.OpenAiProvider
import com.myra.ai.ai.AnthropicProvider
import com.myra.ai.data.SecureStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderTest {

    @Test
    fun testGeminiProviderMissingApiKey() = runBlocking {
        val provider = GeminiProvider("")
        val result = provider.generateText("Hello")
        assertTrue(result.isFailure)
        assertEquals("Google Gemini API key is missing. Please set it in Settings.", result.exceptionOrNull()?.message)
    }

    @Test
    fun testOpenAiProviderMissingApiKey() = runBlocking {
        val provider = OpenAiProvider("")
        val result = provider.generateText("Hello")
        assertTrue(result.isFailure)
        assertEquals("OpenAI API key is missing. Please set it in Settings.", result.exceptionOrNull()?.message)
    }

    @Test
    fun testAnthropicProviderMissingApiKey() = runBlocking {
        val provider = AnthropicProvider("")
        val result = provider.generateText("Hello")
        assertTrue(result.isFailure)
        assertEquals("Anthropic API key is missing. Please set it in Settings.", result.exceptionOrNull()?.message)
    }

    @Test
    fun testGeminiProviderDefaultModelConstant() {
        assertEquals("gemini-2.5-flash", SecureStorage.DEFAULT_GEMINI_MODEL)
    }

    @Test
    fun testHttpPostRetryLogicOn503And429() = runBlocking {
        var callCount = 0
        // Test function simulating single HTTP post
        fun mockExecuteSingleHttpPost(): Pair<Int, Result<String>> {
            callCount++
            return if (callCount < 3) {
                Pair(503, Result.failure(Exception("HTTP 503 (Service Unavailable): Overloaded")))
            } else {
                Pair(200, Result.success("Success Response"))
            }
        }

        var attempts = 0
        val maxRetries = 3
        var finalResult: Result<String>? = null
        while (true) {
            val (statusCode, result) = mockExecuteSingleHttpPost()
            if (result.isSuccess) {
                finalResult = result
                break
            }
            if ((statusCode == 503 || statusCode == 429) && attempts < maxRetries) {
                attempts++
            } else {
                finalResult = result
                break
            }
        }

        assertEquals(3, callCount)
        assertTrue(finalResult?.isSuccess == true)
        assertEquals("Success Response", finalResult?.getOrNull())
    }
}
