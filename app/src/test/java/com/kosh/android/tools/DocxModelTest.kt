package com.kosh.android.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds tiny .docx packages in memory and checks what the parser makes of them. */
class DocxModelTest {

    private val ns = """xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" """ +
        """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" """ +
        """xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" """ +
        """xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" """ +
        """xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture""""

    private fun parts(document: String, styles: String? = null, numbering: String? = null, rels: String? = null): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        out["word/document.xml"] = """<?xml version="1.0"?><w:document $ns><w:body>$document</w:body></w:document>""".toByteArray()
        if (styles != null) out["word/styles.xml"] = """<?xml version="1.0"?><w:styles $ns>$styles</w:styles>""".toByteArray()
        if (numbering != null) out["word/numbering.xml"] = """<?xml version="1.0"?><w:numbering $ns>$numbering</w:numbering>""".toByteArray()
        if (rels != null) {
            out["word/_rels/document.xml.rels"] =
                """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels</Relationships>""".toByteArray()
        }
        return out
    }

    private fun parse(parts: Map<String, ByteArray>): DocxDocument = DocxParser { name -> parts[name]?.let { ByteArrayInputStream(it) } }.parse()

    private fun paragraphs(d: DocxDocument) = d.blocks.filterIsInstance<DocxParagraph>()
    private fun texts(p: DocxParagraph) = p.inlines.filterIsInstance<DocxInline.Text>()

    @Test fun plainTextAndRunStyling() {
        val doc = parse(parts("""<w:p><w:r><w:t>Hello </w:t></w:r><w:r><w:rPr><w:b/><w:i/><w:sz w:val="28"/><w:color w:val="FF0000"/></w:rPr><w:t>world</w:t></w:r></w:p>"""))
        val runs = texts(paragraphs(doc).single())
        assertEquals(listOf("Hello ", "world"), runs.map { it.text })
        assertFalse(runs[0].bold)
        assertTrue(runs[1].bold && runs[1].italic)
        assertEquals(14f, runs[1].sizePt, 0.001f)
        assertEquals(0xFFFF0000.toInt(), runs[1].color)
    }

    @Test fun offSwitchTurnsAStyleOff() {
        val styles = """<w:style w:type="paragraph" w:styleId="Strong1"><w:rPr><w:b/></w:rPr></w:style>"""
        val doc = parse(parts("""<w:p><w:pPr><w:pStyle w:val="Strong1"/></w:pPr><w:r><w:rPr><w:b w:val="0"/></w:rPr><w:t>x</w:t></w:r></w:p>""", styles))
        assertFalse(texts(paragraphs(doc).single()).single().bold)
    }

    @Test fun headingStyleKeepsItsSizeAndBoldness() {
        val styles = """
            <w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val="22"/></w:rPr></w:rPrDefault>
              <w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="259" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
            <w:style w:type="paragraph" w:default="1" w:styleId="Normal"/>
            <w:style w:type="paragraph" w:styleId="Heading1"><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="240"/></w:pPr><w:rPr><w:b/><w:sz w:val="32"/></w:rPr></w:style>
        """
        val doc = parse(parts("""<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Title</w:t></w:r></w:p><w:p><w:r><w:t>Body</w:t></w:r></w:p>""", styles))
        val (heading, body) = paragraphs(doc)
        val h = texts(heading).single()
        assertTrue(h.bold)
        assertEquals(16f, h.sizePt, 0.001f)
        assertEquals(12f, heading.before, 0.001f)
        assertEquals(8f, heading.after, 0.001f) // inherited from the document defaults
        assertEquals(259f / 240f, heading.lineMultiplier, 0.001f)
        assertEquals(11f, texts(body).single().sizePt, 0.001f)
        assertFalse(texts(body).single().bold)
    }

    @Test fun alignmentAndIndents() {
        val doc = parse(parts("""<w:p><w:pPr><w:jc w:val="center"/><w:ind w:left="720" w:firstLine="360"/></w:pPr><w:r><w:t>a</w:t></w:r></w:p>
            <w:p><w:pPr><w:jc w:val="both"/></w:pPr><w:r><w:t>b</w:t></w:r></w:p>"""))
        val (a, b) = paragraphs(doc)
        assertEquals(DocxAlign.CENTER, a.align)
        assertEquals(36f, a.indentLeft, 0.001f)
        assertEquals(18f, a.firstLine, 0.001f)
        assertEquals(DocxAlign.JUSTIFY, b.align)
    }

    @Test fun tabsBreaksAndDeletedText() {
        val doc = parse(parts("""<w:p><w:r><w:t>a</w:t><w:tab/><w:t>b</w:t><w:br/><w:t>c</w:t></w:r><w:del><w:r><w:delText>gone</w:delText></w:r></w:del>
            <w:r><w:br w:type="page"/></w:r><w:ins><w:r><w:t>new</w:t></w:r></w:ins></w:p>"""))
        val inlines = paragraphs(doc).single().inlines
        assertEquals("a\tb\nc", (inlines[0] as DocxInline.Text).text)
        assertEquals(DocxInline.PageBreak, inlines[1])
        assertEquals("new", (inlines[2] as DocxInline.Text).text)
        assertEquals(3, inlines.size)
    }

    @Test fun pictureSizeAndPathComeFromTheDrawing() {
        val drawing = """<w:r><w:drawing><wp:inline><wp:extent cx="1905000" cy="952500"/><a:graphic><a:graphicData><pic:pic><pic:blipFill>
            <a:blip r:embed="rId7"/></pic:blipFill></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"""
        val rels = """<Relationship Id="rId7" Type="x" Target="media/image1.png"/>"""
        val doc = parse(parts("<w:p>$drawing</w:p>", rels = rels))
        val image = paragraphs(doc).single().inlines.single() as DocxInline.Image
        assertEquals("word/media/image1.png", image.path)
        assertEquals(150f, image.widthPt, 0.01f)
        assertEquals(75f, image.heightPt, 0.01f)
    }

    @Test fun pictureInsideAlternateContentIsFoundOnce() {
        val run = """<w:r><mc:AlternateContent xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006"><mc:Choice Requires="wps"><w:drawing>
            <wp:anchor><wp:extent cx="254000" cy="254000"/><a:blip r:embed="rId2"/></wp:anchor></w:drawing></mc:Choice>
            <mc:Fallback><w:pict><v:shape xmlns:v="urn:schemas-microsoft-com:vml" style="width:20pt;height:20pt"><v:imagedata r:id="rId2"/></v:shape></w:pict></mc:Fallback></mc:AlternateContent></w:r>"""
        val doc = parse(parts("<w:p>$run</w:p>", rels = """<Relationship Id="rId2" Type="x" Target="/word/media/pic.jpg"/>"""))
        val images = paragraphs(doc).single().inlines.filterIsInstance<DocxInline.Image>()
        assertEquals(1, images.size)
        assertEquals("word/media/pic.jpg", images[0].path)
        assertEquals(20f, images[0].widthPt, 0.01f)
    }

    @Test fun oldStylePictureTakesItsSizeFromTheShape() {
        val run = """<w:r><w:pict><v:shape xmlns:v="urn:schemas-microsoft-com:vml" style="position:absolute;width:1in;height:36pt"><v:imagedata r:id="rId3"/></v:shape></w:pict></w:r>"""
        val doc = parse(parts("<w:p>$run</w:p>", rels = """<Relationship Id="rId3" Type="x" Target="media/old.png"/>"""))
        val image = paragraphs(doc).single().inlines.single() as DocxInline.Image
        assertEquals(72f, image.widthPt, 0.01f)
        assertEquals(36f, image.heightPt, 0.01f)
    }

    @Test fun pictureWithUnknownRelationshipIsSkipped() {
        val doc = parse(parts("""<w:p><w:r><w:drawing><wp:inline><wp:extent cx="1" cy="1"/><a:blip r:embed="rId99"/></wp:inline></w:drawing></w:r></w:p>"""))
        assertTrue(paragraphs(doc).single().inlines.isEmpty())
    }

    @Test fun externalPictureLinksAreIgnored() {
        val rels = """<Relationship Id="rId5" Type="x" Target="http://example.com/a.png" TargetMode="External"/>"""
        val doc = parse(parts("""<w:p><w:r><w:drawing><wp:inline><wp:extent cx="1" cy="1"/><a:blip r:embed="rId5"/></wp:inline></w:drawing></w:r></w:p>""", rels = rels))
        assertTrue(paragraphs(doc).single().inlines.isEmpty())
    }

    @Test fun bulletAndNumberedLists() {
        val numbering = """
            <w:abstractNum w:abstractNumId="0"><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl>
              <w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="lowerLetter"/><w:lvlText w:val="%2)"/><w:pPr><w:ind w:left="1440" w:hanging="360"/></w:pPr></w:lvl></w:abstractNum>
            <w:abstractNum w:abstractNumId="1"><w:lvl w:ilvl="0"><w:numFmt w:val="bullet"/><w:lvlText w:val="&#xF0B7;"/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl></w:abstractNum>
            <w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num><w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>"""
        fun item(num: Int, lvl: Int, text: String) =
            """<w:p><w:pPr><w:numPr><w:ilvl w:val="$lvl"/><w:numId w:val="$num"/></w:numPr></w:pPr><w:r><w:t>$text</w:t></w:r></w:p>"""
        val body = item(1, 0, "one") + item(1, 0, "two") + item(1, 1, "two-a") + item(1, 1, "two-b") + item(1, 0, "three") + item(2, 0, "dot")
        val doc = parse(parts(body, numbering = numbering))
        val labels = paragraphs(doc).map { texts(it).first().text }
        assertEquals(listOf("1.\t", "2.\t", "a)\t", "b)\t", "3.\t", "•\t"), labels)
        val first = paragraphs(doc).first()
        assertEquals(36f, first.indentLeft, 0.001f)
        assertEquals(-18f, first.firstLine, 0.001f)
        assertEquals(36f, first.labelTab!!, 0.001f)
    }

    @Test fun nestedNumberRestartsAfterAHigherLevel() {
        val numbering = """<w:abstractNum w:abstractNumId="0"><w:lvl w:ilvl="0"><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/></w:lvl>
            <w:lvl w:ilvl="1"><w:numFmt w:val="decimal"/><w:lvlText w:val="%1.%2"/></w:lvl></w:abstractNum><w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>"""
        fun item(lvl: Int) = """<w:p><w:pPr><w:numPr><w:ilvl w:val="$lvl"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>x</w:t></w:r></w:p>"""
        val doc = parse(parts(item(0) + item(1) + item(1) + item(0) + item(1), numbering = numbering))
        assertEquals(listOf("1.\t", "1.1\t", "1.2\t", "2.\t", "2.1\t"), paragraphs(doc).map { texts(it).first().text })
    }

    @Test fun tableWithGridSpanAndBorders() {
        val styles = """<w:style w:type="table" w:styleId="TableGrid"><w:tblPr><w:tblBorders><w:top w:val="single"/><w:left w:val="single"/></w:tblBorders></w:tblPr></w:style>
            <w:style w:type="table" w:styleId="Plain"><w:tblPr><w:tblBorders><w:top w:val="nil"/></w:tblBorders></w:tblPr></w:style>"""
        fun table(style: String) = """<w:tbl><w:tblPr><w:tblStyle w:val="$style"/></w:tblPr><w:tblGrid><w:gridCol w:w="2000"/><w:gridCol w:w="4000"/></w:tblGrid>
            <w:tr><w:tc><w:p><w:r><w:t>a</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>b</w:t></w:r></w:p></w:tc></w:tr>
            <w:tr><w:tc><w:tcPr><w:gridSpan w:val="2"/></w:tcPr><w:p><w:r><w:t>wide</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"""
        val doc = parse(parts(table("TableGrid") + table("Plain"), styles))
        val (grid, plain) = doc.blocks.filterIsInstance<DocxTable>()
        assertTrue(grid.borders)
        assertFalse(plain.borders)
        assertEquals(listOf(100f, 200f), grid.grid)
        assertEquals(2, grid.rows.size)
        assertEquals(listOf(1, 1), grid.rows[0].map { it.span })
        assertEquals(2, grid.rows[1].single().span)
        assertEquals("wide", texts(grid.rows[1].single().blocks.filterIsInstance<DocxParagraph>().single()).single().text)
    }

    @Test fun pageSizeAndMarginsFromTheLastSection() {
        val sect = """<w:sectPr><w:pgSz w:w="15840" w:h="12240" w:orient="landscape"/><w:pgMar w:top="720" w:right="1080" w:bottom="720" w:left="1080"/></w:sectPr>"""
        val page = parse(parts("<w:p/>$sect")).page
        assertEquals(792f, page.width, 0.01f)
        assertEquals(612f, page.height, 0.01f)
        assertEquals(54f, page.left, 0.01f)
        assertEquals(36f, page.top, 0.01f)
    }

    @Test fun missingSectionGivesA4WithInchMargins() {
        val page = parse(parts("<w:p/>")).page
        assertEquals(595.3f, page.width, 0.01f)
        assertEquals(72f, page.left, 0.01f)
    }

    @Test fun emptyParagraphKeepsItsHeight() {
        val doc = parse(parts("""<w:p><w:pPr><w:rPr><w:sz w:val="40"/></w:rPr></w:pPr></w:p>"""))
        val p = paragraphs(doc).single()
        assertTrue(p.inlines.isEmpty())
        assertEquals(20f, p.markSize, 0.001f)
    }

    @Test fun fontFamiliesAreGrouped() {
        val doc = parse(parts("""<w:p><w:r><w:rPr><w:rFonts w:ascii="Times New Roman"/></w:rPr><w:t>a</w:t></w:r>
            <w:r><w:rPr><w:rFonts w:ascii="Courier New"/></w:rPr><w:t>b</w:t></w:r><w:r><w:rPr><w:rFonts w:ascii="Calibri"/></w:rPr><w:t>c</w:t></w:r></w:p>"""))
        assertEquals(listOf(DocxFont.SERIF, DocxFont.MONO, DocxFont.SANS), texts(paragraphs(doc).single()).map { it.font })
    }

    @Test fun notAWordFileIsRejected() {
        try {
            parse(emptyMap())
            fail("expected an exception")
        } catch (e: DocxException) {
            assertNotNull(e.message)
        }
    }

    @Test fun brokenXmlIsRejected() {
        try {
            parse(mapOf("word/document.xml" to "<w:document".toByteArray()))
            fail("expected an exception")
        } catch (e: DocxException) {
            assertNotNull(e.message)
        }
    }

    @Test fun doctypeIsRefused() {
        val evil = """<?xml version="1.0"?><!DOCTYPE d [<!ENTITY x "boom">]><w:document $ns><w:body><w:p><w:r><w:t>&x;</w:t></w:r></w:p></w:body></w:document>"""
        try {
            parse(mapOf("word/document.xml" to evil.toByteArray()))
            fail("a file with a DTD must not be read")
        } catch (e: DocxException) {
            assertNotNull(e.message)
        }
    }

    @Test fun numberFormats() {
        assertEquals("a", formatNumber(1, "lowerLetter"))
        assertEquals("AA", formatNumber(27, "upperLetter"))
        assertEquals("xiv", formatNumber(14, "lowerRoman"))
        assertEquals("MCMXCIV", formatNumber(1994, "upperRoman"))
        assertEquals("07", formatNumber(7, "decimalZero"))
        assertEquals("7", formatNumber(7, "decimal"))
    }

    @Test fun zipRoundTripWorksThroughTheLoaderFunction() {
        val bytes = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { z ->
                for ((name, data) in parts("""<w:p><w:r><w:t>zip</w:t></w:r></w:p>""")) {
                    z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry()
                }
            }
        }.toByteArray()
        val entries = HashMap<String, ByteArray>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            generateSequence { z.nextEntry }.forEach { e -> entries[e.name] = z.readBytes() }
        }
        assertEquals("zip", texts(paragraphs(parse(entries)).single()).single().text)
    }
}
