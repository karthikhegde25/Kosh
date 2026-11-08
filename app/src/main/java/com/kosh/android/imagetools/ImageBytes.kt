package com.kosh.android.imagetools

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/** Byte-level image work: BMP / ICO writing, padding a file's size, and finding / removing metadata. */

// ---- BMP and ICO ---------------------------------------------------------------------------------------------------------

/** A 24-bit BMP. [argb] pixels are laid over white first (BMP here has no transparency). */
fun encodeBmp(w: Int, h: Int, argb: IntArray): ByteArray {
    val rowBytes = (w * 3 + 3) / 4 * 4
    val imageSize = rowBytes * h
    val out = ByteArrayOutputStream(54 + imageSize)
    fun le32(v: Int) { out.write(v and 255); out.write((v shr 8) and 255); out.write((v shr 16) and 255); out.write((v shr 24) and 255) }
    fun le16(v: Int) { out.write(v and 255); out.write((v shr 8) and 255) }
    out.write('B'.code); out.write('M'.code)
    le32(54 + imageSize); le32(0); le32(54)
    le32(40); le32(w); le32(h); le16(1); le16(24); le32(0); le32(imageSize); le32(2835); le32(2835); le32(0); le32(0)
    val row = ByteArray(rowBytes)
    for (y in h - 1 downTo 0) {
        java.util.Arrays.fill(row, 0)
        for (x in 0 until w) {
            val p = argb[y * w + x]
            val a = p ushr 24
            fun over(c: Int) = (c * a + 255 * (255 - a)) / 255
            row[x * 3] = over(p and 255).toByte()
            row[x * 3 + 1] = over((p shr 8) and 255).toByte()
            row[x * 3 + 2] = over((p shr 16) and 255).toByte()
        }
        out.write(row)
    }
    return out.toByteArray()
}

/** An .ico holding one PNG-compressed image of at most 256x256 (a size of 256 is stored as 0). */
fun icoFromPng(png: ByteArray, w: Int, h: Int): ByteArray {
    val out = ByteArrayOutputStream(22 + png.size)
    fun le32(v: Int) { out.write(v and 255); out.write((v shr 8) and 255); out.write((v shr 16) and 255); out.write((v shr 24) and 255) }
    fun le16(v: Int) { out.write(v and 255); out.write((v shr 8) and 255) }
    le16(0); le16(1); le16(1)
    out.write(if (w >= 256) 0 else w); out.write(if (h >= 256) 0 else h); out.write(0); out.write(0)
    le16(1); le16(32); le32(png.size); le32(22)
    out.write(png)
    return out.toByteArray()
}

// ---- metadata -----------------------------------------------------------------------------------------------------------

enum class MetaKind { EXIF, XMP, IPTC, ICC, COMMENT, TEXT, TIME, OTHER }

/** One removable piece of metadata. [bytes] is how much it takes up. */
data class MetaItem(val kind: MetaKind, val bytes: Int)

enum class ImageContainer { JPEG, PNG, OTHER }

fun containerOf(b: ByteArray): ImageContainer = when {
    b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> ImageContainer.JPEG
    b.size > 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte() -> ImageContainer.PNG
    else -> ImageContainer.OTHER
}

private data class JpegSeg(val start: Int, val end: Int, val marker: Int, val kind: MetaKind?)

private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 255) shl 8) or (b[i + 1].toInt() and 255)
private fun u32(b: ByteArray, i: Int) = (u16(b, i) shl 16) or u16(b, i + 2)

private fun startsWith(b: ByteArray, at: Int, text: String): Boolean {
    if (at + text.length > b.size) return false
    for (i in text.indices) if (b[at + i] != text[i].code.toByte()) return false
    return true
}

/** The header segments of a JPEG up to the start of the picture data. Returns the segments and the offset of the first SOS marker. */
private fun jpegSegments(b: ByteArray): Pair<List<JpegSeg>, Int> {
    val segs = ArrayList<JpegSeg>()
    var i = 2
    while (i + 4 <= b.size) {
        if (b[i] != 0xFF.toByte()) break
        val marker = b[i + 1].toInt() and 255
        if (marker == 0xFF) { i++; continue }
        if (marker == 0xDA) return Pair(segs, i)
        if (marker == 0x01 || marker in 0xD0..0xD8) { i += 2; continue }
        val len = u16(b, i + 2)
        val end = i + 2 + len
        if (len < 2 || end > b.size) break
        val dataAt = i + 4
        val kind = when (marker) {
            0xE1 -> if (startsWith(b, dataAt, "Exif")) MetaKind.EXIF else if (startsWith(b, dataAt, "http://ns.adobe.com/xap")) MetaKind.XMP else MetaKind.OTHER
            0xE2 -> if (startsWith(b, dataAt, "ICC_PROFILE")) MetaKind.ICC else MetaKind.OTHER
            0xED -> MetaKind.IPTC
            0xFE -> MetaKind.COMMENT
            in 0xE3..0xEC, 0xEE, 0xEF -> if (marker == 0xEE) null else MetaKind.OTHER // APP14 (Adobe) holds colour info; keep it
            else -> null
        }
        segs += JpegSeg(i, end, marker, kind)
        i = end
    }
    return Pair(segs, b.size)
}

