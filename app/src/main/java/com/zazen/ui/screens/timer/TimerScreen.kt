package com.zazen.ui.screens.timer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zazen.data.model.TimerState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TimerScreen(
    onTimerDone: () -> Unit,
    viewModel: TimerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val vibrateOnly by viewModel.vibrateOnly.collectAsState()

    // Pre-compute bell positions as fractions of total duration (0..1).
    // Keyed on the config because the service populates it asynchronously.
    val cfg = viewModel.config
    val bellFractions = remember(cfg) {
        if (cfg != null && cfg.durationMillis > 0) {
            cfg.bells.map { it.triggerAtMillis.toFloat() / cfg.durationMillis }
                .filter { it in 0f..1f }
        } else emptyList()
    }

    val isActive = state is TimerState.Running || state is TimerState.Paused

    // For open-ended sits the progress ring can't convey "time left", so surface
    // the next repeating bell instead.
    val repeatEveryMillis = cfg?.repeatEveryMillis ?: 0L

    val view = LocalView.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keepScreenOn = viewModel.screenAlwaysOn

    // Combined window-state effect: enter immersive mode FIRST, then apply the
    // FLAG_KEEP_SCREEN_ON flag. Doing it in this order matters because the OS
    // can re-apply window attributes when system bar visibility changes, which
    // may clear FLAG_KEEP_SCREEN_ON if it was set first. We also re-apply the
    // flag on every ON_RESUME as a defensive measure (some OEMs and lifecycle
    // transitions can drop it).
    DisposableEffect(isActive, keepScreenOn) {
        val activity = context.findActivity()
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }

        fun applyKeepScreenOn() {
            if (window == null) return
            if (isActive && keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        // 1. Toggle immersive mode
        if (isActive) {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.requestApplyInsets()
        }

        // 2. Apply keep-screen-on AFTER the immersive toggle so it can't be
        //    clobbered by the system bar visibility change.
        applyKeepScreenOn()

        // 3. Re-assert on every resume — handles cases where the flag is dropped
        //    after switching apps, locking and unlocking, etc.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                applyKeepScreenOn()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.requestApplyInsets()
        }
    }

    // Warn if alarm volume is muted or low and we're not in vibrate-only mode.
    // Fires once per session start (when transitioning into Running).
    LaunchedEffect(isActive) {
        if (isActive && !vibrateOnly) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val vol = am?.getStreamVolume(AudioManager.STREAM_ALARM) ?: -1
            val maxVol = am?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 0
            val pct = if (maxVol > 0) vol.toFloat() / maxVol else 1f
            val msg = when {
                vol == 0 -> "Alarm volume is muted — bell won't be audible"
                pct < 0.2f -> "Alarm volume is low — you might not hear the bell"
                else -> null
            }
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                is TimerState.Running -> ActiveTimerContent(
                    remaining = s.remainingMillis,
                    total = s.totalMillis,
                    elapsed = s.elapsedMillis,
                    openEnded = s.isOpenEnded,
                    repeatEveryMillis = repeatEveryMillis,
                    isPaused = false,
                    bellFractions = bellFractions,
                    vibrateOnly = vibrateOnly,
                    onPause = { viewModel.pause() },
                    onResume = {},
                    onStop = { viewModel.stop() },
                    onToggleVibrateOnly = { viewModel.toggleVibrateOnly() },
                )

                is TimerState.Paused -> ActiveTimerContent(
                    remaining = s.remainingMillis,
                    total = s.totalMillis,
                    elapsed = s.elapsedMillis,
                    openEnded = s.isOpenEnded,
                    repeatEveryMillis = repeatEveryMillis,
                    isPaused = true,
                    bellFractions = bellFractions,
                    vibrateOnly = vibrateOnly,
                    onPause = {},
                    onResume = { viewModel.resume() },
                    onStop = { viewModel.stop() },
                    onToggleVibrateOnly = { viewModel.toggleVibrateOnly() },
                )

                is TimerState.Finished -> FinishedContent(
                    sessionId = s.sessionId,
                    completed = s.completed,
                    openEnded = s.openEnded,
                    elapsedMillis = s.elapsedMillis,
                    onSaveAndDismiss = { sessionId, notes, onError ->
                        viewModel.saveNotesAndDismiss(sessionId, notes, onTimerDone, onError)
                    },
                    onDiscard = { sessionId ->
                        viewModel.discardAndDismiss(sessionId, onTimerDone)
                    },
                )

                is TimerState.Idle -> {
                    LaunchedEffect(Unit) { onTimerDone() }
                }
            }
        }
    }
}

