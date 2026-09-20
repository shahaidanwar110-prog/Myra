package com.myra.ai.accessibility

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings

class PhoneControlManager(private val context: Context) {

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

    fun clickText(targetText: String): Result<String> {
        if (targetText.isBlank()) {
            return Result.failure(Exception("Target text for click cannot be empty."))
        }
        val service = MyraAccessibilityService.getInstance()
            ?: return Result.failure(Exception("Accessibility service is disabled. Enable Myra in Accessibility Settings."))
        return if (service.clickText(targetText)) {
            Result.success("Clicked '$targetText'.")
        } else {
            Result.failure(Exception("Button or text '$targetText' not visible on screen."))
        }
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
}
