package qdvc.notetoself.android.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import qdvc.notetoself.android.app.data.index.IndexDatabase
import qdvc.notetoself.android.app.data.index.NoteEntity
import qdvc.notetoself.android.app.data.index.NoteHit
import qdvc.notetoself.android.app.data.index.WorkspaceMeta

data class IndexStatus(
    val state: State = State.UNKNOWN,
    val currentFile: String = "",
    val processed: Int = 0,
    val count: Int = 0,
    val lastRegenerated: Long = 0L,
) {
    enum class State { UNKNOWN, NOT_BUILT, BUILDING, READY }
}

class IndexRepository(
    context: Context,
    private val notes: NoteRepository,
) {
    private val dao = IndexDatabase.get(context).dao()
    private val mutex = Mutex()
    val status = MutableStateFlow(IndexStatus())

    private fun searchable(title: String, abstract: String, payload: String) =
        listOf(title, abstract, payload).joinToString("\n")

    /** Background reconcile: only re-read new/changed folders. */
    suspend fun reconcile(workspaceUri: String) = mutex.withLock {
        status.value = status.value.copy(state = IndexStatus.State.BUILDING, processed = 0)
        val folders = notes.scanNoteFolders(workspaceUri)
        val known = dao.stubs(workspaceUri).associateBy { it.folderUri }
        val seen = HashSet<String>()
        var processed = 0
        for (entry in folders) {
            seen.add(entry.folderUri)
            val existing = known[entry.folderUri]
            if (existing == null || existing.lastModified != entry.lastModified) {
                val note = notes.readNote(workspaceUri, entry.folderUri)
                if (note != null) {
                    dao.upsert(
                        NoteEntity(
                            workspaceUri = workspaceUri,
                            folderUri = entry.folderUri,
                            folderName = entry.name,
                            title = note.title,
                            lastModified = entry.lastModified,
                            categoryKey = note.category.key,
                            content = searchable(note.title, note.abstract, note.textPayload),
                        )
                    )
                }
            }
            processed++
            status.value = status.value.copy(currentFile = entry.name, processed = processed)
        }
        // Prune vanished.
        known.keys.filter { it !in seen }.forEach { dao.deleteNote(workspaceUri, it) }
        val count = dao.count(workspaceUri)
        val now = System.currentTimeMillis()
        dao.putMeta(WorkspaceMeta(workspaceUri, now, count))
        status.value = IndexStatus(IndexStatus.State.READY, "", processed, count, now)
    }

    suspend fun regenerate(workspaceUri: String) = mutex.withLock {
        dao.clearWorkspace(workspaceUri)
    }.also { reconcile(workspaceUri) }

    suspend fun onSaved(
        workspaceUri: String, folderUri: String, folderName: String,
        title: String, abstract: String, payload: String, lastModified: Long,
        categoryKey: String,
    ) {
        dao.upsert(
            NoteEntity(
                workspaceUri = workspaceUri,
                folderUri = folderUri,
                folderName = folderName,
                title = title,
                lastModified = lastModified,
                categoryKey = categoryKey,
                content = searchable(title, abstract, payload),
            )
        )
    }

    suspend fun onRenamedOrDeleted(workspaceUri: String, oldFolderUri: String) {
        dao.deleteNote(workspaceUri, oldFolderUri)
    }

    /** Returns null when no usable index exists so callers can live-scan (B10). */
    suspend fun listAll(workspaceUri: String): List<NoteHit>? {
        val meta = dao.meta(workspaceUri) ?: return null
        if (meta.count == 0) return null
        return dao.listAll(workspaceUri)
    }

    suspend fun search(workspaceUri: String, query: String): List<NoteHit> {
        val tokens = query.trim().split(Regex("\\s+"))
            .map { it.replace(Regex("[\"*():^-]"), "") }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val match = tokens.joinToString(" ") { "$it*" }
        val body = runCatching { dao.searchBody(workspaceUri, match) }.getOrDefault(emptyList())
        val titles = dao.searchTitle(workspaceUri, "%${tokens.joinToString("%")}%")
        val seen = HashSet<String>()
        val out = ArrayList<NoteHit>()
        for (h in body + titles) if (seen.add(h.folderUri)) out.add(cleanSnippet(h))
        return out
    }

    private fun cleanSnippet(h: NoteHit): NoteHit =
        h.copy(snippet = h.snippet.replace(Regex("[\u0002\u0003]"), ""))

    suspend fun currentMeta(workspaceUri: String): IndexStatus {
        val meta = dao.meta(workspaceUri)
            ?: return IndexStatus(IndexStatus.State.NOT_BUILT)
        return IndexStatus(
            state = IndexStatus.State.READY,
            count = meta.count,
            lastRegenerated = meta.lastRegenerated,
        )
    }
}
