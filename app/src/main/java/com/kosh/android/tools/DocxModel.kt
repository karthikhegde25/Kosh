package com.kosh.android.tools

import org.w3c.dom.Element
import java.io.InputStream

/*
 * Reads a Word (.docx) file into a small model of what is on the pages: paragraphs with their text styling, pictures,
 * lists and tables. A .docx is a ZIP of XML parts plus the picture files; this class never touches Android, it asks for
 * parts by name through [DocxParser.read], so it can be tested on a plain JVM. Drawing the model is DocxPdfRenderer's job.
 *
 * Not read (and so left out of the PDF): headers and footers, footnotes, text boxes, shapes, floating-picture text wrap,
 * fields' computed results beyond their stored text, and section changes after the first (the last section's page setup
 * is used for the whole document).
 */

internal class DocxException(message: String) : Exception(message)

internal enum class DocxAlign { LEFT, CENTER, RIGHT, JUSTIFY }
internal enum class DocxFont { SANS, SERIF, MONO }

internal sealed interface DocxInline {
    /** [script]: 0 normal, 1 superscript, -1 subscript. */
    data class Text(
        val text: String,
        val bold: Boolean,
        val italic: Boolean,
        val underline: Boolean,
        val strike: Boolean,
        val sizePt: Float,
        val color: Int?,
        val font: DocxFont,
        val script: Int,
    ) : DocxInline

    /** A picture stored in the package at [path]. A size of 0 means "use the picture's own size". */
    data class Image(val path: String, val widthPt: Float, val heightPt: Float) : DocxInline

    data object PageBreak : DocxInline
}

internal sealed interface DocxBlock

internal data class DocxParagraph(
    val inlines: List<DocxInline>,
    val align: DocxAlign,
    val before: Float,
    val after: Float,
    val lineMultiplier: Float,
    val indentLeft: Float,
    val indentRight: Float,
    /** Extra indent of the first line (negative for a hanging indent such as a list item). */
    val firstLine: Float,
    /** Where a list label's tab jumps to, measured from the page margin; null when the paragraph isn't a list item. */
    val labelTab: Float?,
    /** Size of the paragraph mark: the height of an empty paragraph. */
    val markSize: Float,
    val pageBreakBefore: Boolean,
) : DocxBlock

internal data class DocxCell(val blocks: List<DocxBlock>, val span: Int)

internal data class DocxTable(val rows: List<List<DocxCell>>, val grid: List<Float>, val borders: Boolean) : DocxBlock

/** Page size and margins in points. */
internal data class DocxPage(val width: Float, val height: Float, val left: Float, val top: Float, val right: Float, val bottom: Float)

internal data class DocxDocument(val page: DocxPage, val blocks: List<DocxBlock>)

// ---- properties as they are written in the XML (every field may be unset) ----------------------------------------------

private data class PP(
    val styleId: String? = null,
    val align: DocxAlign? = null,
    val before: Float? = null,
    val after: Float? = null,
    val lineMultiplier: Float? = null,
    val left: Float? = null,
    val right: Float? = null,
    val firstLine: Float? = null,
    val numId: Int? = null,
    val level: Int? = null,
    val pageBreakBefore: Boolean? = null,
) {
    fun over(o: PP) = PP(
        o.styleId ?: styleId, o.align ?: align, o.before ?: before, o.after ?: after, o.lineMultiplier ?: lineMultiplier,
        o.left ?: left, o.right ?: right, o.firstLine ?: firstLine, o.numId ?: numId, o.level ?: level,
        o.pageBreakBefore ?: pageBreakBefore,
    )
}

private data class RP(
    val styleId: String? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strike: Boolean? = null,
    val size: Float? = null,
    val color: Int? = null,
    val font: DocxFont? = null,
    val script: Int? = null,
) {
    fun over(o: RP) = RP(
        o.styleId ?: styleId, o.bold ?: bold, o.italic ?: italic, o.underline ?: underline, o.strike ?: strike,
        o.size ?: size, o.color ?: color, o.font ?: font, o.script ?: script,
    )
}

