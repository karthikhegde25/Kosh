package com.kosh.android.imagetools

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

class ImageToolsTest {

    // ---- sizes ----
    @Test fun resizeExactLockedFollowsTheTypedSide() {
        assertEquals(Pair(500, 250), resizeTarget(ResizeMode.EXACT, 1000, 500, 500, 999, 0, true, true))
        assertEquals(Pair(400, 200), resizeTarget(ResizeMode.EXACT, 1000, 500, 999, 200, 0, true, false))
        assertEquals(Pair(300, 300), resizeTarget(ResizeMode.EXACT, 1000, 500, 300, 300, 0, false, true))
    }

    @Test fun resizePercentAndFitBox() {
        assertEquals(Pair(250, 125), resizeTarget(ResizeMode.PERCENT, 1000, 500, 0, 0, 25, true, true))
        assertEquals(Pair(200, 100), resizeTarget(ResizeMode.FIT_BOX, 1000, 500, 200, 200, 0, true, true))
        assertEquals(Pair(1, 1), resizeTarget(ResizeMode.PERCENT, 10, 10, 0, 0, 1, true, true))
    }

    @Test fun fitRectCentres() {
        assertEquals(IRect(0, 125, 500, 250), fitRect(1000, 500, 500, 500, false))
        assertEquals(IRect(-250, 0, 1000, 500), fitRect(1000, 500, 500, 500, true))
    }

    @Test fun cropBoxes() {
        val b = largestCropBox(1000, 500, 1.0)
        assertEquals(IRect(250, 0, 500, 500), b.toRect(1000, 500))
        assertEquals(CropBox.FULL, largestCropBox(10, 10, null))
        val c = clampCropBox(CropBox(0.9, 0.9, 0.5, 0.5))
        assertEquals(0.5, c.x, 1e-9)
        assertEquals(IRect(0, 0, 10, 10), CropBox.FULL.toRect(10, 10))
    }

    // ---- collage ----
    @Test fun collageCellsStayInsideAndDoNotOverlap() {
        for (n in 2..4) for (layout in collageLayouts(n)) {
            val cells = collageCells(layout, n, 1000, 800, 10)
            assertEquals("$layout $n", n, cells.size)
            cells.forEach { assertTrue("$layout $it", it.x >= 10 && it.y >= 10 && it.x + it.w <= 990 && it.y + it.h <= 790 && it.w > 0 && it.h > 0) }
            for (i in cells.indices) for (j in i + 1 until cells.size) {
                val a = cells[i]; val b = cells[j]
                val overlap = a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h
                assertTrue("$layout overlap $a $b", !overlap)
            }
        }
    }

    // ---- names ----
    @Test fun outputNames() {
        assertEquals(listOf("a-small.jpg", "b-small.jpg"), outputNames(listOf("a.png", "b.jpeg"), "", "small", "jpg"))
        assertEquals(listOf("trip.png"), outputNames(listOf("a.png"), "trip", "x", "png"))
        assertEquals(listOf("trip-1.png", "trip-2.png"), outputNames(listOf("a.png", "b.png"), "trip", "x", "png"))
        assertEquals(listOf("a-x.png", "a-x (2).png"), outputNames(listOf("a.png", "a.png"), "", "x", "png"))
        assertEquals(listOf("a-1.png", "a-2.png"), outputNames(listOf("z.png"), "a", "", "png", perSource = 2))
        assertEquals("a_b", safeName("a/b"))
    }

    // ---- pixels ----
    @Test fun matrixIdentityAndInvert() {
        val px = intArrayOf(0xFF102030.toInt(), 0x80FFFFFF.toInt())
        val copy = px.copyOf()
        applyMatrix(copy, IDENTITY)
        assertArrayEquals(px, copy)
        applyMatrix(copy, effectMatrix(FilterEffect.INVERT)!!)
        assertEquals(0xFFEFDFCF.toInt(), copy[0])
        assertEquals(0x80000000.toInt(), copy[1])
    }

