package qdvc.notetoself.android.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sell
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qdvc.notetoself.android.app.model.ChatMessage
import qdvc.notetoself.android.app.model.Note
import qdvc.notetoself.android.app.model.Persona
import qdvc.notetoself.android.app.model.Category
import qdvc.notetoself.android.app.model.QuotedMessage
import qdvc.notetoself.android.app.ui.components.topOnlyInsets
import qdvc.notetoself.android.app.ui.edit.CategoryPickerScreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Parse a stored stamp like "Wed 29 Jul 2026 20:32:03 AWST" back to epoch millis (best effort). */
private fun parseStampMillis(stamp: String): Long? {
    // Try with the timezone token first; if that fails (some tz abbreviations don't parse),
    // drop the last whitespace-delimited token and parse as local time — fine for a same-day test.
    runCatching {
        SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(stamp)?.time
    }.getOrNull()?.let { return it }
    val withoutTz = stamp.trim().substringBeforeLast(' ')
    return runCatching {
        SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss", Locale.US).parse(withoutTz)?.time
    }.getOrNull()
}

/** If the message was sent today, show only HH:mm; otherwise the full stored stamp. */
private fun displayStamp(stamp: String): String {
    val millis = parseStampMillis(stamp) ?: return stamp
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val now = Calendar.getInstance()
    val sameDay = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) SimpleDateFormat("HH:mm", Locale.US).format(millis) else stamp
}

private fun personaColor(personaKey: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(Persona.fromKey(personaKey).colorHex)) }
        .getOrDefault(Color(0xFF3B6EA5))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    note: Note,
    persona: Persona,
    fontSize: Float,
    onSend: (String, QuotedMessage?) -> Unit,
    onAttachImage: () -> Unit,
    onEditMessage: (Int, String) -> Unit,
    onSetPersona: (Persona) -> Unit,
    onToggleClosed: () -> Unit,
    onUpdateMeta: (String, Category) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var personaMenuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var editText by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<QuotedMessage?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(note.title) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    val hashViolation = input.split("\n").any { it.trimStart().startsWith("#") }

    Scaffold(
        contentWindowInsets = topOnlyInsets,
        topBar = {
            TopAppBar(
                // Tapping the title area (avatar + title + persona) opens the persona chooser.
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { personaMenuOpen = true },
                    ) {
                        Avatar(persona.initials, personaColor(persona.key))
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(note.displayTitle, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (note.chatClosed) "Closed · as ${persona.key} ▾" else "as ${persona.key} ▾",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = personaMenuOpen, onDismissRequest = { personaMenuOpen = false }) {
                            Persona.entries.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text("${p.initials} · ${p.key}") },
                                    leadingIcon = { Avatar(p.initials, personaColor(p.key), sizeDp = 28) },
                                    trailingIcon = { if (p == persona) Icon(Icons.Filled.Check, null) },
                                    onClick = { personaMenuOpen = false; onSetPersona(p) },
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename chat") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; renameText = note.title; showRename = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Set category") },
                            leadingIcon = { Icon(Icons.Filled.Sell, contentDescription = null) },
                            onClick = { menuOpen = false; showCategoryPicker = true },
                        )
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
                    replyTo = replyTo,
                    onCancelReply = { replyTo = null },
                    onInput = { input = it },
                    onAttach = onAttachImage,
                    onSend = {
                        if (input.isNotBlank() && !hashViolation) {
                            onSend(input, replyTo); input = ""; replyTo = null
                        }
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
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { Box(Modifier.size(8.dp)) }
            items(note.messages.size) { index ->
                val msg = note.messages[index]
                val mine = msg.personaKey.equals(persona.key, ignoreCase = true)
                MessageBubble(
                    msg = msg,
                    mine = mine,
                    fontSize = fontSize,
                    onEdit = { editing = index; editText = msg.text },
                    onReply = {
                        replyTo = QuotedMessage(msg.timestampDisplay, msg.personaKey, msg.text)
                    },
                )
            }
            item { Box(Modifier.size(8.dp)) }
        }
    }

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

    // Rename dialog.
    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename chat") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    isError = renameText.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onUpdateMeta(renameText, note.category); showRename = false },
                    enabled = renameText.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } },
        )
    }

    // Category picker overlay (reuses the shared edit-package picker).
    if (showCategoryPicker) {
        CategoryPickerScreen(
            selected = note.category,
            onPick = { c -> onUpdateMeta(note.title, c); showCategoryPicker = false },
            onBack = { showCategoryPicker = false },
        )
    }
}

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    mine: Boolean,
    fontSize: Float,
    onEdit: () -> Unit,
    onReply: () -> Unit,
) {
    val senderColor = personaColor(msg.personaKey)
    // Mine: filled with the persona colour. Others': neutral surface, with a coloured name + avatar.
    val bubbleColor = if (mine) senderColor else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (mine) Color.White else MaterialTheme.colorScheme.onBackground
    val shape = RoundedCornerShape(
        topStart = 4.dp, topEnd = 4.dp,
        bottomStart = if (mine) 4.dp else 1.dp,
        bottomEnd = if (mine) 1.dp else 4.dp,
    )

    // Swipe-left-to-reply: track horizontal drag and fire onReply past a threshold.
    var offset by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }

    Row(
        Modifier
            .fillMaxWidth()
            .pointerInput(msg.timestampDisplay) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offset < -threshold) onReply()
                        offset = 0f
                    },
                ) { _, drag ->
                    offset = (offset + drag).coerceIn(-threshold * 1.5f, 0f)
                }
            }
            .graphicsLayer { translationX = offset },
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        // Interlocutor avatar on the left of their messages (Signal/WhatsApp style).
        if (!mine) {
            Avatar(Persona.fromKey(msg.personaKey).initials, senderColor, sizeDp = 28)
            Box(Modifier.size(6.dp))
        }
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .background(bubbleColor, shape)
                .clickable { onEdit() }
                .padding(3.dp),
        ) {
            if (!mine) {
                Text(
                    msg.personaKey,
                    fontSize = (fontSize - 3).sp,
                    fontWeight = FontWeight.Bold,
                    color = senderColor,
                )
            }
            msg.quoted?.let { QuotedBlock(it, fontSize) }
            if (msg.imageUri != null) ChatImage(msg.imageUri)
            if (msg.text.isNotBlank()) {
                Text(msg.text, fontSize = fontSize.sp, color = textColor)
            }
            Text(
                displayStamp(msg.timestampDisplay),
                fontSize = (fontSize - 5).sp,
                color = textColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp).align(Alignment.End),
            )
        }
    }
}

