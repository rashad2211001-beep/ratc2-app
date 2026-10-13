package com.spud.ratc2

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocationHistory(private val ctx: Context) {

    private val file: File = File(ctx.filesDir, "locations_history.txt")
    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @Synchronized
    fun add(lat: Double, lon: Double, acc: Float) {
        try {
            file.appendText("${System.currentTimeMillis()}|$lat|$lon|$acc\n")
            val lines = file.readLines()
            if (lines.size > 500) file.writeText(lines.takeLast(500).joinToString("\n", postfix = "\n"))
        } catch (_: Exception) {}
    }

    @Synchronized
    fun last(count: Int): String {
        if (!file.exists()) return "لا يوجد تاريخ مواقع بعد."
        val lines = file.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return "لا يوجد تاريخ مواقع بعد."
        val take = lines.takeLast(count)
        val sb = StringBuilder()
        sb.appendLine("🗺️ آخر ${take.size} موقع")
        sb.appendLine("━━━━━━━━━━━━━━━━")
        for ((i, line) in take.reversed().withIndex()) {
            val parts = line.split("|")
            if (parts.size < 4) continue
            val ts = parts[0].toLongOrNull() ?: continue
            val lat = parts[1].toDoubleOrNull() ?: continue
            val lon = parts[2].toDoubleOrNull() ?: continue
            val acc = parts[3].toFloatOrNull() ?: 0f
            sb.appendLine("📍 موقع ${i + 1}")
            sb.appendLine("الوقت: ${df.format(Date(ts))}")
            sb.appendLine("خط العرض: $lat")
            sb.appendLine("خط الطول: $lon")
            sb.appendLine("الدقة: ${acc.toInt()}م")
            sb.appendLine("الرابط: https://maps.google.com/?q=$lat,$lon")
            sb.appendLine("━━━━━━━━━━━━━━━━")
        }
        return sb.toString()
    }

    fun count(): Int {
        if (!file.exists()) return 0
        return file.readLines().count { it.isNotBlank() }
    }
}
