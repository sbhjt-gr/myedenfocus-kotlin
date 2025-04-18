package com.gorai.myedenfocus.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateOf
import com.gorai.myedenfocus.domain.model.Session
import com.gorai.myedenfocus.domain.repository.SessionRepository
import com.gorai.myedenfocus.domain.repository.TaskRepository
import com.gorai.myedenfocus.util.Constants.ACTION_SERVICE_CANCEL
import com.gorai.myedenfocus.util.Constants.ACTION_SERVICE_START
import com.gorai.myedenfocus.util.Constants.ACTION_SERVICE_STOP
import com.gorai.myedenfocus.util.ServiceHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class StudySessionTimerService : Service() {
    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var taskRepository: TaskRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private var timerJob: Job? = null
    private var totalTimeSeconds = 0
    private var elapsedTimeSeconds = 0
    private var wasManuallyFinished = false
    private var selectedTopicId: Int? = null
    private var totalDurationMinutes = 0
    private var startTime: Long = 0
    
    var hours = mutableStateOf("00")
    var minutes = mutableStateOf("00")
    var seconds = mutableStateOf("00")
    var currentTimerState = mutableStateOf(TimerState.IDLE)
    var subjectId = mutableStateOf<Int?>(null)

    override fun onCreate() {
        super.onCreate()
        ServiceHelper.createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_SERVICE_START -> {
                    val durationMinutes = it.getIntExtra("DURATION", 0)
                    selectedTopicId = it.getIntExtra("TOPIC_ID", -1).let { id -> if (id == -1) null else id }
                    subjectId.value = it.getIntExtra("SUBJECT_ID", -1).let { id -> if (id == -1) null else id }
                    
                    if (durationMinutes > 0) {
                        totalTimeSeconds = durationMinutes * 60
                        totalDurationMinutes = durationMinutes
                        elapsedTimeSeconds = 0
                        wasManuallyFinished = false
                        startTime = System.currentTimeMillis()
                        
                        // Save timer info to SharedPreferences
                        saveTimerState(selectedTopicId, startTime, totalTimeSeconds)
                        
                        startTimer()
                    }
                }
                ACTION_SERVICE_STOP -> pauseTimer()
                ACTION_SERVICE_CANCEL -> {
                    wasManuallyFinished = true  // Changed to true since user manually finished
                    clearTimerState()
                    stopTimer(shouldMarkComplete = true)
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun saveTimerState(topicId: Int?, startTime: Long, duration: Int) {
        val completionTime = startTime + (duration * 1000L)
        println("StudySessionTimerService: Saving timer state - topicId: $topicId, completionTime: $completionTime")
        
        val prefs = getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            topicId?.let { putInt("topic_id", it) }
            putLong("completion_time", completionTime)
            putInt("subject_id", subjectId.value ?: -1)
            putInt("duration_minutes", totalDurationMinutes)
            apply()
        }
    }

    private fun clearTimerState() {
        println("StudySessionTimerService: Clearing timer state")
        val prefs = getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    private fun startTimer() {
        currentTimerState.value = TimerState.STARTED
        
        timerJob = serviceScope.launch {
            try {
                while (true) {
                    val currentTime = System.currentTimeMillis()
                    elapsedTimeSeconds = ((currentTime - startTime) / 1000).toInt()
                    
                    if (elapsedTimeSeconds >= totalTimeSeconds) {
                        completeTimer()
                        break
                    }
                    
                    updateTimeDisplay()
                    updateNotification()
                    delay(1000)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        startForeground(
            ServiceHelper.NOTIFICATION_ID,
            ServiceHelper.createNotification(
                context = this,
                hours = hours.value,
                minutes = minutes.value,
                seconds = seconds.value
            ).build(),
            ServiceHelper.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    private suspend fun completeTimer() {
        if (!wasManuallyFinished) {
            playAlarm()
        }
        stopTimer(shouldMarkComplete = true)  // Always mark complete when timer finishes
    }

    private fun updateTimeDisplay() {
        val remainingSeconds = totalTimeSeconds - elapsedTimeSeconds
        hours.value = String.format("%02d", remainingSeconds / 3600)
        minutes.value = String.format("%02d", (remainingSeconds % 3600) / 60)
        seconds.value = String.format("%02d", remainingSeconds % 60)
    }

    private fun updateNotification() {
        val notification = ServiceHelper.createNotification(
            context = this,
            hours = hours.value,
            minutes = minutes.value,
            seconds = seconds.value
        ).build()
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ServiceHelper.NOTIFICATION_ID, notification)
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        currentTimerState.value = TimerState.STOPPED
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun stopTimer(shouldMarkComplete: Boolean = false) {
        timerJob?.cancel()
        timerJob = null
        
        println("StudySessionTimerService: stopTimer called with shouldMarkComplete=$shouldMarkComplete")
        println("StudySessionTimerService: elapsedTimeSeconds=$elapsedTimeSeconds")
        println("StudySessionTimerService: selectedTopicId=$selectedTopicId")
        println("StudySessionTimerService: subjectId=${subjectId.value}")
        
        // Only save session if there's elapsed time
        if (elapsedTimeSeconds > 0) {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val prefs = getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
                    
                    selectedTopicId?.let { topicId ->
                        taskRepository.getTaskById(topicId)?.let { task ->
                            // Save session with actual elapsed time
                            val actualDurationMinutes = elapsedTimeSeconds / 60
                            println("StudySessionTimerService: Saving session with duration=$actualDurationMinutes minutes")
                            
                            sessionRepository.insertSession(
                                Session(
                                    sessionSubjectId = subjectId.value ?: -1,
                                    relatedToSubject = "",
                                    topicName = task.title,
                                    startTime = startTime,
                                    endTime = System.currentTimeMillis(),
                                    duration = actualDurationMinutes.toLong(),
                                    plannedDuration = totalDurationMinutes.toLong(),
                                    wasCompleted = shouldMarkComplete
                                )
                            )
                            
                            // Mark task as complete if timer completed or finish was clicked
                            if (shouldMarkComplete) {
                                println("StudySessionTimerService: Marking task as complete")
                                taskRepository.upsertTask(
                                    task.copy(isComplete = true)
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("StudySessionTimerService: Error in stopTimer - ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        
        elapsedTimeSeconds = 0
        hours.value = "00"
        minutes.value = "00"
        seconds.value = "00"
        currentTimerState.value = TimerState.IDLE
        
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        clearTimerState()
        
        // Reset duration and wasManuallyFinished flag
        totalDurationMinutes = 0
        wasManuallyFinished = false
        startTime = 0
    }

    override fun onDestroy() {
        super.onDestroy()
        // No need to save session here as it's already handled in stopTimer or completeTimer
        timerJob?.cancel()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun playAlarm() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.isLooping = true
            }
            ringtone.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

enum class TimerState {
    IDLE,
    STARTED,
    STOPPED
} 