private class Style(
    val id: String,
    val type: String,
    val basedOn: String?,
    val isDefault: Boolean,
    val p: PP,
    val r: RP,
    val tableBorders: Boolean?,
)

private class NumLevel(val format: String, val text: String, val start: Int, val p: PP)

private const val DEFAULT_SIZE = 11f
private const val MAX_LIST_DEPTH = 9

internal class DocxParser(private val read: (String) -> InputStream?) {

    private var relations: Map<String, String> = emptyMap()
    private val styles = HashMap<String, Style>()
    private var defaultP = PP()
    private var defaultR = RP()
    private var defaultParagraphStyle: String? = null
    private val abstractLevels = HashMap<Int, Map<Int, NumLevel>>()
    private val numToAbstract = HashMap<Int, Int>()
    private val counters = HashMap<Int, IntArray>()

    fun parse(): DocxDocument {
        val document = xml("word/document.xml") ?: throw DocxException("This isn't a Word (.docx) file")
        relations = readRelations()
        xml("word/styles.xml")?.let(::readStyles)
        xml("word/numbering.xml")?.let(::readNumbering)

        val body = document.children("body").firstOrNull() ?: throw DocxException("The document has no content")
        return DocxDocument(readPage(body.children("sectPr").lastOrNull()), blocks(body))
    }

    // ---- package parts --------------------------------------------------------------------------------------------------

    private fun xml(name: String): Element? = read(name)?.let(::parseXmlPart)

    private fun readRelations(): Map<String, String> {
        val root = xml("word/_rels/document.xml.rels") ?: return emptyMap()
        val out = HashMap<String, String>()
        for (rel in root.children("Relationship")) {
            val id = rel.getAttribute("Id")
            val target = rel.getAttribute("Target")
            if (id.isNotEmpty() && target.isNotEmpty() && !rel.getAttribute("TargetMode").equals("External", ignoreCase = true)) {
                out[id] = resolvePath(target)
            }
        }
        return out
    }

