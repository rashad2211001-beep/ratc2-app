package com.spud.ratc2

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TelegramBot(
    private val token: String,
    private val handler: (String, String) -> String
) {
    private val TAG = "TBOT"
    private val client = OkHttpClient.Builder()
        .readTimeout(45, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .build()

    private val base = "https://api.telegram.org/bot$token"
    @Volatile private var running = true
    private var offset = 0L

    fun start() {
        Log.i(TAG, "start: begin loop")
        while (running) {
            try {
                tick()
            } catch (e: Exception) {
                Log.e(TAG, "loop error", e)
                Thread.sleep(3000)
            }
        }
        Log.i(TAG, "start: loop stopped")
    }

    fun stop() { running = false }

    private fun tick() {
        val url = "$base/getUpdates?offset=$offset&timeout=30"
        Log.d(TAG, "tick: GET $url")
        val body = client.newCall(Request.Builder().url(url).build())
            .execute().use { it.body?.string() ?: return }
        Log.d(TAG, "tick: body=$body")
        val json = JSONObject(body)
        if (!json.optBoolean("ok", false)) {
            Log.e(TAG, "tick: api not ok: $body")
            return
        }
        val arr = json.optJSONArray("result") ?: return
        Log.d(TAG, "tick: ${arr.length()} updates")
        for (i in 0 until arr.length()) {
            val upd = arr.getJSONObject(i)
            offset = upd.getLong("update_id") + 1
            val msg = upd.optJSONObject("message") ?: continue
            val chatId = msg.getJSONObject("chat").getString("id")
            val text = msg.optString("text", "")
            Log.i(TAG, "msg chat=$chatId text=$text")
            if (text.isBlank()) continue
            val reply = handler(chatId, text)
            Log.i(TAG, "reply=$reply")
            if (reply.isNotEmpty()) send(chatId, reply)
        }
    }

    private fun send(chatId: String, text: String) {
        val form = FormBody.Builder()
            .add("chat_id", chatId)
            .add("text", text.take(4000))
            .build()
        val resp = client.newCall(Request.Builder()
            .url("$base/sendMessage").post(form).build())
            .execute()
        Log.i(TAG, "send: code=${resp.code}")
        resp.close()
    }
}
