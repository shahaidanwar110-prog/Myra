package com.myra.ai.ai

object CommandParser {

    fun parseCommand(input: String): SystemAction? {
        val trimmed = input.trim().trimEnd('.', '!', '?').trim()
        if (trimmed.isEmpty()) return null

        val lower = trimmed.lowercase()

        // 0. Screen / Assistant Overlay
        if (isScreenCommand(trimmed, lower)) {
            return SystemAction(
                type = ActionType.ASSISTANT_OVERLAY,
                message = "Starting Screen Assistant Overlay..."
            )
        }

        // 1. Home
        if (isHomeCommand(trimmed, lower)) {
            return SystemAction(
                type = ActionType.PRESS_HOME,
                message = "Going Home..."
            )
        }

        // 2. Back
        if (isBackCommand(trimmed, lower)) {
            return SystemAction(
                type = ActionType.PRESS_BACK,
                message = "Going Back..."
            )
        }

        // 3. Scroll
        val scrollAction = parseScrollCommand(trimmed, lower)
        if (scrollAction != null) return scrollAction

        // 4. Call contact
        val callAction = parseCallCommand(trimmed, lower)
        if (callAction != null) return callAction

        // 5. Send message / SMS / WhatsApp
        val messageAction = parseMessageCommand(trimmed, lower)
        if (messageAction != null) return messageAction

        // 6. YouTube Song Intent command
        val ytPlayAction = parseYouTubePlayCommand(trimmed, lower)
        if (ytPlayAction != null) return ytPlayAction

        // 7. Instagram Comment command
        val commentAction = parseInstagramCommentCommand(trimmed, lower)
        if (commentAction != null) return commentAction

        // 8. Open app
        val openAction = parseOpenAppCommand(trimmed, lower)
        if (openAction != null) return openAction

        return null
    }

    private fun isScreenCommand(original: String, lower: String): Boolean {
        val exactMatches = setOf(
            "screen", "look at my screen", "hey myra, look at my screen", "hey myra look at my screen",
            "look at screen", "watch screen", "screen mode", "look screen", "my screen",
            "میری اسکرین دیکھو", "اسکرین دیکھو", "मेरी स्क्रीन देखो", "स्क्रीन देखो"
        )
        if (lower in exactMatches || original in exactMatches) return true

        if (lower.contains("look at my screen") || lower.contains("look at screen") || lower.contains("watch screen")) return true
        if (original.contains("اسکرین دیکھو") || original.contains("स्क्रीन देखो")) return true
        return false
    }

    private fun isHomeCommand(original: String, lower: String): Boolean {
        val exactMatches = setOf(
            "home", "go home", "press home", "go to home", "go to home screen", "home screen",
            "ہوم", "واپس ہوم", "ہوم اسکرین", "ہوم سکرین",
            "होम", "वापस होम", "होम स्क्रीन", "home jao"
        )
        if (lower in exactMatches || original in exactMatches) return true

        if (lower.startsWith("go home") || lower.startsWith("press home") || lower.startsWith("go to home")) return true
        if (original.contains("ہوم") || original.contains("होम")) return true
        return false
    }

    private fun isBackCommand(original: String, lower: String): Boolean {
        val exactMatches = setOf(
            "back", "go back", "press back",
            "واپس", "پیچھے", "پیچھے جاؤ", "واپس جاؤ",
            "वापस", "पीछे", "वापस जाओ", "पीछे जाओ",
            "wapas", "wapas jao", "peeche jao", "piche jao"
        )
        if (lower in exactMatches || original in exactMatches) return true

        if (lower == "back" || lower == "go back" || lower == "press back") return true
        if (original == "واپس" || original == "پیچھے") return true
        if (original == "वापस" || original == "पीछे") return true
        return false
    }

    private fun parseScrollCommand(original: String, lower: String): SystemAction? {
        if (lower.contains("scroll up") || lower.contains("swipe up") || lower.contains("scroll top") ||
            original.contains("اوپر اسکرول") || original.contains("اوپر سکرول") ||
            original.contains("ऊपर स्क्रॉल") ||
            lower.contains("uper scroll") || lower.contains("oper scroll") || lower.contains("upar scroll")
        ) {
            return SystemAction(
                type = ActionType.SCROLL_UP,
                message = "Scrolling up..."
            )
        }

        if (lower.contains("scroll down") || lower.contains("swipe down") || lower.contains("scroll bottom") ||
            original.contains("نیچے اسکرول") || original.contains("نیچے سکرول") ||
            original.contains("नीचे स्क्रॉल") ||
            lower.contains("neche scroll") || lower.contains("neeche scroll") || lower.contains("niche scroll")
        ) {
            return SystemAction(
                type = ActionType.SCROLL_DOWN,
                message = "Scrolling down..."
            )
        }

        if (lower == "scroll" || lower == "scroll karo" ||
            original == "اسکرول" || original == "سکرول" || original == "اسکرول کرو" ||
            original == "स्क्रॉल" || original == "स्क्रॉल करो"
        ) {
            return SystemAction(
                type = ActionType.SCROLL_DOWN,
                message = "Scrolling down..."
            )
        }

        return null
    }