    @Test fun colorMatrixDefaultsDoNothingAndBrightnessLifts() {
        val m = colorMatrix(0, 0, 0, 0, 0)
        val px = intArrayOf(0xFF336699.toInt())
        applyMatrix(px, m)
        assertEquals(0xFF336699.toInt(), px[0])
        val lift = intArrayOf(0xFF336699.toInt())
        applyMatrix(lift, colorMatrix(50, 0, 0, 0, 0))
        assertTrue(((lift[0] shr 16) and 255) > 0x33)
        val gray = intArrayOf(0xFFFF0000.toInt())
        applyMatrix(gray, colorMatrix(0, 0, -100, 0, 0))
        assertEquals((gray[0] shr 16) and 255, (gray[0] and 255))
    }

    @Test fun thresholdPosterizeBlurSharpen() {
        val t = intArrayOf(0xFF101010.toInt(), 0xFFF0F0F0.toInt())
        threshold(t)
        assertEquals(0xFF000000.toInt(), t[0]); assertEquals(0xFFFFFFFF.toInt(), t[1])
        val p = intArrayOf(0xFF7F7F7F.toInt())
        posterize(p, 2)
        assertTrue(p[0] == 0xFF000000.toInt() || p[0] == 0xFFFFFFFF.toInt())
        // a flat picture stays flat when blurred or sharpened
        val flat = IntArray(25) { 0xFF808080.toInt() }
        val b = flat.copyOf(); boxBlur(b, 5, 5, 2); assertArrayEquals(flat, b)
        val s = flat.copyOf(); sharpen(s, 5, 5); assertArrayEquals(flat, s)
        // blur spreads a bright pixel
        val dot = IntArray(25) { 0xFF000000.toInt() }; dot[12] = 0xFFFFFFFF.toInt()
        boxBlur(dot, 5, 5, 1)
        assertTrue((dot[12] and 255) < 255 && (dot[13] and 255) > 0)
    }

    @Test fun vignetteDarkensCornersMore() {
        val px = IntArray(81) { 0xFFFFFFFF.toInt() }
        vignette(px, 9, 9, 0.8f)
        assertTrue((px[0] and 255) < (px[40] and 255))
    }

    @Test fun paletteFindsTheMainColours() {
        val px = IntArray(1000) { if (it < 700) 0xFFFF0000.toInt() else 0xFF0000FF.toInt() }
        val pal = extractPalette(px, 2)
        assertEquals(2, pal.size)
        assertEquals(0xFFFF0000.toInt(), pal[0])
        assertEquals(0xFF0000FF.toInt(), pal[1])
        assertTrue(extractPalette(IntArray(4) { 0 }, 3).isEmpty())
        assertEquals("#FF0000", argbToHex(0xFFFF0000.toInt()))
        assertEquals(0xFF00FF00.toInt(), parseHex("#00ff00"))
        assertEquals(null, parseHex("zz"))
    }

    // ---- bytes ----
    @Test fun bmpHasTheRightHeaderAndSize() {
        val bmp = encodeBmp(3, 2, IntArray(6) { 0xFFFF0000.toInt() })
        assertEquals('B'.code.toByte(), bmp[0])
        assertEquals(54 + 12 * 2, bmp.size) // rows of 9 bytes padded to 12
        assertEquals(0x00.toByte(), bmp[54]); assertEquals(0xFF.toByte(), bmp[56]) // B, G, R of the first pixel
    }

    @Test fun icoWrapsAPng() {
        val ico = icoFromPng(byteArrayOf(1, 2, 3), 256, 16)
        assertEquals(22 + 3, ico.size)
        assertEquals(0.toByte(), ico[6]); assertEquals(16.toByte(), ico[7])
    }

