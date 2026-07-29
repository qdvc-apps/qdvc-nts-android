package qdvc.notetoself.android.app.util

import qdvc.notetoself.android.app.model.PayloadImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serialises and parses the README.md that backs each note-to-self.
 *
 * Layout:
 *   # 2026-07-29 We should visit the zoo
 *
 *   Recorded Wed 29 Jul 2026 15:34:39 AWST
 *
 *   ## Abstract
 *
 *   <abstract text>
 *
 *   ## Payload
 *
 *   <text payload>
 *
 *   Attached images: [name.png](payloads/name.png); [b.heic](payloads/b.heic)
 */
object ReadmeFormat {

    fun datePrefix(date: Date = Date()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

    fun recordedStamp(date: Date = Date()): String =
        // e.g. "Wed 29 Jul 2026 15:34:39 AWST"
        SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss zzz", Locale.US).format(date)

    /** Heading text after the "yyyy-MM-dd " prefix. */
    private fun heading(datePrefix: String, title: String) = "$datePrefix ${title.trim()}"

    fun build(
        datePrefix: String,
        title: String,
        recordedAt: String,
        abstract: String,
        textPayload: String,
        images: List<PayloadImage>,
    ): String {
        val sb = StringBuilder()
        sb.append("# ").append(heading(datePrefix, title)).append("\n\n")
        sb.append("Recorded ").append(recordedAt).append("\n\n")
        sb.append("## Abstract\n\n")
        sb.append(abstract.trim().ifBlank { "_(no abstract)_" }).append("\n\n")
        sb.append("## Payload\n\n")
        val body = textPayload.trim()
        if (body.isNotEmpty()) sb.append(body).append("\n\n")
        if (images.isNotEmpty()) {
            val parts = images.joinToString("; ") { "[${it.fileName}](payloads/${it.fileName})" }
            sb.append("Attached images: ").append(parts).append("\n")
        }
        return sb.toString().trimEnd() + "\n"
    }

    data class Parsed(
        val title: String,
        val recordedAt: String,
        val abstract: String,
        val textPayload: String,
        val imageFileNames: List<String>,
    )

    private val imgRef = Regex("""\[([^\]]+)]\(payloads/[^)]+\)""")

    /** Best-effort parse; tolerant of hand edits. `datePrefix` strips the leading date from the H1. */
    fun parse(md: String, datePrefix: String): Parsed {
        val lines = md.replace("\r\n", "\n").split("\n")
        var title = ""
        var recordedAt = ""
        val abstractLines = mutableListOf<String>()
        val payloadLines = mutableListOf<String>()
        val images = mutableListOf<String>()

        var section = 0 // 0 none, 1 abstract, 2 payload
        for (raw in lines) {
            val line = raw
            when {
                line.startsWith("# ") -> {
                    var h = line.removePrefix("# ").trim()
                    if (h.startsWith("$datePrefix ")) h = h.removePrefix("$datePrefix ").trim()
                    title = h
                }
                line.startsWith("Recorded ") && recordedAt.isEmpty() ->
                    recordedAt = line.removePrefix("Recorded ").trim()
                line.trim().equals("## Abstract", true) -> section = 1
                line.trim().equals("## Payload", true) -> section = 2
                line.startsWith("Attached images:") -> {
                    imgRef.findAll(line).forEach { images.add(it.groupValues[1]) }
                }
                else -> when (section) {
                    1 -> abstractLines.add(line)
                    2 -> payloadLines.add(line)
                }
            }
        }

        var abs = abstractLines.joinToString("\n").trim()
        if (abs == "_(no abstract)_") abs = ""
        val payload = payloadLines.joinToString("\n").trim()

        return Parsed(
            title = title,
            recordedAt = recordedAt,
            abstract = abs,
            textPayload = payload,
            imageFileNames = images,
        )
    }
}
