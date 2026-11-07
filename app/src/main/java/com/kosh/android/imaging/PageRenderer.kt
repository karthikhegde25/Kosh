package com.kosh.android.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import com.kosh.android.data.model.CropCorners
import com.kosh.android.data.model.FilterMode
import com.kosh.android.data.model.ScannedPage
import com.kosh.android.util.FilterMatrices
import com.kosh.android.util.normalizeRotation
import com.kosh.android.util.outputSize
import com.kosh.android.util.toPixels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The non-destructive page pipeline: original file -> upright (EXIF) -> perspective crop -> rotation -> filter.
 * The original file is never changed; every result is rendered from it, so filters never stack up.
 */
object PageRenderer {

    private val thumbnails = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** The original, upright, scaled so its longer side is about [maxDim]. No crop, rotation or filter. */
    suspend fun renderBase(page: ScannedPage, maxDim: Int): Bitmap? = withContext(Dispatchers.Default) {
        decodeUpright(page.originalPath, maxDim)
    }

    /** Crop and rotation applied, filter NOT applied. The screen applies the filter live with a ColorFilter. */
    suspend fun renderStage(page: ScannedPage, maxDim: Int): Bitmap? = withContext(Dispatchers.Default) {
        val base = decodeUpright(page.originalPath, maxDim) ?: return@withContext null
        val cropped = page.cropCorners?.let { crop(base, it, maxDim) } ?: base
        rotate(cropped, page.rotationDegrees)
    }

    /** Fully processed page for a PDF. The caller owns (and should recycle) the returned bitmap. */
    suspend fun renderFinal(page: ScannedPage, maxDim: Int): Bitmap? = withContext(Dispatchers.Default) {
        val stage = renderStage(page, maxDim) ?: return@withContext null
        applyFilter(stage, page.filterMode)
    }

    /** Small processed preview for grids, cached by everything that affects how the page looks. */
    suspend fun renderThumbnail(page: ScannedPage, sizePx: Int): Bitmap? {
        val key = "${page.id}|${page.rotationDegrees}|${page.filterMode}|${page.cropCorners}|$sizePx"
        thumbnails.get(key)?.let { return it }
        val result = renderFinal(page, sizePx) ?: return null
        thumbnails.put(key, result)
        return result
    }

    // ---- steps ----------------------------------------------------------------------------------

    internal fun decodeUpright(path: String, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val decoded = try {
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: OutOfMemoryError) {
            null
        } ?: return null
        val rotated = applyExif(decoded, path)
        return limitSize(rotated, maxDim)
    }

    private fun applyExif(bitmap: Bitmap, path: String): Bitmap {
        val orientation = try {
            ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return transform(bitmap, m)
    }

    /** Scales down so the longer side is at most [maxDim]; recycles the input if a new bitmap is made. */
    private fun limitSize(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            max(1, (bitmap.width * scale).roundToInt()),
            max(1, (bitmap.height * scale).roundToInt()),
            true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /** Flattens the quadrilateral given by [corners] into a rectangle (perspective correction). */
    private fun crop(src: Bitmap, corners: CropCorners, maxDim: Int): Bitmap? {
        val (w, h) = outputSize(corners, src.width, src.height)
        val scale = minOf(1f, maxDim.toFloat() / max(w, h))
        val outW = max(1, (w * scale).roundToInt())
        val outH = max(1, (h * scale).roundToInt())
        val out = try {
            Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            return src
        }
        val from = corners.toPixels(src.width, src.height)
        val to = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(from, 0, to, 0, 4)) return src
        Canvas(out).drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        src.recycle()
        return out
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        val d = normalizeRotation(degrees)
        if (d == 0) return src
        return transform(src, Matrix().apply { postRotate(d.toFloat()) })
    }

    private fun transform(src: Bitmap, matrix: Matrix): Bitmap {
        val out = try {
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        } catch (e: OutOfMemoryError) {
            return src
        }
        if (out !== src) src.recycle()
        return out
    }

    private fun applyFilter(src: Bitmap, mode: FilterMode): Bitmap {
        val matrix = FilterMatrices.forMode(mode) ?: return src
        val out = try {
            Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            return src
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix)) }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        src.recycle()
        return out
    }
}
