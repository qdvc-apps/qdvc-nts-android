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
import qdvc.notetoself.android.app.model.Category
import qdvc.notetoself.android.app.model.EditDraft
import qdvc.notetoself.android.app.model.FontContext
import qdvc.notetoself.android.app.model.Note
import qdvc.notetoself.android.app.model.NoteKind
import qdvc.notetoself.android.app.model.OpenNote
import qdvc.notetoself.android.app.model.Persona
import qdvc.notetoself.android.app.model.QuotedMessage
import qdvc.notetoself.android.app.model.Tab
import qdvc.notetoself.android.app.model.ThemeMode
import qdvc.notetoself.android.app.model.Workspace

data class HomeState(
    val mode: BrowseMode = BrowseMode.NOTES,
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

    /** Which entry form the New tab shows: classic note vs chat. */
    private val _newNoteKind = MutableStateFlow(NoteKind.CLASSIC)
    val newNoteKind: StateFlow<NoteKind> = _newNoteKind.asStateFlow()

    /** Chat entry-form draft (only title + category). */
    private val _chatDraftTitle = MutableStateFlow("")
    val chatDraftTitle: StateFlow<String> = _chatDraftTitle.asStateFlow()
    private val _chatDraftCategory = MutableStateFlow(Category.NONE)
    val chatDraftCategory: StateFlow<Category> = _chatDraftCategory.asStateFlow()

    /** Persona currently composing in the open chat (drives left/right bubble anchoring). */
    private val _persona = MutableStateFlow(Persona.NOTE_TAKER)
    val persona: StateFlow<Persona> = _persona.asStateFlow()

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
                    refreshListing()
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
            // Single-workspace policy: replace any existing workspace and reset session.
            settings.replaceWorkspace(ws)
            _openNotes.value = emptyList()
            _currentNote.value = null
            _draft.value = EditDraft()
            _home.value = _home.value.copy(activeWorkspace = ws.uri, mode = BrowseMode.NOTES)
            index.reconcile(ws.uri)
            refreshListing()
        }
    }

    // ---- home navigation ---------------------------------------------------

    fun goToMode(mode: BrowseMode) {
        _home.value = _home.value.copy(mode = mode)
        when (mode) {
            BrowseMode.NOTES -> refreshListing()
            BrowseMode.INDEX_STATUS -> refreshIndexStatus()
            else -> {}
        }
    }

    fun homeRoot() {
        _home.value = _home.value.copy(mode = BrowseMode.NOTES, searchQuery = "", searchResults = emptyList())
        refreshListing()
    }

    /** Returns true if it consumed the back action (B2). NOTES is the home root. */
    fun browseUp(): Boolean {
        val h = _home.value
        return when (h.mode) {
            BrowseMode.NOTES -> false
            BrowseMode.SEARCH, BrowseMode.INDEX_STATUS -> {
                _home.value = h.copy(mode = BrowseMode.NOTES); true
            }
        }
    }

    fun refreshListing() {
        val ws = _home.value.activeWorkspace ?: return
        viewModelScope.launch {
            val fromIndex = index.listAll(ws)
            val listing = fromIndex ?: run {
                // Live-scan fallback (cold path, no index yet): read each note for its category+kind.
                notesRepo.scanNoteFolders(ws).map { entry ->
                    val n = notesRepo.readNote(ws, entry.folderUri)
                    val cat = n?.category ?: Category.NONE
                    val kind = (n?.kind ?: NoteKind.CLASSIC).name.lowercase()
                    NoteHit(ws, entry.folderUri, entry.name, prettify(entry.name), cat.key, kind, "")
                }
            }
            _home.value = _home.value.copy(listing = listing)
            if (fromIndex == null) {
                index.reconcile(ws)
                // Re-read from the freshly built index so categories/titles are authoritative.
                index.listAll(ws)?.let { _home.value = _home.value.copy(listing = it) }
            }
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
            val open = OpenNote(workspaceUri, note.folderUri, note.folderName, wsName, note.category.key)
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
        if (note.kind == NoteKind.CHAT) _persona.value = Persona.NOTE_TAKER
        _draft.value = EditDraft(
            isNew = false, note = note, title = note.title, abstract = note.abstract,
            textPayload = note.textPayload, keepImages = note.images.map { it.fileName },
            recordedAtMillis = note.recordedAtMillis, category = note.category,
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
        // Prepare a blank draft but keep the currently-viewed note intact so View (Tab 2)
        // stays usable. The NEW tab renders the Edit surface from this draft.
        _draft.value = EditDraft(isNew = true)
        _chatDraftTitle.value = ""
        _chatDraftCategory.value = Category.NONE
        _tab.value = Tab.NEW
    }

    fun setNewNoteKind(kind: NoteKind) { _newNoteKind.value = kind }
    fun setChatDraftTitle(t: String) { _chatDraftTitle.value = t }
    fun setChatDraftCategory(c: Category) { _chatDraftCategory.value = c }

    /** Creates a chat note, opens it in the switcher, resets persona, and shows it in View (Tab 2). */
    fun createChatNote() {
        val ws = _home.value.activeWorkspace ?: return
        val title = _chatDraftTitle.value.trim()
        if (title.isBlank()) return
        viewModelScope.launch {
            val saved = notesRepo.createChat(ws, title, _chatDraftCategory.value) ?: return@launch
            index.onSaved(
                ws, saved.folderUri, saved.folderName, saved.title, "", "",
                System.currentTimeMillis(), saved.category.key, saved.kind.name.lowercase(),
            )
            _persona.value = Persona.NOTE_TAKER
            addOrRefreshOpen(saved)
            _currentNote.value = saved
            persistSession()
            refreshListing()
            _chatDraftTitle.value = ""
            _chatDraftCategory.value = Category.NONE
            _tab.value = Tab.VIEW
        }
    }

    fun setPersona(p: Persona) { _persona.value = p }

    /** Sends a message from the current persona into the open chat. */
    fun sendChatMessage(
        text: String,
        imageSourceUri: String?,
        imageDisplayName: String?,
        quoted: QuotedMessage? = null,
    ) {
        val note = _currentNote.value ?: return
        if (note.kind != NoteKind.CHAT || note.chatClosed) return
        val body = text.trim()
        if (body.isEmpty() && imageSourceUri == null) return
        // Guard: no line may begin with '#', which would corrupt the chat heading structure.
        if (qdvc.notetoself.android.app.util.ReadmeFormat.hasHashLine(body)) return
        viewModelScope.launch {
            val updated = notesRepo.appendChatMessage(
                note, _persona.value.key, body, imageSourceUri, imageDisplayName, quoted,
            ) ?: return@launch
            afterChatMutation(updated)
        }
    }

    fun editChatMessage(index: Int, newText: String) {
        val note = _currentNote.value ?: return
        if (note.kind != NoteKind.CHAT) return
        if (qdvc.notetoself.android.app.util.ReadmeFormat.hasHashLine(newText.trim())) return
        viewModelScope.launch {
            val updated = notesRepo.editChatMessage(note, index, newText.trim()) ?: return@launch
            afterChatMutation(updated)
        }
    }

    fun toggleChatClosed() {
        val note = _currentNote.value ?: return
        if (note.kind != NoteKind.CHAT) return
        viewModelScope.launch {
            val updated = notesRepo.setChatClosed(note, !note.chatClosed) ?: return@launch
            afterChatMutation(updated)
        }
    }

    /** Changes the open chat's title and/or category (may rename the folder). */
    fun updateChatMeta(newTitle: String, newCategory: Category) {
        val note = _currentNote.value ?: return
        if (note.kind != NoteKind.CHAT) return
        val title = newTitle.trim()
        if (title.isBlank()) return
        val oldFolderUri = note.folderUri
        viewModelScope.launch {
            val updated = notesRepo.updateChatMeta(note, title, newCategory) ?: return@launch
            if (updated.folderUri != oldFolderUri) {
                index.onRenamedOrDeleted(note.workspaceUri, oldFolderUri)
                _openNotes.value = _openNotes.value.map {
                    if (it.folderUri == oldFolderUri)
                        it.copy(
                            folderUri = updated.folderUri,
                            folderName = updated.folderName,
                            categoryKey = updated.category.key,
                        )
                    else it
                }
            } else {
                _openNotes.value = _openNotes.value.map {
                    if (it.folderUri == updated.folderUri)
                        it.copy(folderName = updated.folderName, categoryKey = updated.category.key)
                    else it
                }
            }
            _currentNote.value = updated
            index.onSaved(
                note.workspaceUri, updated.folderUri, updated.folderName, updated.title,
                "", updated.messages.joinToString("\n") { it.text },
                System.currentTimeMillis(), updated.category.key, updated.kind.name.lowercase(),
            )
            persistSession()
            refreshListing()
        }
    }

    private fun afterChatMutation(updated: Note) {
        _currentNote.value = updated
        val ws = updated.workspaceUri
        viewModelScope.launch {
            index.onSaved(
                ws, updated.folderUri, updated.folderName, updated.title,
                "", updated.messages.joinToString("\n") { it.text },
                System.currentTimeMillis(), updated.category.key, updated.kind.name.lowercase(),
            )
        }
        refreshListing()
    }

    private fun addOrRefreshOpen(note: Note) {
        val wsName = workspaces.value.firstOrNull { it.uri == note.workspaceUri }?.name ?: "workspace"
        if (_openNotes.value.none { it.folderUri == note.folderUri }) {
            _openNotes.value = _openNotes.value +
                OpenNote(note.workspaceUri, note.folderUri, note.folderName, wsName, note.category.key)
        }
    }

    /** Enter edit mode for the note currently shown in View (pencil action). */
    fun editCurrentNote() {
        val note = _currentNote.value ?: return
        _draft.value = EditDraft(
            isNew = false, note = note, title = note.title, abstract = note.abstract,
            textPayload = note.textPayload, keepImages = note.images.map { it.fileName },
            recordedAtMillis = note.recordedAtMillis, category = note.category,
        )
        _tab.value = Tab.EDIT
    }

    /** Leave an Edit/New surface via back: return to View if a note is open, else Home. */
    fun backFromEditing() {
        _tab.value = if (_currentNote.value != null) Tab.VIEW else Tab.HOME
    }

    fun updateDraft(transform: (EditDraft) -> EditDraft) {
        _draft.value = transform(_draft.value)
    }

    fun setDraftRecordedAt(millis: Long) {
        _draft.value = _draft.value.copy(recordedAtMillis = millis)
    }

    fun setDraftCategory(category: Category) {
        _draft.value = _draft.value.copy(category = category)
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
                notesRepo.createNote(
                    ws, d.title, d.abstract, d.textPayload, pending,
                    d.recordedAtMillis, d.category,
                )
            } else {
                val old = d.note!!
                val oldFolderUri = old.folderUri
                val n = notesRepo.updateNote(
                    old, d.title, d.abstract, d.textPayload, d.keepImages, pending,
                    d.recordedAtMillis, d.category,
                )
                if (n != null && n.folderUri != oldFolderUri) {
                    // Folder renamed: fix session + index references.
                    index.onRenamedOrDeleted(ws, oldFolderUri)
                    _openNotes.value = _openNotes.value.map {
                        if (it.folderUri == oldFolderUri)
                            it.copy(
                                folderUri = n.folderUri,
                                folderName = n.folderName,
                                categoryKey = n.category.key,
                            )
                        else it
                    }
                }
                n
            }
            if (saved != null) {
                index.onSaved(
                    ws, saved.folderUri, saved.folderName, saved.title,
                    saved.abstract, saved.textPayload, System.currentTimeMillis(),
                    saved.category.key, saved.kind.name.lowercase(),
                )
                // Keep any matching open-note's category/name current (covers category-only edits).
                _openNotes.value = _openNotes.value.map {
                    if (it.folderUri == saved.folderUri)
                        it.copy(folderName = saved.folderName, categoryKey = saved.category.key)
                    else it
                }
                if (_openNotes.value.none { it.folderUri == saved.folderUri }) {
                    val wsName = workspaces.value.firstOrNull { it.uri == ws }?.name ?: "workspace"
                    _openNotes.value = _openNotes.value +
                        OpenNote(ws, saved.folderUri, saved.folderName, wsName, saved.category.key)
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
