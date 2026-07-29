package qdvc.notetoself.android.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import qdvc.notetoself.android.app.model.Note
import qdvc.notetoself.android.app.model.PayloadImage
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
        now: Date = Date(),
    ): Note? = withContext(Dispatchers.IO) {
        val root = treeRoot(workspaceUri) ?: return@withContext null
        val datePrefix = ReadmeFormat.datePrefix(now)
        val baseName = Slug.folderName(datePrefix, title)
        val folderName = uniqueFolderName(root, baseName)

        val folder = root.createDirectory(folderName) ?: return@withContext null
        val recordedAt = ReadmeFormat.recordedStamp(now)

        val savedImages = copyImages(folder, imageSources)
        val md = ReadmeFormat.build(
            datePrefix, title, recordedAt, abstract, textPayload, savedImages,
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
    ): Note? = withContext(Dispatchers.IO) {
        var folder = resolveFolder(note.workspaceUri, note.folderUri) ?: return@withContext null
        val datePrefix = note.folderName.take(10)

        // Rename folder if title changed.
        val desired = Slug.folderName(datePrefix, newTitle)
        if (desired != note.folderName) {
            val renamed = try {
                DocumentsContract.renameDocument(resolver, folder.uri, desired)
            } catch (_: Exception) { null }
            if (renamed != null) {
                folder = DocumentFile.fromSingleUri(context, renamed) ?: folder
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
            datePrefix,
            newTitle,
            note.recordedAt.ifBlank { ReadmeFormat.recordedStamp() },
            newAbstract,
            newTextPayload,
            allImages,
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
            recordedAt = note.recordedAt,
        )
    }

    suspend fun deleteNote(note: Note): Boolean = withContext(Dispatchers.IO) {
        resolveFolder(note.workspaceUri, note.folderUri)?.delete() ?: false
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