    /** A relationship target is relative to the "word" folder unless it starts with a slash. */
    private fun resolvePath(target: String): String {
        val parts = ArrayList<String>()
        val base = if (target.startsWith("/")) target.removePrefix("/") else "word/$target"
        for (part in base.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += part
            }
        }
        return parts.joinToString("/")
    }

    // ---- styles -----------------------------------------------------------------------------------------------------------

    private fun readStyles(root: Element) {
        root.children("docDefaults").firstOrNull()?.let { defaults ->
            defaults.children("pPrDefault").firstOrNull()?.children("pPr")?.firstOrNull()?.let { defaultP = paragraphProps(it) }
            defaults.children("rPrDefault").firstOrNull()?.children("rPr")?.firstOrNull()?.let { defaultR = runProps(it) }
        }
        for (el in root.children("style")) {
            val id = el.w("styleId") ?: continue
            val type = el.w("type") ?: "paragraph"
            val isDefault = el.w("default").let { it == "1" || it.equals("true", true) }
            val borders = el.children("tblPr").firstOrNull()?.let { tableBorders(it) }
            styles[id] = Style(
                id, type, el.children("basedOn").firstOrNull()?.w("val"), isDefault,
                el.children("pPr").firstOrNull()?.let(::paragraphProps) ?: PP(),
                el.children("rPr").firstOrNull()?.let(::runProps) ?: RP(),
                borders,
            )
            if (isDefault && type == "paragraph") defaultParagraphStyle = id
        }
    }

    private fun chain(id: String?): List<Style> {
        val out = ArrayList<Style>()
        var next = id
        while (next != null && out.size < 20) {
            val s = styles[next] ?: break
            if (out.any { it === s }) break
            out += s
            next = s.basedOn
        }
        return out.reversed() // the most basic style first
    }

    private fun styleParagraph(id: String?): PP =
        chain(id ?: defaultParagraphStyle).fold(defaultP) { acc, s -> acc.over(s.p) }

    private fun styleRun(paragraphStyle: String?, charStyle: String?): RP {
        var r = chain(paragraphStyle ?: defaultParagraphStyle).fold(defaultR) { acc, s -> acc.over(s.r) }
        if (charStyle != null) r = chain(charStyle).fold(r) { acc, s -> acc.over(s.r) }
        return r
    }

    /** Only what a character style itself sets, so the paragraph's own look isn't reset. */
    private fun characterStyle(id: String?): RP = chain(id).fold(RP()) { acc, s -> acc.over(s.r) }.copy(styleId = null)

    // ---- numbering --------------------------------------------------------------------------------------------------------

    private fun readNumbering(root: Element) {
        for (abs in root.children("abstractNum")) {
            val id = abs.w("abstractNumId")?.toIntOrNull() ?: continue
            val levels = HashMap<Int, NumLevel>()
            for (lvl in abs.children("lvl")) {
                val index = lvl.w("ilvl")?.toIntOrNull() ?: continue
                levels[index] = NumLevel(
                    format = lvl.children("numFmt").firstOrNull()?.w("val") ?: "decimal",
                    text = lvl.children("lvlText").firstOrNull()?.w("val") ?: "",
                    start = lvl.children("start").firstOrNull()?.w("val")?.toIntOrNull() ?: 1,
                    p = lvl.children("pPr").firstOrNull()?.let(::paragraphProps) ?: PP(),
                )
            }
            abstractLevels[id] = levels
        }
        for (num in root.children("num")) {
            val id = num.w("numId")?.toIntOrNull() ?: continue
            val abs = num.children("abstractNumId").firstOrNull()?.w("val")?.toIntOrNull() ?: continue
            numToAbstract[id] = abs
        }
    }

    private fun numberingLevel(numId: Int, level: Int): NumLevel? = abstractLevels[numToAbstract[numId]]?.get(level)

    /** Advances the list counters for one list item and returns its label ("1.", "a)", a bullet...). */
    private fun nextLabel(numId: Int, level: Int): String? {
        val lvl = numberingLevel(numId, level) ?: return null
        val count = counters.getOrPut(numId) { IntArray(MAX_LIST_DEPTH) { -1 } }
        val at = level.coerceIn(0, MAX_LIST_DEPTH - 1)
        count[at] = if (count[at] < 0) lvl.start else count[at] + 1
        for (deeper in at + 1 until MAX_LIST_DEPTH) count[deeper] = -1
        if (lvl.format == "bullet") return BULLETS[at % BULLETS.size]
        if (lvl.format == "none") return ""
        return Regex("%([1-9])").replace(lvl.text) { m ->
            val index = m.groupValues[1].toInt() - 1
            val format = numberingLevel(numId, index)
            val value = if (count.getOrElse(index) { -1 } < 0) (format?.start ?: 1) else count[index]
            formatNumber(value, format?.format ?: "decimal")
        }
    }

    // ---- page ---------------------------------------------------------------------------------------------------------------

    private fun readPage(sect: Element?): DocxPage {
        var w = 595.3f
        var h = 841.9f
        var top = 72f
        var right = 72f
        var bottom = 72f
        var left = 72f
        sect?.children("pgSz")?.firstOrNull()?.let { size ->
            size.twips("w")?.takeIf { it > 100f }?.let { w = it }
            size.twips("h")?.takeIf { it > 100f }?.let { h = it }
            if (size.w("orient") == "landscape" && w < h) { val t = w; w = h; h = t }
        }
        sect?.children("pgMar")?.firstOrNull()?.let { m ->
            m.twips("top")?.let { top = it }
            m.twips("right")?.let { right = it }
            m.twips("bottom")?.let { bottom = it }
            m.twips("left")?.let { left = it }
        }
        // Margins that leave no room (a broken file) fall back to the defaults.
        if (left + right > w - 72f) { left = 72f; right = 72f }
        if (top + bottom > h - 72f) { top = 72f; bottom = 72f }
        return DocxPage(w, h, left, top, right, bottom)
    }

    // ---- blocks -------------------------------------------------------------------------------------------------------------

    private fun blocks(parent: Element): List<DocxBlock> {
        val out = ArrayList<DocxBlock>()
        for (el in parent.childElements()) {
            when (el.localName) {
                "p" -> out += paragraph(el)
                "tbl" -> out += table(el)
                "sdt" -> el.children("sdtContent").firstOrNull()?.let { out += blocks(it) }
            }
        }
        return out
    }

    private fun paragraph(p: Element): DocxParagraph {
        val pPr = p.children("pPr").firstOrNull()
        val direct = pPr?.let(::paragraphProps) ?: PP()
        val style = styleParagraph(direct.styleId)
        val markRun = pPr?.children("rPr")?.firstOrNull()?.let(::runProps) ?: RP()
        val runBase = styleRun(direct.styleId, null)

        // A list item takes its indent from the list level unless the paragraph sets its own.
        val numId = direct.numId ?: style.numId
        val level = (direct.level ?: style.level ?: 0).coerceIn(0, MAX_LIST_DEPTH - 1)
        val listLevel = if (numId != null && numId != 0) numberingLevel(numId, level) else null
        val effective = (if (listLevel != null) style.over(listLevel.p.copy(styleId = null)) else style).over(direct)

        val inlines = ArrayList<DocxInline>()
        var labelTab: Float? = null
        if (listLevel != null) {
            nextLabel(numId!!, level)?.let { label ->
                if (label.isNotEmpty()) {
                    inlines += textInline(label + "\t", runBase.over(markRun))
                    labelTab = effective.left ?: 0f
                }
            }
        }
        collectInlines(p, runBase, inlines)

        val markSize = runBase.over(markRun).size ?: DEFAULT_SIZE
        return DocxParagraph(
            inlines = inlines,
            align = effective.align ?: DocxAlign.LEFT,
            before = effective.before ?: 0f,
            after = effective.after ?: 0f,
            lineMultiplier = effective.lineMultiplier ?: 1f,
            indentLeft = (effective.left ?: 0f).coerceAtLeast(0f),
            indentRight = (effective.right ?: 0f).coerceAtLeast(0f),
            firstLine = effective.firstLine ?: 0f,
            labelTab = labelTab,
            markSize = markSize,
            pageBreakBefore = effective.pageBreakBefore == true,
        )
    }

    private fun collectInlines(parent: Element, base: RP, out: MutableList<DocxInline>) {
        for (el in parent.childElements()) {
            when (el.localName) {
                "r" -> run(el, base, out)
                // Wrappers whose content is part of the paragraph's text. Deleted text (w:del) is skipped.
                "hyperlink", "ins", "smartTag", "fldSimple", "customXml", "sdtContent" -> collectInlines(el, base, out)
                "sdt" -> el.children("sdtContent").firstOrNull()?.let { collectInlines(it, base, out) }
            }
        }
    }

    private fun run(r: Element, base: RP, out: MutableList<DocxInline>) {
        val direct = r.children("rPr").firstOrNull()?.let(::runProps) ?: RP()
        val props = base.over(characterStyle(direct.styleId)).over(direct)
        val text = StringBuilder()
        fun flush() {
            if (text.isNotEmpty()) out += textInline(text.toString(), props)
            text.setLength(0)
        }
        for (el in r.childElements()) {
            when (el.localName) {
                "t" -> text.append(el.textContent)
                "tab" -> text.append('\t')
                "cr" -> text.append('\n')
                "noBreakHyphen" -> text.append('-')
                "br" -> if (el.w("type") == "page") { flush(); out += DocxInline.PageBreak } else text.append('\n')
                "drawing", "pict", "object", "AlternateContent" -> {
                    flush()
                    image(el)?.let { out += it }
                }
            }
        }
        flush()
    }

    private fun textInline(text: String, p: RP) = DocxInline.Text(
        text = text,
        bold = p.bold == true,
        italic = p.italic == true,
        underline = p.underline == true,
        strike = p.strike == true,
        sizePt = p.size ?: DEFAULT_SIZE,
        color = p.color,
        font = p.font ?: DocxFont.SANS,
        script = p.script ?: 0,
    )

    // ---- pictures -----------------------------------------------------------------------------------------------------------

    /** The first picture inside [container] (a drawing, an old-style VML picture, or an alternate-content wrapper). */
    private fun image(container: Element): DocxInline.Image? {
        val blip = container.firstDescendant { (it.localName == "blip" && it.rel("embed") != null) || (it.localName == "imagedata" && it.rel("id") != null) }
            ?: return null
        val id = blip.rel("embed") ?: blip.rel("id") ?: return null
        val path = relations[id] ?: return null

        var w = 0f
        var h = 0f
        container.firstDescendant { it.localName == "extent" && it.getAttribute("cx").isNotEmpty() }?.let { extent ->
            w = (extent.getAttribute("cx").toFloatOrNull() ?: 0f) / EMU_PER_POINT
            h = (extent.getAttribute("cy").toFloatOrNull() ?: 0f) / EMU_PER_POINT
        }
        if (w <= 0f || h <= 0f) {
            // Old-style pictures give their size in the shape's CSS-like style attribute.
            container.firstDescendant { it.localName == "shape" && it.getAttribute("style").isNotEmpty() }?.let { shape ->
                val style = shape.getAttribute("style")
                w = cssPoints(style, "width")
                h = cssPoints(style, "height")
            }
        }
        return DocxInline.Image(path, w.coerceAtLeast(0f), h.coerceAtLeast(0f))
    }

    private fun cssPoints(style: String, key: String): Float {
        val m = Regex("(?:^|;)\\s*$key\\s*:\\s*([0-9.]+)\\s*(pt|in|cm|mm|px)?", RegexOption.IGNORE_CASE).find(style) ?: return 0f
        val value = m.groupValues[1].toFloatOrNull() ?: return 0f
        return when (m.groupValues[2].lowercase()) {
            "in" -> value * 72f
            "cm" -> value * 72f / 2.54f
            "mm" -> value * 72f / 25.4f
            "px" -> value * 0.75f
            else -> value
        }
    }

    // ---- tables -------------------------------------------------------------------------------------------------------------

    private fun table(t: Element): DocxTable {
        val tblPr = t.children("tblPr").firstOrNull()
        val styleId = tblPr?.children("tblStyle")?.firstOrNull()?.w("val")
        val borders = tblPr?.let { tableBorders(it) } ?: chain(styleId).reversed().firstNotNullOfOrNull { it.tableBorders } ?: false
        val grid = t.children("tblGrid").firstOrNull()?.children("gridCol")?.map { it.twips("w") ?: 0f }.orEmpty()
        val rows = t.children("tr").map { tr ->
            tr.children("tc").map { tc ->
                val span = tc.children("tcPr").firstOrNull()?.children("gridSpan")?.firstOrNull()?.w("val")?.toIntOrNull() ?: 1
                DocxCell(blocks(tc), span.coerceAtLeast(1))
            }
        }
        return DocxTable(rows, grid, borders)
    }

    /** Whether the table properties draw borders; null when they don't say (the table's style decides then). */
    private fun tableBorders(tblPr: Element): Boolean? {
        val borders = tblPr.children("tblBorders").firstOrNull() ?: return null
        return borders.childElements().any { it.w("val").let { v -> v != null && v != "nil" && v != "none" } }
    }

    // ---- property parsing -----------------------------------------------------------------------------------------------------

    private fun paragraphProps(pPr: Element): PP {
        var p = PP()
        for (el in pPr.childElements()) {
            p = when (el.localName) {
                "pStyle" -> p.copy(styleId = el.w("val"))
                "jc" -> p.copy(align = when (el.w("val")) {
                    "center" -> DocxAlign.CENTER
                    "right", "end" -> DocxAlign.RIGHT
                    "both", "distribute", "justify" -> DocxAlign.JUSTIFY
                    else -> DocxAlign.LEFT
                })
                "spacing" -> p.copy(
                    before = el.twips("before") ?: p.before,
                    after = el.twips("after") ?: p.after,
                    // Only "automatic" line spacing is a multiple of the line; exact and at-least heights are left alone.
                    lineMultiplier = if (el.w("lineRule").let { it == null || it == "auto" }) el.w("line")?.toFloatOrNull()?.let { (it / 240f).coerceIn(0.5f, 4f) } ?: p.lineMultiplier else p.lineMultiplier,
                )
                "ind" -> {
                    val hanging = el.twips("hanging")
                    p.copy(
                        left = el.twips("left") ?: el.twips("start") ?: p.left,
                        right = el.twips("right") ?: el.twips("end") ?: p.right,
                        firstLine = if (hanging != null) -hanging else el.twips("firstLine") ?: p.firstLine,
                    )
                }
                "numPr" -> p.copy(
                    numId = el.children("numId").firstOrNull()?.w("val")?.toIntOrNull() ?: p.numId,
                    level = el.children("ilvl").firstOrNull()?.w("val")?.toIntOrNull() ?: p.level,
                )
                "pageBreakBefore" -> p.copy(pageBreakBefore = el.toggle())
                else -> p
            }
        }
        return p
    }

    private fun runProps(rPr: Element): RP {
        var r = RP()
        for (el in rPr.childElements()) {
            r = when (el.localName) {
                "rStyle" -> r.copy(styleId = el.w("val"))
                "b" -> r.copy(bold = el.toggle())
                "i" -> r.copy(italic = el.toggle())
                "u" -> r.copy(underline = el.w("val") != "none")
                "strike", "dstrike" -> r.copy(strike = el.toggle())
                "sz" -> r.copy(size = el.w("val")?.toFloatOrNull()?.div(2f)?.takeIf { it > 0f })
                "color" -> r.copy(color = parseColor(el.w("val")))
                "rFonts" -> r.copy(font = (el.w("ascii") ?: el.w("hAnsi"))?.let(::classifyFont) ?: r.font)
                "vertAlign" -> r.copy(script = when (el.w("val")) { "superscript" -> 1; "subscript" -> -1; else -> 0 })
                else -> r
            }
        }
        return r
    }

    private fun parseColor(value: String?): Int? {
        if (value == null || value.length != 6) return null
        return value.toIntOrNull(16)?.let { 0xFF000000.toInt() or it }
    }

    private fun classifyFont(name: String): DocxFont {
        val n = name.lowercase()
        return when {
            listOf("courier", "consolas", "mono", "lucida console", "menlo").any { it in n } -> DocxFont.MONO
            listOf("times", "georgia", "cambria", "garamond", "palatino", "book antiqua", "century", "serif").any { it in n } && "sans" !in n -> DocxFont.SERIF
            else -> DocxFont.SANS
        }
    }

    companion object {
        private const val EMU_PER_POINT = 12700f
        private val BULLETS = listOf("•", "◦", "▪")
    }
}

