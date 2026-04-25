package com.zazen.ui.screens.timer

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.zazen.data.model.TimerState

@Composable
fun TimerScreen(
    onTimerDone: () -> Unit,
    viewModel: TimerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Keep screen on while timer is active
    val view = LocalView.current
    DisposableEffect(state) {
        view.keepScreenOn = state is TimerState.Running || state is TimerState.Paused
        onDispose { view.keepScreenOn = false }
    }

    // Full-screen immersive mode during active meditation
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val controller = WindowInsetsControllerCompat(window, view)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
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
                    isPaused = false,
                    onPause = { viewModel.pause() },
                    onResume = {},
                    onStop = { viewModel.stop(); onTimerDone() },
                )

                is TimerState.Paused -> ActiveTimerContent(
                    remaining = s.remainingMillis,
                    total = s.totalMillis,
                    isPaused = true,
                    onPause = {},
                    onResume = { viewModel.resume() },
                    onStop = { viewModel.stop(); onTimerDone() },
                )

                is TimerState.Finished -> FinishedContent(
                    onDismiss = { viewModel.dismiss(); onTimerDone() },
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
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            TimerCircle(
                progress = if (total > 0) remaining.toFloat() / total else 0f,
                modifier = Modifier.size(280.dp),
            )
            Text(
                text = formatTime(remaining),
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center,
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

@Composable
private fun FinishedContent(onDismiss: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🧘", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        Text("Session Complete", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(32.dp))
        androidx.compose.material3.Button(onClick = onDismiss) {
            Text("Done")
        }
    }
}

@Composable
private fun TimerCircle(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 8.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressColor: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arcSize = Size(diameter, diameter)

        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
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
