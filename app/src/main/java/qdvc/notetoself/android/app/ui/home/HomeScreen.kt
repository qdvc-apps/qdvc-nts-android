package qdvc.notetoself.android.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
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
    activeWorkspace: String?,
    listing: List<NoteHit>,
    searchQuery: String,
    searchResults: List<NoteHit>,
    indexStatus: IndexStatus,
    onAddWorkspace: () -> Unit,
    onRemoveWorkspace: (String) -> Unit,
    onOpenWorkspace: (String) -> Unit,
    onGoToMode: (BrowseMode) -> Unit,
    onBrowseUp: () -> Unit,
    onOpenNote: (NoteHit) -> Unit,
    onNewNote: () -> Unit,
    onSearchChange: (String) -> Unit,
    onRegenerate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val title = when (mode) {
        BrowseMode.WORKSPACES -> "QDVC NTS"
        BrowseMode.OVERVIEW -> workspaces.firstOrNull { it.uri == activeWorkspace }?.name ?: "Workspace"
        BrowseMode.ALL_NOTES -> "All notes"
        BrowseMode.SEARCH -> "Search"
        BrowseMode.INDEX_STATUS -> "Index status"
    }
    val showBack = mode != BrowseMode.WORKSPACES

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
                    if (mode == BrowseMode.WORKSPACES) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Settings")
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
                    BrowseMode.WORKSPACES -> WorkspacesLevel(
                        workspaces, onAddWorkspace, onRemoveWorkspace, onOpenWorkspace,
                    )
                    BrowseMode.OVERVIEW -> OverviewLevel(onGoToMode, onNewNote)
                    BrowseMode.ALL_NOTES -> NoteListLevel(listing, onOpenNote)
                    BrowseMode.SEARCH -> SearchLevel(searchQuery, searchResults, onSearchChange, onOpenNote)
                    BrowseMode.INDEX_STATUS -> IndexStatusLevel(indexStatus, onRegenerate)
                }
            }
        }
    }
}

@Composable
private fun WorkspacesLevel(
    workspaces: List<Workspace>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    var confirmRemove by remember { mutableStateOf<Workspace?>(null) }
    val hasWorkspace = workspaces.isNotEmpty()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListRow(
                icon = Icons.Filled.CreateNewFolder,
                title = if (hasWorkspace) "Change workspace" else "Add workspace",
                subtitle = if (hasWorkspace)
                    "Pick a different folder (only one workspace is used at a time)"
                else
                    "Grant a device folder to store notes",
                onClick = onAdd,
            )
        }
        if (workspaces.isEmpty()) {
            item { EmptyState("No workspaces yet. Add a folder to get started.") }
        } else {
            items(workspaces, key = { it.uri }) { ws ->
                ListRow(
                    icon = Icons.Filled.Storage,
                    title = ws.name,
                    subtitle = "Tap to open",
                    showChevron = true,
                    trailing = {
                        IconButton(onClick = { confirmRemove = ws }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    onClick = { onOpen(ws.uri) },
                )
            }
        }
    }
    confirmRemove?.let { ws ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove workspace?") },
            text = { Text("This only removes the app's pointer to \"${ws.name}\". Your files are not deleted.") },
            confirmButton = {
                TextButton(onClick = { onRemove(ws.uri); confirmRemove = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun OverviewLevel(onGoToMode: (BrowseMode) -> Unit, onNewNote: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListRow(Icons.Filled.Add, "New note to self", "Write a fresh note", onClick = onNewNote)
        }
        item {
            ListRow(Icons.AutoMirrored.Filled.List, "All notes", "Browse every note in this workspace",
                showChevron = true, onClick = { onGoToMode(BrowseMode.ALL_NOTES) })
        }
        item {
            ListRow(Icons.Filled.Search, "Search", "Full-text search over notes",
                showChevron = true, onClick = { onGoToMode(BrowseMode.SEARCH) })
        }
        item {
            ListRow(Icons.Filled.Storage, "Index status", "State of the search index",
                showChevron = true, onClick = { onGoToMode(BrowseMode.INDEX_STATUS) })
        }
    }
}

@Composable
private fun NoteListLevel(listing: List<NoteHit>, onOpen: (NoteHit) -> Unit) {
    if (listing.isEmpty()) { EmptyState("No notes yet. Create one from the workspace overview."); return }
    LazyColumn(Modifier.fillMaxSize()) {
        items(listing, key = { it.folderUri }) { hit ->
            ListRow(
                icon = Icons.Filled.Description,
                title = hit.title.ifBlank { hit.folderName },
                subtitle = hit.folderName,
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
                title = hit.title.ifBlank { hit.folderName },
                subtitle = hit.snippet.ifBlank { hit.folderName },
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
                    "Building…" to "${status.processed} processed — ${status.currentFile}"
                IndexStatus.State.READY ->
                    "Ready" to "${status.count} notes · last rebuilt ${fmt.format(Date(status.lastRegenerated))}"
            }
            ListRow(Icons.Filled.Storage, label, sub)
        }
        item {
            ListRow(
                Icons.Filled.Add,
                "Regenerate now",
                "Rebuilds only the app's private index; never touches your files.",
                onClick = onRegenerate,
            )
        }
    }
}
