package com.kosh.android.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class XlsxModelTest {

    private val main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private fun workbook(
        sheetXml: String,
        shared: String? = null,
        styles: String? = null,
        sheets: String = """<sheet name="Data" sheetId="1" r:id="rId1"/>""",
        extra: Map<String, String> = emptyMap(),
    ): XlsxWorkbook {
        val parts = HashMap<String, ByteArray>()
        parts["xl/workbook.xml"] = """<workbook xmlns="$main" xmlns:r="$rel"><sheets>$sheets</sheets></workbook>""".toByteArray()
        parts["xl/_rels/workbook.xml.rels"] = """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="x" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="x" Target="/xl/worksheets/sheet2.xml"/></Relationships>""".toByteArray()
        parts["xl/worksheets/sheet1.xml"] = """<worksheet xmlns="$main">$sheetXml</worksheet>""".toByteArray()
        if (shared != null) parts["xl/sharedStrings.xml"] = """<sst xmlns="$main">$shared</sst>""".toByteArray()
        if (styles != null) parts["xl/styles.xml"] = """<styleSheet xmlns="$main">$styles</styleSheet>""".toByteArray()
        extra.forEach { (k, v) -> parts[k] = v.toByteArray() }
        return XlsxParser { parts[it]?.let { b -> ByteArrayInputStream(b) } }.parse()
    }

    private fun cells(w: XlsxWorkbook, row: Int = 0) = w.sheets[0].rows[row].cells

    @Test fun sharedAndInlineStringsAndRichText() {
        val w = workbook(
            """<sheetData><row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="inlineStr"><is><t>inline</t></is></c></row></sheetData>""",
            shared = """<si><t>plain</t></si><si><r><t>rich </t></r><r><rPr><b/></rPr><t>text</t></r><rPh><t>ignored</t></rPh></si>""",
        )
        assertEquals(listOf("plain", "rich text", "inline"), cells(w).map { it.text })
        assertEquals(listOf(0, 1, 2), cells(w).map { it.col })
    }

    @Test fun cellReferencesGiveColumns() {
        assertEquals(0, XlsxParser.columnOf("A1"))
        assertEquals(25, XlsxParser.columnOf("Z9"))
        assertEquals(26, XlsxParser.columnOf("AA10"))
        assertEquals(701, XlsxParser.columnOf("ZZ1"))
    }

    @Test fun gapsInTheRowKeepColumnPositions() {
        val w = workbook("""<sheetData><row r="3"><c r="B3"><v>1</v></c><c r="E3"><v>2</v></c></row></sheetData>""")
        assertEquals(3, w.sheets[0].rows[0].index)
        assertEquals(listOf(1, 4), cells(w).map { it.col })
    }

    @Test fun numbersAreTrimmedAndRightAligned() {
        val w = workbook("""<sheetData><row r="1"><c r="A1"><v>42</v></c><c r="B1"><v>0.1</v></c><c r="C1"><v>3.14159265358979</v></c></row></sheetData>""")
        assertEquals(listOf("42", "0.1", "3.141592654"), cells(w).map { it.text })
        assertTrue(cells(w).all { it.numeric })
    }

    @Test fun numberFormats() {
        val styles = """<numFmts count="3"><numFmt numFmtId="164" formatCode="#,##0.00"/><numFmt numFmtId="165" formatCode="&quot;$&quot;#,##0"/><numFmt numFmtId="166" formatCode="0.0%"/></numFmts>
            <cellXfs count="5"><xf numFmtId="0"/><xf numFmtId="164"/><xf numFmtId="165"/><xf numFmtId="166"/><xf numFmtId="10"/></cellXfs>"""
        val w = workbook(
            """<sheetData><row r="1"><c r="A1" s="1"><v>1234567.891</v></c><c r="B1" s="2"><v>9876.5</v></c><c r="C1" s="3"><v>0.1234</v></c><c r="D1" s="4"><v>0.5</v></c><c r="E1" s="1"><v>-2.5</v></c></row></sheetData>""",
            styles = styles,
        )
        assertEquals(listOf("1,234,567.89", "$9,877", "12.3%", "50.00%", "-2.50"), cells(w).map { it.text })
    }

    @Test fun datesUseTheSerialNumber() {
        val styles = """<cellXfs count="2"><xf numFmtId="0"/><xf numFmtId="14"/></cellXfs>"""
        val w = workbook("""<sheetData><row r="1"><c r="A1" s="1"><v>45000</v></c><c r="B1" s="1"><v>1</v></c></row></sheetData>""", styles = styles)
        assertEquals("2023-03-15", cells(w)[0].text)
        assertEquals("1900-01-01", cells(w)[1].text)
    }

    @Test fun booleanErrorAndFormulaResults() {
        val w = workbook("""<sheetData><row r="1"><c r="A1" t="b"><v>1</v></c><c r="B1" t="e"><v>#DIV/0!</v></c><c r="C1" t="str"><f>A1&amp;"x"</f><v>TRUEx</v></c><c r="D1"><f>1+1</f><v>2</v></c></row></sheetData>""")
        assertEquals(listOf("TRUE", "#DIV/0!", "TRUEx", "2"), cells(w).map { it.text })
    }

    @Test fun boldColourFillAndAlignment() {
        val styles = """<fonts count="2"><font/><font><b/><color rgb="FFFF0000"/></font></fonts>
            <fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFFFF00"/></patternFill></fill></fills>
            <cellXfs count="2"><xf fontId="0" fillId="0"/><xf fontId="1" fillId="2"><alignment horizontal="center"/></xf></cellXfs>"""
        val w = workbook("""<sheetData><row r="1"><c r="A1" s="1" t="inlineStr"><is><t>Head</t></is></c><c r="B1" t="inlineStr"><is><t>plain</t></is></c></row></sheetData>""", styles = styles)
        val (head, plain) = cells(w)
        assertTrue(head.bold)
        assertEquals(0xFFFF0000.toInt(), head.color)
        assertEquals(0xFFFFFF00.toInt(), head.fill)
        assertEquals(2, head.align)
        assertFalse(plain.bold)
        assertNull(plain.fill)
    }

    @Test fun emptyCellsOnlyCountWhenFilled() {
        val styles = """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF00FF00"/></patternFill></fill></fills>
            <cellXfs count="2"><xf fillId="0"/><xf fillId="1"/></cellXfs>"""
        val w = workbook("""<sheetData><row r="1"><c r="A1"/><c r="B1" s="1"/><c r="C1" s="0"/></row></sheetData>""", styles = styles)
        assertEquals(listOf(1), cells(w).map { it.col })
    }

    @Test fun columnWidthsRowHeightsAndHiddenRows() {
        val w = workbook(
            """<sheetFormatPr defaultRowHeight="18"/><cols><col min="1" max="2" width="10"/><col min="4" max="4" width="20" hidden="1"/></cols>
            <sheetData><row r="1" ht="30"><c r="A1"><v>1</v></c></row><row r="2" hidden="1"><c r="A2"><v>2</v></c></row><row r="3"><c r="A3"><v>3</v></c></row></sheetData>""",
        )
        val s = w.sheets[0]
        assertEquals(18f, s.defaultRowHeight, 0.001f)
        assertEquals(10 * 5.25f + 3.75f, s.colWidths[0]!!, 0.001f)
        assertEquals(s.colWidths[0], s.colWidths[1])
        assertEquals(0f, s.colWidths[3]!!, 0.001f)
        assertEquals(listOf(1, 3), s.rows.map { it.index })
        assertEquals(30f, s.rows[0].height!!, 0.001f)
    }

    @Test fun hiddenSheetsAreSkipped() {
        val sheets = """<sheet name="Hidden" sheetId="2" state="hidden" r:id="rId2"/><sheet name="Shown" sheetId="1" r:id="rId1"/>"""
        val w = workbook("""<sheetData><row r="1"><c r="A1"><v>1</v></c></row></sheetData>""", sheets = sheets)
        assertEquals(listOf("Shown"), w.sheets.map { it.name })
    }

    @Test fun notAnExcelFileIsRejected() {
        try {
            XlsxParser { null }.parse()
            fail("expected an exception")
        } catch (e: DocxException) {
            assertTrue(e.message!!.contains("Excel"))
        }
    }
}
