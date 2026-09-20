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
}
