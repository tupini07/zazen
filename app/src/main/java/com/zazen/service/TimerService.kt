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

    /** 0 disables repeating bells. */
    private var repeatEveryMillis = 0L
    private var nextRepeatAtMillis = 0L
    private var openEnded = false

    /** Guards against the session being ended twice while teardown is in flight. */
    private var finishing = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            // tick() may have ended the session. Re-arming here would let a
            // finished timer fire again before the async teardown completes.
            if (!finishing) handler.postDelayed(this, TICK_INTERVAL_MS)
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

        private val _vibrateOnly = MutableStateFlow(false)
        val vibrateOnly: StateFlow<Boolean> = _vibrateOnly.asStateFlow()

        var currentConfig: TimerConfig? = null
            private set

        fun toggleVibrateOnly() {
            _vibrateOnly.value = !_vibrateOnly.value
        }

        fun resetState() {
            _timerState.value = TimerState.Idle
            _vibrateOnly.value = false
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
        _vibrateOnly.value = config.vibrateOnly
        totalDurationMillis = config.durationMillis
        openEnded = config.isOpenEnded
        bells = config.bells.sortedBy { it.triggerAtMillis }
        nextBellIndex = 0
        repeatEveryMillis = config.repeatEveryMillis.coerceAtLeast(0)
        // First repeat lands one full interval in, not at t=0
        nextRepeatAtMillis = repeatEveryMillis
        accumulatedPauseMillis = 0
        finishing = false
        startedAtRealtime = SystemClock.elapsedRealtime()

        // Preload all bell sounds + the end sound
        bells.forEach { soundPlayer.preload(it.soundResId) }
        soundPlayer.preload(config.endSoundResId)
        if (repeatEveryMillis > 0) soundPlayer.preload(config.repeatSoundResId)

        if (config.dndEnabled) enableDnd()

        startForeground(NOTIFICATION_ID, buildNotification(if (openEnded) 0 else totalDurationMillis))
        handler.post(tickRunnable)
        _timerState.value = if (openEnded) {
            TimerState.Running(remainingMillis = 0, totalMillis = 0, elapsedMillis = 0)
        } else {
            TimerState.Running(totalDurationMillis, totalDurationMillis, elapsedMillis = 0)
        }
    }

    private fun pauseTimer() {
        if (_timerState.value !is TimerState.Running) return
        pausedAtRealtime = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tickRunnable)
        val elapsed = computeElapsedMillis()
        val remaining = if (openEnded) 0 else computeRemainingMillis()
        _timerState.value = TimerState.Paused(
            remainingMillis = remaining,
            totalMillis = if (openEnded) 0 else totalDurationMillis,
            elapsedMillis = elapsed,
        )
        updateNotification(if (openEnded) elapsed else remaining)
    }

    private fun resumeTimer() {
        if (_timerState.value !is TimerState.Paused) return
        accumulatedPauseMillis += SystemClock.elapsedRealtime() - pausedAtRealtime
        handler.post(tickRunnable)
        val elapsed = computeElapsedMillis()
        _timerState.value = TimerState.Running(
            remainingMillis = if (openEnded) 0 else computeRemainingMillis(),
            totalMillis = if (openEnded) 0 else totalDurationMillis,
            elapsedMillis = elapsed,
        )
    }

    private fun stopTimer() {
        if (finishing) return
        finishing = true
        handler.removeCallbacks(tickRunnable)
        // Stopping while paused: the in-progress pause hasn't been folded into
        // accumulatedPauseMillis yet, so settle it first or the time spent
        // paused gets logged as sitting time.
        if (_timerState.value is TimerState.Paused) {
            accumulatedPauseMillis += SystemClock.elapsedRealtime() - pausedAtRealtime
        }
        val elapsed = computeElapsedMillis()
        val wasOpenEnded = openEnded

        // An open-ended sit has no scheduled end, so stopping *is* how it ends —
        // ring the closing bell and record it as a complete sit.
        if (wasOpenEnded) playEndSound()

        serviceScope.launch {
            val sessionId = withContext(NonCancellable) {
                saveSession(elapsed, completed = wasOpenEnded)
            }
            _timerState.value = TimerState.Finished(
                sessionId = sessionId,
                completed = wasOpenEnded,
                elapsedMillis = elapsed,
                openEnded = wasOpenEnded,
            )
            currentConfig = null
            restoreDnd()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun tick() {
        val elapsed = computeElapsedMillis()
        val remaining = if (openEnded) 0 else (totalDurationMillis - elapsed).coerceAtLeast(0)
        val volume = currentConfig?.bellVolume ?: 1f

        // Fire any one-shot bells whose trigger time has passed
        while (nextBellIndex < bells.size && bells[nextBellIndex].triggerAtMillis <= elapsed) {
            val bell = bells[nextBellIndex]
            val useVibrate = bell.vibrateOnly || _vibrateOnly.value
            if (useVibrate) soundPlayer.vibrate() else soundPlayer.playFull(bell.soundResId, volume)
            nextBellIndex++
        }

        // Fire repeating bells. If the device slept through several intervals we
        // ring once and fast-forward to the next boundary, rather than firing a
        // burst of overlapping bells for every interval that was missed.
        if (repeatEveryMillis > 0 && nextRepeatAtMillis <= elapsed) {
            val collidesWithEnd = !openEnded && nextRepeatAtMillis >= totalDurationMillis
            if (!collidesWithEnd) {
                if (_vibrateOnly.value) {
                    soundPlayer.vibrate()
                } else {
                    soundPlayer.playFull(currentConfig?.repeatSoundResId ?: R.raw.bell, volume)
                }
            }
            val intervalsMissed = (elapsed - nextRepeatAtMillis) / repeatEveryMillis + 1
            nextRepeatAtMillis += intervalsMissed * repeatEveryMillis
        }

        if (!openEnded && remaining <= 0) {
            onTimerFinished()
        } else {
            _timerState.value = TimerState.Running(
                remainingMillis = remaining,
                totalMillis = if (openEnded) 0 else totalDurationMillis,
                elapsedMillis = elapsed,
            )
            updateNotification(if (openEnded) elapsed else remaining)
        }
    }

    private fun playEndSound() {
        val config = currentConfig
        if (_vibrateOnly.value) {
            soundPlayer.vibrate(strong = true)
        } else {
            soundPlayer.playFull(config?.endSoundResId ?: R.raw.bell, config?.bellVolume ?: 1f)
        }
    }

    private fun onTimerFinished() {
        if (finishing) return
        finishing = true
        handler.removeCallbacks(tickRunnable)
        playEndSound()

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

    private fun buildNotification(displayMillis: Long): Notification {
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
            .setContentText("Meditating — ${formatTime(displayMillis)}")
            .setContentIntent(contentIntent)
            .addAction(pauseAction)
            .addAction(stopAction)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(displayMillis: Long) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(displayMillis))
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }
}