// ---- number labels ----------------------------------------------------------------------------------------------------------

internal fun formatNumber(value: Int, format: String): String = when (format) {
    "lowerLetter" -> letters(value).lowercase()
    "upperLetter" -> letters(value)
    "lowerRoman" -> roman(value).lowercase()
    "upperRoman" -> roman(value)
    "decimalZero" -> value.toString().padStart(2, '0')
    else -> value.toString()
}

/** 1 -> A, 26 -> Z, 27 -> AA. */
private fun letters(value: Int): String {
    var n = value.coerceAtLeast(1)
    val sb = StringBuilder()
    while (n > 0) {
        n -= 1
        sb.append('A' + n % 26)
        n /= 26
    }
    return sb.reverse().toString()
}

private fun roman(value: Int): String {
    var n = value.coerceIn(1, 3999)
    val sb = StringBuilder()
    for ((v, s) in ROMAN) while (n >= v) { sb.append(s); n -= v }
    return sb.toString()
}

private val ROMAN = listOf(
    1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
    50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
)

// ---- small XML helpers ------------------------------------------------------------------------------------------------------

private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

/** A word-processing attribute such as w:val, or null when it isn't there. */
private fun Element.w(name: String): String? =
    getAttributeNS(W_NS, name).takeIf { it.isNotEmpty() } ?: getAttribute(name).takeIf { it.isNotEmpty() } ?: getAttribute("w:$name").takeIf { it.isNotEmpty() }

/** A length in twentieths of a point, as points. */
private fun Element.twips(name: String): Float? = w(name)?.toFloatOrNull()?.div(20f)

/** An on/off property: present without a value means on. */
private fun Element.toggle(): Boolean = w("val").let { it == null || !(it == "0" || it.equals("false", true) || it.equals("off", true)) }
