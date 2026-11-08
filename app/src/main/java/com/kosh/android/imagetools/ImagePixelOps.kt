package com.kosh.android.imagetools

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Pixel arithmetic on ARGB int arrays. No Android classes, so it is tested on a plain JVM. */

// ---- colour matrices (4 rows of R, G, B, A + offset; offsets are 0..255) --------------------------------------------

val IDENTITY: FloatArray = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

/** [a] applied after [b]. */
fun multiplyMatrix(a: FloatArray, b: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (r in 0 until 4) {
        for (c in 0 until 5) {
            var v = 0f
            for (k in 0 until 4) v += a[r * 5 + k] * b[k * 5 + c]
            if (c == 4) v += a[r * 5 + 4]
            out[r * 5 + c] = v
        }
    }
    return out
}

private fun scaleMatrix(k: Float, offset: Float = 0f) = floatArrayOf(
    k, 0f, 0f, 0f, offset,
    0f, k, 0f, 0f, offset,
    0f, 0f, k, 0f, offset,
    0f, 0f, 0f, 1f, 0f,
)

fun saturationMatrix(s: Float): FloatArray {
    val lr = 0.2126f; val lg = 0.7152f; val lb = 0.0722f
    val i = 1f - s
    return floatArrayOf(
        lr * i + s, lg * i, lb * i, 0f, 0f,
        lr * i, lg * i + s, lb * i, 0f, 0f,
        lr * i, lg * i, lb * i + s, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

fun hueMatrix(degrees: Float): FloatArray {
    val a = degrees * PI.toFloat() / 180f
    val c = cos(a); val s = sin(a)
    val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
    return floatArrayOf(
        lr + c * (1 - lr) + s * -lr, lg + c * -lg + s * -lg, lb + c * -lb + s * (1 - lb), 0f, 0f,
        lr + c * -lr + s * 0.143f, lg + c * (1 - lg) + s * 0.140f, lb + c * -lb + s * -0.283f, 0f, 0f,
        lr + c * -lr + s * -(1 - lr), lg + c * -lg + s * lg, lb + c * (1 - lb) + s * lb, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** Brightness, contrast, saturation, exposure: -100..100 (0 = unchanged); hue: -180..180 degrees. */
fun colorMatrix(brightness: Int, contrast: Int, saturation: Int, hue: Int, exposure: Int): FloatArray {
    var m = IDENTITY
    m = multiplyMatrix(scaleMatrix(2f.pow(exposure / 50f)), m)
    val c = 1f + contrast / 100f
    m = multiplyMatrix(scaleMatrix(c, (1f - c) * 128f), m)
    m = multiplyMatrix(scaleMatrix(1f, brightness / 100f * 127f), m)
    m = multiplyMatrix(saturationMatrix(1f + saturation / 100f), m)
    if (hue != 0) m = multiplyMatrix(hueMatrix(hue.toFloat()), m)
    return m
}

enum class FilterEffect { NONE, GRAYSCALE, SEPIA, INVERT, VINTAGE, WARM, COOL, POSTERIZE, THRESHOLD, VIGNETTE, BLUR, SHARPEN }

/** The colour matrix of an effect that is only a colour change; null for the others. */
fun effectMatrix(effect: FilterEffect): FloatArray? = when (effect) {
    FilterEffect.GRAYSCALE -> saturationMatrix(0f)
    FilterEffect.SEPIA -> floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
    FilterEffect.INVERT -> floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )
    FilterEffect.VINTAGE -> multiplyMatrix(
        floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
        multiplyMatrix(scaleMatrix(0.9f, 12f), saturationMatrix(0.8f)),
    )
    FilterEffect.WARM -> floatArrayOf(
        1.10f, 0f, 0f, 0f, 8f,
        0f, 1.0f, 0f, 0f, 0f,
        0f, 0f, 0.88f, 0f, -8f,
        0f, 0f, 0f, 1f, 0f,
    )
    FilterEffect.COOL -> floatArrayOf(
        0.88f, 0f, 0f, 0f, -8f,
        0f, 1.0f, 0f, 0f, 0f,
        0f, 0f, 1.10f, 0f, 8f,
        0f, 0f, 0f, 1f, 0f,
    )
    else -> null
}

fun applyMatrix(px: IntArray, m: FloatArray) {
    for (i in px.indices) {
        val p = px[i]
        val a = p ushr 24
        val r = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
        val nr = (m[0] * r + m[1] * g + m[2] * b + m[4]).toInt().coerceIn(0, 255)
        val ng = (m[5] * r + m[6] * g + m[7] * b + m[9]).toInt().coerceIn(0, 255)
        val nb = (m[10] * r + m[11] * g + m[12] * b + m[14]).toInt().coerceIn(0, 255)
        px[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
    }
}

fun posterize(px: IntArray, levels: Int = 4) {
    val step = 255f / (levels - 1)
    fun q(v: Int) = ((v / 255f * (levels - 1)).let { Math.round(it) } * step).toInt().coerceIn(0, 255)
    for (i in px.indices) {
        val p = px[i]
        px[i] = (p and (255 shl 24)) or (q((p shr 16) and 255) shl 16) or (q((p shr 8) and 255) shl 8) or q(p and 255)
    }
}

fun threshold(px: IntArray, level: Int = 128) {
    for (i in px.indices) {
        val p = px[i]
        val l = 0.2126f * ((p shr 16) and 255) + 0.7152f * ((p shr 8) and 255) + 0.0722f * (p and 255)
        val v = if (l >= level) 255 else 0
        px[i] = (p and (255 shl 24)) or (v shl 16) or (v shl 8) or v
    }
}

/** Darkens towards the corners. [strength] 0..1. */
fun vignette(px: IntArray, w: Int, h: Int, strength: Float = 0.6f) {
    val cx = (w - 1) / 2f; val cy = (h - 1) / 2f
    val maxD = sqrt(cx * cx + cy * cy).coerceAtLeast(1f)
    for (y in 0 until h) for (x in 0 until w) {
        val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / maxD
        val k = 1f - strength * (d * d).coerceIn(0f, 1f)
        val i = y * w + x
        val p = px[i]
        px[i] = (p and (255 shl 24)) or
            ((((p shr 16) and 255) * k).toInt() shl 16) or ((((p shr 8) and 255) * k).toInt() shl 8) or ((p and 255) * k).toInt()
    }
}

/** Box blur (two passes of a running sum, horizontally then vertically), edges repeat. */
fun boxBlur(px: IntArray, w: Int, h: Int, radius: Int) {
    if (radius < 1 || w < 2 || h < 2) return
    val tmp = IntArray(px.size)
    blurPass(px, tmp, w, h, radius, true)
    blurPass(tmp, px, w, h, radius, false)
}

private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    val window = 2 * r + 1
    for (line in 0 until lines) {
        fun at(i: Int): Int {
            val k = i.coerceIn(0, len - 1)
            return if (horizontal) src[line * w + k] else src[k * w + line]
        }
        var sa = 0; var sr = 0; var sg = 0; var sb = 0
        for (i in -r..r) { val p = at(i); sa += p ushr 24; sr += (p shr 16) and 255; sg += (p shr 8) and 255; sb += p and 255 }
        for (i in 0 until len) {
            val o = ((sa / window) shl 24) or ((sr / window) shl 16) or ((sg / window) shl 8) or (sb / window)
            if (horizontal) dst[line * w + i] = o else dst[i * w + line] = o
            val out = at(i - r); val inn = at(i + r + 1)
            sa += (inn ushr 24) - (out ushr 24)
            sr += ((inn shr 16) and 255) - ((out shr 16) and 255)
            sg += ((inn shr 8) and 255) - ((out shr 8) and 255)
            sb += (inn and 255) - (out and 255)
        }
    }
}

/** Sharpen with a 3x3 kernel; [amount] 0..1 is how much of the edge is added back. */
fun sharpen(px: IntArray, w: Int, h: Int, amount: Float = 0.8f) {
    if (w < 3 || h < 3) return
    val src = px.copyOf()
    for (y in 1 until h - 1) for (x in 1 until w - 1) {
        val i = y * w + x
        var out = src[i] and (255 shl 24)
        for (shift in intArrayOf(16, 8, 0)) {
            val c = (src[i] shr shift) and 255
            val n = ((src[i - 1] shr shift) and 255) + ((src[i + 1] shr shift) and 255) +
                ((src[i - w] shr shift) and 255) + ((src[i + w] shr shift) and 255)
            val v = (c + amount * (4 * c - n)).toInt().coerceIn(0, 255)
            out = out or (v shl shift)
        }
        px[i] = out
    }
}

// ---- palette -----------------------------------------------------------------------------------------------------------

/** The [count] most characteristic colours (median cut), most common first, as opaque ARGB ints. */
fun extractPalette(px: IntArray, count: Int): List<Int> {
    val step = max(1, px.size / 40000)
    val samples = ArrayList<Int>()
    var i = 0
    while (i < px.size) { if ((px[i] ushr 24) >= 128) samples += px[i] and 0xFFFFFF; i += step }
    if (samples.isEmpty()) return emptyList()
    var boxes: MutableList<List<Int>> = mutableListOf(samples)
    while (boxes.size < count) {
        val idx = boxes.indices.filter { boxes[it].size > 1 }.maxByOrNull { rangeOf(boxes[it]).second } ?: break
        val box = boxes[idx]
        if (rangeOf(box).second == 0) break
        val channel = rangeOf(box).first
        // Cut halfway along the widest channel's range (separates distinct colours better than cutting at the median).
        var lo = 255; var hi = 0
        for (c in box) { val v = (c shr channel) and 255; if (v < lo) lo = v; if (v > hi) hi = v }
        val cutAt = (lo + hi) / 2
        val low = box.filter { ((it shr channel) and 255) <= cutAt }
        val high = box.filter { ((it shr channel) and 255) > cutAt }
        boxes.removeAt(idx)
        boxes.add(low); boxes.add(high)
    }
    return boxes.filter { it.isNotEmpty() }.sortedByDescending { it.size }.map { b ->
        var r = 0L; var g = 0L; var bl = 0L
        for (c in b) { r += (c shr 16) and 255; g += (c shr 8) and 255; bl += c and 255 }
        val n = b.size
        (255 shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
    }
}

/** (channel shift of the widest colour channel, its range). */
private fun rangeOf(box: List<Int>): Pair<Int, Int> {
    var best = 16; var bestRange = -1
    for (shift in intArrayOf(16, 8, 0)) {
        var lo = 255; var hi = 0
        for (c in box) { val v = (c shr shift) and 255; if (v < lo) lo = v; if (v > hi) hi = v }
        if (hi - lo > bestRange) { bestRange = hi - lo; best = shift }
    }
    return Pair(best, bestRange)
}

fun argbToHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

/** "#RRGGBB" or "RRGGBB" to opaque ARGB, or null. */
fun parseHex(s: String): Int? {
    val t = s.trim().removePrefix("#")
    if (t.length != 6) return null
    val v = t.toIntOrNull(16) ?: return null
    return (255 shl 24) or v
}

/** Mean absolute difference of two equally long arrays, for tests. */
fun meanDiff(a: IntArray, b: IntArray): Double {
    var s = 0L
    for (i in a.indices) s += abs(((a[i] shr 16) and 255) - ((b[i] shr 16) and 255)) + abs(((a[i] shr 8) and 255) - ((b[i] shr 8) and 255)) + abs((a[i] and 255) - (b[i] and 255))
    return s.toDouble() / (a.size * 3).coerceAtLeast(1)
}