@Composable
private fun ActiveTimerContent(
    remaining: Long,
    total: Long,
    elapsed: Long,
    openEnded: Boolean,
    repeatEveryMillis: Long,
    isPaused: Boolean,
    bellFractions: List<Float>,
    vibrateOnly: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onToggleVibrateOnly: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Sound/vibrate toggle in top-right corner
        IconButton(
            onClick = onToggleVibrateOnly,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = if (vibrateOnly) Icons.AutoMirrored.Filled.VolumeOff
                    else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (vibrateOnly) "Sound off (vibrate only)" else "Sound on",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                TimerCircle(
                    // An open-ended sit has no endpoint, so there's no arc to fill —
                    // show the bare track instead of a ring draining toward zero.
                    progress = if (openEnded) 0f else if (total > 0) remaining.toFloat() / total else 0f,
                    bellFractions = if (openEnded) emptyList() else bellFractions,
                    modifier = Modifier.size(280.dp),
                )
                Text(
                    text = formatTime(if (openEnded) elapsed else remaining),
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center,
                )
            }

            if (openEnded && repeatEveryMillis > 0) {
                Spacer(Modifier.height(16.dp))
                val untilNext = repeatEveryMillis - (elapsed % repeatEveryMillis)
                Text(
                    "Next bell in ${formatTime(untilNext)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isPaused) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Paused",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(48.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (isPaused) {
                    FilledTonalIconButton(
                        onClick = onResume,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, "Resume", modifier = Modifier.size(32.dp))
                    }
                } else {
                    FilledTonalIconButton(
                        onClick = onPause,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(Icons.Default.Pause, "Pause", modifier = Modifier.size(32.dp))
                    }
                }
                FilledTonalIconButton(
                    onClick = onStop,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(Icons.Default.Stop, "Stop", modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun FinishedContent(
    sessionId: Long,
    completed: Boolean,
    openEnded: Boolean,
    elapsedMillis: Long,
    onSaveAndDismiss: (Long, String, () -> Unit) -> Unit,
    onDiscard: (Long) -> Unit,
) {
    // Keep draft independent of sessionId to avoid resetting when ID arrives from DB
    var notes by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val hasValidId = sessionId > 0

    val durationText = remember(elapsedMillis) {
        val totalSec = (elapsedMillis / 1000).toInt()
        val min = totalSec / 60
        val sec = totalSec % 60
        if (min > 0 && sec > 0) "${min}m ${sec}s"
        else if (min > 0) "${min} min"
        else "${sec}s"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🧘", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                openEnded -> "Open Sit Complete"
                completed -> "Session Complete"
                else -> "Session Ended"
            },
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                openEnded -> "You sat for $durationText"
                completed -> "$durationText completed"
                else -> "Ended after $durationText"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            placeholder = {
                Text(
                    if (completed) "Jot down any reflections or insights…"
                    else "Anything you want to note about this sit…"
                )
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            enabled = !saving,
        )

        Spacer(Modifier.height(24.dp))

        androidx.compose.material3.Button(
            onClick = {
                saving = true
                onSaveAndDismiss(sessionId, notes) { saving = false }
            },
            enabled = !saving && hasValidId,
        ) {
            Text(
                when {
                    saving -> "Saving…"
                    notes.isBlank() -> "Skip"
                    else -> "Done"
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        androidx.compose.material3.TextButton(
            onClick = { onDiscard(sessionId) },
            enabled = !saving && hasValidId,
        ) {
            Text(
                "Discard session",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimerCircle(
    progress: Float,
    modifier: Modifier = Modifier,
    bellFractions: List<Float> = emptyList(),
    strokeWidth: Dp = 8.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    bellMarkerColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arcSize = Size(diameter, diameter)

        // Background track
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        // Progress arc
        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        // Bell markers as small dots on the circle
        if (bellFractions.isNotEmpty()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val radius = diameter / 2
            val dotRadius = stroke * 1.1f

            for (fraction in bellFractions) {
                val angleDeg = -90f + (1f - fraction) * 360f
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val x = cx + radius * cos(angleRad).toFloat()
                val y = cy + radius * sin(angleRad).toFloat()
                drawCircle(
                    color = bellMarkerColor,
                    radius = dotRadius,
                    center = Offset(x, y),
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
