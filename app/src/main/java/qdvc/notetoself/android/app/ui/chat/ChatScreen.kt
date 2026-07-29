package qdvc.notetoself.android.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qdvc.notetoself.android.app.ui.components.topOnlyInsets
import qdvc.notetoself.android.app.model.ChatMessage
import qdvc.notetoself.android.app.model.Note
import qdvc.notetoself.android.app.model.Persona

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    note: Note,
    persona: Persona,
    fontSize: Float,
    onSend: (String) -> Unit,
    onAttachImage: () -> Unit,
    onEditMessage: (Int, String) -> Unit,
    onSetPersona: (Persona) -> Unit,
    onToggleClosed: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var personaMenuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var editText by remember { mutableStateOf("") }

    // A message starting with '#' on any line would corrupt the README heading structure.
    val hashViolation = input.split("\n").any { it.trimStart().startsWith("#") }

    Scaffold(
        // Take only the top (status-bar) inset; the app's bottom nav bar owns the bottom inset,
        // so the composer must not add it again (would leave a gap above the nav bar).
        contentWindowInsets = topOnlyInsets,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(persona.initials)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(note.displayTitle, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (note.chatClosed) "Closed · as ${persona.key}" else "as ${persona.key}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    // Persona switcher.
                    IconButton(onClick = { personaMenuOpen = true }) {
                        Icon(Icons.Filled.Person, contentDescription = "Persona")
                    }
                    DropdownMenu(expanded = personaMenuOpen, onDismissRequest = { personaMenuOpen = false }) {
                        Persona.entries.forEach { p ->
                            DropdownMenuItem(
                                text = { Text("${p.initials} · ${p.key}") },
                                trailingIcon = { if (p == persona) Icon(Icons.Filled.Check, null) },
                                onClick = { personaMenuOpen = false; onSetPersona(p) },
                            )
                        }
                    }
                    // Close/reopen menu.
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (note.chatClosed) "Reopen chat" else "Close chat") },
                            leadingIcon = {
                                Icon(
                                    if (note.chatClosed) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                    contentDescription = null,
                                )
                            },
                            onClick = { menuOpen = false; onToggleClosed() },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            if (!note.chatClosed) {
                MessageComposer(
                    input = input,
                    fontSize = fontSize,
                    hashViolation = hashViolation,
                    onInput = { input = it },
                    onAttach = onAttachImage,
                    onSend = {
                        if (input.isNotBlank() && !hashViolation) { onSend(input); input = "" }
                    },
                )
            } else {
                ClosedBanner()
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Box(Modifier.size(8.dp)) }
            items(note.messages.size) { index ->
                val msg = note.messages[index]
                val mine = msg.personaKey.equals(persona.key, ignoreCase = true)
                MessageBubble(msg, mine, fontSize) { editing = index; editText = msg.text }
            }
            item { Box(Modifier.size(8.dp)) }
        }
    }

    // Edit dialog for a message.
    editing?.let { idx ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit message") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    isError = editText.split("\n").any { it.trimStart().startsWith("#") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onEditMessage(idx, editText); editing = null },
                    enabled = editText.split("\n").none { it.trimStart().startsWith("#") },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    mine: Boolean,
    fontSize: Float,
    onEdit: () -> Unit,
) {
    val bubbleColor = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (mine) 16.dp else 4.dp,
        bottomEnd = if (mine) 4.dp else 16.dp,
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .background(bubbleColor, shape)
                .clickable { onEdit() }
                .padding(10.dp),
        ) {
            if (!mine) {
                Text(
                    msg.personaKey,
                    fontSize = (fontSize - 3).sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (msg.imageUri != null) ChatImage(msg.imageUri)
            if (msg.text.isNotBlank()) {
                Text(msg.text, fontSize = fontSize.sp, color = textColor)
            }
            Text(
                msg.timestampDisplay,
                fontSize = (fontSize - 5).sp,
                color = textColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp).align(Alignment.End),
            )
        }
    }
}

@Composable
private fun ChatImage(uri: String) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        runCatching {
            context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Attached image",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
        )
    }
}

@Composable
private fun Avatar(initials: String) {
    Box(
        Modifier
            .size(36.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun MessageComposer(
    input: String,
    fontSize: Float,
    hashViolation: Boolean,
    onInput: (String) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        if (hashViolation) {
            Text(
                "Messages can't have a line starting with '#'.",
                color = MaterialTheme.colorScheme.error,
                fontSize = (fontSize - 3).sp,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onAttach) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Attach image")
            }
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize.sp),
                isError = hashViolation,
                maxLines = 6,
            )
            IconButton(onClick = onSend, enabled = input.isNotBlank() && !hashViolation) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun ClosedBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "  This chat is closed. Reopen it from the menu to add messages.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
