package com.kosh.android.imaging

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.IOException

sealed interface ImportResult {
    data class Success(val width: Int, val height: Int) : ImportResult
    data class Failure(val reason: Reason) : ImportResult
}

enum class Reason { UNREADABLE, UNSUPPORTED, TOO_LARGE }

/**
 * Copies a picked image into app-private storage and validates it, without
 * decoding the full bitmap. The picker grants temporary access only, so the copy
 * is what the rest of the app uses.
 */
class ImageImporter(private val resolver: ContentResolver) {

    fun import(uri: Uri, dest: File): ImportResult {
        try {
            dest.parentFile?.mkdirs()
            val input = resolver.openInputStream(uri) ?: return ImportResult.Failure(Reason.UNREADABLE)
            input.use { source -> dest.outputStream().use { out -> source.copyTo(out) } }
        } catch (e: IOException) {
            dest.delete()
            return ImportResult.Failure(Reason.UNREADABLE)
        } catch (e: SecurityException) {
            dest.delete()
            return ImportResult.Failure(Reason.UNREADABLE)
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(dest.path, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) {
            dest.delete()
            return ImportResult.Failure(Reason.UNSUPPORTED)
        }
        if (w.toLong() * h > MAX_PIXELS) {
            dest.delete()
            return ImportResult.Failure(Reason.TOO_LARGE)
        }
        return ImportResult.Success(w, h)
    }

    companion object {
        /** ~120 megapixels: larger images are rejected instead of risking an OOM later. */
        const val MAX_PIXELS = 120_000_000L
    }
}
