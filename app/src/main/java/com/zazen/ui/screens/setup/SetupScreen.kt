package com.zazen.ui.screens.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zazen.data.model.IntervalBell
import com.zazen.data.model.Sound

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onStartTimer: () -> Unit,
    onNavigateToStats: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val durationMinutes by viewModel.durationMinutes.collectAsState()
    val vibrateOnly by viewModel.vibrateOnly.collectAsState()
    val bells by viewModel.bells.collectAsState()
    var showAddBellDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zazen") },
                actions = {
                    IconButton(onClick = onNavigateToStats) {
                        Icon(Icons.Default.History, "Stats")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            // Duration picker
            Text("Duration", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = { viewModel.decrementDuration() }) {
                    Icon(Icons.Default.Remove, "Less")
                }
                Text(
                    text = formatDuration(durationMinutes),
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                IconButton(onClick = { viewModel.incrementDuration() }) {
                    Icon(Icons.Default.Add, "More")
                }
            }

            Spacer(Modifier.height(32.dp))

            // Silent mode toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (vibrateOnly) Icons.Default.Vibration else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Silent mode")
                }
                Switch(checked = vibrateOnly, onCheckedChange = { viewModel.toggleVibrateOnly() })
            }

            Spacer(Modifier.height(24.dp))

            // Interval bells
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Interval Bells", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showAddBellDialog = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.padding(end = 4.dp))
                    Text("Add")
                }
            }

            if (bells.isEmpty()) {
                Text(
                    "No interval bells — tap Add to schedule bells within your session",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                bells.forEachIndexed { index, bell ->
                    BellItem(bell = bell, onRemove = { viewModel.removeBell(index) })
                }
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(24.dp))

            // Start button
            Button(
                onClick = {
                    viewModel.startTimer()
                    onStartTimer()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Start", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddBellDialog) {
        AddBellDialog(
            maxMinutes = durationMinutes,
            onConfirm = { minutes, sound ->
                viewModel.addBell(minutes, sound)
                showAddBellDialog = false
            },
            onDismiss = { showAddBellDialog = false },
        )
    }
}

@Composable
private fun BellItem(bell: IntervalBell, onRemove: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Notifications, null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(formatBellTime(bell.triggerAtMillis))
                Text(
                    Sound.fromResId(bell.soundResId)?.displayName ?: "Unknown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, "Remove")
            }
        }
    }
}

@Composable
private fun AddBellDialog(
    maxMinutes: Int,
    onConfirm: (minutes: Int, sound: Sound) -> Unit,
    onDismiss: () -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(5f.coerceAtMost(maxMinutes.toFloat())) }
    var selectedSound by remember { mutableStateOf(Sound.DEFAULT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Interval Bell") },
        text = {
            Column {
                Text("Time from start")
                Spacer(Modifier.height(8.dp))
                Text(
                    "${sliderValue.toInt()} min",
                    style = MaterialTheme.typography.titleLarge,
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 1f..maxMinutes.toFloat(),
                    steps = (maxMinutes - 2).coerceAtLeast(0),
                )

                Spacer(Modifier.height(16.dp))
                Text("Sound", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                Sound.entries.forEach { sound ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSound = sound }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedSound == sound,
                            onClick = { selectedSound = sound },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(sound.displayName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(sliderValue.toInt(), selectedSound) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "%d:%02d".format(h, m) else "%d min".format(m)
}

private fun formatBellTime(millis: Long): String {
    val totalMin = millis / 60_000
    return "at $totalMin min"
}
