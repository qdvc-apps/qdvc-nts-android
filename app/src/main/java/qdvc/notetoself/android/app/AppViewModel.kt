package qdvc.notetoself.android.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import qdvc.notetoself.android.app.data.IndexRepository
import qdvc.notetoself.android.app.data.IndexStatus
import qdvc.notetoself.android.app.data.NoteRepository
import qdvc.notetoself.android.app.data.SettingsRepository
import qdvc.notetoself.android.app.data.ThemeRepository
import qdvc.notetoself.android.app.data.index.NoteHit
import qdvc.notetoself.android.app.model.BrowseMode
import qdvc.notetoself.android.app.model.EditDraft
import qdvc.notetoself.android.app.model.FontContext
import qdvc.notetoself.android.app.model.Note
import qdvc.notetoself.android.app.model.OpenNote
import qdvc.notetoself.android.app.model.Tab
import qdvc.notetoself.android.app.model.ThemeMode
import qdvc.notetoself.android.app.model.Workspace

data class HomeState(
    val mode: BrowseMode = BrowseMode.WORKSPACES,
    val activeWorkspace: String? = null,
    val listing: List<NoteHit> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<NoteHit> = emptyList(),
) {
    val depth: Int get() = mode.ordinal
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val notesRepo = NoteRepository(app)
    val settings = SettingsRepository(app)
    val themes = ThemeRepository(app)
    val index = IndexRepository(app, notesRepo)

    // ---- tab + home state --------------------------------------------------

    private val _tab = MutableStateFlow(Tab.HOME)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    val workspaces: StateFlow<List<Workspace>> =
        settings.workspaces.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- current note / view -----------------------------------------------

    private val _currentNote = MutableStateFlow<Note?>(null)
    val currentNote: StateFlow<Note?> = _currentNote.asStateFlow()

    private val _openNotes = MutableStateFlow<List<OpenNote>>(emptyList())
    val openNotes: StateFlow<List<OpenNote>> = _openNotes.asStateFlow()

    private val _draft = MutableStateFlow(EditDraft())
    val draft: StateFlow<EditDraft> = _draft.asStateFlow()

    // ---- appearance --------------------------------------------------------

    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.AUTOMATIC)
    val lightThemeId: StateFlow<String> =
        settings.lightTheme.stateIn(viewModelScope, SharingStarted.Eagerly, "light-default")
    val darkThemeId: StateFlow<String> =
        settings.darkTheme.stateIn(viewModelScope, SharingStarted.Eagerly, "dark-default")
    val viewFontSize: StateFlow<Float> =
        settings.fontSize(FontContext.VIEW).stateIn(viewModelScope, SharingStarted.Eagerly, 16f)
    val editFontSize: StateFlow<Float> =
        settings.fontSize(FontContext.EDIT).stateIn(viewModelScope, SharingStarted.Eagerly, 16f)

    val indexStatus: StateFlow<IndexStatus> = index.status.asStateFlow()

    init {
        // Restore session.
        viewModelScope.launch {
            settings.activeWorkspace.collect { active ->
                if (active != null && _home.value.activeWorkspace != active) {
                    _home.value = _home.value.copy(activeWorkspace = active)
                }
            }
        }
        viewModelScope.launch {
            settings.openNotes.collect { restored ->
                if (_openNotes.value.isEmpty() && restored.isNotEmpty()) {
                    // Drop items whose folder no longer resolves to a readable note.
                    val alive = restored.filter {
                        notesRepo.readNote(it.workspaceUri, it.folderUri) != null
                    }
                    _openNotes.value = alive
                }
            }
        }
    }

    // ---- workspaces --------------------------------------------------------

    fun addWorkspace(treeUri: Uri) {
        viewModelScope.launch {
            notesRepo.takePersistablePermission(treeUri)
            val ws = Workspace(treeUri.toString(), notesRepo.workspaceName(treeUri))
            settings.addWorkspace(ws)
            settings.setActiveWorkspace(ws.uri)
            _home.value = _home.value.copy(activeWorkspace = ws.uri, mode = BrowseMode.OVERVIEW)
            index.reconcile(ws.uri)
        }
    }

    fun removeWorkspace(uri: String) {
        viewModelScope.launch { settings.removeWorkspace(uri) }
    }

    fun openWorkspace(uri: String) {
        viewModelScope.launch {
            settings.setActiveWorkspace(uri)
            _home.value = _home.value.copy(activeWorkspace = uri, mode = BrowseMode.OVERVIEW)
        }
    }

    // ---- home navigation ---------------------------------------------------

    fun goToMode(mode: BrowseMode) {
        _home.value = _home.value.copy(mode = mode)
        when (mode) {
            BrowseMode.ALL_NOTES -> refreshListing()
            BrowseMode.INDEX_STATUS -> refreshIndexStatus()
            else -> {}
        }
    }

    fun homeRoot() {
        _home.value = _home.value.copy(mode = BrowseMode.WORKSPACES, searchQuery = "", searchResults = emptyList())
    }

    /** Returns true if it consumed the back action (B2). */
    fun browseUp(): Boolean {
        val h = _home.value
        return when (h.mode) {
            BrowseMode.WORKSPACES -> false
            BrowseMode.OVERVIEW -> { _home.value = h.copy(mode = BrowseMode.WORKSPACES); true }
            BrowseMode.ALL_NOTES, BrowseMode.SEARCH, BrowseMode.INDEX_STATUS -> {
                _home.value = h.copy(mode = BrowseMode.OVERVIEW); true
            }
        }
    }

    fun refreshListing() {
        val ws = _home.value.activeWorkspace ?: return
        viewModelScope.launch {
            val fromIndex = index.listAll(ws)
            val listing = fromIndex ?: run {
                // Live-scan fallback.
                notesRepo.scanNoteFolders(ws).map {
                    NoteHit(ws, it.folderUri, it.name, prettify(it.name), "")
                }
            }
            _home.value = _home.value.copy(listing = listing)
            if (fromIndex == null) index.reconcile(ws)
        }
    }

    private fun prettify(folderName: String): String =
        folderName.removeRange(0, minOf(11, folderName.length)).replace('-', ' ')

    fun setSearchQuery(q: String) {
        _home.value = _home.value.copy(searchQuery = q)
        val ws = _home.value.activeWorkspace ?: return
        viewModelScope.launch {
            val results = if (q.isBlank()) emptyList() else index.search(ws, q)
            _home.value = _home.value.copy(searchResults = results)
        }
    }

    fun refreshIndexStatus() {
        val ws = _home.value.activeWorkspace ?: return
        viewModelScope.launch { index.status.value = index.currentMeta(ws) }
    }

    fun regenerateIndex() {
        val ws = _home.value.activeWorkspace ?: return
        viewModelScope.launch { index.regenerate(ws) }
    }

    // ---- opening / switcher ------------------------------------------------

    fun openNote(workspaceUri: String, folderUri: String, folderName: String) {
        viewModelScope.launch {
            val note = notesRepo.readNote(workspaceUri, folderUri) ?: return@launch
            val wsName = workspaces.value.firstOrNull { it.uri == workspaceUri }?.name ?: "workspace"
            val open = OpenNote(workspaceUri, note.folderUri, note.folderName, wsName)
            if (_openNotes.value.none { it.folderUri == note.folderUri }) {
                _openNotes.value = _openNotes.value + open
            }
            selectNote(note)
            persistSession()
            _tab.value = Tab.VIEW
        }
    }

    private fun selectNote(note: Note) {
        _currentNote.value = note
        _draft.value = EditDraft(
            isNew = false, note = note, title = note.title, abstract = note.abstract,
            textPayload = note.textPayload, keepImages = note.images.map { it.fileName },
        )
    }

    fun switchTo(open: OpenNote) {
        viewModelScope.launch {
            val note = notesRepo.readNote(open.workspaceUri, open.folderUri) ?: return@launch
            selectNote(note)
            persistSession()
            _tab.value = Tab.VIEW
        }
    }

    fun closeNote(open: OpenNote) {
        _openNotes.value = _openNotes.value.filterNot { it.folderUri == open.folderUri }
        if (_currentNote.value?.folderUri == open.folderUri) {
            _currentNote.value = null
            _draft.value = EditDraft()
        }
        persistSession()
    }

    fun moveOpen(from: Int, to: Int) {
        val list = _openNotes.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        _openNotes.value = list
        persistSession()
    }

    private fun persistSession() {
        viewModelScope.launch {
            settings.persistSession(_openNotes.value, _currentNote.value?.folderUri)
        }
    }

    // ---- editing -----------------------------------------------------------

    fun startNewNote() {
        _currentNote.value = null
        _draft.value = EditDraft(isNew = true)
        _tab.value = Tab.EDIT
    }

    fun updateDraft(transform: (EditDraft) -> EditDraft) {
        _draft.value = transform(_draft.value)
    }

    fun addDraftImage(displayName: String, sourceUri: String) {
        _draft.value = _draft.value.copy(newImages = _draft.value.newImages + (displayName to sourceUri))
    }

    fun removeKeepImage(name: String) {
        _draft.value = _draft.value.copy(keepImages = _draft.value.keepImages.filterNot { it == name })
    }

    fun removeNewImage(index: Int) {
        _draft.value = _draft.value.copy(
            newImages = _draft.value.newImages.filterIndexed { i, _ -> i != index },
        )
    }

    fun saveDraft(onDone: (Note?) -> Unit = {}) {
        val d = _draft.value
        val ws = _home.value.activeWorkspace ?: run { onDone(null); return }
        if (d.title.isBlank()) { onDone(null); return } // title required
        viewModelScope.launch {
            val pending = d.newImages.map { NoteRepository.PendingImage(it.first, it.second) }
            val saved: Note? = if (d.isNew) {
                notesRepo.createNote(ws, d.title, d.abstract, d.textPayload, pending)
            } else {
                val old = d.note!!
                val oldFolderUri = old.folderUri
                val n = notesRepo.updateNote(
                    old, d.title, d.abstract, d.textPayload, d.keepImages, pending,
                )
                if (n != null && n.folderUri != oldFolderUri) {
                    // Folder renamed: fix session + index references.
                    index.onRenamedOrDeleted(ws, oldFolderUri)
                    _openNotes.value = _openNotes.value.map {
                        if (it.folderUri == oldFolderUri)
                            it.copy(folderUri = n.folderUri, folderName = n.folderName)
                        else it
                    }
                }
                n
            }
            if (saved != null) {
                index.onSaved(
                    ws, saved.folderUri, saved.folderName, saved.title,
                    saved.abstract, saved.textPayload, System.currentTimeMillis(),
                )
                if (_openNotes.value.none { it.folderUri == saved.folderUri }) {
                    val wsName = workspaces.value.firstOrNull { it.uri == ws }?.name ?: "workspace"
                    _openNotes.value = _openNotes.value +
                        OpenNote(ws, saved.folderUri, saved.folderName, wsName)
                }
                selectNote(saved)
                persistSession()
                refreshListing()
                _tab.value = Tab.VIEW
            }
            onDone(saved)
        }
    }

    fun deleteCurrent(onDone: () -> Unit = {}) {
        val note = _currentNote.value ?: return
        viewModelScope.launch {
            notesRepo.deleteNote(note)
            index.onRenamedOrDeleted(note.workspaceUri, note.folderUri)
            _openNotes.value = _openNotes.value.filterNot { it.folderUri == note.folderUri }
            _currentNote.value = null
            persistSession()
            refreshListing()
            _tab.value = Tab.HOME
            onDone()
        }
    }

    // ---- tab switching -----------------------------------------------------

    fun selectTab(tab: Tab) {
        if (tab == Tab.HOME && _tab.value == Tab.HOME) { homeRoot(); return }
        _tab.value = tab
    }

    // ---- settings mutations ------------------------------------------------

    fun setThemeMode(m: ThemeMode) = viewModelScope.launch { settings.setThemeMode(m) }
    fun setLightTheme(id: String) = viewModelScope.launch { settings.setLightTheme(id) }
    fun setDarkTheme(id: String) = viewModelScope.launch { settings.setDarkTheme(id) }
    fun setFontSize(ctx: FontContext, size: Float) =
        viewModelScope.launch { settings.setFontSize(ctx, size) }
}
