package com.zazen.ui.screens.stats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import com.zazen.R
import com.zazen.data.model.MeditationSession
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsState()
    val totalMillis by viewModel.totalMillis.collectAsState()
    val sessionCount by viewModel.sessionCount.collectAsState()
    var sessionToDelete by remember { mutableStateOf<MeditationSession?>(null) }
    var sessionToEdit by remember { mutableStateOf<MeditationSession?>(null) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Multi-select state
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { pendingImportUri = it } }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is StatsMessage.BackupSaved -> context.getString(R.string.backup_saved)
                is StatsMessage.BackupFailed ->
                    context.getString(R.string.backup_failed, message.reason)
                is StatsMessage.RestoreFailed ->
                    context.getString(R.string.restore_failed, message.reason)
                is StatsMessage.Restored -> context.getString(
                    R.string.restore_succeeded,
                    context.resources.getQuantityString(
                        R.plurals.preset_count, message.presetCount, message.presetCount,
                    ),
                    context.resources.getQuantityString(
                        R.plurals.session_count, message.sessionCount, message.sessionCount,
                    ),
                )
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            pluralStringResource(
                                R.plurals.stats_selected_count,
                                selectedIds.size,
                                selectedIds.size,
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(
                                Icons.Default.Close,
                                stringResource(R.string.stats_cd_cancel_selection),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            selectedIds = sessions.map { it.id }.toSet()
                        }) {
                            Icon(
                                Icons.Default.DoneAll,
                                stringResource(R.string.stats_cd_select_all),
                            )
                        }
                        IconButton(onClick = {
                            val ordered = sessions.filter { it.id in selectedIds }
                            val text = formatSessionsForClipboard(ordered)
                            clipboard.setText(AnnotatedString(text))
                            val n = selectedIds.size
                            Toast.makeText(
                                context,
                                context.resources.getQuantityString(
                                    R.plurals.sessions_copied, n, n,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                            selectedIds = emptySet()
                        }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                stringResource(R.string.stats_cd_copy),
                            )
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.stats_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.stats_cd_back),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { exportLauncher.launch(backupFileName()) }) {
                            Icon(
                                Icons.Default.Download,
                                stringResource(R.string.stats_cd_export),
                            )
                        }
                        IconButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                            Icon(
                                Icons.Default.Upload,
                                stringResource(R.string.stats_cd_restore),
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Summary cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatCard(
                    label = stringResource(R.string.stats_total_time),
                    value = formatTotalTime(totalMillis),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = stringResource(R.string.stats_sessions),
                    value = sessionCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.stats_history),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!selectionMode && sessions.isNotEmpty()) {
                    Text(
                        stringResource(R.string.stats_history_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (sessions.isEmpty()) {
                Text(
                    stringResource(R.string.stats_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sessions, key = { it.id }) { session ->
                        val isSelected = session.id in selectedIds
                        if (selectionMode) {
                            // No swipe-to-delete in selection mode
                            SelectableSessionItem(
                                session = session,
                                isSelected = isSelected,
                                onTap = {
                                    selectedIds = if (isSelected) selectedIds - session.id
                                    else selectedIds + session.id
                                },
                                onLongPress = {
                                    selectedIds = if (isSelected) selectedIds - session.id
                                    else selectedIds + session.id
                                },
                            )
                        } else {
                            SessionItem(
                                session = session,
                                onDelete = { sessionToDelete = session },
                                onTap = { sessionToEdit = session },
                                onLongPress = { selectedIds = selectedIds + session.id },
                            )
                        }
                    }
                }
            }
        }
    }

    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text(stringResource(R.string.session_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.session_delete_message,
                        formatMinutes(session.completedMillis),
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(session)
                    sessionToDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    sessionToEdit?.let { session ->
        EditNotesDialog(
            session = session,
            onDismiss = { sessionToEdit = null },
            onSave = { notes ->
                viewModel.updateNotes(session.id, notes)
                sessionToEdit = null
            },
        )
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.restore_title)) },
            text = { Text(stringResource(R.string.restore_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importBackup(uri)
                    pendingImportUri = null
                }) { Text(stringResource(R.string.action_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private fun backupFileName(): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return "zazen_backup_$timestamp.json"
}

@Composable
private fun EditNotesDialog(
    session: MeditationSession,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var notes by remember(session.id) { mutableStateOf(session.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.session_notes_title))
                Text(
                    stringResource(
                        R.string.session_notes_subtitle,
                        formatDate(session.startTime),
                        formatMinutes(session.completedMillis),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.session_notes_label)) },
                placeholder = { Text(stringResource(R.string.session_notes_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(notes.trim()) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableSessionItem(
    session: MeditationSession,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = isSelected,
                role = Role.Checkbox,
                onValueChange = { onTap() },
            ),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = isSelected, onCheckedChange = null)
            Spacer(Modifier.size(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatDate(session.startTime),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    sessionStatusLabel(session),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (session.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        session.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                formatMinutes(session.completedMillis),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SessionItem(
    session: MeditationSession,
    onDelete: () -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val dateLabel = formatDate(session.startTime)
    val selectLabel = stringResource(R.string.stats_action_select)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            // Long-press is awkward with a screen reader or limited motor control,
            // so surface entering selection mode as an explicit action too.
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(selectLabel) {
                        onLongPress()
                        true
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    sessionStatusLabel(session),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .height(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        session.notes.ifBlank { stringResource(R.string.session_add_notes) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatMinutes(session.completedMillis),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(
                            R.string.session_cd_delete, dateLabel,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatTotalTime(millis: Long): String {
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
private fun formatMinutes(millis: Long): String {
    val minutes = (millis / 60_000).toInt()
    return pluralStringResource(R.plurals.minutes_short, minutes, minutes)
}

/**
 * Locale-aware: the previous "MMM d, h:mm a" pattern forced a 12-hour clock even
 * in locales that use a 24-hour one. Not cached in a val because [SimpleDateFormat]
 * is not thread-safe.
 */
private fun dateFormat(): DateFormat =
    SimpleDateFormat(
        android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMdjmm"),
        Locale.getDefault(),
    )

private fun formatDate(epochMillis: Long): String = dateFormat().format(Date(epochMillis))

@Composable
private fun sessionStatusLabel(session: MeditationSession): String = when {
    // Open-ended sits have no target to fall short of — they end when you end them.
    session.isOpenEnded -> stringResource(R.string.session_status_open)
    session.completed -> stringResource(R.string.session_status_completed)
    else -> stringResource(R.string.session_status_stopped)
}

private fun formatSessionsForClipboard(sessions: List<MeditationSession>): String {
    // Oldest first reads more naturally for sharing chronologically
    return sessions.sortedBy { it.startTime }.joinToString("\n") { s ->
        val minutes = s.completedMillis / 60_000
        val header = "${formatDate(s.startTime)} (${minutes} min)"
        val notes = s.notes.trim()
        if (notes.isNotEmpty()) "- $header: $notes" else "- $header"
    }
}
