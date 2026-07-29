package qdvc.notetoself.android.app.util

import qdvc.notetoself.android.app.model.Category
import qdvc.notetoself.android.app.model.ChatMessage
import qdvc.notetoself.android.app.model.NoteKind
import qdvc.notetoself.android.app.model.PayloadImage
import qdvc.notetoself.android.app.model.QuotedMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serialises and parses the README.md that backs each note-to-self. Two shapes are supported:
 *
 * CLASSIC:
 *   # 2026-07-29 We should visit the zoo
 *
 *   Recorded Wed 29 Jul 2026 15:34:39 AWST (2026-07-29T15:34:39+0800)
 *
 *   Category: action-required
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
 *
 * CHAT (no "Recorded" line; each message is a level-2 heading with a timestamp + persona):
 *   # 2026-07-29 Project kickoff
 *
 *   Category: meeting-notes
 *
 *   Status: closed
 *
 *   ## [Wed 29 Jul 2026 20:32:03 AWST] Note Taker
 *
 *   Message text here.
 *
 *   ## [Wed 29 Jul 2026 20:33:10 AWST] Peer Reviewer
 *
 *   [Image](payloads/2026-07-29_2033a_IMG_4123.HEIC)
 */
object ReadmeFormat {

    private const val ISO = "yyyy-MM-dd'T'HH:mm:ssZ"

    fun datePrefix(date: Date = Date()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

    fun recordedStamp(date: Date = Date()): String =
        SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss zzz", Locale.US).format(date)

    private fun isoStamp(date: Date): String = SimpleDateFormat(ISO, Locale.US).format(date)

    fun parseDatePrefix(datePrefix: String): Long? =
        runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(datePrefix)?.time }.getOrNull()

    private fun heading(datePrefix: String, title: String) = "$datePrefix ${title.trim()}"

    // ----- classic ----------------------------------------------------------

