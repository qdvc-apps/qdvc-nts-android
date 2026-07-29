package qdvc.notetoself.android.app.data.index

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per note. `rowId` MUST stay the SQLite rowid for the FTS linkage (B10). */
@Entity(
    tableName = "notes",
    indices = [Index(value = ["workspaceUri", "folderUri"], unique = true)],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val workspaceUri: String,
    val folderUri: String,
    val folderName: String,
    val title: String,
    val lastModified: Long,
    /** Stable category key ("none" when untagged) so the list icon needs no file re-read. */
    val categoryKey: String = "none",
    /** "classic" or "chat" so the list can annotate chats without re-reading files. */
    val kind: String = "classic",
    /** Full searchable body (title + abstract + payload). */
    val content: String,
)

@Fts4(contentEntity = NoteEntity::class)
@Entity(tableName = "notes_fts")
data class NoteFts(
    val content: String,
)

@Entity(tableName = "workspace_meta")
data class WorkspaceMeta(
    @PrimaryKey val workspaceUri: String,
    val lastRegenerated: Long,
    val count: Int,
)
