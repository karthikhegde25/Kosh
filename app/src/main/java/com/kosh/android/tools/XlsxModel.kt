package com.kosh.android.tools

import org.w3c.dom.Element
import java.io.InputStream
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/*
 * Reads an Excel (.xlsx) workbook into plain rows of already-formatted text: what each cell would show, with its bold,
 * colours and alignment. Formulas are not calculated; the value Excel stored with them is used. Charts, images, merged-cell
 * layout and conditional formatting are not read. No Android classes, so it can be tested on a plain JVM.
 */

internal data class XlsxCell(
    val col: Int,
    val text: String,
    /** Numbers and dates are right-aligned unless the cell says otherwise. */
    val numeric: Boolean,
    val bold: Boolean,
    val color: Int?,
    val fill: Int?,
    /** 0 = by type, 1 = left, 2 = centre, 3 = right. */
    val align: Int,
)

internal data class XlsxRow(val index: Int, val height: Float?, val cells: List<XlsxCell>)

internal data class XlsxSheet(
    val name: String,
    /** Width in points of columns that set one (column index from 0). 0 means hidden. */
    val colWidths: Map<Int, Float>,
    val defaultColWidth: Float,
    val defaultRowHeight: Float,
    val rows: List<XlsxRow>,
)

internal data class XlsxWorkbook(val sheets: List<XlsxSheet>)

private class XfStyle(val numFmtId: Int, val bold: Boolean, val color: Int?, val fill: Int?, val align: Int)

internal class XlsxParser(private val read: (String) -> InputStream?) {

    private var shared: List<String> = emptyList()
    private var formats: Map<Int, String> = emptyMap()
    private var xfs: List<XfStyle> = emptyList()
    private var dates1904 = false

    fun parse(): XlsxWorkbook {
        val workbookPath = "xl/workbook.xml"
        val workbook = read(workbookPath)?.let(::parseXmlPart) ?: throw DocxException("This isn't an Excel (.xlsx) file")
        val relations = readRelationships(workbookPath, read)
        dates1904 = workbook.child("workbookPr")?.attr("date1904").let { it == "1" || it.equals("true", true) }
        read("xl/sharedStrings.xml")?.let(::parseXmlPart)?.let(::readSharedStrings)
        read("xl/styles.xml")?.let(::parseXmlPart)?.let(::readStyles)

        val sheets = ArrayList<XlsxSheet>()
        for (sheet in workbook.child("sheets")?.children("sheet").orEmpty()) {
            if (sheet.attr("state") == "hidden" || sheet.attr("state") == "veryHidden") continue
            val path = sheet.rel("id")?.let { relations[it] } ?: continue
            val root = read(path)?.let(::parseXmlPart) ?: continue
            sheets += readSheet(sheet.attr("name") ?: "Sheet${sheets.size + 1}", root)
        }
        if (sheets.isEmpty()) throw DocxException("This workbook has no visible sheets")
        return XlsxWorkbook(sheets)
    }

    // ---- shared strings and styles ----------------------------------------------------------------------------------------

    private fun readSharedStrings(root: Element) {
        // A string is either one <t> or several rich-text runs <r><t>; phonetic hints (<rPh>) are not part of the text.
        shared = root.children("si").map { si ->
            val sb = StringBuilder()
            for (part in si.childElements()) {
                when (part.localName) {
                    "t" -> sb.append(part.textContent)
                    "r" -> part.child("t")?.let { sb.append(it.textContent) }
                }
            }
            sb.toString()
        }
    }

