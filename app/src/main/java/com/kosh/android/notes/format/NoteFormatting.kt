package com.kosh.android.notes.format

import kotlin.math.max
import kotlin.math.min

/** Text plus selection (start/end are caret offsets; start may be greater than end). */
data class TextState(val text: String, val selStart: Int, val selEnd: Int) {
    val lo get() = min(selStart, selEnd)
    val hi get() = max(selStart, selEnd)
}

enum class FormatOp { BOLD, ITALIC, STRIKE, HEADING, BULLET, NUMBERED, CHECKLIST, INDENT, OUTDENT }

/** Toolbar actions. Each takes the current text and selection and returns the new ones. */
object NoteFormatting {

    fun apply(op: FormatOp, s: TextState): TextState = when (op) {
        FormatOp.BOLD -> toggleInline(s, "**")
        FormatOp.ITALIC -> toggleInline(s, "*")
        FormatOp.STRIKE -> toggleInline(s, "~~")
        FormatOp.HEADING -> cycleHeading(s)
        FormatOp.BULLET -> toggleList(s) { it is LineKind.Bullet }
        FormatOp.NUMBERED -> toggleList(s) { it is LineKind.Numbered }
        FormatOp.CHECKLIST -> toggleList(s) { it is LineKind.Check }
        FormatOp.INDENT -> transformLines(s) { line, _ -> if (line.isBlank()) line else "  $line" }
        FormatOp.OUTDENT -> transformLines(s) { line, _ ->
            when {
                line.startsWith("  ") -> line.removePrefix("  ")
                line.startsWith("\t") -> line.removePrefix("\t")
                line.startsWith(" ") -> line.removePrefix(" ")
                else -> line
            }
        }
    }

    // ---- inline styles ----------------------------------------------------------------------------

    fun toggleInline(s: TextState, marker: String): TextState {
        val t = s.text
        val a = s.lo.coerceIn(0, t.length)
        val b = s.hi.coerceIn(0, t.length)
        val m = marker.length

        // Marker directly around the selection: remove it.
        val before = a >= m && t.substring(a - m, a) == marker
        val after = b + m <= t.length && t.substring(b, b + m) == marker
        if (before && after && !partOfDoubleStar(t, a - m, b, marker)) {
            return TextState(t.removeRange(b, b + m).removeRange(a - m, a), a - m, b - m)
        }
        // Selection itself includes the markers: remove them.
        val sel = t.substring(a, b)
        if (sel.length >= 2 * m && sel.startsWith(marker) && sel.endsWith(marker) && !(marker == "*" && sel.startsWith("**"))) {
            val inner = sel.substring(m, sel.length - m)
            return TextState(t.substring(0, a) + inner + t.substring(b), a, a + inner.length)
        }
        // Otherwise wrap (or insert an empty pair at the caret).
        return if (a == b) {
            TextState(t.substring(0, a) + marker + marker + t.substring(a), a + m, a + m)
        } else {
            TextState(t.substring(0, a) + marker + sel + marker + t.substring(b), a + m, b + m)
        }
    }

    /** Stops "*" (italic) from matching one half of a "**" (bold) pair. */
    private fun partOfDoubleStar(t: String, markerStart: Int, selEnd: Int, marker: String): Boolean {
        if (marker != "*") return false
        val leftExtra = markerStart > 0 && t[markerStart - 1] == '*'
        val rightExtra = selEnd + 1 < t.length && t[selEnd + 1] == '*'
        return leftExtra || rightExtra
    }

    // ---- line styles ------------------------------------------------------------------------------

    /** Applies [transform] to every line touched by the selection and keeps the selection in place. */
    fun transformLines(s: TextState, transform: (line: String, index: Int) -> String): TextState {
        val t = s.text
        val a = s.lo.coerceIn(0, t.length)
        var b = s.hi.coerceIn(0, t.length)
        if (b > a && b > 0 && t[b - 1] == '\n') b-- // selection ending at the start of the next line
        val start = t.lastIndexOf('\n', a - 1).let { if (it < 0) 0 else it + 1 }
        val end = t.indexOf('\n', b).let { if (it < 0) t.length else it }
        val oldLines = t.substring(start, end).split("\n")
        val newLines = oldLines.mapIndexed { i, l -> transform(l, i) }
        val newBlock = newLines.joinToString("\n")
        val firstDelta = newLines.first().length - oldLines.first().length
        val totalDelta = newBlock.length - (end - start)
        val newText = t.substring(0, start) + newBlock + t.substring(end)
        val newStart = max(start, s.lo + firstDelta)
        val newEnd = max(newStart, s.hi + totalDelta)
        return if (s.selStart <= s.selEnd) TextState(newText, newStart, newEnd) else TextState(newText, newEnd, newStart)
    }

