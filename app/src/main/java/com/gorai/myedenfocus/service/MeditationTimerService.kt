package com.gorai.myedenfocus.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gorai.myedenfocus.R
import kotlinx.coroutines.*
import android.media.RingtoneManager
import android.os.Build
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MeditationTimerService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var timeLeft = 0
    private var isTimerRunning = false
    
    companion object {
        const val CHANNEL_ID = "meditation_timer_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val EXTRA_TIME = "time_in_seconds"
        
        private val _timerState = MutableStateFlow<Int>(0)
        val timerState: StateFlow<Int> = _timerState
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                timeLeft = intent.getIntExtra(EXTRA_TIME, 0)
                startTimer()
            }
            ACTION_STOP -> {
                stopTimer()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTimer() {
        try {
            isTimerRunning = true
            startForeground(NOTIFICATION_ID, createNotification(timeLeft))
            
            serviceScope.launch {
                while (isTimerRunning && timeLeft > 0) {
                    delay(1000)
                    timeLeft--
                    _timerState.value = timeLeft
                    updateNotification(timeLeft)
                    
                    if (timeLeft == 0) {
                        isTimerRunning = false
                        showCompletionNotification()
                        stopSelf()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun stopTimer() {
        isTimerRunning = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                CHANNEL_ID,
                "Meditation Timer",
                NotificationManager.IMPORTANCE_LOW
            )
        } else {
            TODO("VERSION.SDK_INT < O")
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(timeInSeconds: Int): android.app.Notification {
        val stopIntent = Intent(this, MeditationTimerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Meditation in Progress")
            .setContentText(formatTime(timeInSeconds))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(timeInSeconds: Int) {
        val notification = createNotification(timeInSeconds)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Meditation Complete")
            .setContentText("Great job! You've completed your meditation session.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(2, notification)
        
        // Play completion sound
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(applicationContext, sound).play()
    }

    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
} 