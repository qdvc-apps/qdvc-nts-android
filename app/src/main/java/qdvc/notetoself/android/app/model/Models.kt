package qdvc.notetoself.android.app.model

/** Bottom-bar tabs. Item1=Home, Item2=View, Item3=Edit, Item4=Switcher. */
enum class Tab { HOME, VIEW, EDIT, SWITCHER }

/** Levels of the Item-1 home hierarchy. Ordinal encodes depth for slide animation. */
enum class BrowseMode { WORKSPACES, OVERVIEW, ALL_NOTES, SEARCH, INDEX_STATUS }

enum class ThemeMode { AUTOMATIC, LIGHT, DARK }

/** A user-granted workspace folder. */
data class Workspace(
    val uri: String,
    val name: String,
)

/** A single payload attachment reference stored inside the note folder's payloads/ dir. */
data class PayloadImage(
    val fileName: String,
    /** Content URI (as string) to the copied image inside the note folder, if resolved. */
    val uri: String? = null,
)

/**
 * A Note-to-Self. Backed by a folder `<yyyy-MM-dd>-<slug>` containing README.md and payloads/.
 */
data class Note(
    val workspaceUri: String,
    /** SAF document id of the note's folder within the workspace tree. */
    val folderDocId: String,
    /** SAF document uri (string) of the note folder. */
    val folderUri: String,
    val folderName: String,
    val title: String,
    val abstract: String,
    /** Free-form text payload (copypaste, URLs, etc). */
    val textPayload: String,
    val images: List<PayloadImage>,
    /** Timestamp line recorded in the README, e.g. "Wed 29 Jul 2026 15:34:39 AWST". */
    val recordedAt: String,
) {
    val displayTitle: String get() = title.ifBlank { folderName }
}

/** A theme spec loaded from the themes asset folder. */
data class ThemeSpec(
    val id: String,
    val name: String,
    val dark: Boolean,
    val colors: Map<String, String>,
)

/** An open item in the multitasking switcher. Identity only; body re-read from disk. */
data class OpenNote(
    val workspaceUri: String,
    val folderUri: String,
    val folderName: String,
    val workspaceName: String,
)

/** Which text surface a font/size setting applies to. */
enum class FontContext { VIEW, EDIT }

/** Draft used by the Edit surface (both new and existing notes). */
data class EditDraft(
    val isNew: Boolean = true,
    val note: Note? = null,
    val title: String = "",
    val abstract: String = "",
    val textPayload: String = "",
    /** Existing image file names to keep. */
    val keepImages: List<String> = emptyList(),
    /** Newly picked images to copy: (displayName, sourceUri). */
    val newImages: List<Pair<String, String>> = emptyList(),
) {
    fun matchesSaved(): Boolean {
        val n = note ?: return title.isBlank() && abstract.isBlank() &&
            textPayload.isBlank() && newImages.isEmpty()
        return title == n.title && abstract == n.abstract && textPayload == n.textPayload &&
            newImages.isEmpty() && keepImages.toSet() == n.images.map { it.fileName }.toSet()
    }
}

object Slug {
    private val nonWord = Regex("[^a-z0-9]+")
    private val edges = Regex("(^-+)|(-+$)")

    /** "We should visit the zoo!" -> "we-should-visit-the-zoo" */
    fun of(title: String): String {
        val s = title.lowercase().replace(nonWord, "-").replace(edges, "")
        return s.ifBlank { "untitled" }
    }

    /** Folder name from date + title, e.g. 2026-07-29-we-should-visit-the-zoo */
    fun folderName(datePrefix: String, title: String): String = "$datePrefix-${of(title)}"
}
