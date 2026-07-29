package qdvc.notetoself.android.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import qdvc.notetoself.android.app.model.Category
import qdvc.notetoself.android.app.model.ChatMessage
import qdvc.notetoself.android.app.model.Note
import qdvc.notetoself.android.app.model.NoteKind
import qdvc.notetoself.android.app.model.PayloadImage
import qdvc.notetoself.android.app.model.QuotedMessage
import qdvc.notetoself.android.app.model.Slug
import qdvc.notetoself.android.app.util.ReadmeFormat
import java.util.Date

/**
 * All Storage Access Framework access lives here (B3). A workspace is a granted tree URI; a
 * note is a folder inside it containing README.md and a payloads/ subfolder.
 */
class NoteRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    // ---- workspace helpers -------------------------------------------------

    fun takePersistablePermission(treeUri: Uri) {
        resolver.takePersistableUriPermission(
            treeUri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    fun workspaceName(treeUri: Uri): String =
        DocumentFile.fromTreeUri(context, treeUri)?.name ?: treeUri.lastPathSegment ?: "workspace"

    private fun treeRoot(workspaceUri: String): DocumentFile? =
        DocumentFile.fromTreeUri(context, Uri.parse(workspaceUri))

    // ---- listing -----------------------------------------------------------

    /** Cheap-ish scan of note folders (folder metadata only, no bodies). */
    suspend fun scanNoteFolders(workspaceUri: String): List<DocEntry> = withContext(Dispatchers.IO) {
        val root = treeRoot(workspaceUri) ?: return@withContext emptyList()
        root.listFiles()
            .filter { it.isDirectory && it.name != null }
            .map { DocEntry(it.uri.toString(), it.name!!, it.lastModified()) }
            .sortedByDescending { it.name }
    }

    data class DocEntry(val folderUri: String, val name: String, val lastModified: Long)

    // ---- read --------------------------------------------------------------

    suspend fun readNote(workspaceUri: String, folderUri: String): Note? =
        withContext(Dispatchers.IO) {
            val folderDoc = resolveFolder(workspaceUri, folderUri) ?: return@withContext null
            val name = folderDoc.name ?: return@withContext null
            val datePrefix = name.take(10)

            val readme = folderDoc.findFile("README.md")
            val md = readme?.let { readText(it.uri) } ?: ""
            val parsed = ReadmeFormat.parse(md, datePrefix)

            val payloadsDir = folderDoc.findFile("payloads")
            val diskImages = payloadsDir?.takeIf { it.isDirectory }?.listFiles()
                ?.filter { it.isFile }
                ?.associate { (it.name ?: "") to it.uri.toString() }
                ?: emptyMap()

            // Prefer images referenced by README; fall back to whatever is on disk.
            val referenced = parsed.imageFileNames.ifEmpty { diskImages.keys.toList() }
            val images = referenced.map { fn ->
                PayloadImage(fileName = fn, uri = diskImages[fn])
            }

            // Recorded millis: prefer the ISO stamp; else derive midnight from the folder date
            // prefix so older notes still sort/backdate sensibly.
            val millis = parsed.recordedAtMillis
                ?: runCatching { ReadmeFormat.parseDatePrefix(datePrefix) }.getOrNull()
                ?: folderDoc.lastModified()

            // Resolve image URIs for chat messages against the payloads dir.
            val messages = parsed.messages.map { m ->
                m.copy(imageUri = m.imageFileName?.let { diskImages[it] })
            }

            Note(
                workspaceUri = workspaceUri,
                folderDocId = DocumentsContract.getDocumentId(folderDoc.uri),
                folderUri = folderDoc.uri.toString(),
                folderName = name,
                title = parsed.title.ifBlank {
                    name.removePrefix("$datePrefix-").replace('-', ' ')
                },
                abstract = parsed.abstract,
                textPayload = parsed.textPayload,
                images = images,
                recordedAt = parsed.recordedAt.ifBlank { name },
                recordedAtMillis = millis,
                category = parsed.category,
                kind = parsed.kind,
                messages = messages,
                chatClosed = parsed.chatClosed,
            )
        }

    /**
     * Re-resolve a note folder within its granted tree. We locate it by matching document id
     * against the workspace's children, staying inside the granted tree (B3) rather than
     * fabricating a URI that carries no permission.
     */
    private fun resolveFolder(workspaceUri: String, folderUri: String): DocumentFile? {
        val root = treeRoot(workspaceUri) ?: return null
        val targetId = runCatching { DocumentsContract.getDocumentId(Uri.parse(folderUri)) }.getOrNull()
        return root.listFiles().firstOrNull { child ->
            child.isDirectory && (
                child.uri.toString() == folderUri ||
                    runCatching { DocumentsContract.getDocumentId(child.uri) == targetId }.getOrDefault(false)
                )
        }
    }

    private fun readText(uri: Uri): String =
        resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""

    // ---- create ------------------------------------------------------------

    /**
     * Creates a new note folder `<yyyy-MM-dd>-<slug>` with README.md and copies images into
     * payloads/. `imageSources` maps desired file name -> source content Uri to copy from.
     */
    suspend fun createNote(
        workspaceUri: String,
        title: String,
        abstract: String,
        textPayload: String,
        imageSources: List<PendingImage>,
        recordedAtMillis: Long,
        category: Category,
    ): Note? = withContext(Dispatchers.IO) {
        val root = treeRoot(workspaceUri) ?: return@withContext null
        val recordedDate = Date(recordedAtMillis)
        val datePrefix = ReadmeFormat.datePrefix(recordedDate)
        val baseName = Slug.folderName(datePrefix, title)
        val folderName = uniqueFolderName(root, baseName)

        val folder = root.createDirectory(folderName) ?: return@withContext null
        val recordedAt = ReadmeFormat.recordedStamp(recordedDate)

        val savedImages = copyImages(folder, imageSources)
        val md = ReadmeFormat.build(
            datePrefix, title, recordedAt, recordedAtMillis, category,
            abstract, textPayload, savedImages,
        )
        writeReadme(folder, md)

        Note(
            workspaceUri = workspaceUri,
            folderDocId = DocumentsContract.getDocumentId(folder.uri),
            folderUri = folder.uri.toString(),
            folderName = folderName,
            title = title,
            abstract = abstract,
            textPayload = textPayload,
            images = savedImages,
            recordedAt = recordedAt,
            recordedAtMillis = recordedAtMillis,
            category = category,
        )
    }

    data class PendingImage(val fileName: String, val sourceUri: String)

    // ---- update ------------------------------------------------------------

    /**
     * Updates a note. If the title changed the folder is renamed (date prefix preserved).
     * `keepImages` are existing file names to retain; `newImages` are copied in; images not in
     * either set are removed. README.md is regenerated.
     */
    suspend fun updateNote(
        note: Note,
        newTitle: String,
        newAbstract: String,
        newTextPayload: String,
        keepImages: List<String>,
        newImages: List<PendingImage>,
        newRecordedAtMillis: Long,
        newCategory: Category,
    ): Note? = withContext(Dispatchers.IO) {
        var folder = resolveFolder(note.workspaceUri, note.folderUri) ?: return@withContext null

        // Date prefix now derives from the (possibly backdated) recorded time.
        val recordedDate = Date(newRecordedAtMillis)
        val datePrefix = ReadmeFormat.datePrefix(recordedDate)
        val recordedAt = ReadmeFormat.recordedStamp(recordedDate)

        // Rename folder if the derived name (date prefix + title slug) changed.
        val desired = Slug.folderName(datePrefix, newTitle)
        val root = treeRoot(note.workspaceUri)
        if (desired != note.folderName && root != null) {
            val uniqueDesired = uniqueFolderName(root, desired)
            val renamed = try {
                DocumentsContract.renameDocument(resolver, folder.uri, uniqueDesired)
            } catch (_: Exception) { null }
            if (renamed != null) {
                // IMPORTANT: renameDocument returns a *single-document* URI. findFile/createFile
                // only work on a *tree*-backed DocumentFile, so re-resolve the folder by walking
                // the granted tree (matching document id, then name) instead of using
                // DocumentFile.fromSingleUri, which would make README/payload writes silently fail.
                folder = resolveFolder(note.workspaceUri, renamed.toString())
                    ?: root.listFiles().firstOrNull { it.isDirectory && it.name == uniqueDesired }
                    ?: folder
            }
        }

        // Reconcile payload images.
        val payloadsDir = folder.findFile("payloads")?.takeIf { it.isDirectory }
        val existing = payloadsDir?.listFiles()?.filter { it.isFile } ?: emptyList()
        val keepSet = keepImages.toSet()
        existing.forEach { f ->
            val n = f.name
            if (n != null && n !in keepSet) f.delete()
        }
        val kept = existing.mapNotNull { it.name }.filter { it in keepSet }
            .map { PayloadImage(it, payloadsDir?.findFile(it)?.uri?.toString()) }
        val added = copyImages(folder, newImages)
        val allImages = kept + added

        val md = ReadmeFormat.build(
            datePrefix, newTitle, recordedAt, newRecordedAtMillis, newCategory,
            newAbstract, newTextPayload, allImages,
        )
        writeReadme(folder, md)

        Note(
            workspaceUri = note.workspaceUri,
            folderDocId = DocumentsContract.getDocumentId(folder.uri),
            folderUri = folder.uri.toString(),
            folderName = folder.name ?: desired,
            title = newTitle,
            abstract = newAbstract,
            textPayload = newTextPayload,
            images = allImages,
            recordedAt = recordedAt,
            recordedAtMillis = newRecordedAtMillis,
            category = newCategory,
        )
    }

    suspend fun deleteNote(note: Note): Boolean = withContext(Dispatchers.IO) {
        resolveFolder(note.workspaceUri, note.folderUri)?.delete() ?: false
    }

    /**
     * Backfills the `Data-type:` tag on an existing note's README if missing, inserting it
     * immediately after the H1 heading and preserving the rest of the file verbatim. The kind is
     * inferred from the presence of chat message headings. Returns true if the file was rewritten.
     */
    suspend fun ensureDataTypeTag(workspaceUri: String, folderUri: String): Boolean =
        withContext(Dispatchers.IO) {
            val folder = resolveFolder(workspaceUri, folderUri) ?: return@withContext false
            val readme = folder.findFile("README.md") ?: return@withContext false
            val md = readText(readme.uri)
            if (md.isBlank()) return@withContext false
            val lines = md.replace("\r\n", "\n").split("\n")
            if (lines.any { it.trim().startsWith("Data-type:", ignoreCase = true) }) {
                return@withContext false // already tagged
            }
            val isChat = lines.any { Regex("""^##\s*\[[^\]]+]""").containsMatchIn(it) }
            val tag = if (isChat) "Data-type: chat" else "Data-type: classic-note"

            val h1Index = lines.indexOfFirst { it.startsWith("# ") }
            val out = StringBuilder()
            if (h1Index < 0) {
                // No heading: prepend the tag at the very top.
                out.append(tag).append("\n\n").append(md.trimEnd()).append("\n")
            } else {
                // Rebuild: everything up to and including the H1, then a blank line, the tag,
                // a blank line, then the remainder with any leading blank lines collapsed.
                val head = lines.take(h1Index + 1)
                val rest = lines.drop(h1Index + 1).dropWhile { it.isBlank() }
                head.forEach { out.append(it).append("\n") }
                out.append("\n").append(tag).append("\n\n")
                rest.forEach { out.append(it).append("\n") }
            }
            writeReadme(folder, out.toString().trimEnd() + "\n")
            true
        }

    // ---- chat --------------------------------------------------------------

    /** Creates an empty chat-kind note folder with a README containing only the header. */
    suspend fun createChat(
        workspaceUri: String,
        title: String,
        category: Category,
        now: Date = Date(),
    ): Note? = withContext(Dispatchers.IO) {
        val root = treeRoot(workspaceUri) ?: return@withContext null
        val datePrefix = ReadmeFormat.datePrefix(now)
        val folderName = uniqueFolderName(root, Slug.folderName(datePrefix, title))
        val folder = root.createDirectory(folderName) ?: return@withContext null

        val md = ReadmeFormat.buildChat(datePrefix, title, category, closed = false, messages = emptyList())
        writeReadme(folder, md)

        Note(
            workspaceUri = workspaceUri,
            folderDocId = DocumentsContract.getDocumentId(folder.uri),
            folderUri = folder.uri.toString(),
            folderName = folderName,
            title = title,
            abstract = "",
            textPayload = "",
            images = emptyList(),
            recordedAt = "",
            recordedAtMillis = now.time,
            category = category,
            kind = NoteKind.CHAT,
            messages = emptyList(),
            chatClosed = false,
        )
    }

    /**
     * Appends a message to a chat. If [imageSourceUri] is provided the image is copied into
     * payloads/ with a `yyyy-MM-dd_HHmm<slot>_` prefix. Returns the reloaded note.
     */
    suspend fun appendChatMessage(
        note: Note,
        personaKey: String,
        text: String,
        imageSourceUri: String?,
        imageDisplayName: String?,
        quoted: QuotedMessage? = null,
        now: Date = Date(),
    ): Note? = withContext(Dispatchers.IO) {
        val folder = resolveFolder(note.workspaceUri, note.folderUri) ?: return@withContext null

        var imageFileName: String? = null
        if (imageSourceUri != null) {
            imageFileName = copyChatImage(folder, imageSourceUri, imageDisplayName ?: "image", now)
        }

        val message = ChatMessage(
            timestampMillis = now.time,
            timestampDisplay = ReadmeFormat.messageStamp(now),
            personaKey = personaKey,
            text = text,
            imageFileName = imageFileName,
            quoted = quoted,
        )
        val newMessages = note.messages + message
        rewriteChat(folder, note, newMessages, note.chatClosed)
        readNote(note.workspaceUri, folder.uri.toString())
    }

    /** Replaces the text of the message at [index] (image unchanged). */
    suspend fun editChatMessage(note: Note, index: Int, newText: String): Note? =
        withContext(Dispatchers.IO) {
            val folder = resolveFolder(note.workspaceUri, note.folderUri) ?: return@withContext null
            if (index !in note.messages.indices) return@withContext note
            val updated = note.messages.toMutableList().also {
                it[index] = it[index].copy(text = newText)
            }
            rewriteChat(folder, note, updated, note.chatClosed)
            readNote(note.workspaceUri, folder.uri.toString())
        }

    suspend fun setChatClosed(note: Note, closed: Boolean): Note? = withContext(Dispatchers.IO) {
        val folder = resolveFolder(note.workspaceUri, note.folderUri) ?: return@withContext null
        rewriteChat(folder, note, note.messages, closed)
        readNote(note.workspaceUri, folder.uri.toString())
    }

    /** Changes a chat's title (renaming its folder if the slug changes) and/or category. */
    suspend fun updateChatMeta(note: Note, newTitle: String, newCategory: Category): Note? =
        withContext(Dispatchers.IO) {
            var folder = resolveFolder(note.workspaceUri, note.folderUri) ?: return@withContext null
            val datePrefix = note.folderName.take(10)

            val desired = Slug.folderName(datePrefix, newTitle)
            val root = treeRoot(note.workspaceUri)
            if (desired != note.folderName && root != null) {
                val uniqueDesired = uniqueFolderName(root, desired)
                val renamed = try {
                    DocumentsContract.renameDocument(resolver, folder.uri, uniqueDesired)
                } catch (_: Exception) { null }
                if (renamed != null) {
                    // Re-resolve through the tree (see updateNote for why fromSingleUri is unsafe).
                    folder = resolveFolder(note.workspaceUri, renamed.toString())
                        ?: root.listFiles().firstOrNull { it.isDirectory && it.name == uniqueDesired }
                        ?: folder
                }
            }

            val md = ReadmeFormat.buildChat(
                folder.name?.take(10) ?: datePrefix, newTitle, newCategory, note.chatClosed, note.messages,
            )
            writeReadme(folder, md)
            readNote(note.workspaceUri, folder.uri.toString())
        }

    private fun rewriteChat(
        folder: DocumentFile,
        note: Note,
        messages: List<ChatMessage>,
        closed: Boolean,
    ) {
        val datePrefix = note.folderName.take(10)
        val md = ReadmeFormat.buildChat(datePrefix, note.title, note.category, closed, messages)
        writeReadme(folder, md)
    }

    /** Copies a chat image into payloads/, applying the timestamp+slot prefix and returning its name. */
    private fun copyChatImage(
        folder: DocumentFile,
        sourceUri: String,
        displayName: String,
        now: Date,
    ): String? {
        val payloads = folder.findFile("payloads")?.takeIf { it.isDirectory }
            ?: folder.createDirectory("payloads") ?: return null
        // Find a free letter slot (a..z) for this minute so same-named images don't collide.
        var slot = 0
        var candidate: String
        do {
            candidate = ReadmeFormat.imagePrefix(now, slot) + displayName
            slot++
        } while (payloads.findFile(candidate) != null && slot < 26)
        return try {
            val mime = resolver.getType(Uri.parse(sourceUri)) ?: "application/octet-stream"
            val dest = payloads.createFile(mime, candidate) ?: return null
            resolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
                resolver.openOutputStream(dest.uri, "wt")?.use { output -> input.copyTo(output) }
            }
            dest.name ?: candidate
        } catch (_: Exception) { null }
    }

    // ---- internals ---------------------------------------------------------

    private fun uniqueFolderName(root: DocumentFile, base: String): String {
        if (root.findFile(base) == null) return base
        var i = 2
        while (root.findFile("$base-$i") != null) i++
        return "$base-$i"
    }

    private fun writeReadme(folder: DocumentFile, md: String) {
        val existing = folder.findFile("README.md")
        val target = existing ?: folder.createFile("text/markdown", "README.md")
        target ?: return
        resolver.openOutputStream(target.uri, "wt")?.use {
            it.write(md.toByteArray(Charsets.UTF_8))
        }
    }

    private fun copyImages(folder: DocumentFile, sources: List<PendingImage>): List<PayloadImage> {
        if (sources.isEmpty()) return emptyList()
        val payloads = folder.findFile("payloads")?.takeIf { it.isDirectory }
            ?: folder.createDirectory("payloads") ?: return emptyList()
        val out = mutableListOf<PayloadImage>()
        for (src in sources) {
            try {
                val mime = resolver.getType(Uri.parse(src.sourceUri)) ?: "application/octet-stream"
                val name = uniqueFileName(payloads, src.fileName)
                val dest = payloads.createFile(mime, name) ?: continue
                resolver.openInputStream(Uri.parse(src.sourceUri))?.use { input ->
                    resolver.openOutputStream(dest.uri, "wt")?.use { output ->
                        input.copyTo(output)
                    }
                }
                out.add(PayloadImage(dest.name ?: name, dest.uri.toString()))
            } catch (_: Exception) { /* skip unreadable source */ }
        }
        return out
    }

    private fun uniqueFileName(dir: DocumentFile, name: String): String {
        if (dir.findFile(name) == null) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 2
        while (dir.findFile("$stem-$i$ext") != null) i++
        return "$stem-$i$ext"
    }
}
