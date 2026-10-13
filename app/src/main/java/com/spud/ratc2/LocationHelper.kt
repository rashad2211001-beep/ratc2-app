package com.spud.ratc2

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
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
            for (provider in listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            )) {
                try {
                    lm.requestLocationUpdates(provider, 60_000L, 10f, listener, Looper.getMainLooper())
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
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                lm.getLastKnownLocation(p)?.let { return it }
            }
            null
        } catch (_: Exception) { null }
    }

    fun describe(loc: Location): String {
        val lat = loc.latitude
        val lon = loc.longitude
        val acc = loc.accuracy.toInt()
        val sb = StringBuilder()
        sb.appendLine("📍 موقع الجهاز")
        sb.appendLine("━━━━━━━━━━━━━━━━")
        sb.appendLine("خط العرض: $lat")
        sb.appendLine("خط الطول: $lon")
        sb.appendLine("الدقة: $acc متر")
        sb.appendLine()
        sb.appendLine("🗺️ رابط الخريطة:")
        sb.appendLine("https://maps.google.com/?q=$lat,$lon")
        sb.appendLine()
        val addr = reverseGeocode(lat, lon)
        if (addr != null) {
            sb.appendLine("📌 العنوان:")
            sb.appendLine(addr)
        } else {
            sb.appendLine("📌 العنوان: غير متوفر (تحتاج إنترنت)")
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
            a.subAdminArea?.let { parts.add(it) }
            a.locality?.let { parts.add(it) }
            a.subLocality?.let { parts.add(it) }
            a.thoroughfare?.let { parts.add(it) }
            if (parts.isEmpty()) null else parts.joinToString("، ")
        } catch (_: Exception) { null }
    }
}