fun listMetadata(b: ByteArray): List<MetaItem> = when (containerOf(b)) {
    ImageContainer.JPEG -> jpegSegments(b).first.filter { it.kind != null }.groupBy { it.kind!! }
        .map { (k, v) -> MetaItem(k, v.sumOf { it.end - it.start }) }
    ImageContainer.PNG -> pngChunks(b).filter { it.kind != null }.groupBy { it.kind!! }
        .map { (k, v) -> MetaItem(k, v.sumOf { it.end - it.start }) }
    ImageContainer.OTHER -> emptyList()
}

/** The file without the given kinds of metadata. The picture data is copied untouched. */
fun stripMetadata(b: ByteArray, remove: Set<MetaKind>): ByteArray {
    if (remove.isEmpty()) return b
    return when (containerOf(b)) {
        ImageContainer.JPEG -> {
            val drop = jpegSegments(b).first.filter { it.kind in remove }
            cut(b, drop.map { it.start to it.end })
        }
        ImageContainer.PNG -> cut(b, pngChunks(b).filter { it.kind in remove }.map { it.start to it.end })
        ImageContainer.OTHER -> b
    }
}

private fun cut(b: ByteArray, ranges: List<Pair<Int, Int>>): ByteArray {
    if (ranges.isEmpty()) return b
    val out = ByteArrayOutputStream(b.size)
    var at = 0
    for ((s, e) in ranges.sortedBy { it.first }) { out.write(b, at, s - at); at = e }
    out.write(b, at, b.size - at)
    return out.toByteArray()
}

private data class PngChunk(val start: Int, val end: Int, val type: String, val kind: MetaKind?)

private fun pngChunks(b: ByteArray): List<PngChunk> {
    val out = ArrayList<PngChunk>()
    var i = 8
    while (i + 12 <= b.size) {
        val len = u32(b, i)
        if (len < 0 || i + 12 + len > b.size) break
        val type = String(b, i + 4, 4, Charsets.ISO_8859_1)
        val kind = when (type) {
            "tEXt", "zTXt", "iTXt" -> MetaKind.TEXT
            "eXIf" -> MetaKind.EXIF
            "tIME" -> MetaKind.TIME
            "iCCP" -> MetaKind.ICC
            else -> null
        }
        out += PngChunk(i, i + 12 + len, type, kind)
        i += 12 + len
        if (type == "IEND") break
    }
    return out
}

// ---- making a file bigger ----------------------------------------------------------------------------------------------

private fun fillBytes(fill: String, n: Int): ByteArray {
    val src = (fill.ifEmpty { "0" }).toByteArray(Charsets.UTF_8)
    return ByteArray(n) { src[it % src.size] }
}

/** Splits [extra] bytes into pieces of at most [maxPiece], none smaller than [minPiece]. */
private fun pieces(extra: Long, maxPiece: Int, minPiece: Int): List<Int> {
    val out = ArrayList<Int>()
    var left = extra
    while (left > 0) {
        var p = minOf(left, maxPiece.toLong())
        val rest = left - p
        if (rest in 1 until minPiece) p -= (minPiece - rest)
        out += p.toInt()
        left -= p
    }
    return out
}

/**
 * Adds about [extra] bytes of padding without touching the picture: JPEG comment segments (just before the picture data) or
 * PNG text chunks (before IEND). The file still opens normally. Other formats are returned unchanged. When [extra] is too
 * small to hold one segment the file is returned as it was.
 */
fun padFile(b: ByteArray, extra: Long, fill: String): ByteArray = when (containerOf(b)) {
    ImageContainer.JPEG -> padJpeg(b, extra, fill)
    ImageContainer.PNG -> padPng(b, extra, fill)
    ImageContainer.OTHER -> b
}

private fun padJpeg(b: ByteArray, extra: Long, fill: String): ByteArray {
    if (extra < 5) return b
    val at = jpegSegments(b).second
    if (at >= b.size) return b
    val out = ByteArrayOutputStream(b.size + extra.toInt())
    out.write(b, 0, at)
    for (p in pieces(extra, 65537, 5)) {
        val data = p - 4
        out.write(0xFF); out.write(0xFE); out.write((data + 2) shr 8); out.write((data + 2) and 255)
        out.write(fillBytes(fill, data))
    }
    out.write(b, at, b.size - at)
    return out.toByteArray()
}

private fun padPng(b: ByteArray, extra: Long, fill: String): ByteArray {
    if (extra < 21) return b
    val chunks = pngChunks(b)
    val iend = chunks.lastOrNull { it.type == "IEND" } ?: return b
    val out = ByteArrayOutputStream(b.size + extra.toInt())
    out.write(b, 0, iend.start)
    val key = "Comment".toByteArray(Charsets.ISO_8859_1)
    // a tEXt chunk: 12 bytes of framing + "Comment" + NUL + text
    for (p in pieces(extra, 1 shl 20, 21)) {
        val textLen = p - 12 - key.size - 1
        val data = ByteArrayOutputStream(p)
        data.write(key); data.write(0); data.write(fillBytes(fill, textLen))
        val body = data.toByteArray()
        val type = "tEXt".toByteArray(Charsets.ISO_8859_1)
        for (s in intArrayOf(24, 16, 8, 0)) out.write((body.size shr s) and 255)
        out.write(type); out.write(body)
        val crc = CRC32(); crc.update(type); crc.update(body)
        val c = crc.value
        for (s in intArrayOf(24, 16, 8, 0)) out.write(((c shr s) and 255).toInt())
    }
    out.write(b, iend.start, b.size - iend.start)
    return out.toByteArray()
}
