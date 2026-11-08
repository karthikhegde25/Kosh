package com.kosh.android.notes.tts

import com.kosh.android.notes.format.NoteImageTokens
import com.kosh.android.notes.format.StyleKind
import com.kosh.android.notes.format.editorStyles

/*
 * Turns a note (Markdown text) into what should be spoken, and keeps track of where each spoken character came from so the
 * word being read can be highlighted in the editor. No Android classes, so it can be tested on a plain JVM.
 */

/**
 * The text to speak. [raw] has one entry per character of [text]: its index in the note, plus one extra entry holding the
 * note length so that the end of the last word can be mapped too.
 */
class Speakable(val text: String, val raw: IntArray) {
    /** The range of the note that the spoken characters [from] until [to] came from. */
    fun noteRange(from: Int, to: Int): IntRange {
        if (text.isEmpty()) return IntRange.EMPTY
        val a = raw[from.coerceIn(0, text.length - 1)]
        val b = raw[(to - 1).coerceIn(0, text.length - 1)] + 1
        return a until maxOf(a, b)
    }

    /** The spoken position that belongs to the note position [noteIndex] (the first character at or after it). */
    fun positionOfNote(noteIndex: Int): Int {
        val i = raw.indexOfFirst { it >= noteIndex }
        return if (i < 0 || i >= text.length) 0 else i
    }
}

/** A sentence-sized stretch of the spoken text: [start] inclusive, [end] exclusive. */
data class SpeechChunk(val start: Int, val end: Int)

private val LIST_PREFIX = Regex("^ *(?:[-*+] \\[[ xX]] |[-*+] |>+ ?)")
private val LINK = Regex("!?\\[([^\\]\\n]*)]\\(([^)\\n]*)\\)")

/**
 * What a note sounds like: Markdown signs (`#`, `**`, bullets, check-box markers, link addresses) are left out, picture lines
 * are skipped, and everything else is read as written.
 */
fun buildSpeakable(note: String): Speakable {
    val skip = BooleanArray(note.length)
    for (r in editorStyles(note)) {
        if (r.kind == StyleKind.MARKER) for (i in r.start until r.end.coerceAtMost(note.length)) skip[i] = true
    }
    val out = StringBuilder()
    val raw = ArrayList<Int>(note.length + 1)
    var lineStart = 0
    while (lineStart <= note.length) {
        val lineEnd = note.indexOf('\n', lineStart).let { if (it < 0) note.length else it }
        val line = note.substring(lineStart, lineEnd)
        if (NoteImageTokens.nameOf(line) == null) {
            LIST_PREFIX.find(line)?.let { m -> for (i in 0 until m.value.length) skip[lineStart + i] = true }
            for (m in LINK.findAll(line)) {
                // Keep the link text; drop "[", "](" and the address.
                skip[lineStart + m.range.first] = true
                if (m.value.startsWith("!")) skip[lineStart + m.range.first + 1] = true
                val textEnd = lineStart + m.range.first + m.value.indexOf("](")
                for (i in textEnd..(lineStart + m.range.last)) skip[i] = true
            }
            for (i in lineStart until lineEnd) {
                if (skip[i]) continue
                val ch = note[i]
                if (ch == '\r') continue
                out.append(ch)
                raw += i
            }
        }
        if (lineEnd < note.length) { out.append('\n'); raw += lineEnd }
        if (lineEnd >= note.length) break
        lineStart = lineEnd + 1
    }
    raw += note.length
    return Speakable(out.toString(), raw.toIntArray())
}

private fun isSentenceEnd(c: Char) = c == '.' || c == '!' || c == '?' || c == '।' || c == '…' || c == '。'

/** Cuts [text] into pieces of at most [maxLength] characters, at the end of a sentence or line where possible. */
fun speechChunks(text: String, maxLength: Int = 300): List<SpeechChunk> {
    val out = ArrayList<SpeechChunk>()
    var p = 0
    val n = text.length
    while (p < n) {
        while (p < n && text[p].isWhitespace()) p++
        if (p >= n) break
        val limit = minOf(n, p + maxLength)
        var end = -1
        var i = p
        while (i < limit) {
            val c = text[i]
            if (c == '\n') { end = i; break }
            if (isSentenceEnd(c)) {
                // Include a run like "?!" or "..." and closing quotes, then it must be followed by space or the end.
                var j = i + 1
                while (j < n && (isSentenceEnd(text[j]) || text[j] == '"' || text[j] == '\'' || text[j] == ')')) j++
                if (j >= n || text[j].isWhitespace()) { end = j; i = j; if (j >= limit) break; continue }
            }
            i++
        }
        if (end < 0 || end == p) {
            end = if (limit >= n) n else {
                val space = text.lastIndexOf(' ', limit - 1)
                if (space > p) space else limit
            }
        } else if (end < limit && end < n && text[end] != '\n') {
            // A sentence ended inside the limit: take the last complete one.
        }
        val trimmedEnd = text.substring(p, end).trimEnd().length + p
        if (trimmedEnd > p) out += SpeechChunk(p, trimmedEnd)
        p = maxOf(end, p + 1)
    }
    return out
}

/** The index of the chunk that holds spoken position [pos] (or the first one after it); the last chunk if none does. */
fun chunkIndexAt(chunks: List<SpeechChunk>, pos: Int): Int {
    val i = chunks.indexOfFirst { it.end > pos }
    return if (i < 0) chunks.lastIndex else i
}
