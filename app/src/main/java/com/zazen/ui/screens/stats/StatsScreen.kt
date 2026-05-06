package com.zazen.ui.screens.stats

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import com.zazen.data.model.MeditationSession
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

    // Multi-select state
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            selectedIds = sessions.map { it.id }.toSet()
                        }) {
                            Icon(Icons.Default.DoneAll, "Select all")
                        }
                        IconButton(onClick = {
                            val ordered = sessions.filter { it.id in selectedIds }
                            val text = formatSessionsForClipboard(ordered)
                            clipboard.setText(AnnotatedString(text))
                            val n = selectedIds.size
                            Toast.makeText(
                                context,
                                "Copied $n session${if (n == 1) "" else "s"}",
                                Toast.LENGTH_SHORT,
                            ).show()
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.ContentCopy, "Copy to clipboard")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Stats") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                    label = "Total Time",
                    value = formatTotalTime(totalMillis),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Sessions",
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
                Text("History", style = MaterialTheme.typography.titleMedium)
                if (!selectionMode && sessions.isNotEmpty()) {
                    Text(
                        "Long-press to select",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (sessions.isEmpty()) {
                Text(
                    "No sessions yet — start meditating!",
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
                            DismissibleSessionItem(
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
            title = { Text("Delete session?") },
            text = { Text("This will permanently remove this ${formatMinutes(session.completedMillis)} session from your history.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(session)
                    sessionToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) { Text("Cancel") }
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
                Text("Session Notes")
                Text(
                    "${formatDate(session.startTime)} · ${formatMinutes(session.completedMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                placeholder = { Text("Add reflections…") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(notes.trim()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleSessionItem(
    session: MeditationSession,
    onDelete: () -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                false // don't auto-dismiss; let the dialog confirm first
            } else false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        SessionItem(
            session = session,
            onDelete = onDelete,
            onTap = onTap,
            onLongPress = onLongPress,
        )
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
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
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
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onTap() },
            )
            Spacer(Modifier.size(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatDate(session.startTime),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (session.completed) "Completed" else "Stopped early",
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
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
                    formatDate(session.startTime),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (session.completed) "Completed" else "Stopped early",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (session.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .height(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Text(
                            session.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .height(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Text(
                            "Tap to add notes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
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
                        contentDescription = "Delete session",
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

private fun formatMinutes(millis: Long): String {
    val minutes = millis / 60_000
    return "${minutes} min"
}

private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

private fun formatDate(epochMillis: Long): String = dateFormat.format(Date(epochMillis))

private fun formatSessionsForClipboard(sessions: List<MeditationSession>): String {
    // Oldest first reads more naturally for sharing chronologically
    return sessions.sortedBy { it.startTime }.joinToString("\n") { s ->
        val header = "${formatDate(s.startTime)} (${formatMinutes(s.completedMillis)})"
        val notes = s.notes.trim()
        if (notes.isNotEmpty()) "- $header: $notes" else "- $header"
    }
}