    private fun readStyles(root: Element) {
        formats = root.child("numFmts")?.children("numFmt").orEmpty()
            .mapNotNull { f -> f.attr("numFmtId")?.toIntOrNull()?.let { it to (f.attr("formatCode") ?: "") } }.toMap()

        val fonts = root.child("fonts")?.children("font").orEmpty().map { f ->
            val b = f.child("b")?.let { it.attr("val").let { v -> v == null || !(v == "0" || v.equals("false", true)) } } ?: false
            b to argb(f.child("color")?.attr("rgb"))
        }
        val fills = root.child("fills")?.children("fill").orEmpty().map { f ->
            val pattern = f.child("patternFill")
            if (pattern?.attr("patternType") == "solid") argb(pattern.child("fgColor")?.attr("rgb")) else null
        }
        xfs = root.child("cellXfs")?.children("xf").orEmpty().map { xf ->
            val font = xf.attr("fontId")?.toIntOrNull()?.let { fonts.getOrNull(it) }
            val align = when (xf.child("alignment")?.attr("horizontal")) {
                "left" -> 1
                "center", "centerContinuous" -> 2
                "right" -> 3
                else -> 0
            }
            XfStyle(
                numFmtId = xf.attr("numFmtId")?.toIntOrNull() ?: 0,
                bold = font?.first ?: false,
                color = font?.second,
                fill = xf.attr("fillId")?.toIntOrNull()?.let { fills.getOrNull(it) }?.takeIf { it != 0xFFFFFFFF.toInt() },
                align = align,
            )
        }
    }

    /** "FF1F4E79" (alpha first) to an ARGB int; null for anything else, such as theme colours. */
    private fun argb(value: String?): Int? {
        if (value == null) return null
        val hex = if (value.length == 8) value.substring(2) else value
        if (hex.length != 6) return null
        return hex.toIntOrNull(16)?.let { 0xFF000000.toInt() or it }
    }

    // ---- one sheet ----------------------------------------------------------------------------------------------------------

    private fun readSheet(name: String, root: Element): XlsxSheet {
        val format = root.child("sheetFormatPr")
        val defaultWidth = (format?.attr("defaultColWidth")?.toFloatOrNull() ?: 8.43f).let(::charsToPoints)
        val defaultHeight = format?.attr("defaultRowHeight")?.toFloatOrNull() ?: 15f

        val widths = HashMap<Int, Float>()
        for (col in root.child("cols")?.children("col").orEmpty()) {
            val min = (col.attr("min")?.toIntOrNull() ?: continue) - 1
            val max = ((col.attr("max")?.toIntOrNull() ?: continue) - 1).coerceAtMost(min + MAX_COLUMNS)
            val hidden = col.attr("hidden").let { it == "1" || it.equals("true", true) }
            val width = if (hidden) 0f else col.attr("width")?.toFloatOrNull()?.let(::charsToPoints) ?: defaultWidth
            for (c in min..max) widths[c] = width
        }

        val rows = ArrayList<XlsxRow>()
        var nextRow = 1
        for (row in root.child("sheetData")?.children("row").orEmpty()) {
            val index = row.attr("r")?.toIntOrNull() ?: nextRow
            nextRow = index + 1
            if (row.attr("hidden").let { it == "1" || it.equals("true", true) }) continue
            val cells = ArrayList<XlsxCell>()
            var nextCol = 0
            for (c in row.children("c")) {
                val col = c.attr("r")?.let(::columnOf) ?: nextCol
                nextCol = col + 1
                readCell(c, col)?.let { cells += it }
            }
            val height = row.attr("ht")?.toFloatOrNull()
            if (cells.isNotEmpty() || height != null) rows += XlsxRow(index, height, cells)
        }
        return XlsxSheet(name, widths, defaultWidth, defaultHeight, rows)
    }

    private fun readCell(c: Element, col: Int): XlsxCell? {
        val style = c.attr("s")?.toIntOrNull()?.let { xfs.getOrNull(it) }
        val type = c.attr("t")
        val raw = c.child("v")?.textContent
        var numeric = false
        val text: String = when (type) {
            "s" -> raw?.trim()?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
            "inlineStr" -> c.child("is")?.descendantText("t").orEmpty()
            "b" -> if (raw?.trim() == "1") "TRUE" else "FALSE"
            "str", "e" -> raw.orEmpty()
            else -> {
                val number = raw?.trim()?.toDoubleOrNull()
                if (number == null) raw.orEmpty() else {
                    numeric = true
                    formatCell(number, style?.numFmtId ?: 0)
                }
            }
        }
        // An empty cell only matters if it has a fill that should be drawn.
        if (text.isEmpty() && style?.fill == null) return null
        return XlsxCell(col, text, numeric, style?.bold ?: false, style?.color, style?.fill, style?.align ?: 0)
    }

    // ---- number formats ---------------------------------------------------------------------------------------------------------

