package qdvc.notetoself.android.app.ui.edit

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qdvc.notetoself.android.app.AppViewModel
import qdvc.notetoself.android.app.model.EditDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    draft: EditDraft,
    fontSize: Float,
    canSave: Boolean,
    onTitle: (String) -> Unit,
    onAbstract: (String) -> Unit,
    onPayload: (String) -> Unit,
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
                label = { Text("Abstract — why this matters") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize.sp),
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedTextField(
                value = draft.textPayload,
                onValueChange = onPayload,
                label = { Text("Payload — paste text, URLs, article body…") },
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
}

@Composable
private fun ImageRow(name: String, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
