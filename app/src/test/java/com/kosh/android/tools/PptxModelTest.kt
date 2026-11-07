package com.kosh.android.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class PptxModelTest {

    private val a = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private val p = "http://schemas.openxmlformats.org/presentationml/2006/main"
    private val r = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private val relNs = "http://schemas.openxmlformats.org/package/2006/relationships"

    private fun rels(vararg pairs: Pair<String, String>) =
        """<Relationships xmlns="$relNs">${pairs.joinToString("") { """<Relationship Id="${it.first}" Type="x" Target="${it.second}"/>""" }}</Relationships>"""

    private fun slideXml(shapes: String, background: String = "") =
        """<p:sld xmlns:a="$a" xmlns:p="$p" xmlns:r="$r"><p:cSld>$background<p:spTree>$shapes</p:spTree></p:cSld></p:sld>"""

    private fun deck(
        slide: String,
        layoutShapes: String = "",
        slideRels: String = rels("rId1" to "../slideLayouts/slideLayout1.xml", "rId2" to "../media/image1.png"),
    ): PptxDeck {
        val parts = HashMap<String, String>()
        parts["ppt/presentation.xml"] = """<p:presentation xmlns:a="$a" xmlns:p="$p" xmlns:r="$r"><p:sldIdLst><p:sldId id="256" r:id="rId1"/></p:sldIdLst><p:sldSz cx="9144000" cy="5143500"/></p:presentation>"""
        parts["ppt/_rels/presentation.xml.rels"] = rels("rId1" to "slides/slide1.xml")
        parts["ppt/slides/slide1.xml"] = slide
        parts["ppt/slides/_rels/slide1.xml.rels"] = slideRels
        parts["ppt/slideLayouts/slideLayout1.xml"] = """<p:sldLayout xmlns:a="$a" xmlns:p="$p"><p:cSld><p:spTree>$layoutShapes</p:spTree></p:cSld></p:sldLayout>"""
        parts["ppt/slideLayouts/_rels/slideLayout1.xml.rels"] = rels("rId1" to "../slideMasters/slideMaster1.xml")
        parts["ppt/slideMasters/slideMaster1.xml"] = """<p:sldMaster xmlns:a="$a" xmlns:p="$p"><p:cSld><p:spTree/></p:cSld></p:sldMaster>"""
        parts["ppt/slideMasters/_rels/slideMaster1.xml.rels"] = rels("rId1" to "../theme/theme1.xml")
        parts["ppt/theme/theme1.xml"] = """<a:theme xmlns:a="$a"><a:themeElements><a:clrScheme name="x"><a:dk1><a:sysClr val="windowText" lastClr="000000"/></a:dk1><a:lt1><a:sysClr val="window" lastClr="FFFFFF"/></a:lt1><a:accent1><a:srgbClr val="4472C4"/></a:accent1></a:clrScheme></a:themeElements></a:theme>"""
        return PptxParser { parts[it]?.let { s -> ByteArrayInputStream(s.toByteArray()) } }.parse()
    }

    private fun sp(x: Int, y: Int, w: Int, h: Int, body: String, fill: String = "", extra: String = "") =
        """<p:sp><p:nvSpPr><p:cNvPr id="2" name="s"/><p:cNvSpPr/><p:nvPr>$extra</p:nvPr></p:nvSpPr><p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$w" cy="$h"/></a:xfrm>$fill</p:spPr>$body</p:sp>"""

    private fun text(vararg runs: String) =
        """<p:txBody><a:bodyPr/><a:p>${runs.joinToString("") { it }}</a:p></p:txBody>"""

    @Test fun slideSizeIsConvertedToPoints() {
        val d = deck(slideXml(""))
        assertEquals(720f, d.width, 0.01f)
        assertEquals(405f, d.height, 0.01f)
        assertEquals(1, d.slides.size)
    }

    @Test fun textBoxKeepsPositionAndRunFormatting() {
        val shape = sp(1270000, 2540000, 6350000, 635000, text("""<a:r><a:rPr sz="2400" b="1" i="1"><a:solidFill><a:srgbClr val="FF0000"/></a:solidFill></a:rPr><a:t>Hello</a:t></a:r>"""))
        val s = deck(slideXml(shape)).slides[0].shapes.single() as PptxShape.Block
        assertEquals(100f, s.box.x, 0.01f)
        assertEquals(200f, s.box.y, 0.01f)
        assertEquals(500f, s.box.w, 0.01f)
        val run = s.paragraphs.single().runs.single()
        assertEquals("Hello", run.text)
        assertEquals(24f, run.size!!, 0.001f)
        assertTrue(run.bold && run.italic)
        assertEquals(0xFFFF0000.toInt(), run.color)
    }

    @Test fun filledShapeWithoutTextIsKept() {
        val shape = sp(0, 0, 1270000, 1270000, """<p:txBody><a:bodyPr/><a:p/></p:txBody>""", fill = """<a:solidFill><a:srgbClr val="00FF00"/></a:solidFill>""")
        val s = deck(slideXml(shape)).slides[0].shapes.single() as PptxShape.Block
        assertEquals(0xFF00FF00.toInt(), s.fill)
        assertTrue(s.paragraphs.isEmpty())
    }

    @Test fun emptyUnfilledShapeIsDropped() {
        val shape = sp(0, 0, 1270000, 1270000, """<p:txBody><a:bodyPr/><a:p/></p:txBody>""")
        assertTrue(deck(slideXml(shape)).slides[0].shapes.isEmpty())
    }

    @Test fun themeColoursAreResolved() {
        val shape = sp(0, 0, 1270000, 1270000, text("""<a:r><a:rPr><a:solidFill><a:schemeClr val="accent1"/></a:solidFill></a:rPr><a:t>x</a:t></a:r>"""), fill = """<a:solidFill><a:schemeClr val="bg1"/></a:solidFill>""")
        val s = deck(slideXml(shape)).slides[0].shapes.single() as PptxShape.Block
        assertEquals(0xFF4472C4.toInt(), s.paragraphs[0].runs[0].color)
        assertEquals(0xFFFFFFFF.toInt(), s.fill)
    }

    @Test fun placeholderTakesItsPositionFromTheLayout() {
        val layout = """<p:sp><p:nvSpPr><p:cNvPr id="2" name="t"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr><p:spPr><a:xfrm><a:off x="635000" y="635000"/><a:ext cx="7620000" cy="1270000"/></a:xfrm></p:spPr></p:sp>"""
        val slideShape = """<p:sp><p:nvSpPr><p:cNvPr id="2" name="t"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:p><a:r><a:t>Title</a:t></a:r></a:p></p:txBody></p:sp>"""
        val s = deck(slideXml(slideShape), layoutShapes = layout).slides[0].shapes.single() as PptxShape.Block
        assertEquals(50f, s.box.x, 0.01f)
        assertEquals(600f, s.box.w, 0.01f)
        val run = s.paragraphs[0].runs[0]
        assertEquals(32f, run.size!!, 0.001f)
        assertTrue(run.bold)
    }

    @Test fun pictureReferencesItsMediaPath() {
        val pic = """<p:pic><p:nvPicPr><p:cNvPr id="3" name="p"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed="rId2"/></p:blipFill><p:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="2540000" cy="1270000"/></a:xfrm></p:spPr></p:pic>"""
        val s = deck(slideXml(pic)).slides[0].shapes.single() as PptxShape.Picture
        assertEquals("ppt/media/image1.png", s.path)
        assertEquals(200f, s.box.w, 0.01f)
    }

    @Test fun groupChildrenAreScaledIntoTheSlide() {
        val inner = sp(0, 0, 1270000, 1270000, text("""<a:r><a:t>in</a:t></a:r>"""))
        val group = """<p:grpSp><p:nvGrpSpPr><p:cNvPr id="9" name="g"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="2540000" y="2540000"/><a:ext cx="2540000" cy="2540000"/><a:chOff x="0" y="0"/><a:chExt cx="1270000" cy="1270000"/></a:xfrm></p:grpSpPr>$inner</p:grpSp>"""
        val s = deck(slideXml(group)).slides[0].shapes.single() as PptxShape.Block
        assertEquals(200f, s.box.x, 0.01f)
        assertEquals(200f, s.box.w, 0.01f)
    }

    @Test fun tablesKeepCellText() {
        val tbl = """<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id="4" name="t"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr><p:xfrm><a:off x="0" y="0"/><a:ext cx="5080000" cy="1270000"/></p:xfrm>
            <a:graphic><a:graphicData><a:tbl><a:tblGrid><a:gridCol w="2540000"/><a:gridCol w="2540000"/></a:tblGrid>
            <a:tr><a:tc><p:txBody><a:bodyPr/><a:p><a:r><a:t>A</a:t></a:r></a:p></p:txBody></a:tc><a:tc><p:txBody><a:bodyPr/><a:p><a:r><a:t>B</a:t></a:r></a:p></p:txBody></a:tc></a:tr></a:tbl></a:graphicData></a:graphic></p:graphicFrame>"""
        val t = deck(slideXml(tbl)).slides[0].shapes.single() as PptxShape.Table
        assertEquals(listOf(listOf("A", "B")), t.rows)
        assertEquals(listOf(200f, 200f), t.colWidths)
    }

    @Test fun backgroundColourIsRead() {
        val d = deck(slideXml("", background = """<p:bg><p:bgPr><a:solidFill><a:srgbClr val="112233"/></a:solidFill></p:bgPr></p:bg>"""))
        assertEquals(0xFF112233.toInt(), d.slides[0].background)
        assertNull(deck(slideXml("")).slides[0].background)
    }

    @Test fun autofitShrinksFontSizes() {
        val shape = """<p:sp><p:nvSpPr><p:cNvPr id="2" name="s"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1270000" cy="1270000"/></a:xfrm></p:spPr><p:txBody><a:bodyPr><a:normAutofit fontScale="50000"/></a:bodyPr><a:p><a:r><a:rPr sz="2000"/><a:t>x</a:t></a:r></a:p></p:txBody></p:sp>"""
        val s = deck(slideXml(shape)).slides[0].shapes.single() as PptxShape.Block
        assertEquals(10f, s.paragraphs[0].runs[0].size!!, 0.001f)
    }

    @Test fun notAPresentationIsRejected() {
        try {
            PptxParser { null }.parse()
            fail("expected an exception")
        } catch (e: DocxException) {
            assertTrue(e.message!!.contains("PowerPoint"))
        }
    }
}
