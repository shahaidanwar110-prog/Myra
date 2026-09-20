package com.myra.ai

import com.myra.ai.ai.ActionType
import com.myra.ai.ai.CommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CommandParserTest {

    @Test
    fun testOpenAppEnglishUrduHindi() {
        val cmdEn = CommandParser.parseCommand("open WhatsApp")
        assertNotNull(cmdEn)
        assertEquals(ActionType.OPEN_APP, cmdEn?.type)
        assertEquals("WhatsApp", cmdEn?.target)

        val cmdUr1 = CommandParser.parseCommand("کھولو WhatsApp")
        assertNotNull(cmdUr1)
        assertEquals(ActionType.OPEN_APP, cmdUr1?.type)
        assertEquals("WhatsApp", cmdUr1?.target)

        val cmdUr2 = CommandParser.parseCommand("WhatsApp کھولو")
        assertNotNull(cmdUr2)
        assertEquals(ActionType.OPEN_APP, cmdUr2?.type)
        assertEquals("WhatsApp", cmdUr2?.target)

        val cmdHi = CommandParser.parseCommand("WhatsApp खोलो")
        assertNotNull(cmdHi)
        assertEquals(ActionType.OPEN_APP, cmdHi?.type)
        assertEquals("WhatsApp", cmdHi?.target)

        val cmdRoman = CommandParser.parseCommand("kholo YouTube")
        assertNotNull(cmdRoman)
        assertEquals(ActionType.OPEN_APP, cmdRoman?.type)
        assertEquals("YouTube", cmdRoman?.target)
    }

    @Test
    fun testCallContactEnglishUrduHindi() {
        val cmdEn = CommandParser.parseCommand("call Mom")
        assertNotNull(cmdEn)
        assertEquals(ActionType.CALL, cmdEn?.type)
        assertEquals("Mom", cmdEn?.recipient)

        val cmdUr = CommandParser.parseCommand("Mom کو کال کرو")
        assertNotNull(cmdUr)
        assertEquals(ActionType.CALL, cmdUr?.type)
        assertEquals("Mom", cmdUr?.recipient)

        val cmdHi = CommandParser.parseCommand("Mom को कॉल करो")
        assertNotNull(cmdHi)
        assertEquals(ActionType.CALL, cmdHi?.type)
        assertEquals("Mom", cmdHi?.recipient)

        val cmdRoman = CommandParser.parseCommand("Mom ko call karo")
        assertNotNull(cmdRoman)
        assertEquals(ActionType.CALL, cmdRoman?.type)
        assertEquals("Mom", cmdRoman?.recipient)
    }

    @Test
    fun testSendMessageSmsWhatsApp() {
        val cmdSms = CommandParser.parseCommand("send sms to Mom: Hello how are you")
        assertNotNull(cmdSms)
        assertEquals(ActionType.SEND_SMS, cmdSms?.type)
        assertEquals("Mom", cmdSms?.recipient)
        assertEquals("Hello how are you", cmdSms?.textToType)

        val cmdWa = CommandParser.parseCommand("send whatsapp to Mom: I am reaching soon")
        assertNotNull(cmdWa)
        assertEquals(ActionType.WHATSAPP, cmdWa?.type)
        assertEquals("Mom", cmdWa?.recipient)
        assertEquals("I am reaching soon", cmdWa?.textToType)

        val cmdUrWa = CommandParser.parseCommand("Mom کو واٹس ایپ میسج کرو: Hello")
        assertNotNull(cmdUrWa)
        assertEquals(ActionType.WHATSAPP, cmdUrWa?.type)
        assertEquals("Mom", cmdUrWa?.recipient)
        assertEquals("Hello", cmdUrWa?.textToType)

        val cmdHiSms = CommandParser.parseCommand("Mom को मैसेज भेजो Hello")
        assertNotNull(cmdHiSms)
        assertEquals(ActionType.SEND_SMS, cmdHiSms?.type)
        assertEquals("Mom", cmdHiSms?.recipient)
        assertEquals("Hello", cmdHiSms?.textToType)
    }

    @Test
    fun testHomeBackScroll() {
        val homeEn = CommandParser.parseCommand("go home")
        assertNotNull(homeEn)
        assertEquals(ActionType.PRESS_HOME, homeEn?.type)

        val homeUr = CommandParser.parseCommand("ہوم")
        assertNotNull(homeUr)
        assertEquals(ActionType.PRESS_HOME, homeUr?.type)

        val backEn = CommandParser.parseCommand("back")
        assertNotNull(backEn)
        assertEquals(ActionType.PRESS_BACK, backEn?.type)

        val backUr = CommandParser.parseCommand("واپس")
        assertNotNull(backUr)
        assertEquals(ActionType.PRESS_BACK, backUr?.type)

        val scrollUp = CommandParser.parseCommand("scroll up")
        assertNotNull(scrollUp)
        assertEquals(ActionType.SCROLL_UP, scrollUp?.type)

        val scrollDownUr = CommandParser.parseCommand("نیچے اسکرول")
        assertNotNull(scrollDownUr)
        assertEquals(ActionType.SCROLL_DOWN, scrollDownUr?.type)
    }

    @Test
    fun testComplexQueriesReturnNullForAiFallback() {
        val complex = CommandParser.parseCommand("What is the weather in Tokyo today?")
        assertNull(complex)
    }
}
