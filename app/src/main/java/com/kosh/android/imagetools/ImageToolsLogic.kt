package com.kosh.android.imagetools

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The tools in the "Image tools" section of the Tools tab. */
enum class ImageTool(val id: String) {
    CONVERT("img_convert"),
    COMPRESS("img_compress"),
    RESIZE("img_resize"),
    CROP("img_crop"),
    ROTATE("img_rotate"),
    COLOR("img_color"),
    FILTERS("img_filters"),
    BORDERS("img_borders"),
    ROUNDED("img_rounded"),
    WATERMARK("img_watermark"),
    STAMP("img_stamp"),
    SOCIAL("img_social"),
    PALETTE("img_palette"),
    COLLAGE("img_collage"),
    INCREASE("img_increase"),
    STRIP("img_strip"),
    ;

    /** How many pictures the tool takes: [minInputs]..[maxInputs]. */
    val minInputs: Int get() = if (this == COLLAGE) 2 else 1
    val maxInputs: Int get() = when (this) {
        COLLAGE -> 4
        PALETTE, STAMP, INCREASE, STRIP -> 1
        else -> MAX_BATCH
    }

    companion object {
        const val MAX_BATCH = 30
        fun fromId(id: String?): ImageTool? = entries.firstOrNull { it.id == id }
    }
}

enum class OutFormat(val extension: String, val mime: String, val alpha: Boolean, val lossy: Boolean) {
    PNG("png", "image/png", true, false),
    JPG("jpg", "image/jpeg", false, true),
    WEBP("webp", "image/webp", true, true),
    BMP("bmp", "image/bmp", false, false),
    ICO("ico", "image/x-icon", true, false),
}

/** The formats a tool can write. */
fun formatsFor(tool: ImageTool): List<OutFormat> = when (tool) {
    ImageTool.CONVERT -> listOf(OutFormat.PNG, OutFormat.JPG, OutFormat.WEBP, OutFormat.BMP, OutFormat.ICO)
    ImageTool.COMPRESS -> listOf(OutFormat.JPG, OutFormat.WEBP)
    ImageTool.ROUNDED -> listOf(OutFormat.PNG, OutFormat.WEBP)
    ImageTool.INCREASE, ImageTool.STRIP, ImageTool.PALETTE -> emptyList()
    else -> listOf(OutFormat.PNG, OutFormat.JPG, OutFormat.WEBP)
}

/** A rectangle in whole pixels. */
data class IRect(val x: Int, val y: Int, val w: Int, val h: Int)

// ---- resize ----------------------------------------------------------------------------------------------------------

enum class ResizeMode { EXACT, PERCENT, FIT_BOX }

/**
 * The size an image becomes. EXACT with [lockAspect] keeps the proportions using whichever of width / height was typed
 * ([widthLed]); without it the size is taken as typed. FIT_BOX shrinks or grows the image to sit inside the box.
 */
fun resizeTarget(
    mode: ResizeMode, srcW: Int, srcH: Int,
    width: Int, height: Int, percent: Int, lockAspect: Boolean, widthLed: Boolean,
): Pair<Int, Int> {
    val (w, h) = when (mode) {
        ResizeMode.PERCENT -> Pair(srcW * percent / 100.0, srcH * percent / 100.0)
        ResizeMode.FIT_BOX -> {
            val k = min(width.toDouble() / srcW, height.toDouble() / srcH)
            Pair(srcW * k, srcH * k)
        }
        ResizeMode.EXACT -> when {
            !lockAspect -> Pair(width.toDouble(), height.toDouble())
            widthLed -> Pair(width.toDouble(), width.toDouble() * srcH / srcW)
            else -> Pair(height.toDouble() * srcW / srcH, height.toDouble())
        }
    }
    return Pair(max(1, w.roundToInt()), max(1, h.roundToInt()))
}

