package com.myra.ai

import android.content.Context
import android.content.ContextWrapper
import com.myra.ai.ai.AiProvider
import com.myra.ai.ai.AiProviderManager
import com.myra.ai.data.SecureStorage
import com.myra.ai.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class DummyContext : ContextWrapper(null)

    private class TestSecureStorage : SecureStorage(DummyContext()) {
        override fun getActiveProvider(): String = "Google Gemini"
        override fun getGeminiModel(): String = "gemini-2.5-flash"
        override fun getModel(provider: String): String = "gemini-2.5-flash"
        override fun getUserName(): String = "Friend"
        override fun getPersonalityStyle(): String = "Caring friend"
        override fun getLanguageMix(): String = "Urdu/Hindi/English Mix"
    }

    private class FakeAiProvider(
        var generateResult: Result<String> = Result.success("Fake response")
    ) : AiProvider {
        override val name: String = "Google Gemini"
        var lastPrompt: String? = null

        override suspend fun generateText(prompt: String, systemPrompt: String?): Result<String> {
            lastPrompt = prompt
            return generateResult
        }

        override suspend fun describeScreen(
            image: android.graphics.Bitmap?,
            screenTreeText: String,
            prompt: String
        ): Result<String> {
            return Result.success("Fake screen description")
        }
    }

    private class FakeAiProviderManager(
        private val fakeProvider: FakeAiProvider,
        storage: SecureStorage
    ) : AiProviderManager(storage) {
        override fun getActiveProvider(): AiProvider = fakeProvider

        override suspend fun generateText(prompt: String, systemPrompt: String?): Result<String> {
            return fakeProvider.generateText(prompt, systemPrompt)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testOnDeviceCommandExecutionDoesNotCallAi() = runTest {
        val storage = TestSecureStorage()
        val fakeProvider = FakeAiProvider()
        val fakeManager = FakeAiProviderManager(fakeProvider, storage)

        val viewModel = ChatViewModel(storage, fakeManager)

        viewModel.sendMessage("go home")
        testDispatcher.scheduler.advanceUntilIdle()

        val msgs = viewModel.messages.value
        assertEquals(2, msgs.size)
        assertEquals("User", msgs[0].sender)
        assertEquals("go home", msgs[0].text)
        assertEquals("Myra", msgs[1].sender)
        assertEquals("Going Home...", msgs[1].text)
        assertEquals(null, fakeProvider.lastPrompt)
    }

    @Test
    fun testNonPhoneCommandDispatchesToAiProviderAndAppendsReplyWithProviderInfo() = runTest {
        val storage = TestSecureStorage()
        val fakeProvider = FakeAiProvider(Result.success("Hello! How can I assist you today?"))
        val fakeManager = FakeAiProviderManager(fakeProvider, storage)

        val viewModel = ChatViewModel(storage, fakeManager)

        viewModel.sendMessage("Hello Myra")
        testDispatcher.scheduler.advanceUntilIdle()

        val msgs = viewModel.messages.value
        assertEquals(2, msgs.size)
        assertEquals("User", msgs[0].sender)
        assertEquals("Hello Myra", msgs[0].text)
        assertEquals("Myra", msgs[1].sender)
        assertEquals("Hello! How can I assist you today?", msgs[1].text)
        assertEquals("Google Gemini (gemini-2.5-flash)", msgs[1].providerInfo)
        assertFalse(viewModel.isThinking.value)
    }

    @Test
    fun testNonJsonAndInvalidActionJsonResponsesArePreservedAsReplies() = runTest {
        val plainText = "I am doing great, thank you!"
        val storage = TestSecureStorage()
        val fakeProvider = FakeAiProvider(Result.success(plainText))
        val fakeManager = FakeAiProviderManager(fakeProvider, storage)

        val viewModel = ChatViewModel(storage, fakeManager)

        viewModel.sendMessage("How are you?")
        testDispatcher.scheduler.advanceUntilIdle()

        val msgs = viewModel.messages.value
        assertEquals(2, msgs.size)
        assertEquals("Myra", msgs[1].sender)
        assertEquals(plainText, msgs[1].text)
        assertEquals("Google Gemini (gemini-2.5-flash)", msgs[1].providerInfo)
        assertFalse(msgs[1].isError)
    }

    @Test
    fun testProviderErrorAppendedAsErrorMessageWithProviderInfo() = runTest {
        val storage = TestSecureStorage()
        val fakeProvider = FakeAiProvider(Result.failure(Exception("HTTP 429: Daily free quota is finished.")))
        val fakeManager = FakeAiProviderManager(fakeProvider, storage)

        val viewModel = ChatViewModel(storage, fakeManager)

        viewModel.sendMessage("Test query")
        testDispatcher.scheduler.advanceUntilIdle()

        val msgs = viewModel.messages.value
        assertEquals(2, msgs.size)
        assertEquals("Myra", msgs[1].sender)
        assertTrue(msgs[1].isError)
        assertEquals("Error: HTTP 429: Daily free quota is finished.", msgs[1].text)
        assertEquals("Google Gemini (gemini-2.5-flash)", msgs[1].providerInfo)
        assertFalse(viewModel.isThinking.value)
    }
}
