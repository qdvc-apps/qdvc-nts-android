package qdvc.notetoself.android.app.model

/** Bottom-bar tabs. Item1=Home, Item2=View, Item3=Jump (switcher), Item4=New (action).
 *  EDIT is an internal surface reached from View's pencil, not a bottom-bar item. */
enum class Tab { HOME, VIEW, EDIT, SWITCHER, NEW }

/** Levels of the Item-1 home hierarchy. Home opens straight to the note list; Search and
 *  Index status are sub-levels reached from the toolbar menu. Ordinal encodes depth. */
enum class BrowseMode { NOTES, SEARCH, INDEX_STATUS }

enum class ThemeMode { AUTOMATIC, LIGHT, DARK }

/** Whether a note is the classic single-body form or a chat thread. */
enum class NoteKind { CLASSIC, CHAT }

/**
 * A chat persona. [key] is the stable display name stored in the README level-2 headings;
 * [initials] is the WhatsApp-style avatar placeholder; [colorHex] tints the persona's bubbles,
 * avatar circle, and name label (a lighter/darker variation is derived as needed).
 */
enum class Persona(val key: String, val initials: String, val colorHex: String) {
    NOTE_TAKER("Note Taker", "NT", "#3B6EA5"),   // muted blue
    PEER_REVIEWER("Peer Reviewer", "PR", "#8A5A9B"); // muted purple

    companion object {
        fun fromKey(key: String?): Persona =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: NOTE_TAKER
    }
}

/** A reference to the message a reply is quoting (rendered as a WhatsApp-style quote block). */
data class QuotedMessage(
    val timestampDisplay: String,
    val personaKey: String,
    val text: String,
)

/** A single chat message within a chat-kind note. */
data class ChatMessage(
    /** Epoch millis of when the message was recorded (drives the [timestampDisplay]). */
    val timestampMillis: Long,
    /** Human-readable stamp stored in the heading, e.g. "Wed 29 Jul 2026 20:32:03 AWST". */
    val timestampDisplay: String,
    /** Persona display name, e.g. "Note Taker". */
    val personaKey: String,
    val text: String,
    /** Optional attached image file name inside payloads/ (already prefixed). */
    val imageFileName: String? = null,
    /** Resolved content URI for the image, if available. */
    val imageUri: String? = null,
    /** Optional quoted message this one is replying to. */
    val quoted: QuotedMessage? = null,
)

/**
 * Optional category a note can be tagged against. [none] means untagged. The [emoji] doubles as
 * the note's list/detail icon, and [label] is the stored/displayed name. [key] is the stable
 * token persisted in the README frontmatter (emoji-independent so it survives font changes).
 */
enum class Category(val key: String, val emoji: String, val label: String) {
    NONE("none", "", "Uncategorised"),
    ACTION_REQUIRED("action-required", "\u26A0\uFE0F", "Action required"),
    IDEAS_PLANNING("ideas-planning", "\uD83D\uDCD8", "Ideas and planning"),
    MEETING_NOTES("meeting-notes", "\u260E\uFE0F", "Meeting notes"),
    USEFUL_ARTICLE("useful-article", "\uD83D\uDCD7", "Useful article");

    companion object {
        fun fromKey(key: String?): Category =
            entries.firstOrNull { it.key == key?.trim()?.lowercase() } ?: NONE

        /** Selectable categories (excludes NONE for the "clear" case handled separately). */
        val selectable: List<Category> get() = entries.filter { it != NONE }
    }
}

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
    /** Machine-readable epoch millis for the recorded time (drives the folder date + backdating). */
    val recordedAtMillis: Long,
    /** Optional category tag; NONE when untagged. */
    val category: Category = Category.NONE,
    /** Classic note or chat thread. */
    val kind: NoteKind = NoteKind.CLASSIC,
    /** Chat messages (chat kind only). */
    val messages: List<ChatMessage> = emptyList(),
    /** Whether the chat is closed to new messages (chat kind only). */
    val chatClosed: Boolean = false,
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
    /** Stable category key for the switcher icon ("none" when untagged). */
    val categoryKey: String = "none",
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
    /** Recorded time in epoch millis. Defaults to now for a new note; editable (backdating). */
    val recordedAtMillis: Long = System.currentTimeMillis(),
    /** Category tag for the note. */
    val category: Category = Category.NONE,
) {
    fun matchesSaved(): Boolean {
        val n = note ?: return title.isBlank() && abstract.isBlank() &&
            textPayload.isBlank() && newImages.isEmpty() && category == Category.NONE
        return title == n.title && abstract == n.abstract && textPayload == n.textPayload &&
            newImages.isEmpty() && keepImages.toSet() == n.images.map { it.fileName }.toSet() &&
            recordedAtMillis == n.recordedAtMillis && category == n.category
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
