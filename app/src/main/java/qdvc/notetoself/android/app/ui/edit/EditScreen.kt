package qdvc.notetoself.android.app.ui.edit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qdvc.notetoself.android.app.model.Category
import qdvc.notetoself.android.app.model.EditDraft
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    draft: EditDraft,
    fontSize: Float,
    canSave: Boolean,
    onTitle: (String) -> Unit,
    onAbstract: (String) -> Unit,
    onPayload: (String) -> Unit,
    onCategory: (Category) -> Unit,
    onRecordedAt: (Long) -> Unit,
    onPickImage: () -> Unit,
    onRemoveKeepImage: (String) -> Unit,
    onRemoveNewImage: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val stamp = remember { SimpleDateFormat("EEE dd MMM yyyy, HH:mm", Locale.US) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft.isNew) "New note" else "Edit note", maxLines = 1) },
                actions = {
                    if (!draft.isNew) IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                    if (canSave) IconButton(onClick = onSave) {
                        Icon(Icons.Filled.Check, contentDescription = "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = onTitle,
                label = { Text("Title (required)") },
                isError = draft.title.isBlank(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize.sp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.abstract,
                onValueChange = onAbstract,
                label = { Text("Abstract - why this matters") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize.sp),
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            // ---- Date & time (with backdating) --------------------------------
            Text(
                "Recorded date & time",
                fontSize = (fontSize + 1).sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Schedule, contentDescription = null)
                Text("  ${stamp.format(Date(draft.recordedAtMillis))}", fontSize = fontSize.sp)
            }

            // ---- Category -----------------------------------------------------
            Text(
                "Category",
                fontSize = (fontSize + 1).sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // A "None" chip to clear the tag, then one chip per category.
                FilterChip(
                    selected = draft.category == Category.NONE,
                    onClick = { onCategory(Category.NONE) },
                    label = { Text("None") },
                )
                Category.selectable.forEach { cat ->
                    FilterChip(
                        selected = draft.category == cat,
                        onClick = { onCategory(cat) },
                        label = { Text("${cat.emoji} ${cat.label}") },
                    )
                }
            }

            OutlinedTextField(
                value = draft.textPayload,
                onValueChange = onPayload,
                label = { Text("Payload - paste text, URLs, article body...") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize.sp),
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )

            Text(
                "Payload image(s)",
                fontSize = (fontSize + 1).sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )

            draft.keepImages.forEach { name ->
                ImageRow(name) { onRemoveKeepImage(name) }
            }
            draft.newImages.forEachIndexed { i, (name, _) ->
                ImageRow("$name  (new)") { onRemoveNewImage(i) }
            }
            if (draft.keepImages.isEmpty() && draft.newImages.isEmpty()) {
                Text(
                    "No images attached.",
                    fontSize = (fontSize - 2).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                Text("  Attach image", fontSize = fontSize.sp)
            }
        }
    }

    // ---- Date picker dialog ----------------------------------------------------
    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = draft.recordedAtMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val picked = dateState.selectedDateMillis
                    if (picked != null) {
                        // Combine the picked calendar date with the existing time-of-day.
                        val old = Calendar.getInstance().apply { timeInMillis = draft.recordedAtMillis }
                        val pickedCal = Calendar.getInstance().apply { timeInMillis = picked }
                        val merged = Calendar.getInstance().apply {
                            set(Calendar.YEAR, pickedCal.get(Calendar.YEAR))
                            set(Calendar.MONTH, pickedCal.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, pickedCal.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, old.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, old.get(Calendar.MINUTE))
                            set(Calendar.SECOND, old.get(Calendar.SECOND))
                        }
                        onRecordedAt(merged.timeInMillis)
                    }
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("Next: time") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    // ---- Time picker dialog ----------------------------------------------------
    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = draft.recordedAtMillis }
        val timeState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true,
        )
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val merged = Calendar.getInstance().apply {
                        timeInMillis = draft.recordedAtMillis
                        set(Calendar.HOUR_OF_DAY, timeState.hour)
                        set(Calendar.MINUTE, timeState.minute)
                        set(Calendar.SECOND, 0)
                    }
                    onRecordedAt(merged.timeInMillis)
                    showTimePicker = false
                }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Set the time", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
                TimePicker(state = timeState)
            }
        }
    }
}

@Composable
private fun ImageRow(name: String, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Text(
            name,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
        }
    }
}
