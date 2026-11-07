package com.kosh.android.notes.format

enum class StyleKind { MARKER, BOLD, ITALIC, STRIKE, CODE, HEADING1, HEADING2, HEADING3 }

/** A styled stretch of the note text: [start] inclusive, [end] exclusive. */
data class StyleRange(val start: Int, val end: Int, val kind: StyleKind)

private val HEADING = Regex("^(#{1,6})( +)(.*)$")
private val BOLD = Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*")
private val STRIKE = Regex("~~(?=\\S)(.+?)(?<=\\S)~~")
private val CODE = Regex("`([^`\\n]+)`")
private val ITALIC = Regex("(?<![*\\\\])\\*(?![*\\s])(.+?)(?<![*\\s])\\*(?!\\*)")

/**
 * Which parts of the text the editor should draw bold, italic, struck through, as code or as a heading, as they are typed.
 * The Markdown characters themselves stay in the text (so nothing about the saved note changes); they are only marked as
 * MARKER so the editor can show them faintly. Only the ranges are computed here, so this can be tested without Compose.
 */
fun editorStyles(text: String): List<StyleRange> {
    val out = ArrayList<StyleRange>()
    var lineStart = 0
    while (lineStart <= text.length) {
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        // Code first, so that stars inside `code` are not read as bold or italic.
        val codeRanges = CODE.findAll(line).map { it.range }.toList()
        fun free(r: IntRange) = codeRanges.none { c -> r.first <= c.last && c.first <= r.last }

        HEADING.find(line)?.let { m ->
            val level = m.groupValues[1].length
            val markerEnd = m.groupValues[1].length + m.groupValues[2].length
            val kind = when (level) { 1 -> StyleKind.HEADING1; 2 -> StyleKind.HEADING2; else -> StyleKind.HEADING3 }
            out += StyleRange(lineStart, lineStart + line.length, kind)
            out += StyleRange(lineStart, lineStart + markerEnd, StyleKind.MARKER)
        }
        for (c in codeRanges) {
            out += StyleRange(lineStart + c.first, lineStart + c.last + 1, StyleKind.CODE)
            out += StyleRange(lineStart + c.first, lineStart + c.first + 1, StyleKind.MARKER)
            out += StyleRange(lineStart + c.last, lineStart + c.last + 1, StyleKind.MARKER)
        }
        fun pairs(regex: Regex, marker: Int, kind: StyleKind) {
            for (m in regex.findAll(line)) {
                if (!free(m.range)) continue
                out += StyleRange(lineStart + m.range.first + marker, lineStart + m.range.last + 1 - marker, kind)
                out += StyleRange(lineStart + m.range.first, lineStart + m.range.first + marker, StyleKind.MARKER)
                out += StyleRange(lineStart + m.range.last + 1 - marker, lineStart + m.range.last + 1, StyleKind.MARKER)
            }
        }
        pairs(BOLD, 2, StyleKind.BOLD)
        pairs(STRIKE, 2, StyleKind.STRIKE)
        pairs(ITALIC, 1, StyleKind.ITALIC)

        if (lineEnd >= text.length) break
        lineStart = lineEnd + 1
    }
    return out.filter { it.end > it.start }
}