    private fun formatCell(value: Double, numFmtId: Int): String {
        val code = formats[numFmtId] ?: BUILTIN[numFmtId]
        if (isDate(numFmtId, code)) return formatDate(value, code.orEmpty())
        if (code == null || code.equals("General", true) || numFmtId == 0) return general(value)

        val section = code.split(';').first()
        val plain = section.replace(Regex("\"[^\"]*\""), "").replace(Regex("\\[[^]]*]"), "").replace(Regex("\\\\."), "")
        val percent = '%' in plain
        val decimals = plain.substringAfter('.', "").takeWhile { it == '0' || it == '#' || it == '?' }.length
        val thousands = Regex("[#0?],[#0?]").containsMatchIn(plain)
        val shown = if (percent) value * 100 else value
        val rounded = BigDecimal(abs(shown)).setScale(decimals, java.math.RoundingMode.HALF_UP)
        var digits = if (thousands) java.text.DecimalFormat("#,##0" + if (decimals > 0) "." + "0".repeat(decimals) else "", java.text.DecimalFormatSymbols(java.util.Locale.US)).format(rounded) else rounded.toPlainString()
        val negative = shown < 0 && rounded.signum() != 0
        val currency = CURRENCIES.firstOrNull { it in section }?.toString().orEmpty()
        digits = currency + digits + if (percent) "%" else ""
        return if (negative) "-$digits" else digits
    }

    private fun isDate(id: Int, code: String?): Boolean {
        if (id in 14..22 || id in 45..47) return true
        if (code == null) return false
        val bare = code.replace(Regex("\"[^\"]*\""), "").replace(Regex("\\[[^]]*]"), "").replace(Regex("\\\\."), "")
        return Regex("[dmyhs]", RegexOption.IGNORE_CASE).containsMatchIn(bare) && !bare.contains("General", true)
    }

    private fun formatDate(serial: Double, code: String): String {
        val whole = floor(serial).toLong()
        val seconds = ((serial - whole) * 86400.0).roundToLong()
        val hasTime = Regex("[hs]", RegexOption.IGNORE_CASE).containsMatchIn(code.replace(Regex("\"[^\"]*\""), ""))
        val hasDate = Regex("[dy]", RegexOption.IGNORE_CASE).containsMatchIn(code.replace(Regex("\"[^\"]*\""), "")) || code.isEmpty()
        // Excel believes 1900 was a leap year, so serials before 61 (1 March 1900) sit one day later than the real calendar.
        val epoch = when {
            dates1904 -> LocalDate.of(1904, 1, 1)
            whole < 61 -> LocalDate.of(1899, 12, 31)
            else -> LocalDate.of(1899, 12, 30)
        }
        val moment = LocalDateTime.of(epoch.plusDays(whole), java.time.LocalTime.MIDNIGHT).plusSeconds(seconds)
        val date = "%04d-%02d-%02d".format(moment.year, moment.monthValue, moment.dayOfMonth)
        val time = "%02d:%02d".format(moment.hour, moment.minute)
        return when {
            hasDate && hasTime -> "$date $time"
            hasTime -> time
            else -> date
        }
    }

    private fun general(v: Double): String {
        if (v == floor(v) && abs(v) < 1e15) return v.toLong().toString()
        return BigDecimal(v).round(MathContext(10)).stripTrailingZeros().toPlainString()
    }

    private fun charsToPoints(chars: Float): Float = chars * 5.25f + 3.75f

    companion object {
        private const val MAX_COLUMNS = 1000
        private val CURRENCIES = listOf('$', '₹', '€', '£', '¥')
        private val BUILTIN = mapOf(
            1 to "0", 2 to "0.00", 3 to "#,##0", 4 to "#,##0.00", 9 to "0%", 10 to "0.00%",
            37 to "#,##0", 38 to "#,##0", 39 to "#,##0.00", 40 to "#,##0.00",
        )

        /** "AB12" -> 27 (A = 0). */
        fun columnOf(ref: String): Int {
            var n = 0
            for (ch in ref) {
                if (ch !in 'A'..'Z' && ch !in 'a'..'z') break
                n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
            }
            return (n - 1).coerceAtLeast(0)
        }
    }
}