    private fun parseCallCommand(original: String, lower: String): SystemAction? {
        val callRegexes = listOf(
            Regex("^(?:make a call to|call|dial)\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(.+)\\s+کو\\s+(?:کال|فون)\\s+کرو$", RegexOption.IGNORE_CASE),
            Regex("^(.+)\\s+کو\\s+(?:کال|فون)\\s+کریں$", RegexOption.IGNORE_CASE),
            Regex("^(?:کال|فون)\\s+کرو\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(.+)\\s+کو\\s+(?:کال|فون)\\s+کریں\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(.+)\\s+को\\s+(?:कॉल|फोन)\\s+(?:کرو|करो|कीजिए|करें)$", RegexOption.IGNORE_CASE),
            Regex("^(?:कॉल|फोन)\\s+करो\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(.+)\\s+ko\\s+call\\s+(?:karo|karein|karna)$", RegexOption.IGNORE_CASE),
            Regex("^call\\s+karo\\s+(.+)$", RegexOption.IGNORE_CASE)
        )

        for (regex in callRegexes) {
            val match = regex.find(original) ?: regex.find(lower)
            if (match != null) {
                val target = match.groupValues[1].trim()
                if (target.isNotEmpty() && !target.equals("me", ignoreCase = true)) {
                    return SystemAction(
                        type = ActionType.CALL,
                        recipient = target,
                        message = "Calling $target..."
                    )
                }
            }
        }

        return null
    }

