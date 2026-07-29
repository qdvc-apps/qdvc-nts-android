package qdvc.notetoself.android.app.ui.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qdvc.notetoself.android.app.model.Category
import qdvc.notetoself.android.app.model.Note

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewScreen(note: Note?, fontSize: Float, onEdit: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(note?.displayTitle ?: "View", maxLines = 1) },
                actions = {
                    if (note != null) IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        if (note == null) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "No note open. Open one from Home or the switcher.",
                    modifier = Modifier.padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        SelectionContainer {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(
                    note.displayTitle,
                    fontSize = (fontSize + 6).sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (note.category != Category.NONE) {
                    Text(
                        "${note.category.emoji}  ${note.category.label}",
                        fontSize = (fontSize - 1).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    "Recorded ${note.recordedAt}",
                    fontSize = (fontSize - 3).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )

                SectionHeader("Abstract", fontSize)
                Text(
                    note.abstract.ifBlank { "(no abstract)" },
                    fontSize = fontSize.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                SectionHeader("Payload", fontSize)
                if (note.textPayload.isNotBlank()) {
                    Text(
                        "Payload text",
                        fontSize = (fontSize - 1).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        note.textPayload,
                        fontSize = fontSize.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                if (note.images.isNotEmpty()) {
                    Text(
                        "Payload image(s)",
                        fontSize = (fontSize - 1).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    note.images.forEach { img -> ImageCard(img.fileName, img.uri, fontSize) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, fontSize: Float) {
    Text(
        text,
        fontSize = (fontSize + 2).sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun ImageCard(name: String, uri: String?, fontSize: Float) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        if (uri == null) null else runCatching {
            context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        }.getOrNull()
    }
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(8.dp)) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = name,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(
                    Modifier.fillMaxWidth().height(80.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(name, fontSize = (fontSize - 3).sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}
