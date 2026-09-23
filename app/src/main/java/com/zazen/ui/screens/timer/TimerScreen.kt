package com.zazen.ui.screens.timer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.accessibility.AccessibilityManager
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zazen.R
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

        // 1. Toggle immersive mode. Hiding the system bars mid-sit is disorienting
        //    when exploring by touch, so leave them up for screen-reader users.
        val touchExplorationOn = (context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as? AccessibilityManager)?.isTouchExplorationEnabled == true
        if (isActive && !touchExplorationOn) {
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
                vol == 0 -> context.getString(R.string.timer_toast_volume_muted)
                pct < 0.2f -> context.getString(R.string.timer_toast_volume_low)
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
        val overtime = !openEnded && elapsed >= total
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
                contentDescription = if (vibrateOnly) {
                    stringResource(R.string.timer_cd_sound_off)
                } else {
                    stringResource(R.string.timer_cd_sound_on)
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                // The ring and the countdown are deliberately silent to assistive
                // tech: a value that ticks four times a second would talk over the
                // sit, and the bells are what mark time here.
                TimerCircle(
                    // An open-ended sit has no endpoint, so there's no arc to fill —
                    // show the bare track instead of a ring draining toward zero.
                    progress = if (openEnded || overtime) 0f else if (total > 0) remaining.toFloat() / total else 0f,
                    bellFractions = if (openEnded || overtime) emptyList() else bellFractions,
                    modifier = Modifier
                        .size(280.dp)
                        .clearAndSetSemantics { },
                )
                Text(
                    text = formatTime(if (openEnded) elapsed else if (overtime) elapsed - total else remaining),
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }

            if (overtime) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Extra time · Stop when ready",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (openEnded && repeatEveryMillis > 0) {
                Spacer(Modifier.height(16.dp))
                val untilNext = repeatEveryMillis - (elapsed % repeatEveryMillis)
                Text(
                    stringResource(R.string.timer_next_bell_in, formatTime(untilNext)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }

            if (isPaused) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.timer_paused),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // With the countdown silent this is the only cue that pausing
                    // worked, and no bell rings for it.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            Spacer(Modifier.height(48.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (isPaused) {
                    FilledTonalIconButton(
                        onClick = onResume,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            stringResource(R.string.timer_cd_resume),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                } else {
                    FilledTonalIconButton(
                        onClick = onPause,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(
                            Icons.Default.Pause,
                            stringResource(R.string.timer_cd_pause),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                FilledTonalIconButton(
                    onClick = onStop,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        Icons.Default.Stop,
                        stringResource(R.string.timer_cd_stop),
                        modifier = Modifier.size(32.dp),
                    )
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

    val durationText = formatElapsed(elapsedMillis)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "\uD83E\uDDD8",
            style = MaterialTheme.typography.displayLarge,
            // Decorative — otherwise announced as "person in lotus position".
            modifier = Modifier.clearAndSetSemantics { },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                openEnded -> stringResource(R.string.finished_title_open)
                completed -> stringResource(R.string.finished_title_complete)
                else -> stringResource(R.string.finished_title_ended)
            },
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                openEnded -> stringResource(R.string.finished_summary_open, durationText)
                completed -> stringResource(R.string.finished_summary_complete, durationText)
                else -> stringResource(R.string.finished_summary_ended, durationText)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.finished_notes_label)) },
            placeholder = {
                Text(
                    if (completed) stringResource(R.string.finished_notes_placeholder_complete)
                    else stringResource(R.string.finished_notes_placeholder_ended)
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
                    saving -> stringResource(R.string.action_saving)
                    notes.isBlank() -> stringResource(R.string.action_skip)
                    else -> stringResource(R.string.action_done)
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        androidx.compose.material3.TextButton(
            onClick = { onDiscard(sessionId) },
            enabled = !saving && hasValidId,
        ) {
            Text(
                stringResource(R.string.finished_discard),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun formatElapsed(elapsedMillis: Long): String {
    val totalSec = (elapsedMillis / 1000).toInt()
    val min = totalSec / 60
    val sec = totalSec % 60
    return when {
        min > 0 && sec > 0 -> stringResource(R.string.duration_min_sec_short, min, sec)
        min > 0 -> pluralStringResource(R.plurals.minutes_short, min, min)
        else -> stringResource(R.string.duration_sec_short, sec)
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