    private fun jpeg(vararg extraSegments: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        out.write(segment(0xE0, "JFIF\u0000".toByteArray() + ByteArray(9)))
        extraSegments.forEach { out.write(it) }
        out.write(byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0, 4, 0, 0, 1, 2, 3, 0xFF.toByte(), 0xD9.toByte()))
        return out.toByteArray()
    }

    private fun segment(marker: Int, data: ByteArray): ByteArray =
        byteArrayOf(0xFF.toByte(), marker.toByte(), ((data.size + 2) shr 8).toByte(), ((data.size + 2) and 255).toByte()) + data

    @Test fun jpegMetadataIsListedAndStripped() {
        val exif = segment(0xE1, "Exif\u0000\u0000abcdef".toByteArray())
        val xmp = segment(0xE1, "http://ns.adobe.com/xap/1.0/\u0000<x/>".toByteArray())
        val com = segment(0xFE, "hello".toByteArray())
        val j = jpeg(exif, xmp, com)
        assertEquals(setOf(MetaKind.EXIF, MetaKind.XMP, MetaKind.COMMENT), listMetadata(j).map { it.kind }.toSet())
        val noExif = stripMetadata(j, setOf(MetaKind.EXIF))
        assertEquals(j.size - exif.size, noExif.size)
        assertTrue(listMetadata(noExif).none { it.kind == MetaKind.EXIF })
        val all = stripMetadata(j, MetaKind.entries.toSet())
        assertEquals(j.size - exif.size - xmp.size - com.size, all.size)
        // the JFIF header and the picture data survive
        assertEquals(ImageContainer.JPEG, containerOf(all))
        assertEquals(0xD9.toByte(), all.last())
    }

    @Test fun jpegPaddingIsExactAndKeepsTheTail() {
        val j = jpeg()
        for (extra in longArrayOf(5, 100, 1024, 65537, 65538, 65541, 70000, 200_000)) {
            val padded = padFile(j, extra, "ab")
            assertEquals("extra $extra", j.size + extra, padded.size.toLong())
            assertEquals(0xD9.toByte(), padded.last())
        }
        assertEquals(j.size, padFile(j, 3, "x").size)
    }

    private fun crcOf(type: String, body: ByteArray): ByteArray {
        val c = CRC32(); c.update(type.toByteArray()); c.update(body)
        return byteArrayOf((c.value shr 24).toByte(), (c.value shr 16).toByte(), (c.value shr 8).toByte(), c.value.toByte())
    }

    private fun chunk(type: String, body: ByteArray): ByteArray =
        byteArrayOf((body.size shr 24).toByte(), (body.size shr 16).toByte(), (body.size shr 8).toByte(), body.size.toByte()) + type.toByteArray() + body + crcOf(type, body)

    private fun png(vararg extra: ByteArray): ByteArray {
        val sig = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
        var out = sig + chunk("IHDR", ByteArray(13))
        extra.forEach { out += it }
        return out + chunk("IDAT", byteArrayOf(1, 2, 3)) + chunk("IEND", ByteArray(0))
    }

    @Test fun pngMetadataAndPadding() {
        val text = chunk("tEXt", "Software\u0000Cam".toByteArray())
        val time = chunk("tIME", ByteArray(7))
        val p = png(text, time)
        assertEquals(setOf(MetaKind.TEXT, MetaKind.TIME), listMetadata(p).map { it.kind }.toSet())
        assertEquals(p.size - text.size, stripMetadata(p, setOf(MetaKind.TEXT)).size)
        for (extra in longArrayOf(21, 500, 1024, 1_048_576, 1_048_580, 3_000_000)) {
            val padded = padFile(p, extra, "xyz")
            assertEquals("extra $extra", p.size + extra, padded.size.toLong())
            // the padding is valid chunks: IEND is still last and nothing but text was added
            assertEquals(listOf(MetaKind.TEXT), listMetadata(padded).map { it.kind }.distinct().filter { it == MetaKind.TEXT })
            assertEquals("IEND", String(padded, padded.size - 8, 4))
        }
        assertEquals(p.size, padFile(p, 10, "x").size)
    }

    @Test fun otherFormatsAreLeftAlone() {
        val junk = ByteArray(20) { 7 }
        assertEquals(ImageContainer.OTHER, containerOf(junk))
        assertArrayEquals(junk, padFile(junk, 1000, "x"))
        assertArrayEquals(junk, stripMetadata(junk, MetaKind.entries.toSet()))
        assertNotNull(formatsFor(ImageTool.CONVERT))
        assertEquals(emptyList<OutFormat>(), formatsFor(ImageTool.STRIP))
    }
}
