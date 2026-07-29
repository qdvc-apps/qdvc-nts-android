package qdvc.notetoself.android.app.data.index

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

data class NoteHit(
    val workspaceUri: String,
    val folderUri: String,
    val folderName: String,
    val title: String,
    val categoryKey: String,
    val snippet: String,
)

@Dao
interface IndexDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE workspaceUri = :ws")
    suspend fun clearWorkspace(ws: String)

    @Query("DELETE FROM notes WHERE workspaceUri = :ws AND folderUri = :folder")
    suspend fun deleteNote(ws: String, folder: String)

    @Query("SELECT workspaceUri, folderUri, folderName, lastModified FROM notes WHERE workspaceUri = :ws")
    suspend fun stubs(ws: String): List<NoteStub>

    @Query("SELECT COUNT(*) FROM notes WHERE workspaceUri = :ws")
    suspend fun count(ws: String): Int

    @Query(
        "SELECT workspaceUri, folderUri, folderName, title, categoryKey, '' AS snippet " +
            "FROM notes WHERE workspaceUri = :ws ORDER BY folderName DESC"
    )
    suspend fun listAll(ws: String): List<NoteHit>

    @Query(
        "SELECT n.workspaceUri, n.folderUri, n.folderName, n.title, n.categoryKey, " +
            "snippet(notes_fts) AS snippet " +
            "FROM notes n JOIN notes_fts ON n.rowId = notes_fts.rowid " +
            "WHERE n.workspaceUri = :ws AND notes_fts MATCH :match"
    )
    suspend fun searchBody(ws: String, match: String): List<NoteHit>

    @Query(
        "SELECT workspaceUri, folderUri, folderName, title, categoryKey, '' AS snippet " +
            "FROM notes WHERE workspaceUri = :ws AND (title LIKE :like OR folderName LIKE :like)"
    )
    suspend fun searchTitle(ws: String, like: String): List<NoteHit>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(meta: WorkspaceMeta)

    @Query("SELECT * FROM workspace_meta WHERE workspaceUri = :ws")
    suspend fun meta(ws: String): WorkspaceMeta?
}

data class NoteStub(
    val workspaceUri: String,
    val folderUri: String,
    val folderName: String,
    val lastModified: Long,
)

@Database(
    entities = [NoteEntity::class, NoteFts::class, WorkspaceMeta::class],
    version = 2,
    exportSchema = false,
)
abstract class IndexDatabase : RoomDatabase() {
    abstract fun dao(): IndexDao

    companion object {
        @Volatile private var instance: IndexDatabase? = null

        fun get(context: Context): IndexDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                IndexDatabase::class.java,
                "nts-index.db",
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
