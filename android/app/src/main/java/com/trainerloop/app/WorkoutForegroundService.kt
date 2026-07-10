package com.trainerloop.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.trainerloop.ble.BlePermissions

class WorkoutForegroundService : Service() {

  private var wakeLock: PowerManager.WakeLock? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    // Wake lock is acquired lazily only while running (see onStartCommand), so a
    // paused workout doesn't keep the CPU out of deep sleep.
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
      }
      ACTION_UPDATE -> {
        val power = intent.getIntExtra(EXTRA_POWER, 0)
        val time = intent.getStringExtra(EXTRA_TIME) ?: "0:00"
        val isRunning = intent.getBooleanExtra(EXTRA_IS_RUNNING, true)
        updateWakeLock(isRunning)
        startForeground(NOTIFICATION_ID, buildNotification(power, time, isRunning))
      }
      else -> {
        val power = intent?.getIntExtra(EXTRA_POWER, 0) ?: 0
        val time = intent?.getStringExtra(EXTRA_TIME) ?: "0:00"
        val isRunning = intent?.getBooleanExtra(EXTRA_IS_RUNNING, true) ?: true
        updateWakeLock(isRunning)
        startForeground(NOTIFICATION_ID, buildNotification(power, time, isRunning))
      }
    }
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    releaseWakeLock()
    super.onDestroy()
  }

  private fun buildNotification(power: Int, time: String, isRunning: Boolean): Notification {
    val stopIntent = PendingIntent.getService(
      this, 0, Intent(this, WorkoutForegroundService::class.java).apply {
        action = ACTION_STOP
      }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("Trainer Loop")
      .setContentText("${power}W \u2022 $time")
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setOngoing(isRunning)
      .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Workout",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Ongoing workout notification"
      }
      val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }
  }

  /** Hold the partial wake lock only while running; release it when paused. */
  private fun updateWakeLock(isRunning: Boolean) {
    if (isRunning) acquireWakeLock() else releaseWakeLock()
  }

  private fun acquireWakeLock() {
    if (wakeLock?.isHeld == true) return
    val powerManager = getSystemService(POWER_SERVICE) as PowerManager
    wakeLock = powerManager.newWakeLock(
      PowerManager.PARTIAL_WAKE_LOCK,
      "TrainerLoop:WorkoutWakeLock"
    ).apply {
      acquire(WAKE_LOCK_TIMEOUT_MS)
    }
  }

  private fun releaseWakeLock() {
    wakeLock?.let {
      if (it.isHeld) it.release()
    }
    wakeLock = null
  }

  companion object {
    private const val CHANNEL_ID = "workout_channel"
    private const val NOTIFICATION_ID = 1001
    private const val WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L // 6 hours

    const val ACTION_UPDATE = "com.trainerloop.action.UPDATE_WORKOUT"
    const val ACTION_STOP = "com.trainerloop.action.STOP_WORKOUT"
    const val EXTRA_POWER = "power"
    const val EXTRA_TIME = "time"
    const val EXTRA_IS_RUNNING = "is_running"

    fun start(context: Context, power: Int, time: String) {
      if (!BlePermissions.hasPermissions(context)) return
      val intent = Intent(context, WorkoutForegroundService::class.java).apply {
        putExtra(EXTRA_POWER, power)
        putExtra(EXTRA_TIME, time)
        putExtra(EXTRA_IS_RUNNING, true)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun update(context: Context, power: Int, time: String, isRunning: Boolean) {
      if (!BlePermissions.hasPermissions(context)) return
      val intent = Intent(context, WorkoutForegroundService::class.java).apply {
        action = ACTION_UPDATE
        putExtra(EXTRA_POWER, power)
        putExtra(EXTRA_TIME, time)
        putExtra(EXTRA_IS_RUNNING, isRunning)
      }
      context.startService(intent)
    }

    fun stop(context: Context) {
      val intent = Intent(context, WorkoutForegroundService::class.java).apply {
        action = ACTION_STOP
      }
      context.startService(intent)
    }
  }
}