    fun build(
        datePrefix: String,
        title: String,
        recordedAt: String,
        recordedAtMillis: Long,
        category: Category,
        abstract: String,
        textPayload: String,
        images: List<PayloadImage>,
    ): String {
        val sb = StringBuilder()
        sb.append("# ").append(heading(datePrefix, title)).append("\n\n")
        sb.append("Data-type: classic-note\n\n")
        sb.append("Recorded ").append(recordedAt)
            .append(" (").append(isoStamp(Date(recordedAtMillis))).append(")").append("\n\n")
        if (category != Category.NONE) {
            sb.append("Category: ").append(category.key).append("\n\n")
        }
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

    // ----- chat -------------------------------------------------------------

    /** Chat message timestamp heading label, e.g. "Wed 29 Jul 2026 20:32:03 AWST". */
    fun messageStamp(date: Date = Date()): String =
        SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss zzz", Locale.US).format(date)

    /**
     * Image filename prefix guarding against duplicate names within one chat:
     * yyyy-MM-dd_HHmm followed by a letter slot a..z (26 slots per minute).
     */
    fun imagePrefix(date: Date, slotIndex: Int): String {
        val base = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(date)
        val letter = ('a' + (slotIndex.coerceIn(0, 25)))
        return "${base}${letter}_"
    }

    fun buildChat(
        datePrefix: String,
        title: String,
        category: Category,
        closed: Boolean,
        messages: List<ChatMessage>,
    ): String {
        val sb = StringBuilder()
        sb.append("# ").append(heading(datePrefix, title)).append("\n\n")
        sb.append("Data-type: chat\n\n")
        if (category != Category.NONE) {
            sb.append("Category: ").append(category.key).append("\n\n")
        }
        if (closed) sb.append("Status: closed\n\n")
        for (m in messages) {
            sb.append("## [").append(m.timestampDisplay).append("] ").append(m.personaKey).append("\n\n")
            // Quoted reply: a Markdown blockquote wrapping the quoted message's heading + text.
            val q = m.quoted
            if (q != null) {
                sb.append("> ## [").append(q.timestampDisplay).append("] ").append(q.personaKey).append("\n")
                sb.append("> \n")
                q.text.trim().split("\n").forEach { line ->
                    sb.append("> ").append(line).append("\n")
                }
                sb.append("\n")
            }
            val body = m.text.trim()
            if (body.isNotEmpty()) sb.append(body).append("\n\n")
            if (m.imageFileName != null) {
                sb.append("[Image](payloads/").append(m.imageFileName).append(")\n\n")
            }
        }
        return sb.toString().trimEnd() + "\n"
    }

    /** True if any line of [text] begins with '#' (would corrupt the chat's heading structure). */
    fun hasHashLine(text: String): Boolean =
        text.replace("\r\n", "\n").split("\n").any { it.trimStart().startsWith("#") }

    // ----- parse ------------------------------------------------------------

    data class Parsed(
        val kind: NoteKind,
        val title: String,
        val recordedAt: String,
        val recordedAtMillis: Long?,
        val category: Category,
        val abstract: String,
        val textPayload: String,
        val imageFileNames: List<String>,
        val messages: List<ChatMessage>,
        val chatClosed: Boolean,
    )

    private val imgRef = Regex("""\[([^\]]+)]\(payloads/([^)]+)\)""")
    private val isoInParens = Regex("""\(([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:]+[+\-][0-9]{4})\)""")
    // Matches "## [Wed 29 Jul 2026 20:32:03 AWST] Note Taker"
    private val chatHeading = Regex("""^##\s*\[([^\]]+)]\s*(.*)$""")

    fun parse(md: String, datePrefix: String): Parsed {
        val lines = md.replace("\r\n", "\n").split("\n")
        // The Data-type tag (if present) is authoritative; else fall back to detecting chat
        // message headings; else classic.
        val declared = lines.firstOrNull { it.trim().startsWith("Data-type:", ignoreCase = true) }
            ?.substringAfter(":")?.trim()?.lowercase()
        val isChat = when (declared) {
            "chat" -> true
            "classic-note" -> false
            else -> lines.any { chatHeading.matches(it) }
        }
        return if (isChat) parseChat(lines, datePrefix) else parseClassic(lines, datePrefix)
    }

    private fun parseClassic(lines: List<String>, datePrefix: String): Parsed {
        var title = ""
        var recordedAt = ""
        var recordedMillis: Long? = null
        var category = Category.NONE
        val abstractLines = mutableListOf<String>()
        val payloadLines = mutableListOf<String>()
        val images = mutableListOf<String>()

        var section = 0 // 0 none, 1 abstract, 2 payload
        for (line in lines) {
            when {
                line.startsWith("# ") -> {
                    var h = line.removePrefix("# ").trim()
                    if (h.startsWith("$datePrefix ")) h = h.removePrefix("$datePrefix ").trim()
                    title = h
                }
                line.startsWith("Recorded ") && recordedAt.isEmpty() -> {
                    var value = line.removePrefix("Recorded ").trim()
                    val iso = isoInParens.find(value)
                    if (iso != null) {
                        recordedMillis = runCatching {
                            SimpleDateFormat(ISO, Locale.US).parse(iso.groupValues[1])?.time
                        }.getOrNull()
                        value = value.replace(iso.value, "").trim()
                    }
                    recordedAt = value
                }
                line.startsWith("Category:") ->
                    category = Category.fromKey(line.removePrefix("Category:").trim())
                line.trim().equals("## Abstract", true) -> section = 1
                line.trim().equals("## Payload", true) -> section = 2
                line.startsWith("Attached images:") ->
                    imgRef.findAll(line).forEach { images.add(it.groupValues[1]) }
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
            kind = NoteKind.CLASSIC,
            title = title,
            recordedAt = recordedAt,
            recordedAtMillis = recordedMillis,
            category = category,
            abstract = abs,
            textPayload = payload,
            imageFileNames = images,
            messages = emptyList(),
            chatClosed = false,
        )
    }

    private fun parseChat(lines: List<String>, datePrefix: String): Parsed {
        var title = ""
        var category = Category.NONE
        var closed = false
        val messages = mutableListOf<ChatMessage>()

        var curStamp: String? = null
        var curPersona = ""
        val curBody = mutableListOf<String>()
        var curImage: String? = null
        // Quote accumulation for the current message.
        val quoteLines = mutableListOf<String>()

        fun buildQuote(): QuotedMessage? {
            if (quoteLines.isEmpty()) return null
            // First quoted line is the heading "## [stamp] Persona"; the rest (after a blank) is text.
            var qStamp = ""
            var qPersona = ""
            val qText = mutableListOf<String>()
            for (ql in quoteLines) {
                val h = Regex("""^##\s*\[([^\]]+)]\s*(.*)$""").matchEntire(ql)
                if (h != null && qStamp.isEmpty()) {
                    qStamp = h.groupValues[1].trim()
                    qPersona = h.groupValues[2].trim()
                } else {
                    qText.add(ql)
                }
            }
            val text = qText.joinToString("\n").trim()
            return QuotedMessage(qStamp, qPersona.ifBlank { "Note Taker" }, text)
        }

        fun flush() {
            val stamp = curStamp ?: return
            val text = curBody.joinToString("\n").trim()
            messages.add(
                ChatMessage(
                    timestampMillis = 0L, // display-only; recomputed on load by repository if needed
                    timestampDisplay = stamp,
                    personaKey = curPersona.trim().ifBlank { "Note Taker" },
                    text = text,
                    imageFileName = curImage,
                    quoted = buildQuote(),
                )
            )
            curBody.clear()
            curImage = null
            quoteLines.clear()
        }

        for (line in lines) {
            val m = chatHeading.matchEntire(line)
            when {
                m != null -> {
                    flush()
                    curStamp = m.groupValues[1].trim()
                    curPersona = m.groupValues[2].trim()
                }
                line.startsWith("# ") && curStamp == null -> {
                    var h = line.removePrefix("# ").trim()
                    if (h.startsWith("$datePrefix ")) h = h.removePrefix("$datePrefix ").trim()
                    title = h
                }
                line.startsWith("Category:") && curStamp == null ->
                    category = Category.fromKey(line.removePrefix("Category:").trim())
                line.startsWith("Status:") && curStamp == null ->
                    closed = line.removePrefix("Status:").trim().equals("closed", true)
                curStamp != null && line.trimStart().startsWith(">") -> {
                    // Blockquote line belonging to a quoted reply.
                    val stripped = line.trimStart().removePrefix(">").let {
                        if (it.startsWith(" ")) it.substring(1) else it
                    }
                    if (stripped.isNotBlank()) quoteLines.add(stripped)
                }
                curStamp != null -> {
                    val img = imgRef.find(line)
                    if (img != null) curImage = img.groupValues[2] else curBody.add(line)
                }
            }
        }
        flush()

        return Parsed(
            kind = NoteKind.CHAT,
            title = title,
            recordedAt = "",
            recordedAtMillis = null,
            category = category,
            abstract = "",
            textPayload = "",
            imageFileNames = messages.mapNotNull { it.imageFileName },
            messages = messages,
            chatClosed = closed,
        )
    }
}
