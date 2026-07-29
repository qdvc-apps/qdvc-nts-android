package qdvc.notetoself.android.app.ui.edit

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qdvc.notetoself.android.app.model.Category
import qdvc.notetoself.android.app.model.EditDraft
import qdvc.notetoself.android.app.model.NoteKind
import qdvc.notetoself.android.app.ui.components.hierarchySlide

/**
 * The New tab: a top tab-bar choosing between a "Classic note" (the full [EditScreen] form) and
 * a "Chat" (a minimal title + category form that creates a chat thread).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewNoteScreen(
    kind: NoteKind,
    onKindChange: (NoteKind) -> Unit,
    // Classic form wiring
    draft: EditDraft,
    editFontSize: Float,
    classicCanSave: Boolean,
    onTitle: (String) -> Unit,
    onAbstract: (String) -> Unit,
    onPayload: (String) -> Unit,
    onCategory: (Category) -> Unit,
    onRecordedAt: (Long) -> Unit,
    onPickImage: () -> Unit,
    onRemoveKeepImage: (String) -> Unit,
    onRemoveNewImage: (Int) -> Unit,
    onSaveClassic: () -> Unit,
    // Chat form wiring
    chatTitle: String,
    chatCategory: Category,
    onChatTitle: (String) -> Unit,
    onChatCategory: (Category) -> Unit,
    onCreateChat: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = if (kind == NoteKind.CLASSIC) 0 else 1,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Tab(
                selected = kind == NoteKind.CLASSIC,
                onClick = { onKindChange(NoteKind.CLASSIC) },
                text = { Text("Classic note") },
            )
            Tab(
                selected = kind == NoteKind.CHAT,
                onClick = { onKindChange(NoteKind.CHAT) },
                text = { Text("Chat") },
            )
        }

        if (kind == NoteKind.CLASSIC) {
            EditScreen(
                draft = draft,
                fontSize = editFontSize,
                canSave = classicCanSave,
                onTitle = onTitle,
                onAbstract = onAbstract,
                onPayload = onPayload,
                onCategory = onCategory,
                onRecordedAt = onRecordedAt,
                onPickImage = onPickImage,
                onRemoveKeepImage = onRemoveKeepImage,
                onRemoveNewImage = onRemoveNewImage,
                onSave = onSaveClassic,
                onDelete = {}, // never shown for a new note
            )
        } else {
            var showChatCategoryPicker by remember { mutableStateOf(false) }
            AnimatedContent(
                targetState = showChatCategoryPicker,
                transitionSpec = {
                    hierarchySlide(if (targetState) 1 else 0, if (initialState) 1 else 0)
                },
                label = "chat-category",
            ) { picking ->
                if (picking) {
                    CategoryPickerScreen(
                        selected = chatCategory,
                        onPick = { c -> onChatCategory(c); showChatCategoryPicker = false },
                        onBack = { showChatCategoryPicker = false },
                    )
                } else {
                    ChatEntryForm(
                        title = chatTitle,
                        category = chatCategory,
                        fontSize = editFontSize,
                        onTitle = onChatTitle,
                        onOpenCategoryPicker = { showChatCategoryPicker = true },
                        onCreate = onCreateChat,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatEntryForm(
    title: String,
    category: Category,
    fontSize: Float,
    onTitle: (String) -> Unit,
    onOpenCategoryPicker: () -> Unit,
    onCreate: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New chat") },
                actions = {
                    if (title.isNotBlank()) IconButton(onClick = onCreate) {
                        Icon(Icons.Filled.Check, contentDescription = "Create chat")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "A chat is a running thread of timestamped messages you send to yourself " +
                    "(and between personas). Set a title and optional category to begin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = title,
                onValueChange = onTitle,
                label = { Text("Title (required)") },
                isError = title.isBlank(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize.sp),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Category",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            OutlinedButton(onClick = onOpenCategoryPicker, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Sell, contentDescription = null)
                val label = if (category == Category.NONE) "Set category\u2026"
                else "${category.emoji} ${category.label}"
                Text("  $label", fontSize = fontSize.sp)
            }
        }
    }
}
