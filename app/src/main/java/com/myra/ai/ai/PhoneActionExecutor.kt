package com.myra.ai.ai

import com.myra.ai.accessibility.PhoneControlManager

enum class ActionType {
    OPEN_APP,
    OPEN_SETTINGS,
    OPEN_CAMERA,
    OPEN_BROWSER,
    OPEN_CONTACTS,
    PRESS_HOME,
    PRESS_BACK,
    PRESS_RECENTS,
    SCROLL_UP,
    SCROLL_DOWN,
    CLICK_TEXT,
    TYPE_TEXT,
    CHAT_RESPONSE
}

data class SystemAction(
    val type: ActionType,
    val target: String? = null,
    val textToType: String? = null,
    val message: String? = null
)

object PhoneActionExecutor {

    val SYSTEM_PROMPT = """
        You are Myra, an intelligent AI phone assistant.
        When the user gives a command, evaluate if it requires a phone action or a conversational response.
        Respond STRICTLY with a valid JSON object matching one of the following formats, without any markdown code fences or extra text:

        1. To open an app by name:
           {"action": "OPEN_APP", "target": "App Name", "message": "Opening App Name..."}

        2. To open settings:
           {"action": "OPEN_SETTINGS", "message": "Opening Settings..."}

        3. To open camera:
           {"action": "OPEN_CAMERA", "message": "Opening Camera..."}

        4. To open browser:
           {"action": "OPEN_BROWSER", "target": "optional url", "message": "Opening Browser..."}

        5. To open contacts:
           {"action": "OPEN_CONTACTS", "message": "Opening Contacts..."}

        6. To press Home button:
           {"action": "PRESS_HOME", "message": "Going Home..."}

        7. To press Back button:
           {"action": "PRESS_BACK", "message": "Going Back..."}

        8. To press Recent Apps:
           {"action": "PRESS_RECENTS", "message": "Opening Recent Apps..."}

        9. To scroll up:
           {"action": "SCROLL_UP", "message": "Scrolling up..."}

        10. To scroll down:
           {"action": "SCROLL_DOWN", "message": "Scrolling down..."}

        11. To click a visible button or text:
           {"action": "CLICK_TEXT", "target": "Text to click", "message": "Clicking Text..."}

        12. To type text into focused field:
           {"action": "TYPE_TEXT", "text": "Text to type", "message": "Typing text..."}

        13. For standard conversation or answers:
           {"action": "CHAT_RESPONSE", "message": "Your conversational answer here"}
    """.trimIndent()

    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
        val match = pattern.find(json) ?: return null
        val raw = match.groupValues[1]
        return raw.replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    fun parseAction(aiResponse: String): SystemAction {
        var cleanJson = aiResponse.trim()
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.substring("```json".length)
        } else if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.substring("```".length)
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.substring(0, cleanJson.length - 3)
        }
        cleanJson = cleanJson.trim()

        val actionStr = extractJsonValue(cleanJson, "action")
        val message = extractJsonValue(cleanJson, "message")
        val target = extractJsonValue(cleanJson, "target")
        val textToType = extractJsonValue(cleanJson, "text")

        val actionType = if (actionStr != null) {
            try {
                ActionType.valueOf(actionStr)
            } catch (e: Exception) {
                ActionType.CHAT_RESPONSE
            }
        } else {
            ActionType.CHAT_RESPONSE
        }

        return SystemAction(
            type = actionType,
            target = target,
            textToType = textToType,
            message = if (actionType == ActionType.CHAT_RESPONSE && message == null) cleanJson else message
        )
    }

    fun executeAction(action: SystemAction, phoneControlManager: PhoneControlManager): Result<String> {
        return when (action.type) {
            ActionType.OPEN_APP -> {
                val appName = action.target ?: return Result.failure(Exception("App name not specified."))
                phoneControlManager.openAppByName(appName)
            }
            ActionType.OPEN_SETTINGS -> phoneControlManager.openSettings()
            ActionType.OPEN_CAMERA -> phoneControlManager.openCamera()
            ActionType.OPEN_BROWSER -> phoneControlManager.openBrowser(action.target)
            ActionType.OPEN_CONTACTS -> phoneControlManager.openContacts()
            ActionType.PRESS_HOME -> phoneControlManager.pressHome()
            ActionType.PRESS_BACK -> phoneControlManager.pressBack()
            ActionType.PRESS_RECENTS -> phoneControlManager.pressRecents()
            ActionType.SCROLL_UP -> phoneControlManager.scrollUp()
            ActionType.SCROLL_DOWN -> phoneControlManager.scrollDown()
            ActionType.CLICK_TEXT -> {
                val targetText = action.target ?: return Result.failure(Exception("Target text for click not specified."))
                phoneControlManager.clickText(targetText)
            }
            ActionType.TYPE_TEXT -> {
                val text = action.textToType ?: action.target ?: return Result.failure(Exception("Text to type not specified."))
                phoneControlManager.typeText(text)
            }
            ActionType.CHAT_RESPONSE -> {
                Result.success(action.message ?: "")
            }
        }
    }
}
