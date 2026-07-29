package qdvc.notetoself.android.app.ui.switcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qdvc.notetoself.android.app.model.Category
import qdvc.notetoself.android.app.model.OpenNote
import qdvc.notetoself.android.app.ui.components.EmptyState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwitcherScreen(
    openNotes: List<OpenNote>,
    currentFolderUri: String?,
    onSwitch: (OpenNote) -> Unit,
    onClose: (OpenNote) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var reordering by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open notes") },
                actions = {
                    if (openNotes.isNotEmpty()) IconButton(onClick = { reordering = !reordering }) {
                        Icon(
                            if (reordering) Icons.Filled.Check else Icons.Filled.SwapVert,
                            contentDescription = if (reordering) "Done" else "Reorder",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        if (openNotes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState("Nothing open. Open a note from Home.")
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(openNotes, key = { it.folderUri }) { note ->
                val idx = openNotes.indexOf(note)
                SwitcherRow(
                    note = note,
                    isCurrent = note.folderUri == currentFolderUri,
                    reordering = reordering,
                    canMoveUp = idx > 0,
                    canMoveDown = idx < openNotes.lastIndex,
                    onTap = { onSwitch(note) },
                    onClose = { onClose(note) },
                    onUp = { onMove(idx, idx - 1) },
                    onDown = { onMove(idx, idx + 1) },
                )
            }
        }
    }
}

@Composable
private fun SwitcherRow(
    note: OpenNote,
    isCurrent: Boolean,
    reordering: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onTap: () -> Unit,
    onClose: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    val revealWidthDp = 96.dp
    val revealPx = with(LocalDensity.current) { revealWidthDp.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    val animated by animateFloatAsState(offset, label = "swipe")

    Box(Modifier.fillMaxWidth().height(72.dp)) {
        // Behind: the Close action (X icon above a "Close" label), centred in the strip.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(revealWidthDp)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.error)
                .clickable { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onPrimary)
                Text("Close", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
            }
        }

        // Foreground row.
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = if (reordering) 0f else animated }
                .background(MaterialTheme.colorScheme.surface)
                .then(
                    if (reordering) Modifier else Modifier.pointerInput(note.folderUri) {
                        detectHorizontalDragGestures(
                            onDragEnd = { offset = if (offset < -revealPx / 2) -revealPx else 0f },
                        ) { _, drag ->
                            offset = (offset + drag).coerceIn(-revealPx, 0f)
                        }
                    },
                )
                .clickable(enabled = !reordering) { onTap() }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val emoji = Category.fromKey(note.categoryKey).emoji
            if (emoji.isNotBlank()) {
                Text(
                    emoji,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    note.folderName.let { if (it.length > 10) it.substring(11).replace('-', ' ') else it },
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
                Text(
                    // Second line: the date the note was made (the folder's YYYY-MM-DD prefix).
                    note.folderName.take(10),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Right-side "OPEN" pill marks the currently-open note (hidden while reordering).
            if (isCurrent && !reordering) {
                OpenPill()
            }
            if (reordering) {
                IconButton(onClick = onUp, enabled = canMoveUp) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                }
                IconButton(onClick = onDown, enabled = canMoveDown) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                }
            }
        }
    }
}

@Composable
private fun OpenPill() {
    Text(
        "OPEN",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onBackground,
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
