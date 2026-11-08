package com.kosh.android.pdf

import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Writes a genuine multi-page PDF where every page is one JPEG image, streaming page by page so memory stays low.
 * Embedding the JPEG as-is (DCTDecode) keeps files far smaller than Android's PdfDocument, which stores
 * bitmaps losslessly. No Android classes are used, so the output is unit-tested on the JVM.
 *
 * Object numbering: 1 catalog, 2 page tree, 3 info, then for page i (0-based): 4+3i page, 5+3i content, 6+3i image.
 */
class JpegPdfWriter(
    output: OutputStream,
    private val pageCount: Int,
    private val title: String? = null,
) {
    class PageImage(val jpeg: ByteArray, val widthPx: Int, val heightPx: Int, val geometry: PageGeometry)

    private class Counting(private val out: OutputStream) : OutputStream() {
        var count = 0L
            private set
        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
        override fun flush() = out.flush()
    }

    private val out = Counting(output)
    private val objectCount = 3 + 3 * pageCount
    private val offsets = LongArray(objectCount + 1)
    private var pagesWritten = 0
    private var begun = false
    private var finished = false

    init {
        require(pageCount > 0) { "a PDF needs at least one page" }
    }

    fun begin() {
        check(!begun) { "already started" }
        begun = true
        write("%PDF-1.4\n")
        out.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))

        obj(1) { write("<< /Type /Catalog /Pages 2 0 R >>\n") }
        obj(2) {
            val kids = (0 until pageCount).joinToString(" ") { "${4 + 3 * it} 0 R" }
            write("<< /Type /Pages /Kids [$kids] /Count $pageCount >>\n")
        }
        obj(3) {
            val date = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
            write("<< /Producer (Kosh) /CreationDate (D:${date}Z)")
            if (!title.isNullOrBlank()) write(" /Title <${utf16Hex(title)}>")
            write(" >>\n")
        }
    }

    fun addPage(page: PageImage) {
        check(begun && !finished) { "writer is not open" }
        check(pagesWritten < pageCount) { "more pages than announced" }
        val i = pagesWritten
        val pageObj = 4 + 3 * i
        val contentObj = pageObj + 1
        val imageObj = pageObj + 2
        val g = page.geometry
        val yBottom = g.pageHeight - g.bottom // PDF's origin is the bottom-left corner

        obj(pageObj) {
            write(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${f(g.pageWidth)} ${f(g.pageHeight)}] " +
                    "/Resources << /XObject << /Im0 $imageObj 0 R >> >> /Contents $contentObj 0 R >>\n",
            )
        }
        val content = "q\n${f(g.imageWidth)} 0 0 ${f(g.imageHeight)} ${f(g.left)} ${f(yBottom)} cm\n/Im0 Do\nQ\n"
        obj(contentObj) {
            write("<< /Length ${content.length} >>\nstream\n$content" + "endstream\n")
        }
        obj(imageObj) {
            write(
                "<< /Type /XObject /Subtype /Image /Width ${page.widthPx} /Height ${page.heightPx} " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${page.jpeg.size} >>\nstream\n",
            )
            out.write(page.jpeg)
            write("\nendstream\n")
        }
        pagesWritten++
    }

    fun finish() {
        check(begun && !finished) { "writer is not open" }
        check(pagesWritten == pageCount) { "wrote $pagesWritten of $pageCount pages" }
        finished = true
        val xrefOffset = out.count
        write("xref\n0 ${objectCount + 1}\n")
        write("0000000000 65535 f \n")
        for (id in 1..objectCount) write(String.format(Locale.US, "%010d 00000 n \n", offsets[id]))
        write("trailer\n<< /Size ${objectCount + 1} /Root 1 0 R /Info 3 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        out.flush()
    }

    private inline fun obj(id: Int, body: () -> Unit) {
        offsets[id] = out.count
        write("$id 0 obj\n")
        body()
        write("endobj\n")
    }

    private fun write(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))

    private fun f(v: Float): String = String.format(Locale.US, "%.2f", v)

    private fun utf16Hex(s: String): String =
        "FEFF" + s.toByteArray(Charsets.UTF_16BE).joinToString("") { String.format(Locale.US, "%02X", it) }
}
