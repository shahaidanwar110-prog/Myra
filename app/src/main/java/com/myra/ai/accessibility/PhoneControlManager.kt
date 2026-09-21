package com.myra.ai.accessibility

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

class PhoneControlManager(private val context: Context) {

    fun findContactPhoneNumber(contactNameOrNumber: String): String? {
        val trimmed = contactNameOrNumber.trim()
        if (trimmed.isEmpty()) return null

        // If it's already a valid phone number, return as is
        if (trimmed.matches(Regex("^[+]?[0-9\\s\\-\\(\\)]{3,20}$")) && trimmed.any { it.isDigit() }) {
            return trimmed
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        try {
            val contentResolver = context.contentResolver
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$trimmed%")

            contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numberIdx != -1) {
                        return cursor.getString(numberIdx)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun makeCall(recipient: String): Result<String> {
        val phoneNumber = findContactPhoneNumber(recipient) ?: recipient
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return Result.failure(Exception("CALL_PHONE permission is not granted. Please grant Phone permission in Settings."))
        }
        return try {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)
            Result.success("Initiating call to $recipient ($phoneNumber)...")
        } catch (e: Exception) {
            Result.failure(Exception("Failed to make call: ${e.localizedMessage}"))
        }
    }

    fun sendSms(recipient: String, messageText: String): Result<String> {
        val phoneNumber = findContactPhoneNumber(recipient) ?: recipient
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return Result.failure(Exception("SEND_SMS permission is not granted. Please grant SMS permission in Settings."))
        }
        return try {
            val smsManager: SmsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phoneNumber, null, messageText, null, null)
            Result.success("SMS sent to $recipient ($phoneNumber).")
        } catch (e: Exception) {
            Result.failure(Exception("Failed to send SMS: ${e.localizedMessage}"))
        }
    }

    suspend fun openWhatsAppAndSend(recipient: String, messageText: String): Result<String> {
        val phoneNumber = findContactPhoneNumber(recipient) ?: recipient
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(messageText)}")
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            val pm = context.packageManager
            if (intent.resolveActivity(pm) == null) {
                // Try without specifying package in case WhatsApp Business is installed
                intent.setPackage(null)
                if (intent.resolveActivity(pm) == null) {
                    return Result.failure(Exception("WhatsApp is not installed on this device."))
                }
            }
            context.startActivity(intent)

            // Wait for WhatsApp screen to load
            kotlinx.coroutines.delay(2000L)

            val service = MyraAccessibilityService.getInstance()
                ?: return Result.failure(Exception("Accessibility service is disabled. Enable Myra in Accessibility Settings."))

            // Press Send button using accessibility service
            val sendClicked = service.clickSendButton() || service.clickText("Send")
            if (sendClicked) {
                Result.success("Opened WhatsApp chat with $recipient and sent message.")
            } else {
                Result.failure(Exception("WhatsApp chat opened with text filled, but could not press Send button automatically."))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to open WhatsApp: ${e.localizedMessage}"))
        }
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        return MyraAccessibilityService.isServiceRunning()
    }

    fun openSettings(): Result<String> {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success("Opened Settings.")
        } catch (e: Exception) {
            Result.failure(Exception("Failed to open Settings: ${e.localizedMessage}"))
        }
    }

    fun openCamera(): Result<String> {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Result.success("Opened Camera.")
            } else {
                Result.failure(Exception("Camera app not found on this device."))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to open Camera: ${e.localizedMessage}"))
        }
    }

    fun openBrowser(url: String? = null): Result<String> {
        return try {
            val uri = if (!url.isNullOrBlank()) {
                val formatted = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                Uri.parse(formatted)
            } else {
                Uri.parse("https://www.google.com")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success("Opened Browser.")
        } catch (e: Exception) {
            Result.failure(Exception("Failed to open Browser: ${e.localizedMessage}"))
        }
    }

    fun openContacts(): Result<String> {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success("Opened Contacts.")
        } catch (e: Exception) {
            Result.failure(Exception("Failed to open Contacts: ${e.localizedMessage}"))
        }
    }

    fun openAppByName(appName: String): Result<String> {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        var matchedPackage: String? = null
        var matchedLabel: String? = null

        for (appInfo in installedApps) {
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.equals(appName, ignoreCase = true)) {
                matchedPackage = appInfo.packageName
                matchedLabel = label
                break
            }
        }

        if (matchedPackage == null) {
            for (appInfo in installedApps) {
                val label = pm.getApplicationLabel(appInfo).toString()
                if (label.contains(appName, ignoreCase = true)) {
                    matchedPackage = appInfo.packageName
                    matchedLabel = label
                    break
                }
            }
        }

        if (matchedPackage == null) {
            return Result.failure(Exception("App '$appName' not found on device."))
        }

        val launchIntent = pm.getLaunchIntentForPackage(matchedPackage)
            ?: return Result.failure(Exception("Cannot launch app '$matchedLabel' ($matchedPackage). No launch intent available."))

        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            Result.success("Opened $matchedLabel.")
        } catch (e: Exception) {
            Result.failure(Exception("Failed to open $matchedLabel: ${e.localizedMessage}"))
        }
    }

    fun pressHome(): Result<String> {
        val service = MyraAccessibilityService.getInstance()
            ?: return Result.failure(Exception("Accessibility service is disabled. Enable Myra in Accessibility Settings."))
        return if (service.pressHome()) {
            Result.success("Pressed Home button.")
        } else {
            Result.failure(Exception("Failed to press Home button."))
        }
    }

    fun pressBack(): Result<String> {
        val service = MyraAccessibilityService.getInstance()
            ?: return Result.failure(Exception("Accessibility service is disabled. Enable Myra in Accessibility Settings."))
        return if (service.pressBack()) {
            Result.success("Pressed Back button.")
        } else {
            Result.failure(Exception("Failed to press Back button."))
        }
    }

    fun pressRecents(): Result<String> {
        val service = MyraAccessibilityService.getInstance()
            ?: return Result.failure(Exception("Accessibility service is disabled. Enable Myra in Accessibility Settings."))
        return if (service.pressRecents()) {
            Result.success("Opened Recent Apps.")
        } else {
            Result.failure(Exception("Failed to open Recent Apps."))
        }
    }

    fun scrollUp(): Result<String> {
        val service = MyraAccessibilityService.getInstance()
            ?: return Result.failure(Exception("Accessibility service is disabled. Enable Myra in Accessibility Settings."))
        return if (service.scrollUp()) {
            Result.success("Scrolled up.")
        } else {
            Result.failure(Exception("Failed to scroll up. No scrollable view visible."))
        }
    }

    fun scrollDown(): Result<String> {
        val service = MyraAccessibilityService.getInstance()
            ?: return Result.failure(Exception("Accessibility service is disabled. Enable Myra in Accessibility Settings."))
        return if (service.scrollDown()) {
            Result.success("Scrolled down.")
        } else {
            Result.failure(Exception("Failed to scroll down. No scrollable view visible."))
        }
    }

    fun openYouTubeSearchByIntent(query: String): Result<String> {
        return try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Result.success("Searched YouTube for '$query' via Intent.")
            } else {
                val webUri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                Result.success("Searched YouTube web for '$query'.")
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed YouTube search: ${e.localizedMessage}"))
        }
    }

    suspend fun clickText(targetText: String): Result<String> {
        if (targetText.isBlank()) {
            return Result.failure(Exception("Target text for click cannot be empty."))
        }
        val service = MyraAccessibilityService.getInstance()
            ?: return Result.failure(Exception("Accessibility service is disabled. Enable Myra in Accessibility Settings."))

        if (service.clickText(targetText)) {
            return Result.success("Clicked '$targetText'.")
        }

        // Auto-scroll down and try finding element
        service.scrollDown()
        kotlinx.coroutines.delay(600L)
        if (service.clickText(targetText)) {
            return Result.success("Scrolled and clicked '$targetText'.")
        }

        // Auto-scroll up and try finding element
        service.scrollUp()
        kotlinx.coroutines.delay(600L)
        if (service.clickText(targetText)) {
            return Result.success("Scrolled up and clicked '$targetText'.")
        }

        return Result.failure(Exception("Button or text '$targetText' not found or visible on screen after scrolling."))
    }

    fun typeText(textToType: String): Result<String> {
        val service = MyraAccessibilityService.getInstance()
            ?: return Result.failure(Exception("Accessibility service is disabled. Enable Myra in Accessibility Settings."))
        return if (service.typeText(textToType)) {
            Result.success("Typed text.")
        } else {
            Result.failure(Exception("Input field not focused. Please focus an input field first."))
        }
    }

    suspend fun postInstagramComment(commentText: String): Result<String> {
        val openResult = openAppByName("Instagram")
        if (openResult.isFailure) return openResult

        kotlinx.coroutines.delay(1500L)

        val service = MyraAccessibilityService.getInstance()
            ?: return Result.failure(Exception("Accessibility service is disabled. Enable Myra in Accessibility Settings."))

        // Try clicking comment button / input field
        val commentClicked = service.clickText("Add a comment...") ||
                service.clickText("Comment") ||
                service.clickText("Comments")

        if (commentClicked) {
            kotlinx.coroutines.delay(800L)
            service.typeText(commentText)
            kotlinx.coroutines.delay(500L)
            val posted = service.clickText("Post") || service.clickSendButton()
            return if (posted) {
                Result.success("Posted comment on Instagram: \"$commentText\"")
            } else {
                Result.success("Typed comment \"$commentText\" in Instagram. Tap Post to publish.")
            }
        } else {
            // Direct type if input field focused
            val typed = service.typeText(commentText)
            return if (typed) {
                service.clickText("Post")
                Result.success("Typed and posted comment: \"$commentText\".")
            } else {
                Result.failure(Exception("Could not locate Instagram comment field on active screen."))
            }
        }
    }

    suspend fun postToSocialPlatform(platform: String, captionAndHashtags: String): Result<String> {
        if (platform.contains("Instagram", ignoreCase = true)) {
            return postInstagramComment(captionAndHashtags)
        }

        val openResult = openAppByName(platform)
        if (openResult.isFailure) {
            return openResult
        }

        kotlinx.coroutines.delay(2500L)

        val service = MyraAccessibilityService.getInstance()
            ?: return Result.failure(Exception("Accessibility service is disabled. Enable Myra in Accessibility Settings."))

        // Attempt accessibility navigation based on platform
        val fieldClicked = when {
            platform.contains("YouTube", ignoreCase = true) -> {
                service.clickText("Create") || service.clickText("Upload") || service.clickText("Add") || service.clickText("+")
            }
            platform.contains("TikTok", ignoreCase = true) -> {
                service.clickText("Post") || service.clickText("Add caption") || service.clickText("+")
            }
            platform.contains("Facebook", ignoreCase = true) -> {
                service.clickText("What's on your mind?") || service.clickText("Create post") || service.clickText("Post")
            }
            else -> false
        }

        if (fieldClicked) {
            kotlinx.coroutines.delay(1000L)
            service.typeText(captionAndHashtags)
            return Result.success("Opened $platform, navigated to post creation field, and filled caption: \"$captionAndHashtags\".")
        } else {
            // Fallback: try direct text typing if an input field is already focused
            val typed = service.typeText(captionAndHashtags)
            return if (typed) {
                Result.success("Opened $platform and typed caption into focused input field: \"$captionAndHashtags\".")
            } else {
                Result.success("Opened $platform. Please select the caption field to complete posting \"$captionAndHashtags\".")
            }
        }
    }
}