    private fun toggleList(s: TextState, matches: (LineKind) -> Boolean): TextState {
        // Decide once, from the original lines, whether this removes or applies the list style.
        val probe = linesTouched(s)
        val nonBlank = probe.filter { it.isNotBlank() }
        val remove = nonBlank.isNotEmpty() && nonBlank.all { matches(splitLine(it).kind) }
        val target = listKindFor(matches)
        var counter = 0
        return transformLines(s) { line, _ ->
            if (line.isBlank() && probe.size > 1) return@transformLines line
            val p = splitLine(line)
            if (remove) {
                renderLine(p.indent, LineKind.None, p.content)
            } else {
                counter++
                val indent = if (p.kind is LineKind.Heading) "" else p.indent
                val kind = when (target) {
                    is LineKind.Numbered -> LineKind.Numbered(counter)
                    is LineKind.Check -> (p.kind as? LineKind.Check) ?: LineKind.Check(false)
                    else -> target
                }
                renderLine(indent, kind, p.content)
            }
        }
    }

    private fun listKindFor(matches: (LineKind) -> Boolean): LineKind = when {
        matches(LineKind.Bullet) -> LineKind.Bullet
        matches(LineKind.Numbered(1)) -> LineKind.Numbered(1)
        else -> LineKind.Check(false)
    }

    private fun cycleHeading(s: TextState): TextState {
        val lines = linesTouched(s)
        val current = (splitLine(lines.first()).kind as? LineKind.Heading)?.level ?: 0
        val next = (current + 1) % 4
        return transformLines(s) { line, _ ->
            if (line.isBlank() && lines.size > 1) return@transformLines line
            val p = splitLine(line)
            if (next == 0) renderLine("", LineKind.None, p.content)
            else renderLine("", LineKind.Heading(next), p.content)
        }
    }

    private fun linesTouched(s: TextState): List<String> {
        val t = s.text
        val a = s.lo.coerceIn(0, t.length)
        var b = s.hi.coerceIn(0, t.length)
        if (b > a && b > 0 && t[b - 1] == '\n') b--
        val start = t.lastIndexOf('\n', a - 1).let { if (it < 0) 0 else it + 1 }
        val end = t.indexOf('\n', b).let { if (it < 0) t.length else it }
        return t.substring(start, end).split("\n")
    }

    // ---- Enter key in lists -----------------------------------------------------------------------

    /**
     * If [new] is [old] with a single newline typed at the caret, continues the list on the next line
     * (or ends the list when the item was empty). Returns null when nothing special should happen.
     */
    fun continueList(old: TextState, new: TextState): TextState? {
        if (new.selStart != new.selEnd) return null
        val caret = new.selStart
        if (new.text.length != old.text.length + 1 || caret < 1 || new.text[caret - 1] != '\n') return null
        if (new.text.removeRange(caret - 1, caret) != old.text) return null

        val lineStart = new.text.lastIndexOf('\n', caret - 2).let { if (it < 0) 0 else it + 1 }
        val prevLine = new.text.substring(lineStart, caret - 1)
        val p = splitLine(prevLine)
        val next: LineKind = when (val k = p.kind) {
            LineKind.Bullet -> LineKind.Bullet
            is LineKind.Numbered -> LineKind.Numbered(k.number + 1)
            is LineKind.Check -> LineKind.Check(false)
            else -> return null
        }
        if (p.content.isBlank()) {
            // Enter on an empty item ends the list: the item becomes an empty line.
            val text = new.text.substring(0, lineStart) + new.text.substring(caret - 1)
            return TextState(text, lineStart, lineStart)
        }
        val prefix = renderLine(p.indent, next, "")
        val text = new.text.substring(0, caret) + prefix + new.text.substring(caret)
        return TextState(text, caret + prefix.length, caret + prefix.length)
    }
}

/** Undo/redo history. Typing within [coalesceMs] of the previous edit is grouped into one undo step. */
class UndoStack(private val limit: Int = 100, private val coalesceMs: Long = 1000) {
    private val past = ArrayDeque<TextState>()
    private val future = ArrayDeque<TextState>()
    private var lastRecordAt = 0L

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    /** Call with the state *before* an edit. [force] starts a new undo step (toolbar actions). */
    fun record(before: TextState, now: Long, force: Boolean) {
        if (force || past.isEmpty() || now - lastRecordAt > coalesceMs) {
            past.addLast(before)
            if (past.size > limit) past.removeFirst()
        }
        lastRecordAt = now
        future.clear()
    }

    fun undo(current: TextState): TextState? {
        val previous = past.removeLastOrNull() ?: return null
        future.addLast(current)
        lastRecordAt = 0L
        return previous
    }

    fun redo(current: TextState): TextState? {
        val next = future.removeLastOrNull() ?: return null
        past.addLast(current)
        lastRecordAt = 0L
        return next
    }
}
