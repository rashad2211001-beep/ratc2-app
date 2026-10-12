package com.spud.ratc2

import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TelegramBot(
    private val token: String,
    private val handler: (String, String) -> String
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(40, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private val base = "https://api.telegram.org/bot$token"
    @Volatile private var running = true
    private var offset = 0L

    fun start() {
        while (running) {
            try { tick() } catch (_: Exception) { Thread.sleep(2000) }
        }
    }

    fun stop() { running = false }

    private fun tick() {
        val url = "$base/getUpdates?offset=$offset&timeout=30"
        val body = client.newCall(Request.Builder().url(url).build())
            .execute().use { it.body?.string() ?: return }
        val arr = JSONObject(body).optJSONArray("result") ?: return
        for (i in 0 until arr.length()) {
            val upd = arr.getJSONObject(i)
            offset = upd.getLong("update_id") + 1
            val msg = upd.optJSONObject("message") ?: continue
            val chatId = msg.getJSONObject("chat").getString("id")
            val text = msg.optString("text", "")
            if (text.isBlank()) continue
            val reply = handler(chatId, text)
            if (reply.isNotEmpty()) send(chatId, reply)
        }
    }

    private fun send(chatId: String, text: String) {
        val form = FormBody.Builder()
            .add("chat_id", chatId)
            .add("text", text.take(4000))
            .build()
        client.newCall(Request.Builder()
            .url("$base/sendMessage").post(form).build()).execute().close()
    }
}
