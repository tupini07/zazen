package com.zazen.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.zazen.MainActivity
import com.zazen.R
import com.zazen.data.model.IntervalBell
import com.zazen.data.model.MeditationSession
import com.zazen.data.model.TimerConfig
import com.zazen.data.model.TimerState
import com.zazen.data.repository.SessionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class TimerService : Service() {

    @Inject lateinit var soundPlayer: SoundPlayer
    @Inject lateinit var sessionRepository: SessionRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    // Absolute-time tracking (immune to tick drift)
    private var startedAtRealtime = 0L
    private var pausedAtRealtime = 0L
    private var accumulatedPauseMillis = 0L
    private var totalDurationMillis = 0L

    private var bells = listOf<IntervalBell>()
    private var nextBellIndex = 0

    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    companion object {
        const val ACTION_START = "com.zazen.START"
        const val ACTION_PAUSE = "com.zazen.PAUSE"
        const val ACTION_RESUME = "com.zazen.RESUME"
        const val ACTION_STOP = "com.zazen.STOP"
        const val EXTRA_CONFIG = "config"

        private const val TICK_INTERVAL_MS = 250L
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "zazen_timer"
        private const val DND_PREFS = "zazen_dnd"

        private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
        val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

        var currentConfig: TimerConfig? = null
            private set

        fun resetState() {
            _timerState.value = TimerState.Idle
            currentConfig = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                @Suppress("DEPRECATION")
                val config = intent.getParcelableExtra<TimerConfig>(EXTRA_CONFIG)
                if (config != null) startTimer(config)
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopTimer()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        restoreDnd()
        serviceScope.cancel()
        super.onDestroy()
    }

    // --- Timer logic ---

    private fun startTimer(config: TimerConfig) {
        currentConfig = config
        totalDurationMillis = config.durationMillis
        bells = config.bells.sortedBy { it.triggerAtMillis }
        nextBellIndex = 0
        accumulatedPauseMillis = 0
        startedAtRealtime = SystemClock.elapsedRealtime()

        // Preload all bell sounds + the end sound
        bells.forEach { soundPlayer.preload(it.soundResId) }
        soundPlayer.preload(config.endSoundResId)

        if (config.dndEnabled) enableDnd()

        startForeground(NOTIFICATION_ID, buildNotification(totalDurationMillis))
        handler.post(tickRunnable)
        _timerState.value = TimerState.Running(totalDurationMillis, totalDurationMillis)
    }

    private fun pauseTimer() {
        if (_timerState.value !is TimerState.Running) return
        pausedAtRealtime = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tickRunnable)
        val remaining = computeRemainingMillis()
        _timerState.value = TimerState.Paused(remaining, totalDurationMillis)
        updateNotification(remaining)
    }

    private fun resumeTimer() {
        if (_timerState.value !is TimerState.Paused) return
        accumulatedPauseMillis += SystemClock.elapsedRealtime() - pausedAtRealtime
        handler.post(tickRunnable)
        val remaining = computeRemainingMillis()
        _timerState.value = TimerState.Running(remaining, totalDurationMillis)
    }

    private fun stopTimer() {
        handler.removeCallbacks(tickRunnable)
        val elapsed = computeElapsedMillis()
        serviceScope.launch {
            val sessionId = withContext(NonCancellable) {
                saveSession(elapsed, completed = false)
            }
            _timerState.value = TimerState.Finished(sessionId = sessionId, completed = false, elapsedMillis = elapsed)
            currentConfig = null
            restoreDnd()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun tick() {
        val elapsed = computeElapsedMillis()
        val remaining = (totalDurationMillis - elapsed).coerceAtLeast(0)
        val volume = currentConfig?.bellVolume ?: 1f

        // Fire any bells whose trigger time has passed
        while (nextBellIndex < bells.size && bells[nextBellIndex].triggerAtMillis <= elapsed) {
            val bell = bells[nextBellIndex]
            val useVibrate = bell.vibrateOnly || (currentConfig?.vibrateOnly == true)
            if (useVibrate) soundPlayer.vibrate() else soundPlayer.play(bell.soundResId, volume)
            nextBellIndex++
        }

        if (remaining <= 0) {
            onTimerFinished()
        } else {
            _timerState.value = TimerState.Running(remaining, totalDurationMillis)
            updateNotification(remaining)
        }
    }

    private fun onTimerFinished() {
        handler.removeCallbacks(tickRunnable)
        val config = currentConfig

        if (config?.vibrateOnly == true) {
            soundPlayer.vibrate(strong = true)
        } else {
            soundPlayer.play(config?.endSoundResId ?: R.raw.bell, config?.bellVolume ?: 1f)
        }

        _timerState.value = TimerState.Finished(sessionId = 0, completed = true, elapsedMillis = totalDurationMillis)
        serviceScope.launch {
            val sessionId = withContext(NonCancellable) {
                saveSession(totalDurationMillis, completed = true)
            }
            _timerState.value = TimerState.Finished(sessionId = sessionId, completed = true, elapsedMillis = totalDurationMillis)
            restoreDnd()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun computeElapsedMillis(): Long =
        SystemClock.elapsedRealtime() - startedAtRealtime - accumulatedPauseMillis

    private fun computeRemainingMillis(): Long =
        (totalDurationMillis - computeElapsedMillis()).coerceAtLeast(0)

    private suspend fun saveSession(elapsedMillis: Long, completed: Boolean): Long {
        return sessionRepository.logSession(
            MeditationSession(
                startTime = System.currentTimeMillis() - elapsedMillis,
                durationMillis = totalDurationMillis,
                completedMillis = elapsedMillis,
                completed = completed,
            )
        )
    }

    // --- Do Not Disturb ---

    private fun enableDnd() {
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return
        val previousFilter = nm.currentInterruptionFilter
        getSharedPreferences(DND_PREFS, Context.MODE_PRIVATE).edit()
            .putInt("prev_filter", previousFilter)
            .putBoolean("dnd_active", true)
            .apply()
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
    }

    private fun restoreDnd() {
        val prefs = getSharedPreferences(DND_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("dnd_active", false)) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            val filter = prefs.getInt("prev_filter", NotificationManager.INTERRUPTION_FILTER_ALL)
            nm.setInterruptionFilter(filter)
        }
        prefs.edit().putBoolean("dnd_active", false).apply()
    }

    // --- Notifications ---

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Timer",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Active meditation timer" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(remainingMillis: Long): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val pauseAction = NotificationCompat.Action.Builder(
            null, "Pause",
            PendingIntent.getService(
                this, 1,
                Intent(this, TimerService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_IMMUTABLE,
            )
        ).build()

        val stopAction = NotificationCompat.Action.Builder(
            null, "Stop",
            PendingIntent.getService(
                this, 2,
                Intent(this, TimerService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_IMMUTABLE,
            )
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Zazen")
            .setContentText("Meditating — ${formatTime(remainingMillis)}")
            .setContentIntent(contentIntent)
            .addAction(pauseAction)
            .addAction(stopAction)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(remainingMillis: Long) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(remainingMillis))
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
