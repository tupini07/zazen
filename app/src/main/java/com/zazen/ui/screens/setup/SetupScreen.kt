package com.zazen.ui.screens.setup

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.zazen.data.model.IntervalBell
import com.zazen.data.model.Preset
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
    val bellVolume by viewModel.bellVolume.collectAsState()
    val endSound by viewModel.endSound.collectAsState()
    val dndEnabled by viewModel.dndEnabled.collectAsState()
    val bells by viewModel.bells.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    var showAddBellDialog by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showVolumeWarning by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var presetToDelete by remember { mutableStateOf<Preset?>(null) }

    // Notification permission (Android 13+)
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — we start the timer either way */ }

    fun attemptStart() {
        // Volume warning: check alarm stream (our SoundPool uses USAGE_ALARM)
        if (!vibrateOnly) {
            val audioManager = context.getSystemService(AudioManager::class.java)
            val volume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (volume == 0) {
                showVolumeWarning = true
                return
            }
        }
        // Request notification permission if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Check DND permission if enabled
        if (dndEnabled) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (!nm.isNotificationPolicyAccessGranted) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                return
            }
        }
        viewModel.startTimer()
        onStartTimer()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zazen") },
                actions = {
                    IconButton(onClick = { showThemePicker = true }) {
                        Icon(Icons.Default.Palette, "Theme")
                    }
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
            // --- Presets ---
            if (presets.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.loadPreset(preset) },
                            label = {
                                Text(
                                    preset.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    "Delete",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { presetToDelete = preset },
                                )
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Duration picker ---
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

            Spacer(Modifier.height(24.dp))

            // --- End bell sound ---
            EndBellSelector(
                selected = endSound,
                onSelect = { viewModel.setEndSound(it) },
                onPreview = { viewModel.previewSound(it) },
            )

            Spacer(Modifier.height(16.dp))

            // --- Bell volume ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Bell Volume", modifier = Modifier.width(100.dp))
                Slider(
                    value = bellVolume,
                    onValueChange = { viewModel.setBellVolume(it) },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(bellVolume * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(40.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // --- Vibrate toggle ---
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
                    Column {
                        Text("Vibrate")
                        Text(
                            "Vibrate instead of playing sounds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = vibrateOnly, onCheckedChange = { viewModel.toggleVibrateOnly() })
            }

            Spacer(Modifier.height(8.dp))

            // --- Do Not Disturb toggle ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DoNotDisturbOn, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Do Not Disturb")
                }
                Switch(checked = dndEnabled, onCheckedChange = { viewModel.toggleDnd() })
            }

            Spacer(Modifier.height(16.dp))

            // --- Interval bells ---
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
                    BellItem(
                        bell = bell,
                        onRemove = { viewModel.removeBell(index) },
                        onPreview = {
                            Sound.fromResId(bell.soundResId)?.let { viewModel.previewSound(it) }
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Save preset ---
            AssistChip(
                onClick = { showSavePresetDialog = true },
                label = { Text("Save as Preset") },
                leadingIcon = { Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp)) },
            )

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(16.dp))

            // --- Start button ---
            Button(
                onClick = { attemptStart() },
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

    // --- Dialogs ---

    if (showAddBellDialog) {
        AddBellDialog(
            maxMinutes = durationMinutes,
            onConfirm = { minutes, sound ->
                viewModel.addBell(minutes, sound)
                showAddBellDialog = false
            },
            onDismiss = { showAddBellDialog = false },
            onPreview = { viewModel.previewSound(it) },
        )
    }

    if (showSavePresetDialog) {
        SavePresetDialog(
            onSave = { name ->
                viewModel.savePreset(name)
                showSavePresetDialog = false
            },
            onDismiss = { showSavePresetDialog = false },
        )
    }

    if (showVolumeWarning) {
        AlertDialog(
            onDismissRequest = { showVolumeWarning = false },
            title = { Text("Volume is muted") },
            text = {
                Text("Your alarm volume is at zero — bells won't be audible. " +
                    "Raise the volume or enable vibrate mode.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showVolumeWarning = false
                    viewModel.startTimer()
                    onStartTimer()
                }) { Text("Start Anyway") }
            },
            dismissButton = {
                TextButton(onClick = { showVolumeWarning = false }) { Text("Cancel") }
            },
        )
    }

    presetToDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = { Text("Delete preset?") },
            text = { Text("Delete \"${preset.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePreset(preset)
                    presetToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (showThemePicker) {
        ThemePickerDialog(
            currentMode = themeMode,
            onSelect = { viewModel.setThemeMode(it); showThemePicker = false },
            onDismiss = { showThemePicker = false },
        )
    }
}

@Composable
private fun ThemePickerDialog(
    currentMode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                listOf(
                    "system" to "System default",
                    "light" to "Light",
                    "dark" to "Dark",
                ).forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = currentMode == value, onClick = { onSelect(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndBellSelector(
    selected: Sound,
    onSelect: (Sound) -> Unit,
    onPreview: (Sound) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("End Bell", modifier = Modifier.width(80.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = selected.displayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                Sound.entries.forEach { sound ->
                    DropdownMenuItem(
                        text = { Text(sound.displayName) },
                        onClick = {
                            onSelect(sound)
                            expanded = false
                        },
                    )
                }
            }
        }
        IconButton(onClick = { onPreview(selected) }) {
            Icon(Icons.Default.PlayCircle, "Preview bell")
        }
    }
}

@Composable
private fun BellItem(bell: IntervalBell, onRemove: () -> Unit, onPreview: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
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
            IconButton(onClick = onPreview) {
                Icon(Icons.Default.PlayCircle, "Preview", modifier = Modifier.size(20.dp))
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
    onPreview: (Sound) -> Unit,
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
                        Spacer(Modifier.width(4.dp))
                        Text(sound.displayName, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { onPreview(sound) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Default.PlayCircle, "Preview", modifier = Modifier.size(20.dp))
                        }
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

@Composable
private fun SavePresetDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Preset") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Preset name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
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
