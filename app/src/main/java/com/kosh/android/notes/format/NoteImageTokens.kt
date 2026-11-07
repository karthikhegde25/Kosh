package com.kosh.android.notes.format

/**
 * Images inside a note are stored in the Markdown text as a line of their own: `![image](kosh-image:<file name>)`.
 * That line is where the picture shows up, so an image can sit anywhere between paragraphs. Pure Kotlin (no Android types).
 *
 * Canonical form: every image line has a text line (possibly empty) above and below it, so the text between images is
 * always an editable piece of its own.
 */
sealed interface BodyPiece

/** A run of text lines. [start] is where it begins in the note text; [firstLine] is the index of its first line. */
data class TextPiece(val text: String, val start: Int, val firstLine: Int) : BodyPiece {
    val end: Int get() = start + text.length
}

data class ImagePiece(val name: String, val start: Int, val end: Int) : BodyPiece

object NoteImageTokens {

    private val LINE = Regex("""^!\[[^\]]*]\(kosh-image:([A-Za-z0-9._-]+)\)$""")

    fun token(name: String): String = "![image](kosh-image:$name)"

    /** The file name if [line] is exactly an image line, otherwise null. */
    fun nameOf(line: String): String? = LINE.matchEntire(line.trimEnd('\r'))?.groupValues?.get(1)

    fun referencedNames(content: String): Set<String> =
        content.split('\n').mapNotNull { nameOf(it) }.toSet()

    fun hasImages(content: String): Boolean = content.contains("kosh-image:") && referencedNames(content).isNotEmpty()

    /** Makes sure each image line has a text line above and below it. Text without images is returned unchanged. */
    fun normalize(content: String): String {
        if (!hasImages(content)) return content
        val out = ArrayList<String>()
        for (line in content.split('\n')) {
            if (nameOf(line) != null && (out.isEmpty() || nameOf(out.last()) != null)) out += ""
            out += line
        }
        if (nameOf(out.last()) != null) out += ""
        return out.joinToString("\n")
    }

    /** Splits the (normalized) text into editable text pieces and image pieces, in reading order. */
    fun split(content: String): List<BodyPiece> {
        val pieces = ArrayList<BodyPiece>()
        val run = ArrayList<String>()
        var runStart = 0
        var runFirstLine = 0
        var offset = 0
        var lineIndex = 0

        fun flush(at: Int) {
            pieces += TextPiece(run.joinToString("\n"), if (run.isEmpty()) at else runStart, if (run.isEmpty()) lineIndex else runFirstLine)
            run.clear()
        }

        for (line in content.split('\n')) {
            val name = nameOf(line)
            if (name != null) {
                flush(offset)
                pieces += ImagePiece(name, offset, offset + line.length)
            } else {
                if (run.isEmpty()) { runStart = offset; runFirstLine = lineIndex }
                run += line
            }
            offset += line.length + 1
            lineIndex++
        }
        flush(content.length)
        return pieces
    }

    /**
     * Puts an image on its own line at [offset] (the caret), splitting the line there. Returns the new text and the caret
     * position just after the image, at the start of the text that follows it.
     */
    fun insertAt(content: String, offset: Int, name: String): Pair<String, Int> {
        val at = offset.coerceIn(0, content.length)
        val before = content.substring(0, at)
        val after = content.substring(at)
        val sepBefore = if (before.isEmpty() || !before.endsWith("\n")) "\n" else ""
        val sepAfter = if (after.startsWith("\n")) "" else "\n"
        val raw = before + sepBefore + token(name) + sepAfter + after
        val normalized = normalize(raw)
        val caret = normalized.indexOf(token(name)) + token(name).length + 1
        return normalized to caret.coerceAtMost(normalized.length)
    }

    /** Removes the image line (and so the picture) from the text. */
    fun removeImage(content: String, name: String): String {
        val lines = content.split('\n').toMutableList()
        val i = lines.indexOfFirst { nameOf(it) == name }
        if (i < 0) return content
        lines.removeAt(i)
        return normalize(lines.joinToString("\n"))
    }

    /** Adds image lines at the very top for files that no text line refers to yet (images from before inline pictures). */
    fun adoptUnreferenced(content: String, fileNames: List<String>): String {
        val referenced = referencedNames(content)
        val missing = fileNames.filter { it !in referenced }
        if (missing.isEmpty()) return normalize(content)
        val head = missing.joinToString("\n") { token(it) }
        return normalize(if (content.isEmpty()) "$head\n" else "$head\n$content")
    }

    /** The text without image lines, for Markdown / plain text export and list previews. */
    fun stripImages(content: String): String {
        if (!hasImages(content)) return content
        val lines = content.split('\n')
        val out = ArrayList<String>()
        for ((i, line) in lines.withIndex()) {
            if (nameOf(line) == null) { out += line; continue }
            // Drop the blank line that only existed to keep the picture separate, so exports don't gain gaps.
            val prevBlank = out.lastOrNull()?.isBlank() == true
            val nextBlank = lines.getOrNull(i + 1)?.isBlank() == true
            if (prevBlank && nextBlank && out.size > 1) out.removeAt(out.lastIndex)
        }
        return out.joinToString("\n")
    }
}
