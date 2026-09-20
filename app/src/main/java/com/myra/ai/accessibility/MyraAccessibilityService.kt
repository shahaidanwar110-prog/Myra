package com.myra.ai.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

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
}