    private fun parseMessageCommand(original: String, lower: String): SystemAction? {
        val isWhatsApp = lower.contains("whatsapp") || original.contains("واٹس ایپ") || original.contains("व्हाट्सएप")

        val actionKeywordsRegex = "(?:میسج|ایس ایم ایس|واٹس ایپ|مسیج|मैसेज|एसएमएस|व्हाट्सएप|message|sms|whatsapp|text|کال|فون)"
        val verbKeywordsRegex = "(?:کرو|کریں|بھیجو|بھیجیں|کروں|کریں|करो|भेजो|कीजिए|karen|karo|bhejen|bhejo|karein)?"

        val colonRegex = Regex("^(?:send\\s+(?:a\\s+)?(?:message|sms|whatsapp|text)(?:\\s+message)?\\s+to\\s+|msg\\s+to\\s+)?(.+?)\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
        var match = colonRegex.find(original)
        if (match != null) {
            var rawRecipient = match.groupValues[1].trim()
            val textMsg = match.groupValues[2].trim()

            rawRecipient = rawRecipient.replace(Regex("^(?:send\\s+(?:a\\s+)?(?:message|sms|whatsapp|text)(?:\\s+message)?\\s+to\\s+)", RegexOption.IGNORE_CASE), "").trim()
            // Clean suffix keywords like "کو واٹس ایپ میسج کرو" or "کو میسج"
            rawRecipient = rawRecipient.replace(Regex("\\s+(?:کو|को|ko)(?:\\s+" + actionKeywordsRegex + ")*" + "(?:\\s+" + verbKeywordsRegex + ")*$", RegexOption.IGNORE_CASE), "").trim()
            rawRecipient = rawRecipient.replace(Regex("\\s+(?:کو|को|ko)$", RegexOption.IGNORE_CASE), "").trim()

            if (rawRecipient.isNotEmpty() && textMsg.isNotEmpty()) {
                val actionType = if (isWhatsApp) ActionType.WHATSAPP else ActionType.SEND_SMS
                val msgStr = if (isWhatsApp) "Opening WhatsApp to message $rawRecipient..." else "Sending SMS to $rawRecipient..."
                return SystemAction(
                    type = actionType,
                    recipient = rawRecipient,
                    textToType = textMsg,
                    message = msgStr
                )
            }
        }

        val englishPattern = Regex("^(?:send|write)\\s+(?:a\\s+)?(?:message|sms|whatsapp|text)(?:\\s+message)?\\s+to\\s+(.+?)\\s+(?:saying\\s+)?(.+)$", RegexOption.IGNORE_CASE)
        match = englishPattern.find(original)
        if (match != null) {
            val recipient = match.groupValues[1].trim()
            val textMsg = match.groupValues[2].trim()
            if (recipient.isNotEmpty() && textMsg.isNotEmpty()) {
                val actionType = if (isWhatsApp) ActionType.WHATSAPP else ActionType.SEND_SMS
                val msgStr = if (isWhatsApp) "Opening WhatsApp to message $recipient..." else "Sending SMS to $recipient..."
                return SystemAction(
                    type = actionType,
                    recipient = recipient,
                    textToType = textMsg,
                    message = msgStr
                )
            }
        }

        val multilingualPattern = Regex("^(.+?)\\s+(?:کو|को|ko)\\s+(?:میسج|ایس ایم ایس|واٹس ایپ|مسیج|मैसेज|एसएमएस|व्हाट्सएप|message|sms|whatsapp|text)(?:\\s+(?:کرو|کریں|بھیجو|بھیجیں|کروں|करो|भेजो|कीजिए|karen|karo|bhejen|bhejo|karein))?\\s*(.+)$", RegexOption.IGNORE_CASE)
        match = multilingualPattern.find(original)
        if (match != null) {
            val recipient = match.groupValues[1].trim()
            var textMsg = match.groupValues[2].trim()
            textMsg = textMsg.removePrefix(":").trim()
            if (recipient.isNotEmpty() && textMsg.isNotEmpty()) {
                val actionType = if (isWhatsApp) ActionType.WHATSAPP else ActionType.SEND_SMS
                val msgStr = if (isWhatsApp) "Opening WhatsApp to message $recipient..." else "Sending SMS to $recipient..."
                return SystemAction(
                    type = actionType,
                    recipient = recipient,
                    textToType = textMsg,
                    message = msgStr
                )
            }
        }

        return null
    }

    private fun parseYouTubePlayCommand(original: String, lower: String): SystemAction? {
        val isYt = lower.contains("youtube") || original.contains("یوٹیوب") || original.contains("یوٹیوب")
        if (!isYt) return null

        val playPatterns = listOf(
            "play a good song", "play song", "play music", "play a song",
            "گانا چلاؤ", "میوزک چلاؤ", "سونگ چلاؤ", "گانا سناؤ", "میوزک پلے کرو",
            "गाने चलाओ", "म्यूजिक चलाओ", "गाना बजाओ", "म्यूजिक बजाओ"
        )

        val matchesPlay = playPatterns.any { lower.contains(it) || original.contains(it) }
        if (matchesPlay) {
            val playRegex = Regex("^(?:play|start|search)\\s+(.+?)\\s+(?:on|in)\\s+youtube$", RegexOption.IGNORE_CASE)
            val match = playRegex.find(lower)
            val rawSong = match?.groupValues?.get(1)?.trim()

            val songQuery = if (!rawSong.isNullOrBlank() && !rawSong.equals("a good song", ignoreCase = true) && !rawSong.equals("song", ignoreCase = true) && !rawSong.equals("music", ignoreCase = true)) {
                rawSong
            } else {
                "top trending music hits"
            }

            return SystemAction(
                type = ActionType.OPEN_APP,
                target = "YouTube",
                textToType = songQuery,
                message = "Playing '$songQuery' on YouTube..."
            )
        }
        return null
    }

    private fun parseInstagramCommentCommand(original: String, lower: String): SystemAction? {
        val patterns = listOf(
            "open instagram and comment", "comment on this video", "comment on instagram video",
            "comment on this reel", "comment on reel", "write a comment on this video",
            "انسٹاگرام پر تبصرہ کرو", "انسٹاگرام پر کمنٹ کرو", "इस वीडियो पर कमेंट करो"
        )

        for (pattern in patterns) {
            if (lower.contains(pattern) || original.contains(pattern)) {
                return SystemAction(
                    type = ActionType.POST_SOCIAL_MEDIA,
                    platform = "Instagram",
                    message = "Drafting comment for Instagram video..."
                )
            }
        }
        return null
    }

    private fun parseOpenAppCommand(original: String, lower: String): SystemAction? {
        val openRegexes = listOf(
            Regex("^(?:open|launch|start)\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(?:کھولو|کھولیں)\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(.+)\\s+(?:کھولو|کھولیں)$", RegexOption.IGNORE_CASE),
            Regex("^(?:खोलो|खोलिए|खोलें)\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(.+)\\s+(?:खोलो|खोलिए|खोलें)$", RegexOption.IGNORE_CASE),
            Regex("^(?:kholo|chalao)\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(.+)\\s+(?:kholo|chalao)$", RegexOption.IGNORE_CASE)
        )

        for (regex in openRegexes) {
            val match = regex.find(original) ?: regex.find(lower)
            if (match != null) {
                var appName = match.groupValues[1].trim()
                appName = appName.replace(Regex("^(?:app|the\\s+app)\\s+", RegexOption.IGNORE_CASE), "").trim()
                if (appName.isNotEmpty()) {
                    return SystemAction(
                        type = ActionType.OPEN_APP,
                        target = appName,
                        message = "Opening $appName..."
                    )
                }
            }
        }

        return null
    }
}
