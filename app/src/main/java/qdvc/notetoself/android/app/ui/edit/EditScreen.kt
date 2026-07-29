package qdvc.notetoself.android.app.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import qdvc.notetoself.android.app.ui.components.hierarchySlide
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
    var showCategoryPicker by remember { mutableStateOf(false) }
    val stamp = remember { SimpleDateFormat("EEE dd MMM yyyy, HH:mm", Locale.US) }

    // The category picker slides over the form (and back) using the shared hierarchy animation.
    // System back closes it with no change via a BackHandler inside CategoryPickerScreen.
    AnimatedContent(
        targetState = showCategoryPicker,
        transitionSpec = {
            // depth 1 = picker, depth 0 = form; deeper slides in from the right.
            hierarchySlide(if (targetState) 1 else 0, if (initialState) 1 else 0)
        },
        label = "edit-category",
    ) { picking ->
        if (picking) {
            CategoryPickerScreen(
                selected = draft.category,
                onPick = { cat -> onCategory(cat); showCategoryPicker = false },
                onBack = { showCategoryPicker = false },
            )
        } else {
            EditForm(
                draft = draft,
                fontSize = fontSize,
                canSave = canSave,
                stamp = stamp,
                onTitle = onTitle,
                onAbstract = onAbstract,
                onPayload = onPayload,
                onOpenCategoryPicker = { showCategoryPicker = true },
                onOpenDatePicker = { showDatePicker = true },
                onPickImage = onPickImage,
                onRemoveKeepImage = onRemoveKeepImage,
                onRemoveNewImage = onRemoveNewImage,
                onSave = onSave,
                onDelete = onDelete,
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditForm(
    draft: EditDraft,
    fontSize: Float,
    canSave: Boolean,
    stamp: SimpleDateFormat,
    onTitle: (String) -> Unit,
    onAbstract: (String) -> Unit,
    onPayload: (String) -> Unit,
    onOpenCategoryPicker: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveKeepImage: (String) -> Unit,
    onRemoveNewImage: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
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
            // ===== MANDATORY =====
            SectionHeader("Mandatory", fontSize)
            OutlinedTextField(
                value = draft.title,
                onValueChange = onTitle,
                label = { Text("Title (required)") },
                isError = draft.title.isBlank(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize.sp),
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel("Payload text", fontSize)
            OutlinedTextField(
                value = draft.textPayload,
                onValueChange = onPayload,
                label = { Text("Paste text, URLs, article body...") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize.sp),
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )

            FieldLabel("Payload image(s)", fontSize)
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

            // ===== OPTIONAL =====
            SectionHeader("Optional", fontSize)
            OutlinedTextField(
                value = draft.abstract,
                onValueChange = onAbstract,
                label = { Text("Abstract - why this matters") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize.sp),
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            FieldLabel("Recorded date & time", fontSize)
            OutlinedButton(onClick = onOpenDatePicker, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Schedule, contentDescription = null)
                Text("  ${stamp.format(Date(draft.recordedAtMillis))}", fontSize = fontSize.sp)
            }

            FieldLabel("Category", fontSize)
            OutlinedButton(onClick = onOpenCategoryPicker, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Sell, contentDescription = null)
                val label = if (draft.category == Category.NONE) {
                    "Set category\u2026"
                } else {
                    "${draft.category.emoji} ${draft.category.label}"
                }
                Text("  $label", fontSize = fontSize.sp)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, fontSize: Float) {
    Text(
        text.uppercase(),
        fontSize = (fontSize + 2).sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
}

@Composable
private fun FieldLabel(text: String, fontSize: Float) {
    Text(
        text,
        fontSize = (fontSize + 1).sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryPickerScreen(
    selected: Category,
    onPick: (Category) -> Unit,
    onBack: () -> Unit,
) {
    // System back returns to the edit screen without making a change.
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set category") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // "None" clears the tag.
            item {
                CategoryRow(
                    emoji = "",
                    label = "None (uncategorised)",
                    isSelected = selected == Category.NONE,
                    onClick = { onPick(Category.NONE) },
                )
            }
            items(Category.selectable) { cat ->
                CategoryRow(
                    emoji = cat.emoji,
                    label = cat.label,
                    isSelected = selected == cat,
                    onClick = { onPick(cat) },
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    emoji: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                emoji.ifBlank { "\u2205" },
                fontSize = 22.sp,
                modifier = Modifier.width(36.dp),
            )
            Text(
                label,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (isSelected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    }
}
