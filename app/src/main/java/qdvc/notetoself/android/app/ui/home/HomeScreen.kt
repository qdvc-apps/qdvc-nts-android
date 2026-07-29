package qdvc.notetoself.android.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import qdvc.notetoself.android.app.data.IndexStatus
import qdvc.notetoself.android.app.data.index.NoteHit
import qdvc.notetoself.android.app.model.BrowseMode
import qdvc.notetoself.android.app.model.Category
import qdvc.notetoself.android.app.model.Workspace
import qdvc.notetoself.android.app.ui.components.EmptyState
import qdvc.notetoself.android.app.ui.components.ListRow
import qdvc.notetoself.android.app.ui.components.hierarchySlide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    mode: BrowseMode,
    workspaces: List<Workspace>,
    listing: List<NoteHit>,
    searchQuery: String,
    searchResults: List<NoteHit>,
    indexStatus: IndexStatus,
    onAddWorkspace: () -> Unit,
    onGoToMode: (BrowseMode) -> Unit,
    onBrowseUp: () -> Unit,
    onOpenNote: (NoteHit) -> Unit,
    onSearchChange: (String) -> Unit,
    onRegenerate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val title = when (mode) {
        BrowseMode.NOTES -> "QDVC Note to Self"
        BrowseMode.SEARCH -> "Search"
        BrowseMode.INDEX_STATUS -> "Index status"
    }
    val showBack = mode != BrowseMode.NOTES
    var menuOpen by remember { mutableStateOf(false) }
    var confirmChange by remember { mutableStateOf(false) }
    val hasWorkspace = workspaces.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (showBack) IconButton(onClick = onBrowseUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (mode == BrowseMode.NOTES) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Search") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                onClick = { menuOpen = false; onGoToMode(BrowseMode.SEARCH) },
                            )
                            DropdownMenuItem(
                                text = { Text("Index status") },
                                leadingIcon = { Icon(Icons.Filled.Storage, contentDescription = null) },
                                onClick = { menuOpen = false; onGoToMode(BrowseMode.INDEX_STATUS) },
                            )
                            DropdownMenuItem(
                                text = { Text(if (hasWorkspace) "Change workspace" else "Add workspace") },
                                leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    if (hasWorkspace) confirmChange = true else onAddWorkspace()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                onClick = { menuOpen = false; onOpenSettings() },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = { hierarchySlide(targetState.ordinal, initialState.ordinal) },
                label = "home",
            ) { m ->
                when (m) {
                    BrowseMode.NOTES -> NoteListLevel(hasWorkspace, listing, onAddWorkspace, onOpenNote)
                    BrowseMode.SEARCH -> SearchLevel(searchQuery, searchResults, onSearchChange, onOpenNote)
                    BrowseMode.INDEX_STATUS -> IndexStatusLevel(indexStatus, onRegenerate)
                }
            }
        }
    }

    if (confirmChange) {
        AlertDialog(
            onDismissRequest = { confirmChange = false },
            title = { Text("Change workspace?") },
            text = {
                Text(
                    "Only one workspace is used at a time. Picking a different folder will switch " +
                        "to it and close any open notes. Your files are not deleted.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmChange = false; onAddWorkspace() }) { Text("Choose folder") }
            },
            dismissButton = { TextButton(onClick = { confirmChange = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NoteListLevel(
    hasWorkspace: Boolean,
    listing: List<NoteHit>,
    onAddWorkspace: () -> Unit,
    onOpen: (NoteHit) -> Unit,
) {
    if (!hasWorkspace) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                ListRow(
                    icon = Icons.Filled.FolderOpen,
                    title = "Add workspace",
                    subtitle = "Grant a device folder to store your notes",
                    onClick = onAddWorkspace,
                )
            }
            item { EmptyState("No workspace yet. Add a folder to start writing notes.") }
        }
        return
    }
    if (listing.isEmpty()) {
        EmptyState("No notes yet. Tap \"New\" below to write your first note to self.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(listing, key = { it.folderUri }) { hit ->
            ListRow(
                icon = Icons.Filled.Description,
                leadingEmoji = Category.fromKey(hit.categoryKey).emoji,
                title = hit.title.ifBlank { hit.folderName },
                italicSuffix = if (hit.kind == "chat") " (chat)" else "",
                subtitle = hit.folderName.take(10),
                showChevron = true,
                onClick = { onOpen(hit) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchLevel(
    query: String,
    results: List<NoteHit>,
    onChange: (String) -> Unit,
    onOpen: (NoteHit) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onChange,
                label = { Text("Search notes") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            )
        }
        if (query.isNotBlank() && results.isEmpty()) {
            item { EmptyState("No matches for \"$query\".") }
        }
        items(results, key = { it.folderUri }) { hit ->
            ListRow(
                icon = Icons.Filled.Description,
                leadingEmoji = Category.fromKey(hit.categoryKey).emoji,
                title = hit.title.ifBlank { hit.folderName },
                italicSuffix = if (hit.kind == "chat") " (chat)" else "",
                subtitle = hit.snippet.ifBlank { hit.folderName.take(10) },
                showChevron = true,
                onClick = { onOpen(hit) },
            )
        }
    }
}

@Composable
private fun IndexStatusLevel(status: IndexStatus, onRegenerate: () -> Unit) {
    val fmt = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            val (label, sub) = when (status.state) {
                IndexStatus.State.NOT_BUILT, IndexStatus.State.UNKNOWN ->
                    "Not built" to "The index will build on demand."
                IndexStatus.State.BUILDING ->
                    "Building..." to "${status.processed} processed - ${status.currentFile}"
                IndexStatus.State.READY ->
                    "Ready" to "${status.count} notes - last rebuilt ${fmt.format(Date(status.lastRegenerated))}"
            }
            ListRow(Icons.Filled.Storage, label, sub)
        }
        item {
            ListRow(
                Icons.Filled.Search,
                "Regenerate now",
                "Rebuilds only the app's private index; never touches your files.",
                onClick = onRegenerate,
            )
        }
    }
}
