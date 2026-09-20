package com.myra.ai

import com.myra.ai.ai.ActionType
import com.myra.ai.ai.PhoneActionExecutor
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneActionExecutorTest {

    @Test
    fun testParseActionOpenApp() {
        val json = """{"action": "OPEN_APP", "target": "WhatsApp", "message": "Opening WhatsApp..."}"""
        val action = PhoneActionExecutor.parseAction(json)
        println("DEBUG: type=${action.type}, target=${action.target}, message=${action.message}")

        assertEquals(ActionType.OPEN_APP, action.type)
        assertEquals("WhatsApp", action.target)
        assertEquals("Opening WhatsApp...", action.message)
    }

    @Test
    fun testParseActionWithMarkdownCodeFence() {
        val json = """
            ```json
            {"action": "CLICK_TEXT", "target": "Submit", "message": "Clicking Submit"}
            ```
        """.trimIndent()
        val action = PhoneActionExecutor.parseAction(json)

        assertEquals(ActionType.CLICK_TEXT, action.type)
        assertEquals("Submit", action.target)
        assertEquals("Clicking Submit", action.message)
    }

    @Test
    fun testParseActionChatResponse() {
        val json = """{"action": "CHAT_RESPONSE", "message": "Hello, how can I help you?"}"""
        val action = PhoneActionExecutor.parseAction(json)

        assertEquals(ActionType.CHAT_RESPONSE, action.type)
        assertEquals("Hello, how can I help you?", action.message)
    }

    @Test
    fun testParseActionPlainTextFallback() {
        val text = "Just a plain text response from AI"
        val action = PhoneActionExecutor.parseAction(text)

        assertEquals(ActionType.CHAT_RESPONSE, action.type)
        assertEquals("Just a plain text response from AI", action.message)
    }

    @Test
    fun testParseActionTypeText() {
        val json = """{"action": "TYPE_TEXT", "text": "Hello World", "message": "Typing text..."}"""
        val action = PhoneActionExecutor.parseAction(json)

        assertEquals(ActionType.TYPE_TEXT, action.type)
        assertEquals("Hello World", action.textToType)
    }
}
