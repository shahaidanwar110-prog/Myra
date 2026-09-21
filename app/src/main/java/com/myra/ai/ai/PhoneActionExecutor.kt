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
    CALL,
    SEND_SMS,
    WHATSAPP,
    POST_SOCIAL_MEDIA,
    MULTI_STEP,
    CHAT_RESPONSE,
    ASSISTANT_OVERLAY
}

data class SystemAction(
    val type: ActionType,
    val target: String? = null,
    val textToType: String? = null,
    val recipient: String? = null,
    val platform: String? = null,
    val caption: String? = null,
    val hashtags: String? = null,
    val steps: List<SystemAction>? = null,
    val message: String? = null
)

object PhoneActionExecutor {

    val SYSTEM_PROMPT = """
        You are Myra, an intelligent AI phone assistant.
        When the user gives a command, evaluate if it requires a phone action, a call/SMS/WhatsApp message, a multi-step task, or a conversational response.
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

        13. To call a contact:
           {"action": "CALL", "recipient": "Contact Name or Phone Number", "message": "Calling Contact..."}

        14. To send an SMS:
           {"action": "SEND_SMS", "recipient": "Contact Name or Phone Number", "text": "Message content", "message": "Sending SMS..."}

        15. To WhatsApp a contact:
           {"action": "WHATSAPP", "recipient": "Contact Name or Phone Number", "text": "Message content", "message": "Opening WhatsApp..."}

        16. To post to social media (YouTube, TikTok, Facebook, Instagram):
           {"action": "POST_SOCIAL_MEDIA", "platform": "YouTube/TikTok/Facebook/Instagram", "caption": "Caption text", "hashtags": "#hashtag1 #hashtag2", "message": "Preparing social media post..."}

        17. For multi-step tasks requiring sequential actions (e.g., "Open YouTube and search for cats"):
           {"action": "MULTI_STEP", "steps": [
               {"action": "OPEN_APP", "target": "YouTube", "message": "Launching YouTube"},
               {"action": "CLICK_TEXT", "target": "Search", "message": "Finding search control"},
               {"action": "TYPE_TEXT", "text": "cats", "message": "Typing cats"},
               {"action": "CLICK_TEXT", "target": "Search", "message": "Submitting search"}
           ], "message": "Starting multi-step task..."}

        18. For standard conversation or answers:
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

        return try {
            val jsonObject = org.json.JSONObject(cleanJson)
            parseJsonObject(jsonObject)
        } catch (e: Throwable) {
            // Fallback regex parsing if org.json parsing fails or response isn't strict JSON
            val actionStr = extractJsonValue(cleanJson, "action")
            val message = extractJsonValue(cleanJson, "message")
            val target = extractJsonValue(cleanJson, "target")
            val textToType = extractJsonValue(cleanJson, "text")
            val recipient = extractJsonValue(cleanJson, "recipient")
            val platform = extractJsonValue(cleanJson, "platform")
            val caption = extractJsonValue(cleanJson, "caption")
            val hashtags = extractJsonValue(cleanJson, "hashtags")

            val actionType = if (actionStr != null) {
                try {
                    ActionType.valueOf(actionStr)
                } catch (e: Exception) {
                    ActionType.CHAT_RESPONSE
                }
            } else {
                ActionType.CHAT_RESPONSE
            }

            SystemAction(
                type = actionType,
                target = target,
                textToType = textToType,
                recipient = recipient,
                platform = platform,
                caption = caption,
                hashtags = hashtags,
                message = if (actionType == ActionType.CHAT_RESPONSE && message == null) cleanJson else message
            )
        }
    }

    private fun parseJsonObject(jsonObject: org.json.JSONObject): SystemAction {
        val actionStr = jsonObject.optString("action", "CHAT_RESPONSE")
        val actionType = try {
            ActionType.valueOf(actionStr)
        } catch (e: Exception) {
            ActionType.CHAT_RESPONSE
        }

        val target = if (jsonObject.has("target") && !jsonObject.isNull("target")) jsonObject.optString("target") else null
        val textToType = if (jsonObject.has("text") && !jsonObject.isNull("text")) jsonObject.optString("text") else null
        val recipient = if (jsonObject.has("recipient") && !jsonObject.isNull("recipient")) jsonObject.optString("recipient") else null
        val platform = if (jsonObject.has("platform") && !jsonObject.isNull("platform")) jsonObject.optString("platform") else null
        val caption = if (jsonObject.has("caption") && !jsonObject.isNull("caption")) jsonObject.optString("caption") else null
        val hashtags = if (jsonObject.has("hashtags") && !jsonObject.isNull("hashtags")) jsonObject.optString("hashtags") else null
        val message = if (jsonObject.has("message") && !jsonObject.isNull("message")) jsonObject.optString("message") else null

        val steps = if (actionType == ActionType.MULTI_STEP && jsonObject.has("steps") && !jsonObject.isNull("steps")) {
            val stepsArray = jsonObject.optJSONArray("steps")
            val list = mutableListOf<SystemAction>()
            if (stepsArray != null) {
                for (i in 0 until stepsArray.length()) {
                    val stepObj = stepsArray.getJSONObject(i)
                    list.add(parseJsonObject(stepObj))
                }
            }
            list
        } else null

        return SystemAction(
            type = actionType,
            target = target,
            textToType = textToType,
            recipient = recipient,
            platform = platform,
            caption = caption,
            hashtags = hashtags,
            steps = steps,
            message = message
        )
    }

    suspend fun executeAction(
        action: SystemAction,
        phoneControlManager: PhoneControlManager,
        maxRetries: Int = 2,
        aiProviderManager: AiProviderManager? = null
    ): Result<String> {
        // Direct intent optimization: YouTube Search
        if (action.type == ActionType.OPEN_APP && action.target.equals("YouTube", ignoreCase = true) && !action.textToType.isNullOrBlank()) {
            return phoneControlManager.openYouTubeSearchByIntent(action.textToType)
        }

        var attempt = 0
        var lastException: Exception? = null

        while (attempt <= maxRetries) {
            val result = when (action.type) {
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
                ActionType.CALL -> {
                    val recipient = action.recipient ?: action.target ?: return Result.failure(Exception("Recipient not specified for call."))
                    phoneControlManager.makeCall(recipient)
                }
                ActionType.SEND_SMS -> {
                    val recipient = action.recipient ?: return Result.failure(Exception("Recipient not specified for SMS."))
                    val text = action.textToType ?: action.target ?: ""
                    phoneControlManager.openSmsAppAndSend(recipient, text)
                }
                ActionType.WHATSAPP -> {
                    val recipient = action.recipient ?: return Result.failure(Exception("Recipient not specified for WhatsApp."))
                    val text = action.textToType ?: action.target ?: ""
                    phoneControlManager.openWhatsAppAndSend(recipient, text)
                }
                ActionType.POST_SOCIAL_MEDIA -> {
                    val plat = action.platform ?: action.target ?: "Social App"
                    val fullCaption = "${action.caption ?: ""} ${action.hashtags ?: ""}".trim()
                    phoneControlManager.postToSocialPlatform(plat, fullCaption, aiProviderManager)
                }
                ActionType.MULTI_STEP -> Result.success(action.message ?: "Starting multi-step task...")
                ActionType.CHAT_RESPONSE -> Result.success(action.message ?: "")
                ActionType.ASSISTANT_OVERLAY -> Result.success(action.message ?: "Starting Assistant Overlay...")
            }

            if (result.isSuccess) {
                return result
            } else {
                lastException = result.exceptionOrNull() as? Exception
                attempt++
                if (attempt <= maxRetries) {
                    kotlinx.coroutines.delay(1200L)
                }
            }
        }

        val targetInfo = action.target ?: action.recipient ?: action.textToType ?: "N/A"
        val failReason = lastException?.localizedMessage ?: lastException?.message ?: "Element or screen state not ready after 2 retries"
        return Result.failure(Exception("Action '${action.type.name}' on target '$targetInfo' failed after 2 retries. Reason: $failReason"))
    }
}
