package com.kosh.android.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class JpegPdfWriterTest {

    private val geometry = PageGeometry(842f, 631.5f, 0f, 0f, 842f, 631.5f)

    private fun build(pages: Int, title: String? = "My Scan", geo: PageGeometry = geometry): ByteArray {
        val out = ByteArrayOutputStream()
        val writer = JpegPdfWriter(out, pages, title)
        writer.begin()
        repeat(pages) { writer.addPage(JpegPdfWriter.PageImage(ByteArray(100) { it.toByte() }, 640, 480, geo)) }
        writer.finish()
        return out.toByteArray()
    }

    private fun text(bytes: ByteArray) = String(bytes, Charsets.ISO_8859_1) // 1 char = 1 byte, so offsets match

    @Test
    fun hasPdfHeaderAndEndMarker() {
        val t = text(build(2))
        assertTrue(t.startsWith("%PDF-1.4"))
        assertTrue(t.trimEnd().endsWith("%%EOF"))
    }

    @Test
    fun containsOnePageObjectPerImage() {
        val t = text(build(3))
        assertEquals(3, "/Type /Page ".toRegex().findAll(t).count())
        assertTrue(t.contains("/Count 3"))
        assertEquals(3, "/Filter /DCTDecode".toRegex().findAll(t).count())
    }

    @Test
    fun crossReferenceOffsetsPointAtTheirObjects() {
        val pages = 3
        val t = text(build(pages))
        val startXref = t.lastIndexOf("startxref\n")
        val xrefOffset = t.substring(startXref + "startxref\n".length).lineSequence().first().trim().toInt()
        val lines = t.substring(xrefOffset).split("\n")
        assertEquals("xref", lines[0])
        val objectCount = 3 + 3 * pages
        assertEquals("0 ${objectCount + 1}", lines[1])
        for (id in 1..objectCount) {
            val entry = lines[2 + id] // lines[2] is the free entry
            assertEquals("entry $id must be 19 chars plus newline", 19, entry.length)
            val offset = entry.substring(0, 10).toInt()
            assertTrue("object $id at $offset", t.substring(offset).startsWith("$id 0 obj"))
        }
    }

    @Test
    fun imageStreamLengthMatchesTheData() {
        val t = text(build(1))
        assertTrue(t.contains("/Length 100 >>"))
        assertTrue(t.contains("/Width 640") && t.contains("/Height 480"))
    }

    @Test
    fun placesTheImageUsingTopLeftCoordinates() {
        // Image 200pt high with its top edge 100pt from the top of an 842pt page: bottom edge is 542pt from the bottom.
        val geo = PageGeometry(595f, 842f, 50f, 100f, 350f, 300f)
        val t = text(build(1, geo = geo))
        assertTrue(t.contains("300.00 0 0 200.00 50.00 542.00 cm"))
        assertTrue(t.contains("/MediaBox [0 0 595.00 842.00]"))
    }

    @Test
    fun titleIsStoredAsUtf16Hex() {
        val t = text(build(1, title = "My Scan"))
        assertTrue(t.contains("/Title <FEFF004D00790020005300630061006E>"))
        assertTrue(!text(build(1, title = null)).contains("/Title"))
    }

    @Test(expected = IllegalStateException::class)
    fun finishingWithMissingPagesFails() {
        val writer = JpegPdfWriter(ByteArrayOutputStream(), 2)
        writer.begin()
        writer.addPage(JpegPdfWriter.PageImage(ByteArray(4), 1, 1, geometry))
        writer.finish()
    }

    @Test(expected = IllegalStateException::class)
    fun addingMorePagesThanAnnouncedFails() {
        val writer = JpegPdfWriter(ByteArrayOutputStream(), 1)
        writer.begin()
        writer.addPage(JpegPdfWriter.PageImage(ByteArray(4), 1, 1, geometry))
        writer.addPage(JpegPdfWriter.PageImage(ByteArray(4), 1, 1, geometry))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroPagesIsRejected() {
        JpegPdfWriter(ByteArrayOutputStream(), 0)
    }
}
