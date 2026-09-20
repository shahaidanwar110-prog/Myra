package com.myra.ai.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors

class MyraAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No specific event handling needed for basic phone control
    }

    override fun onInterrupt() {
        // Interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    companion object {
        private var instance: MyraAccessibilityService? = null

        fun getInstance(): MyraAccessibilityService? = instance

        fun isServiceRunning(): Boolean = instance != null
    }

    fun pressHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun pressBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun pressRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun scrollDown(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val scrollableNode = findScrollableNode(rootNode)
        val result = if (scrollableNode != null) {
            scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        } else {
            false
        }
        rootNode.recycle()
        return result
    }

    fun scrollUp(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val scrollableNode = findScrollableNode(rootNode)
        val result = if (scrollableNode != null) {
            scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        } else {
            false
        }
        rootNode.recycle()
        return result
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    fun clickSendButton(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val sendNode = findSendButtonNode(rootNode)
        val result = if (sendNode != null) {
            var curr: AccessibilityNodeInfo? = sendNode
            var clicked = false
            while (curr != null) {
                if (curr.isClickable) {
                    clicked = curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) break
                }
                curr = curr.parent
            }
            sendNode.recycle()
            clicked
        } else {
            false
        }
        rootNode.recycle()
        return result
    }

    private fun findSendButtonNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        val viewId = node.viewIdResourceName ?: ""
        if ((text != null && (text.equals("Send", ignoreCase = true) || text.equals("Send message", ignoreCase = true))) ||
            viewId.contains("send", ignoreCase = true)
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSendButtonNode(child)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    fun clickText(targetText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val clickableNode = findClickableNodeByText(rootNode, targetText)
        val result = if (clickableNode != null) {
            var curr: AccessibilityNodeInfo? = clickableNode
            var clicked = false
            while (curr != null) {
                if (curr.isClickable) {
                    clicked = curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) break
                }
                curr = curr.parent
            }
            clickableNode.recycle()
            clicked
        } else {
            false
        }
        rootNode.recycle()
        return result
    }

    private fun findClickableNodeByText(node: AccessibilityNodeInfo, targetText: String): AccessibilityNodeInfo? {
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (text != null && text.contains(targetText, ignoreCase = true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findClickableNodeByText(child, targetText)
            if (found != null) {
                if (found != child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    fun typeText(textToType: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val result = if (focusedNode != null) {
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            focusedNode.recycle()
            success
        } else {
            false
        }
        rootNode.recycle()
        return result
    }

    fun captureScreenshot(callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val executor = mainExecutor ?: Executors.newSingleThreadExecutor()
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshot.hardwareBuffer
                            val colorSpace = screenshot.colorSpace
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            val swBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            hardwareBuffer.close()
                            callback(swBitmap ?: bitmap)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            callback(null)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        callback(null)
                    }
                }
            )
        } else {
            callback(null)
        }
    }

    fun dumpNodeTreeText(): String {
        val rootNode = rootInActiveWindow ?: return "Screen tree unavailable (root node null)."
        val sb = StringBuilder()
        traverseAndDumpNode(rootNode, sb, 0)
        rootNode.recycle()
        return if (sb.isNotEmpty()) sb.toString() else "Empty screen tree."
    }

    private fun traverseAndDumpNode(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        val indent = "  ".repeat(depth)
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val viewId = node.viewIdResourceName
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val labels = mutableListOf<String>()
        if (!text.isNullOrEmpty()) labels.add("text=\"$text\"")
        if (!desc.isNullOrEmpty()) labels.add("desc=\"$desc\"")
        if (!viewId.isNullOrEmpty()) labels.add("id=\"$viewId\"")
        if (node.isClickable) labels.add("clickable=true")
        if (node.isEditable) labels.add("editable=true")
        labels.add("bounds=[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]")

        if (labels.isNotEmpty()) {
            sb.append(indent).append("- ").append(labels.joinToString(", ")).append("\n")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseAndDumpNode(child, sb, depth + 1)
            child.recycle()
        }
    }

    fun findNodeBounds(targetText: String): Rect? {
        val rootNode = rootInActiveWindow ?: return null
        val bounds = Rect()
        val found = findNodeByTextInternal(rootNode, targetText, bounds)
        rootNode.recycle()
        return if (found) bounds else null
    }

    private fun findNodeByTextInternal(node: AccessibilityNodeInfo, targetText: String, outBounds: Rect): Boolean {
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (text != null && text.contains(targetText, ignoreCase = true)) {
            node.getBoundsInScreen(outBounds)
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findNodeByTextInternal(child, targetText, outBounds)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    fun showGuideHighlight(targetText: String, instruction: String): Boolean {
        val bounds = findNodeBounds(targetText)
        return if (bounds != null && !bounds.isEmpty) {
            GuideOverlayManager.showHighlight(this, bounds, instruction)
            true
        } else {
            GuideOverlayManager.clearHighlight()
            false
        }
    }

    fun clearGuideHighlight() {
        GuideOverlayManager.clearHighlight()
    }
}
