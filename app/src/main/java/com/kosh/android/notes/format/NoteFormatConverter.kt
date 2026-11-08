package com.kosh.android.notes.format

/** Converts a note (Markdown source plus a title) to the export formats. Pure Kotlin, no Android types. */
object NoteFormatConverter {

    /** Markdown export: the title becomes a top-level heading unless the body already starts with it. */
    fun toMarkdown(title: String, content: String): String {
        val body = normalizeNewlines(NoteImageTokens.stripImages(content)).trimEnd('\n')
        val t = cleanTitle(title)
        val firstLine = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        val titleAlreadyInBody = t.isNotEmpty() && firstLine == "# $t"
        return buildString {
            if (t.isNotEmpty() && !titleAlreadyInBody) {
                append("# ").append(t).append("\n")
                if (body.isNotEmpty()) append("\n")
            }
            if (body.isNotEmpty()) append(body).append("\n")
        }
    }

    /** Plain-text export: no Markdown syntax, lists as simple markers, checklists as `[ ]` / `[x]`. */
    fun toPlainText(title: String, content: String): String {
        val body = bodyAsPlainText(content).trimEnd('\n')
        val t = cleanTitle(title)
        return buildString {
            if (t.isNotEmpty()) {
                append(t).append("\n")
                if (body.isNotEmpty()) append("\n")
            }
            if (body.isNotEmpty()) append(body).append("\n")
        }
    }

    fun bodyAsPlainText(content: String): String =
        parseMarkdown(NoteImageTokens.stripImages(content)).joinToString("\n") { block ->
            when (block) {
                is MdBlank -> ""
                is MdHeading -> plain(block.spans)
                is MdParagraph -> plain(block.spans)
                is MdBullet -> "  ".repeat(block.level) + "- " + plain(block.spans)
                is MdNumbered -> "  ".repeat(block.level) + "${block.number}. " + plain(block.spans)
                is MdChecklist -> "  ".repeat(block.level) + (if (block.checked) "[x] " else "[ ] ") + plain(block.spans)
            }
        }

    /** One short line for list cards: plain text, blank lines dropped, cut at [maxLength]. */
    fun preview(content: String, maxLength: Int = 160): String {
        val joined = bodyAsPlainText(content).lineSequence().filter { it.isNotBlank() }.joinToString("  ") { it.trim() }
        return if (joined.length <= maxLength) joined else joined.take(maxLength).trimEnd() + "…"
    }

    private fun plain(spans: List<Span>) = spans.joinToString("") { it.text }

    private fun cleanTitle(title: String) = title.replace(Regex("\\s+"), " ").trim()
}