/** Where a [srcW]x[srcH] picture goes when it is fitted inside / fills a [boxW]x[boxH] box (the box is centred). */
fun fitRect(srcW: Int, srcH: Int, boxW: Int, boxH: Int, fill: Boolean): IRect {
    val k = if (fill) max(boxW.toDouble() / srcW, boxH.toDouble() / srcH) else min(boxW.toDouble() / srcW, boxH.toDouble() / srcH)
    val w = max(1, (srcW * k).roundToInt())
    val h = max(1, (srcH * k).roundToInt())
    return IRect((boxW - w) / 2, (boxH - h) / 2, w, h)
}

// ---- crop ------------------------------------------------------------------------------------------------------------

enum class CropAspect(val label: String, val ratio: Double?) {
    FREE("Free", null), SQUARE("1:1", 1.0), FOUR_FIVE("4:5", 4.0 / 5), WIDE("16:9", 16.0 / 9),
    THREE_TWO("3:2", 1.5), TALL("9:16", 9.0 / 16),
}

/** A crop box as fractions (0..1) of the picture, so the same area can be cut from pictures of any size. */
data class CropBox(val x: Double, val y: Double, val w: Double, val h: Double) {
    fun toRect(imgW: Int, imgH: Int): IRect {
        val rx = (x * imgW).roundToInt().coerceIn(0, imgW - 1)
        val ry = (y * imgH).roundToInt().coerceIn(0, imgH - 1)
        val rw = (w * imgW).roundToInt().coerceIn(1, imgW - rx)
        val rh = (h * imgH).roundToInt().coerceIn(1, imgH - ry)
        return IRect(rx, ry, rw, rh)
    }

    companion object {
        val FULL = CropBox(0.0, 0.0, 1.0, 1.0)

        fun fromRect(r: IRect, imgW: Int, imgH: Int) =
            CropBox(r.x.toDouble() / imgW, r.y.toDouble() / imgH, r.w.toDouble() / imgW, r.h.toDouble() / imgH)
    }
}

/** The biggest box with the given [ratio] (width / height in pixels) that fits in the picture, centred. */
fun largestCropBox(imgW: Int, imgH: Int, ratio: Double?): CropBox {
    if (ratio == null) return CropBox.FULL
    var w = imgW.toDouble()
    var h = w / ratio
    if (h > imgH) { h = imgH.toDouble(); w = h * ratio }
    return CropBox((imgW - w) / 2 / imgW, (imgH - h) / 2 / imgH, w / imgW, h / imgH)
}

/** Keeps a box inside the picture and no smaller than [minFrac] of it. */
fun clampCropBox(b: CropBox, minFrac: Double = 0.02): CropBox {
    val w = b.w.coerceIn(minFrac, 1.0)
    val h = b.h.coerceIn(minFrac, 1.0)
    return CropBox(b.x.coerceIn(0.0, 1.0 - w), b.y.coerceIn(0.0, 1.0 - h), w, h)
}

// ---- rotate / flip -------------------------------------------------------------------------------------------------

enum class Mirror { NONE, HORIZONTAL, VERTICAL, BOTH }

// ---- social presets -----------------------------------------------------------------------------------------------

data class SocialPreset(val id: String, val label: String, val w: Int, val h: Int)

val SOCIAL_PRESETS: List<SocialPreset> = listOf(
    SocialPreset("ig_square", "Instagram post (square)", 1080, 1080),
    SocialPreset("ig_portrait", "Instagram post (portrait)", 1080, 1350),
    SocialPreset("ig_story", "Instagram story / reel", 1080, 1920),
    SocialPreset("x_post", "X post", 1200, 675),
    SocialPreset("x_header", "X header", 1500, 500),
    SocialPreset("x_profile", "X profile picture", 400, 400),
    SocialPreset("yt_thumb", "YouTube thumbnail", 1280, 720),
    SocialPreset("yt_banner", "YouTube channel banner", 2560, 1440),
    SocialPreset("yt_profile", "YouTube profile picture", 800, 800),
)

enum class SocialFit { FIT, FILL, LETTERBOX }

// ---- collage ---------------------------------------------------------------------------------------------------------

enum class CollageAspect(val label: String, val w: Int, val h: Int) {
    SQUARE("1:1", 1, 1), FOUR_THREE("4:3", 4, 3), THREE_TWO("3:2", 3, 2), WIDE("16:9", 16, 9),
    FOUR_FIVE("4:5", 4, 5), TALL("9:16", 9, 16),
}

