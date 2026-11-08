package com.kosh.android.notes.format

/** An inline run of text with its styling. Markdown syntax characters are not part of [text]. */
data class Span(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val code: Boolean = false,
)

sealed interface LineKind {
    data object None : LineKind
    data class Heading(val level: Int) : LineKind
    data object Bullet : LineKind
    data class Numbered(val number: Int) : LineKind
    data class Check(val checked: Boolean) : LineKind
}

/** One source line split into its leading whitespace, list/heading marker and the remaining text. */
data class ParsedLine(val indent: String, val kind: LineKind, val content: String)

private val CHECK_RE = Regex("""^[-*+]\s+\[([ xX])\](?:\s+(.*))?$""")
private val BULLET_RE = Regex("""^[-*+]\s+(.*)$""")
private val NUMBER_RE = Regex("""^(\d{1,9})[.)]\s+(.*)$""")
private val HEADING_RE = Regex("""^(#{1,6})\s+(.*)$""")

fun splitLine(line: String): ParsedLine {
    val indentLen = line.indexOfFirst { it != ' ' && it != '\t' }.let { if (it < 0) line.length else it }
    val indent = line.substring(0, indentLen)
    val rest = line.substring(indentLen)
    CHECK_RE.matchEntire(rest)?.let {
        return ParsedLine(indent, LineKind.Check(it.groupValues[1].isNotBlank()), it.groupValues[2])
    }
    BULLET_RE.matchEntire(rest)?.let { return ParsedLine(indent, LineKind.Bullet, it.groupValues[1]) }
    NUMBER_RE.matchEntire(rest)?.let {
        val n = it.groupValues[1].toIntOrNull() ?: 1
        return ParsedLine(indent, LineKind.Numbered(n), it.groupValues[2])
    }
    if (indent.isEmpty()) {
        HEADING_RE.matchEntire(rest)?.let {
            return ParsedLine(indent, LineKind.Heading(it.groupValues[1].length), it.groupValues[2])
        }
    }
    return ParsedLine(indent, LineKind.None, rest)
}

fun renderLine(indent: String, kind: LineKind, content: String): String = when (kind) {
    LineKind.None -> indent + content
    is LineKind.Heading -> "#".repeat(kind.level) + " " + content
    LineKind.Bullet -> "$indent- $content"
    is LineKind.Numbered -> "$indent${kind.number}. $content"
    is LineKind.Check -> "$indent- [${if (kind.checked) "x" else " "}] $content"
}

sealed interface MdBlock {
    /** Zero-based index of the source line, used to toggle checklist items in place. */
    val line: Int
}

data class MdHeading(val level: Int, val spans: List<Span>, override val line: Int) : MdBlock
data class MdParagraph(val spans: List<Span>, override val line: Int) : MdBlock
data class MdBullet(val level: Int, val spans: List<Span>, override val line: Int) : MdBlock
data class MdNumbered(val number: Int, val level: Int, val spans: List<Span>, override val line: Int) : MdBlock
data class MdChecklist(val checked: Boolean, val level: Int, val spans: List<Span>, override val line: Int) : MdBlock
data class MdBlank(override val line: Int) : MdBlock

fun indentLevel(indent: String): Int = indent.map { if (it == '\t') 2 else 1 }.sum() / 2

fun normalizeNewlines(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')

fun parseMarkdown(source: String): List<MdBlock> =
    normalizeNewlines(source).split("\n").mapIndexed { i, raw ->
        if (raw.isBlank()) return@mapIndexed MdBlank(i)
        val p = splitLine(raw)
        val spans = parseInline(p.content)
        val level = indentLevel(p.indent)
        when (val k = p.kind) {
            LineKind.None -> MdParagraph(parseInline(p.indent + p.content), i)
            is LineKind.Heading -> MdHeading(k.level, spans, i)
            LineKind.Bullet -> MdBullet(level, spans, i)
            is LineKind.Numbered -> MdNumbered(k.number, level, spans, i)
            is LineKind.Check -> MdChecklist(k.checked, level, spans, i)
        }
    }

/** Flips `- [ ]` and `- [x]` on the given source line. Returns the text unchanged for other lines. */
fun toggleChecklistLine(text: String, lineIndex: Int): String {
    val lines = normalizeNewlines(text).split("\n").toMutableList()
    if (lineIndex !in lines.indices) return text
    val p = splitLine(lines[lineIndex])
    val kind = p.kind as? LineKind.Check ?: return text
    lines[lineIndex] = renderLine(p.indent, LineKind.Check(!kind.checked), p.content)
    return lines.joinToString("\n")
}

/**
 * Parses the inline subset we support: **bold**, *italic* / _italic_, ~~strike~~, `code` and backslash escapes.
 * Markers without a matching partner are kept as literal text.
 */
fun parseInline(s: String): List<Span> {
    val out = ArrayList<Span>()
    val sb = StringBuilder()
    var bold = false
    var italic = false
    var strike = false

    fun flush() {
        if (sb.isNotEmpty()) {
            out += Span(sb.toString(), bold, italic, strike)
            sb.clear()
        }
    }

    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '\\' && i + 1 < s.length && s[i + 1] in "\\`*_~[]#-" -> {
                sb.append(s[i + 1]); i += 2
            }
            c == '`' -> {
                val end = s.indexOf('`', i + 1)
                if (end > i + 1) {
                    flush()
                    out += Span(s.substring(i + 1, end), bold, italic, strike, code = true)
                    i = end + 1
                } else {
                    sb.append(c); i++
                }
            }
            s.startsWith("**", i) -> {
                if (bold || canOpen(s, i, 2, "**")) { flush(); bold = !bold } else sb.append("**")
                i += 2
            }
            s.startsWith("~~", i) -> {
                if (strike || canOpen(s, i, 2, "~~")) { flush(); strike = !strike } else sb.append("~~")
                i += 2
            }
            c == '*' || (c == '_' && (i == 0 || !s[i - 1].isLetterOrDigit())) -> {
                val marker = c.toString()
                val closing = italic && i > 0 && !s[i - 1].isWhitespace() &&
                    (c == '*' || i + 1 >= s.length || !s[i + 1].isLetterOrDigit())
                if (closing || (!italic && canOpen(s, i, 1, marker))) { flush(); italic = !italic } else sb.append(c)
                i++
            }
            else -> {
                sb.append(c); i++
            }
        }
    }
    flush()
    return out
}

/** An opening marker needs a non-space character after it and a later matching marker. */
private fun canOpen(s: String, at: Int, len: Int, marker: String): Boolean {
    val next = at + len
    if (next >= s.length || s[next].isWhitespace()) return false
    val close = s.indexOf(marker, next + 1)
    return close > next && !s[close - 1].isWhitespace()
}
