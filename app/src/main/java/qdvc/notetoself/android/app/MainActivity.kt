package qdvc.notetoself.android.app

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import qdvc.notetoself.android.app.model.BrowseMode
import qdvc.notetoself.android.app.model.Tab
import qdvc.notetoself.android.app.ui.components.NtsBottomBar
import qdvc.notetoself.android.app.ui.components.TabSpec
import qdvc.notetoself.android.app.ui.edit.EditScreen
import qdvc.notetoself.android.app.ui.home.HomeScreen
import qdvc.notetoself.android.app.ui.settings.SettingsScreen
import qdvc.notetoself.android.app.ui.switcher.SwitcherScreen
import qdvc.notetoself.android.app.ui.theme.QdvcNtsTheme
import qdvc.notetoself.android.app.ui.theme.resolveDark
import qdvc.notetoself.android.app.ui.view.ViewScreen

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot(vm) }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val themeMode by vm.themeMode.collectAsState()
    val lightId by vm.lightThemeId.collectAsState()
    val darkId by vm.darkThemeId.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = resolveDark(themeMode, systemDark)
    val spec = if (dark) vm.themes.byId(darkId, true) else vm.themes.byId(lightId, false)

    QdvcNtsTheme(spec = spec, darkTheme = dark) {
        AppContent(vm)
    }
}

@Composable
private fun AppContent(vm: AppViewModel) {
    val tab by vm.tab.collectAsState()
    val home by vm.home.collectAsState()
    val workspaces by vm.workspaces.collectAsState()
    val currentNote by vm.currentNote.collectAsState()
    val openNotes by vm.openNotes.collectAsState()
    val draft by vm.draft.collectAsState()
    val indexStatus by vm.indexStatus.collectAsState()
    val viewFontSize by vm.viewFontSize.collectAsState()
    val editFontSize by vm.editFontSize.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val lightId by vm.lightThemeId.collectAsState()
    val darkId by vm.darkThemeId.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf<qdvc.notetoself.android.app.model.OpenNote?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    // Workspace folder picker.
    val openTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) vm.addWorkspace(uri) }

    // Image picker for payloads.
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val name = queryDisplayName(context, uri)
            vm.addDraftImage(name, uri.toString())
        }
    }

    if (showSettings) {
        SettingsScreen(
            themeMode = themeMode,
            lightThemeId = lightId,
            darkThemeId = darkId,
            lightThemes = vm.themes.light(),
            darkThemes = vm.themes.dark(),
            viewFontSize = viewFontSize,
            editFontSize = editFontSize,
            onThemeMode = vm::setThemeMode,
            onLightTheme = vm::setLightTheme,
            onDarkTheme = vm::setDarkTheme,
            onFontSize = vm::setFontSize,
            onClose = { showSettings = false },
        )
        return
    }

    // System back mirrors the active surface's toolbar back (B2).
    val backConsumable = when (tab) {
        Tab.HOME -> home.mode != BrowseMode.NOTES
        Tab.EDIT, Tab.NEW -> true
        else -> false
    }
    BackHandler(enabled = backConsumable) {
        when (tab) {
            Tab.HOME -> vm.browseUp()
            Tab.EDIT, Tab.NEW -> vm.backFromEditing()
            else -> {}
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NtsBottomBar(
                current = tab,
                hasNote = currentNote != null,
                tabs = listOf(
                    TabSpec(Tab.HOME, "Home", Icons.Filled.Home, requiresNote = false),
                    TabSpec(Tab.VIEW, "View", Icons.Filled.Visibility, requiresNote = true),
                    TabSpec(Tab.SWITCHER, "Jump", Icons.Filled.Layers, requiresNote = false),
                    TabSpec(Tab.NEW, "New", Icons.Filled.Add, requiresNote = false),
                ),
                onSelect = { selected ->
                    if (selected == Tab.NEW) vm.startNewNote() else vm.selectTab(selected)
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .windowInsetsPadding(WindowInsets.systemBars.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)),
        ) {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    mode = home.mode,
                    workspaces = workspaces,
                    listing = home.listing,
                    searchQuery = home.searchQuery,
                    searchResults = home.searchResults,
                    indexStatus = indexStatus,
                    onAddWorkspace = { openTree.launch(null) },
                    onGoToMode = vm::goToMode,
                    onBrowseUp = { vm.browseUp() },
                    onOpenNote = { vm.openNote(it.workspaceUri, it.folderUri, it.folderName) },
                    onSearchChange = vm::setSearchQuery,
                    onRegenerate = vm::regenerateIndex,
                    onOpenSettings = { showSettings = true },
                )
                Tab.VIEW -> ViewScreen(
                    note = currentNote,
                    fontSize = viewFontSize,
                    onEdit = { vm.editCurrentNote() },
                )
                Tab.EDIT -> EditScreen(
                    draft = draft,
                    fontSize = editFontSize,
                    canSave = draft.title.isNotBlank() && !draft.matchesSaved(),
                    onTitle = { t -> vm.updateDraft { it.copy(title = t) } },
                    onAbstract = { a -> vm.updateDraft { it.copy(abstract = a) } },
                    onPayload = { p -> vm.updateDraft { it.copy(textPayload = p) } },
                    onCategory = vm::setDraftCategory,
                    onRecordedAt = vm::setDraftRecordedAt,
                    onPickImage = { pickImage.launch(arrayOf("image/*")) },
                    onRemoveKeepImage = vm::removeKeepImage,
                    onRemoveNewImage = vm::removeNewImage,
                    onSave = { vm.saveDraft() },
                    onDelete = { vm.deleteCurrent() },
                )
                Tab.SWITCHER -> SwitcherScreen(
                    openNotes = openNotes,
                    currentFolderUri = currentNote?.folderUri,
                    onSwitch = vm::switchTo,
                    onClose = { note ->
                        val isCurrentDraftDirty = currentNote?.folderUri == note.folderUri && !draft.matchesSaved()
                        if (isCurrentDraftDirty) confirmClose = note else vm.closeNote(note)
                    },
                    onMove = vm::moveOpen,
                )
                // NEW is an action (startNewNote switches to EDIT); render Edit as a safe fallback.
                Tab.NEW -> EditScreen(
                    draft = draft,
                    fontSize = editFontSize,
                    canSave = draft.title.isNotBlank() && !draft.matchesSaved(),
                    onTitle = { t -> vm.updateDraft { it.copy(title = t) } },
                    onAbstract = { a -> vm.updateDraft { it.copy(abstract = a) } },
                    onPayload = { p -> vm.updateDraft { it.copy(textPayload = p) } },
                    onCategory = vm::setDraftCategory,
                    onRecordedAt = vm::setDraftRecordedAt,
                    onPickImage = { pickImage.launch(arrayOf("image/*")) },
                    onRemoveKeepImage = vm::removeKeepImage,
                    onRemoveNewImage = vm::removeNewImage,
                    onSave = { vm.saveDraft() },
                    onDelete = { vm.deleteCurrent() },
                )
            }
        }
    }

    confirmClose?.let { note ->
        AlertDialog(
            onDismissRequest = { confirmClose = null },
            title = { Text("Close with unsaved changes?") },
            text = { Text("This note has unsaved edits. Closing will discard them.") },
            confirmButton = {
                TextButton(onClick = { vm.closeNote(note); confirmClose = null }) {
                    Text("Close anyway", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClose = null }) { Text("Cancel") } },
        )
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "image"
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) name = c.getString(i)
        }
    }
    return name
}