/** The layouts offered for [count] pictures (2..4). */
fun collageLayouts(count: Int): List<String> = when (count) {
    2 -> listOf("side", "stack")
    3 -> listOf("big_left", "big_top", "row", "column")
    else -> listOf("grid", "row", "column", "big_left")
}

/** Cells (x, y, w, h) of a collage canvas [width]x[height] with [gap] pixels between and around the pictures. */
fun collageCells(layout: String, count: Int, width: Int, height: Int, gap: Int): List<IRect> {
    val fw = (width - 2 * gap).coerceAtLeast(count)
    val fh = (height - 2 * gap).coerceAtLeast(count)
    fun split(total: Int, parts: Int): List<Pair<Int, Int>> {
        val room = total - gap * (parts - 1)
        val each = room / parts
        return List(parts) { i -> Pair(i * (each + gap), if (i == parts - 1) room - each * (parts - 1) else each) }
    }
    fun box(cols: List<Pair<Int, Int>>, rows: List<Pair<Int, Int>>, c: Int, r: Int) =
        IRect(gap + cols[c].first, gap + rows[r].first, cols[c].second, rows[r].second)
    val one = listOf(Pair(0, fw))
    val oneRow = listOf(Pair(0, fh))
    return when (layout) {
        "side", "row" -> { val c = split(fw, count); List(count) { box(c, oneRow, it, 0) } }
        "stack", "column" -> { val r = split(fh, count); List(count) { box(one, r, 0, it) } }
        "grid" -> { val c = split(fw, 2); val r = split(fh, 2); List(count) { box(c, r, it % 2, it / 2) } }
        "big_left" -> {
            val c = split(fw, 2); val r = split(fh, count - 1)
            listOf(IRect(gap + c[0].first, gap, c[0].second, fh)) + List(count - 1) { IRect(gap + c[1].first, gap + r[it].first, c[1].second, r[it].second) }
        }
        else -> { // big_top
            val r = split(fh, 2); val c = split(fw, count - 1)
            listOf(IRect(gap, gap + r[0].first, fw, r[0].second)) + List(count - 1) { IRect(gap + c[it].first, gap + r[1].first, c[it].second, r[1].second) }
        }
    }
}

// ---- names and sizes -------------------------------------------------------------------------------------------------

/** "photo.jpeg" -> "photo". */
fun baseName(fileName: String): String = fileName.substringBeforeLast('.', fileName).ifBlank { "image" }

/** Characters that can't go in a file name are replaced. */
fun safeName(s: String): String = s.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_").trim().trim('.').ifBlank { "image" }

/**
 * The names of the output files. With a typed [custom] name a batch becomes "name-1", "name-2"...; otherwise each file is
 * "<original>-<suffix>". Duplicates get " (2)", " (3)"...
 */
fun outputNames(sources: List<String>, custom: String, suffix: String, ext: String, perSource: Int = 1): List<String> {
    val seen = HashMap<String, Int>()
    val names = ArrayList<String>()
    val total = sources.size * perSource
    for ((i, s) in sources.withIndex()) {
        for (j in 0 until perSource) {
            val n = i * perSource + j + 1
            val stem = when {
                custom.isNotBlank() && total == 1 -> safeName(custom)
                custom.isNotBlank() -> safeName(custom) + "-" + n
                else -> safeName(baseName(s)) + (if (suffix.isNotBlank()) "-$suffix" else "")
            }
            val key = "$stem.$ext".lowercase()
            val count = (seen[key] ?: 0) + 1
            seen[key] = count
            names += if (count == 1) "$stem.$ext" else "$stem ($count).$ext"
        }
    }
    return names
}

fun formatBytes(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "%.1f KB".format(n / 1024.0)
    else -> "%.2f MB".format(n / 1048576.0)
}

/** What a typed size means in bytes. */
fun sizeToBytes(value: Double, megabytes: Boolean): Long = (value * if (megabytes) 1048576.0 else 1024.0).toLong()

const val INCREASE_MIN_BYTES = 1024L
const val INCREASE_MAX_BYTES = 50L * 1048576
