package com.kosh.android.notes.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import com.kosh.android.notes.format.StyleKind
import com.kosh.android.notes.format.editorStyles

const val BOX_EMPTY = '\u2610'
const val BOX_CHECKED = '\u2611'

private val CHECK_PREFIX = Regex("^( *)[-*+] \\[([ xX])\\] ")
private const val MARKER_LENGTH = 6 // "- [ ] "

/**
 * The editor's text with checklist markers shown as check boxes. Pure data (no Compose types) so the offset
 * mapping can be unit-tested. The stored note text is never changed by this: it is only how the editor draws it.
 */
class ChecklistView(
    val text: String,
    val originalToTransformed: IntArray,
    val transformedToOriginal: IntArray,
    /** Index in [text] of every box glyph, with whether it is ticked. */
    val boxes: List<Pair<Int, Boolean>>,
    /** Ranges of [text] that belong to ticked items (drawn struck through). */
    val tickedText: List<IntRange>,
)

fun buildChecklistView(src: String): ChecklistView {
    val out = StringBuilder()
    val origToTrans = IntArray(src.length + 1)
    val transToOrig = ArrayList<Int>(src.length + 8)
    val boxes = ArrayList<Pair<Int, Boolean>>()
    val ticked = ArrayList<IntRange>()

    var lineStart = 0
    while (true) {
        val lineEnd = src.indexOf('\n', lineStart).let { if (it < 0) src.length else it }
        var pos = lineStart
        val match = CHECK_PREFIX.find(src.substring(lineStart, lineEnd))
        var isTicked = false
        if (match != null) {
            val indent = match.groupValues[1].length
            repeat(indent) {
                origToTrans[pos] = out.length
                transToOrig.add(pos)
                out.append(' ')
                pos++
            }
            isTicked = match.groupValues[2].isNotBlank()
            val boxIndex = out.length
            // Typing position inside the hidden marker is the same as "just before the box".
            for (k in 0 until MARKER_LENGTH) origToTrans[pos + k] = boxIndex
            transToOrig.add(pos)
            out.append(if (isTicked) BOX_CHECKED else BOX_EMPTY)
            transToOrig.add(pos + MARKER_LENGTH)
            out.append(' ')
            boxes += boxIndex to isTicked
            pos += MARKER_LENGTH
        }
        val contentStart = out.length
        while (pos < lineEnd) {
            origToTrans[pos] = out.length
            transToOrig.add(pos)
            out.append(src[pos])
            pos++
        }
        if (isTicked && out.length > contentStart) ticked += contentStart until out.length
        if (lineEnd >= src.length) break
        origToTrans[lineEnd] = out.length
        transToOrig.add(lineEnd)
        out.append('\n')
        lineStart = lineEnd + 1
    }
    origToTrans[src.length] = out.length
    transToOrig.add(src.length)
    return ChecklistView(out.toString(), origToTrans, transToOrig.toIntArray(), boxes, ticked)
}

/** Shows `- [ ] item` as a check box followed by the item, and `- [x] item` as a ticked box with struck-through text. */
class ChecklistTransformation(
    private val boxColor: Color,
    private val tickedColor: Color,
    private val tickedTextColor: Color,
    /** Faint colour for the Markdown characters (`**`, `#`...); unspecified keeps them the normal colour. */
    private val markerColor: Color = Color.Unspecified,
    /** The word being read aloud, in the text's own offsets, and how to draw it. */
    private val highlight: IntRange? = null,
    private val highlightColor: Color = Color.Unspecified,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val view = buildChecklistView(text.text)
        val styles = ArrayList<AnnotatedString.Range<SpanStyle>>()
        for ((index, ticked) in view.boxes) {
            styles += AnnotatedString.Range(
                SpanStyle(color = if (ticked) tickedColor else boxColor, fontSize = 1.3.em, fontWeight = FontWeight.Bold),
                index,
                index + 1,
            )
        }
        for (range in view.tickedText) {
            styles += AnnotatedString.Range(
                SpanStyle(textDecoration = TextDecoration.LineThrough, color = tickedTextColor),
                range.first,
                range.last + 1,
            )
        }
        // Bold, italic, headings and so on are shown as they are typed. The Markdown characters stay in the text; only how
        // it is drawn changes, so the offsets are mapped through the checklist view and nothing is added or removed.
        val last = view.text.length
        for (r in editorStyles(text.text)) {
            val from = view.originalToTransformed[r.start.coerceIn(0, view.originalToTransformed.lastIndex)].coerceIn(0, last)
            val to = view.originalToTransformed[r.end.coerceIn(0, view.originalToTransformed.lastIndex)].coerceIn(0, last)
            if (to <= from) continue
            val style = when (r.kind) {
                StyleKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                StyleKind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                StyleKind.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                StyleKind.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Gray.copy(alpha = 0.18f))
                StyleKind.HEADING1 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.5.em)
                StyleKind.HEADING2 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.3.em)
                StyleKind.HEADING3 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.15.em)
                StyleKind.MARKER -> if (markerColor == Color.Unspecified) continue else SpanStyle(color = markerColor)
            }
            styles += AnnotatedString.Range(style, from, to)
        }
        if (highlight != null && highlightColor != Color.Unspecified && !highlight.isEmpty()) {
            val map = view.originalToTransformed
            val from = map[highlight.first.coerceIn(0, map.lastIndex)].coerceIn(0, last)
            val to = map[(highlight.last + 1).coerceIn(0, map.lastIndex)].coerceIn(0, last)
            if (to > from) styles += AnnotatedString.Range(SpanStyle(background = highlightColor), from, to)
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                view.originalToTransformed[offset.coerceIn(0, view.originalToTransformed.lastIndex)]

            override fun transformedToOriginal(offset: Int): Int =
                view.transformedToOriginal[offset.coerceIn(0, view.transformedToOriginal.lastIndex)]
        }
        return TransformedText(AnnotatedString(view.text, styles), mapping)
    }
}

/**
 * If [position] (inside the text area) is on a check box, returns the index of that line in the stored note,
 * otherwise null. Taps further along a line, on wrapped lines, or below the text are not hits.
 */
fun checklistHit(layout: TextLayoutResult, position: Offset, slopPx: Float): Int? {
    val shown = layout.layoutInput.text.text
    if (shown.isEmpty() || position.y < 0f) return null
    val line = layout.getLineForVerticalPosition(position.y)
    if (position.y > layout.getLineBottom(line)) return null
    val start = layout.getLineStart(line)
    var glyph = start
    while (glyph < shown.length && shown[glyph] == ' ') glyph++
    if (glyph >= shown.length || (shown[glyph] != BOX_EMPTY && shown[glyph] != BOX_CHECKED)) return null
    if (position.x > layout.getBoundingBox(glyph).right + slopPx) return null
    // The editor never adds or removes line breaks, so counting them gives the line in the stored note.
    return shown.substring(0, start).count { it == '\n' }
}
