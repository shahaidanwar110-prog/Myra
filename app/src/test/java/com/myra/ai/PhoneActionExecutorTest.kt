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

    @Test
    fun testParseActionCall() {
        val json = """{"action": "CALL", "recipient": "Ali", "message": "Calling Ali..."}"""
        val action = PhoneActionExecutor.parseAction(json)

        assertEquals(ActionType.CALL, action.type)
        assertEquals("Ali", action.recipient)
        assertEquals("Calling Ali...", action.message)
    }

    @Test
    fun testParseActionSendSms() {
        val json = """{"action": "SEND_SMS", "recipient": "Ali", "text": "hello", "message": "Sending SMS to Ali..."}"""
        val action = PhoneActionExecutor.parseAction(json)

        assertEquals(ActionType.SEND_SMS, action.type)
        assertEquals("Ali", action.recipient)
        assertEquals("hello", action.textToType)
        assertEquals("Sending SMS to Ali...", action.message)
    }

    @Test
    fun testParseActionWhatsApp() {
        val json = """{"action": "WHATSAPP", "recipient": "Ali", "text": "hello", "message": "WhatsApping Ali..."}"""
        val action = PhoneActionExecutor.parseAction(json)

        assertEquals(ActionType.WHATSAPP, action.type)
        assertEquals("Ali", action.recipient)
        assertEquals("hello", action.textToType)
    }

    @Test
    fun testParseMultiStepAction() {
        val json = """
            {
              "action": "MULTI_STEP",
              "message": "Open YouTube and search for cats",
              "steps": [
                {"action": "OPEN_APP", "target": "YouTube", "message": "Launching YouTube"},
                {"action": "CLICK_TEXT", "target": "Search", "message": "Clicking Search"},
                {"action": "TYPE_TEXT", "text": "cats", "message": "Typing cats"},
                {"action": "CLICK_TEXT", "target": "Search", "message": "Submitting search"}
              ]
            }
        """.trimIndent()
        val action = PhoneActionExecutor.parseAction(json)

        println("TEST MULTI_STEP: type=${action.type}, steps=${action.steps}")
        assertEquals(ActionType.MULTI_STEP, action.type)
        val steps = action.steps
        if (steps == null) {
            println("STEPS IS NULL!")
        } else {
            println("STEPS SIZE=${steps.size}")
        }
        val numSteps = steps?.size ?: 0
        assertEquals(4, numSteps)
        assertEquals(ActionType.OPEN_APP, steps?.get(0)?.type)
        assertEquals("YouTube", steps?.get(0)?.target)
        assertEquals(ActionType.TYPE_TEXT, steps?.get(2)?.type)
        assertEquals("cats", steps?.get(2)?.textToType)
    }
}
