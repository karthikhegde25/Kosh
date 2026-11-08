package com.kosh.android.imagetools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Draws and encodes pictures for the image tools. Everything runs on the device. */
class ImageEngine(private val context: Context) {

    // ---- reading ----

    fun readBytes(uri: Uri): ByteArray =
        (context.contentResolver.openInputStream(uri) ?: throw IOException("Can't read the picture")).use { it.readBytes() }

    /** Width and height without decoding the picture (after the EXIF turn), or null if it isn't an image. */
    fun dimensions(uri: Uri): Pair<Int, Int>? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        return if (orientationOf(uri) in setOf(ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE))
            Pair(o.outHeight, o.outWidth) else Pair(o.outWidth, o.outHeight)
    }

    private fun orientationOf(uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    /** The picture, turned upright by its EXIF orientation, and shrunk (never enlarged) so its longer side is at most [maxSide]. */
    fun decode(uri: Uri, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) throw IOException("That doesn't look like a picture")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        var bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("Can't open the picture")
        if (max(bmp.width, bmp.height) > maxSide) {
            val k = maxSide.toFloat() / max(bmp.width, bmp.height)
            val scaled = Bitmap.createScaledBitmap(bmp, max(1, (bmp.width * k).roundToInt()), max(1, (bmp.height * k).roundToInt()), true)
            if (scaled !== bmp) bmp.recycle()
            bmp = scaled
        }
        val m = Matrix()
        when (orientationOf(uri)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bmp
        }
        val turned = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (turned !== bmp) bmp.recycle()
        return turned
    }

    // ---- one picture in, one picture out ----

    /**
     * Applies [tool] to [src]. [scale] is below 1 when [src] is a shrunken copy used for the live preview, so pixel settings
     * (padding, radius, text size) are shrunk with it. [overlay] is the stamp picture. The result may be [src] itself.
     */
    fun render(tool: ImageTool, o: ImageOptions, src: Bitmap, scale: Float = 1f, overlay: Bitmap? = null): Bitmap = when (tool) {
        ImageTool.RESIZE -> resize(src, o)
        ImageTool.CROP -> {
            val r = o.crop.toRect(src.width, src.height)
            Bitmap.createBitmap(src, r.x, r.y, r.w, r.h)
        }
        ImageTool.ROTATE -> rotate(src, o)
        ImageTool.COLOR -> mapPixels(src) { px, _, _ -> applyMatrix(px, colorMatrix(o.brightness, o.contrast, o.saturation, o.hue, o.exposure)) }
        ImageTool.FILTERS -> filter(src, o)
        ImageTool.BORDERS -> borders(src, o, scale)
        ImageTool.ROUNDED -> roundCorners(src, if (o.fullRound) min(src.width, src.height) / 2f else o.radius * scale)
        ImageTool.WATERMARK -> watermark(src, o, scale)
        ImageTool.STAMP -> if (overlay == null) src else stamp(src, overlay, o)
        else -> src
    }

    private fun mapPixels(src: Bitmap, f: (IntArray, Int, Int) -> Unit): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        f(px, w, h)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun resize(src: Bitmap, o: ImageOptions): Bitmap {
        val typedW = o.width.trim().toIntOrNull()?.takeIf { it > 0 } ?: src.width
        val typedH = o.height.trim().toIntOrNull()?.takeIf { it > 0 } ?: src.height
        val (w, h) = resizeTarget(o.resizeMode, src.width, src.height, typedW.coerceAtMost(MAX_SIDE), typedH.coerceAtMost(MAX_SIDE), o.percent, o.lockAspect, o.widthLed)
        if (o.letterbox && o.resizeMode == ResizeMode.EXACT) {
            val out = Bitmap.createBitmap(typedW.coerceAtMost(MAX_SIDE), typedH.coerceAtMost(MAX_SIDE), Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            c.drawColor(o.letterboxColor)
            val r = fitRect(src.width, src.height, out.width, out.height, false)
            c.drawBitmap(src, null, Rect(r.x, r.y, r.x + r.w, r.y + r.h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            return out
        }
        return Bitmap.createScaledBitmap(src, w.coerceAtMost(MAX_SIDE), h.coerceAtMost(MAX_SIDE), true)
    }

    private fun rotate(src: Bitmap, o: ImageOptions): Bitmap {
        val m = Matrix()
        if (o.mirror == Mirror.HORIZONTAL || o.mirror == Mirror.BOTH) m.postScale(-1f, 1f)
        if (o.mirror == Mirror.VERTICAL || o.mirror == Mirror.BOTH) m.postScale(1f, -1f)
        if (o.rotation % 360 != 0) m.postRotate(o.rotation.toFloat())
        if (m.isIdentity) return src
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun filter(src: Bitmap, o: ImageOptions): Bitmap = mapPixels(src) { px, w, h ->
        val matrix = effectMatrix(o.effect)
        if (matrix != null) applyMatrix(px, matrix)
        when (o.effect) {
            FilterEffect.POSTERIZE -> posterize(px, 4)
            FilterEffect.THRESHOLD -> threshold(px, 128)
            FilterEffect.VIGNETTE -> vignette(px, w, h, 0.7f)
            FilterEffect.BLUR -> boxBlur(px, w, h, max(1, max(w, h) / 120))
            FilterEffect.SHARPEN -> sharpen(px, w, h, 0.9f)
            else -> Unit
        }
        if (o.filterBrightness != 0 || o.filterContrast != 0) applyMatrix(px, colorMatrix(o.filterBrightness, o.filterContrast, 0, 0, 0))
    }

    private fun borders(src: Bitmap, o: ImageOptions, scale: Float): Bitmap {
        fun px(v: Int) = (v * scale).roundToInt().coerceAtLeast(0)
        val t = px(if (o.padAll) o.padding else o.padTop)
        val r = px(if (o.padAll) o.padding else o.padRight)
        val b = px(if (o.padAll) o.padding else o.padBottom)
        val l = px(if (o.padAll) o.padding else o.padLeft)
        val out = Bitmap.createBitmap(src.width + l + r, src.height + t + b, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (o.gradient) {
            val w = out.width.toFloat(); val h = out.height.toFloat()
            val shader = when (o.gradientDirection) {
                1 -> LinearGradient(0f, 0f, w, 0f, o.fillColor, o.fillColor2, Shader.TileMode.CLAMP)
                2 -> LinearGradient(0f, 0f, w, h, o.fillColor, o.fillColor2, Shader.TileMode.CLAMP)
                else -> LinearGradient(0f, 0f, 0f, h, o.fillColor, o.fillColor2, Shader.TileMode.CLAMP)
            }
            c.drawRect(0f, 0f, w, h, Paint().apply { setShader(shader) })
        } else c.drawColor(o.fillColor)
        c.drawBitmap(src, l.toFloat(), t.toFloat(), null)
        return if (o.outerRadius > 0) roundCorners(out, o.outerRadius * scale).also { if (it !== out) out.recycle() } else out
    }

    /** Makes the corners transparent. [radius] is capped at half the shorter side. */
    fun roundCorners(src: Bitmap, radius: Float): Bitmap {
        val r = radius.coerceIn(0f, min(src.width, src.height) / 2f)
        if (r <= 0f) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), r, r, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun watermark(src: Bitmap, o: ImageOptions, scale: Float): Bitmap {
        val text = o.watermarkText
        if (text.isBlank()) return src
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val size = (o.watermarkSize * scale).coerceAtLeast(6f)
        val alpha = (o.watermarkOpacity.coerceIn(0, 100) * 255 / 100)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; typeface = Typeface.DEFAULT_BOLD
            color = (alpha shl 24) or 0xFFFFFF
            setShadowLayer(size / 12f, 0f, size / 20f, (alpha shl 24))
        }
        val tw = paint.measureText(text)
        val fm = paint.fontMetrics
        val margin = size * 0.6f
        val x = when (o.watermarkPosition) {
            Corner.BOTTOM_LEFT, Corner.TOP_LEFT -> margin
            Corner.CENTER -> (out.width - tw) / 2f
            else -> out.width - tw - margin
        }
        val y = when (o.watermarkPosition) {
            Corner.TOP_LEFT, Corner.TOP_RIGHT -> margin - fm.ascent
            Corner.CENTER -> (out.height - (fm.descent - fm.ascent)) / 2f - fm.ascent
            else -> out.height - margin - fm.descent
        }
        c.drawText(text, x, y, paint)
        return out
    }

    private fun stamp(src: Bitmap, overlay: Bitmap, o: ImageOptions): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val w = (out.width * o.stampSize.coerceIn(1, 100) / 100f).coerceAtLeast(2f)
        val h = w * overlay.height / overlay.width
        val margin = out.width * o.stampMargin.coerceIn(0, 40) / 100f
        val cx: Float; val cy: Float
        when (o.stampPosition) {
            Corner.FREE -> { cx = o.stampX * out.width; cy = o.stampY * out.height }
            Corner.CENTER -> { cx = out.width / 2f; cy = out.height / 2f }
            Corner.TOP_LEFT -> { cx = margin + w / 2; cy = margin + h / 2 }
            Corner.TOP_RIGHT -> { cx = out.width - margin - w / 2; cy = margin + h / 2 }
            Corner.BOTTOM_LEFT -> { cx = margin + w / 2; cy = out.height - margin - h / 2 }
            Corner.BOTTOM_RIGHT -> { cx = out.width - margin - w / 2; cy = out.height - margin - h / 2 }
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = o.stampOpacity.coerceIn(0, 100) * 255 / 100 }
        c.save()
        c.rotate(o.stampRotation.toFloat(), cx, cy)
        c.drawBitmap(overlay, null, RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), paint)
        c.restore()
        return out
    }

    // ---- social, collage, palette ----

    fun renderSocial(src: Bitmap, preset: SocialPreset, fit: SocialFit, background: Int): Bitmap = when (fit) {
        SocialFit.FIT -> {
            val r = fitRect(src.width, src.height, preset.w, preset.h, false)
            Bitmap.createScaledBitmap(src, r.w, r.h, true)
        }
        SocialFit.FILL, SocialFit.LETTERBOX -> {
            val out = Bitmap.createBitmap(preset.w, preset.h, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            if (fit == SocialFit.LETTERBOX) c.drawColor(background)
            val r = fitRect(src.width, src.height, preset.w, preset.h, fit == SocialFit.FILL)
            c.drawBitmap(src, null, Rect(r.x, r.y, r.x + r.w, r.y + r.h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            out
        }
    }

    fun renderCollage(srcs: List<Bitmap>, o: ImageOptions, width: Int = o.collageWidth, scale: Float = 1f): Bitmap {
        val w = width.coerceIn(100, MAX_SIDE)
        val h = (w.toLong() * o.collageAspect.h / o.collageAspect.w).toInt().coerceIn(50, MAX_SIDE)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (o.collageBackground != TRANSPARENT) c.drawColor(o.collageBackground)
        val layouts = collageLayouts(srcs.size)
        val layout = o.collageLayout.takeIf { it in layouts } ?: layouts.first()
        val cells = collageCells(layout, srcs.size, w, h, (o.collageGap * scale).roundToInt())
        val radius = o.collageRadius * scale
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        for ((i, cell) in cells.withIndex()) {
            val s = srcs[i]
            c.save()
            val rect = RectF(cell.x.toFloat(), cell.y.toFloat(), (cell.x + cell.w).toFloat(), (cell.y + cell.h).toFloat())
            val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
            c.clipPath(path)
            val r = fitRect(s.width, s.height, cell.w, cell.h, o.collageFill)
            c.drawBitmap(s, null, Rect(cell.x + r.x, cell.y + r.y, cell.x + r.x + r.w, cell.y + r.y + r.h), paint)
            c.restore()
        }
        return out
    }

    fun palette(src: Bitmap, count: Int): List<Int> {
        val k = 200f / max(src.width, src.height)
        val small = if (k < 1f) Bitmap.createScaledBitmap(src, max(1, (src.width * k).roundToInt()), max(1, (src.height * k).roundToInt()), true) else src
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        if (small !== src) small.recycle()
        return extractPalette(px, count)
    }

    /** A picture of the colours with their codes underneath. */
    fun paletteImage(colors: List<Int>): Bitmap {
        val cell = 220; val labelH = 70
        val perRow = min(colors.size, 4).coerceAtLeast(1)
        val rows = (colors.size + perRow - 1) / perRow
        val out = Bitmap.createBitmap(perRow * cell, rows * (cell + labelH), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(WHITE)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; color = BLACK; textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE }
        val fill = Paint()
        for ((i, col) in colors.withIndex()) {
            val x = (i % perRow) * cell; val y = (i / perRow) * (cell + labelH)
            fill.color = col
            c.drawRect(x.toFloat(), y.toFloat(), (x + cell).toFloat(), (y + cell).toFloat(), fill)
            c.drawText(argbToHex(col), x + cell / 2f, y + cell + 46f, text)
        }
        return out
    }

    // ---- encoding ----

    /** Lays the picture over white, for formats with no transparency. */
    fun flatten(src: Bitmap, background: Int = WHITE): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(background)
        c.drawBitmap(src, 0f, 0f, null)
        return out
    }

    fun encode(src: Bitmap, format: OutFormat, quality: Int): ByteArray {
        val q = quality.coerceIn(1, 100)
        return when (format) {
            OutFormat.PNG -> compress(src, Bitmap.CompressFormat.PNG, 100)
            OutFormat.JPG -> {
                val flat = if (src.hasAlpha()) flatten(src) else src
                try { compress(flat, Bitmap.CompressFormat.JPEG, q) } finally { if (flat !== src) flat.recycle() }
            }
            OutFormat.WEBP -> compress(src, webp(q), q)
            OutFormat.BMP -> {
                val px = IntArray(src.width * src.height)
                src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
                encodeBmp(src.width, src.height, px)
            }
            OutFormat.ICO -> {
                val k = 256f / max(src.width, src.height)
                val small = if (k < 1f) Bitmap.createScaledBitmap(src, max(1, (src.width * k).roundToInt()), max(1, (src.height * k).roundToInt()), true) else src
                try { icoFromPng(compress(small, Bitmap.CompressFormat.PNG, 100), small.width, small.height) } finally { if (small !== src) small.recycle() }
            }
        }
    }

    private fun webp(quality: Int): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= 30) (if (quality >= 100) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY)
        else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP

    private fun compress(src: Bitmap, f: Bitmap.CompressFormat, q: Int): ByteArray {
        val out = ByteArrayOutputStream(max(1024, src.byteCount / 8))
        if (!src.compress(f, q, out)) throw IOException("Couldn't save the picture")
        return out.toByteArray()
    }

    /**
     * JPG / WEBP at a fixed [ImageOptions.quality], or the best quality that fits under the typed target size. If even the
     * lowest quality is too big the picture is made a little smaller until it fits. JPG can then be padded to the exact size.
     */
    fun compress(src: Bitmap, o: ImageOptions): ByteArray {
        val target = if (o.compressByTarget) o.targetBytes() else null
        if (target == null) return encode(src, o.format, o.quality)
        var bmp = src
        var shrunk = 0
        try {
            while (true) {
                var lo = 1; var hi = 100
                var best: ByteArray? = null
                while (lo <= hi) {
                    val mid = (lo + hi) / 2
                    val bytes = encode(bmp, o.format, mid)
                    if (bytes.size <= target) { best = bytes; lo = mid + 1 } else hi = mid - 1
                }
                if (best != null) {
                    return if (o.exactSize && o.format == OutFormat.JPG) padFile(best, target - best.size, " ") else best
                }
                if (shrunk >= 10 || min(bmp.width, bmp.height) < 32) return encode(bmp, o.format, 1)
                val next = Bitmap.createScaledBitmap(bmp, (bmp.width * 0.85f).roundToInt(), (bmp.height * 0.85f).roundToInt(), true)
                if (bmp !== src) bmp.recycle()
                bmp = next
                shrunk++
            }
        } finally {
            if (bmp !== src) bmp.recycle()
        }
    }

    companion object {
        /** No picture is made larger than this on a side. */
        const val MAX_SIDE = 8000
    }
}
