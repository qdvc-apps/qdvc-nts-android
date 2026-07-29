package qdvc.notetoself.android.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import qdvc.notetoself.android.app.model.FontContext
import qdvc.notetoself.android.app.model.OpenNote
import qdvc.notetoself.android.app.model.ThemeMode
import qdvc.notetoself.android.app.model.Workspace

private val Context.dataStore by preferencesDataStore("nts-settings")

private const val SEP = "\u241F"      // unit separator between fields
private const val ITEM_SEP = "\u241E" // record separator between items

class SettingsRepository(private val context: Context) {
    private val ds get() = context.dataStore

    private object Keys {
        val WORKSPACES = stringPreferencesKey("workspaces")
        val WORKSPACE_ORDER = stringPreferencesKey("workspace_order")
        val ACTIVE_WORKSPACE = stringPreferencesKey("active_workspace")
        val OPEN_NOTES = stringPreferencesKey("open_notes")
        val CURRENT_NOTE = stringPreferencesKey("current_note")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LIGHT_THEME = stringPreferencesKey("light_theme")
        val DARK_THEME = stringPreferencesKey("dark_theme")
        val FONT_VIEW = stringPreferencesKey("font_view")
        val FONT_EDIT = stringPreferencesKey("font_edit")
        val SIZE_VIEW = floatPreferencesKey("size_view")
        val SIZE_EDIT = floatPreferencesKey("size_edit")
    }

    // ---- workspaces --------------------------------------------------------

    val workspaces: Flow<List<Workspace>> = ds.data.map { p ->
        val raw = p[Keys.WORKSPACES].orEmpty()
        val order = p[Keys.WORKSPACE_ORDER].orEmpty().split(ITEM_SEP).filter { it.isNotBlank() }
        val map = raw.split(ITEM_SEP).filter { it.isNotBlank() }.mapNotNull { rec ->
            val f = rec.split(SEP)
            if (f.size >= 2) f[0] to Workspace(f[0], f[1]) else null
        }.toMap()
        val ordered = order.mapNotNull { map[it] }
        val rest = map.values.filter { it.uri !in order }
        ordered + rest
    }

    val activeWorkspace: Flow<String?> = ds.data.map { it[Keys.ACTIVE_WORKSPACE] }

    suspend fun addWorkspace(ws: Workspace) {
        ds.edit { p ->
            val current = p[Keys.WORKSPACES].orEmpty().split(ITEM_SEP)
                .filter { it.isNotBlank() && it.substringBefore(SEP) != ws.uri }
            val rec = "${ws.uri}$SEP${ws.name}"
            p[Keys.WORKSPACES] = (current + rec).joinToString(ITEM_SEP)
            val order = p[Keys.WORKSPACE_ORDER].orEmpty().split(ITEM_SEP)
                .filter { it.isNotBlank() && it != ws.uri }
            p[Keys.WORKSPACE_ORDER] = (order + ws.uri).joinToString(ITEM_SEP)
            if (p[Keys.ACTIVE_WORKSPACE].isNullOrBlank()) p[Keys.ACTIVE_WORKSPACE] = ws.uri
        }
    }

    /**
     * Make [ws] the one and only workspace. Any previously granted workspace pointer is dropped
     * (the user's files are untouched), and the open-note session is cleared since those notes
     * belonged to the previous workspace.
     */
    suspend fun replaceWorkspace(ws: Workspace) {
        ds.edit { p ->
            p[Keys.WORKSPACES] = "${ws.uri}$SEP${ws.name}"
            p[Keys.WORKSPACE_ORDER] = ws.uri
            p[Keys.ACTIVE_WORKSPACE] = ws.uri
            p.remove(Keys.OPEN_NOTES)
            p.remove(Keys.CURRENT_NOTE)
        }
    }

    suspend fun removeWorkspace(uri: String) {
        ds.edit { p ->
            p[Keys.WORKSPACES] = p[Keys.WORKSPACES].orEmpty().split(ITEM_SEP)
                .filter { it.isNotBlank() && it.substringBefore(SEP) != uri }
                .joinToString(ITEM_SEP)
            p[Keys.WORKSPACE_ORDER] = p[Keys.WORKSPACE_ORDER].orEmpty().split(ITEM_SEP)
                .filter { it.isNotBlank() && it != uri }.joinToString(ITEM_SEP)
            if (p[Keys.ACTIVE_WORKSPACE] == uri) p.remove(Keys.ACTIVE_WORKSPACE)
        }
    }

    suspend fun setActiveWorkspace(uri: String) {
        ds.edit { it[Keys.ACTIVE_WORKSPACE] = uri }
    }

    // ---- open-note session -------------------------------------------------

    val openNotes: Flow<List<OpenNote>> = ds.data.map { p ->
        p[Keys.OPEN_NOTES].orEmpty().split(ITEM_SEP).filter { it.isNotBlank() }.mapNotNull { rec ->
            val f = rec.split(SEP)
            when {
                f.size >= 5 -> OpenNote(f[0], f[1], f[2], f[3], f[4])
                f.size == 4 -> OpenNote(f[0], f[1], f[2], f[3])
                else -> null
            }
        }
    }

    val currentNote: Flow<String?> = ds.data.map { it[Keys.CURRENT_NOTE] }

    suspend fun persistSession(open: List<OpenNote>, current: String?) {
        ds.edit { p ->
            p[Keys.OPEN_NOTES] = open.joinToString(ITEM_SEP) {
                "${it.workspaceUri}$SEP${it.folderUri}$SEP${it.folderName}$SEP${it.workspaceName}$SEP${it.categoryKey}"
            }
            if (current == null) p.remove(Keys.CURRENT_NOTE) else p[Keys.CURRENT_NOTE] = current
        }
    }

    // ---- appearance --------------------------------------------------------

    val themeMode: Flow<ThemeMode> = ds.data.map {
        runCatching { ThemeMode.valueOf(it[Keys.THEME_MODE] ?: "AUTOMATIC") }.getOrDefault(ThemeMode.AUTOMATIC)
    }
    val lightTheme: Flow<String> = ds.data.map { it[Keys.LIGHT_THEME] ?: "light-default" }
    val darkTheme: Flow<String> = ds.data.map { it[Keys.DARK_THEME] ?: "dark-default" }

    suspend fun setThemeMode(m: ThemeMode) = ds.edit { it[Keys.THEME_MODE] = m.name }
    suspend fun setLightTheme(id: String) = ds.edit { it[Keys.LIGHT_THEME] = id }
    suspend fun setDarkTheme(id: String) = ds.edit { it[Keys.DARK_THEME] = id }

    // ---- fonts -------------------------------------------------------------

    fun fontId(ctx: FontContext): Flow<String> = ds.data.map {
        it[if (ctx == FontContext.VIEW) Keys.FONT_VIEW else Keys.FONT_EDIT] ?: "default"
    }
    fun fontSize(ctx: FontContext): Flow<Float> = ds.data.map {
        it[if (ctx == FontContext.VIEW) Keys.SIZE_VIEW else Keys.SIZE_EDIT] ?: 16f
    }

    suspend fun setFontId(ctx: FontContext, id: String) = ds.edit {
        it[if (ctx == FontContext.VIEW) Keys.FONT_VIEW else Keys.FONT_EDIT] = id
    }
    suspend fun setFontSize(ctx: FontContext, size: Float) = ds.edit {
        it[if (ctx == FontContext.VIEW) Keys.SIZE_VIEW else Keys.SIZE_EDIT] = size
    }
}
