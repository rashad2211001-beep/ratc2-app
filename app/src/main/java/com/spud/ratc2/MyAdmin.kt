package com.spud.ratc2

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MyAdmin : DeviceAdminReceiver() {
    override fun onEnabled(c: Context, i: Intent) {
        Log.i("ADMIN", "Device admin enabled")
    }
    override fun onDisabled(c: Context, i: Intent) {
        Log.i("ADMIN", "Device admin disabled")
    }
}