@Composable
private fun QuotedBlock(q: QuotedMessage, fontSize: Float) {
    val accent = personaColor(q.personaKey)
    val lightAccent = lighten(accent, 0.6f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            // Semi-transparent DARK scrim behind the quote (independent of bubble colour).
            .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(4.dp)),
    ) {
        Box(
            Modifier
                .padding(vertical = 2.dp)
                .size(width = 3.dp, height = 34.dp)
                .background(lightAccent, RoundedCornerShape(2.dp)),
        )
        Column(Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 8.dp)) {
            Text(
                q.personaKey,
                fontSize = (fontSize - 4).sp,
                fontWeight = FontWeight.Bold,
                color = lightAccent,
            )
            Text(
                q.text.ifBlank { "(no text)" },
                fontSize = (fontSize - 3).sp,
                color = lightAccent.copy(alpha = 0.85f),
                maxLines = 2,
            )
        }
    }
}

/** Blend [c] toward white by [fraction] (0 = unchanged, 1 = white) for a lighter shade. */
private fun lighten(c: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = c.red + (1f - c.red) * f,
        green = c.green + (1f - c.green) * f,
        blue = c.blue + (1f - c.blue) * f,
        alpha = c.alpha,
    )
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
private fun Avatar(initials: String, color: Color, sizeDp: Int = 36) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = (sizeDp / 2.6f).sp,
        )
    }
}

@Composable
private fun MessageComposer(
    input: String,
    fontSize: Float,
    hashViolation: Boolean,
    replyTo: QuotedMessage?,
    onCancelReply: () -> Unit,
    onInput: (String) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
) {
    // Lift the composer by only the part of the keyboard that exceeds the navigation bar,
    // since the composer already sits above the app's bottom nav bar (plain imePadding would
    // double-count the nav-bar height and leave a gap between the keyboard and the toolbar).
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
    ) {
        if (replyTo != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(width = 3.dp, height = 32.dp)
                        .background(personaColor(replyTo.personaKey), RoundedCornerShape(2.dp)),
                )
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        "Replying to ${replyTo.personaKey}",
                        fontSize = (fontSize - 4).sp,
                        fontWeight = FontWeight.Bold,
                        color = personaColor(replyTo.personaKey),
                    )
                    Text(
                        replyTo.text.ifBlank { "(no text)" },
                        fontSize = (fontSize - 3).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = onCancelReply) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel reply")
                }
            }
        }
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
