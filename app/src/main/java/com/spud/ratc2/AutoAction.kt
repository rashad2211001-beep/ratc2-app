package com.spud.ratc2

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object AutoAction {
    private val TAG = "AUTOACT"

    fun openApp(ctx: Context, packageName: String): Boolean {
        return try {
            val intent = ctx.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            true
        } catch (e: Exception) { Log.e(TAG, "openApp", e); false }
    }

    fun dumpScreen(svc: AutoAccess): String {
        val sb = StringBuilder()
        val root = svc.rootInActiveWindow ?: return "(ما في شاشة نشطة)"
        collect(root, sb, 0)
        return sb.toString().take(8000)
    }

    private fun collect(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 30) return
        node.text?.let { if (it.isNotBlank()) sb.appendLine("  ".repeat(depth) + "• $it") }
        node.contentDescription?.let { if (it.isNotBlank()) sb.appendLine("  ".repeat(depth) + "▸ $it") }
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            collect(c, sb, depth + 1)
        }
    }

    fun findByText(svc: AutoAccess, text: String): AccessibilityNodeInfo? {
        val root = svc.rootInActiveWindow ?: return null
        return find(root, text, 0)
    }

    private fun find(node: AccessibilityNodeInfo, text: String, depth: Int): AccessibilityNodeInfo? {
        if (depth > 30) return null
        if (node.text?.contains(text, true) == true) return node
        if (node.contentDescription?.contains(text, true) == true) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val f = find(c, text, depth + 1)
            if (f != null) return f
        }
        return null
    }

    fun clickText(svc: AutoAccess, text: String): Boolean {
        val node = findByText(svc, text) ?: return false
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
        }
        return false
    }

    fun typeText(svc: AutoAccess, text: String): Boolean {
        val root = svc.rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun back(svc: AutoAccess): Boolean = svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    fun home(svc: AutoAccess): Boolean = svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    fun recents(svc: AutoAccess): Boolean = svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    fun openNotifications(svc: AutoAccess): Boolean = svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
}
