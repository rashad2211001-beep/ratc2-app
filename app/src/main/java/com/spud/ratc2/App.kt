package com.spud.ratc2

import android.app.Application
import android.content.Intent
import android.os.Build

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val svc = Intent(this, SyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(svc)
        else startService(svc)
    }
}
