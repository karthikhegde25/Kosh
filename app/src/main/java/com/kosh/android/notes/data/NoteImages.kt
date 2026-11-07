package com.kosh.android.notes.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.kosh.android.imaging.PageRenderer
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

private val SAFE_ID = Regex("[A-Za-z0-9-]+")

/**
 * Images attached to notes. Files live in app-private storage under `<root>/<noteId>/`, so no database change
 * is needed. Names start with a timestamp, so sorting by name keeps the order the user added them in.
 * Pure file handling: the Android-specific decoding is in [NoteImageImporter].
 */
class NoteImageStore(private val root: File) {

    private fun dir(noteId: String): File {
        require(SAFE_ID.matches(noteId)) { "bad note id" } // ids become folder names: no path tricks
        return File(root, noteId)
    }

    fun list(noteId: String): List<File> =
        dir(noteId).listFiles { f -> f.isFile && f.extension == "jpg" }?.sortedBy { it.name } ?: emptyList()

    /** A new, empty target file for the next image of this note. */
    fun newFile(noteId: String): File {
        val d = dir(noteId).also { it.mkdirs() }
        val stamp = String.format(Locale.US, "%013d", System.currentTimeMillis())
        return File(d, "${stamp}_${UUID.randomUUID().toString().take(6)}.jpg")
    }

    fun remove(file: File): Boolean = file.delete()

    /**
     * Copies every image of one note to another (used when a note is duplicated). File names are kept, because the note
     * text refers to its pictures by name. Returns how many were copied.
     */
    fun copyAll(fromId: String, toId: String): Int {
        val target = dir(toId).also { it.mkdirs() }
        var copied = 0
        list(fromId).forEach { file ->
            file.copyTo(File(target, file.name), overwrite = true)
            copied++
        }
        return copied
    }

    fun deleteAll(noteId: String) {
        dir(noteId).deleteRecursively()
    }
}

/** Turns a picked image into a compact JPEG inside the note's folder (max about 2000 px on the long side). */
class NoteImageImporter(private val resolver: ContentResolver, private val cacheDir: File) {

    fun import(uri: Uri, target: File): Boolean {
        val temp = File(cacheDir, "note_import_${UUID.randomUUID()}.tmp")
        try {
            val input = resolver.openInputStream(uri) ?: return false
            input.use { source -> temp.outputStream().use { source.copyTo(it) } }
            val decoded = PageRenderer.decodeUpright(temp.path, MAX_DIMENSION) ?: return false
            // Flatten onto white so transparent PNGs don't turn black as JPEG.
            val flat = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply {
                drawColor(Color.WHITE)
                drawBitmap(decoded, 0f, 0f, null)
            }
            decoded.recycle()
            target.parentFile?.mkdirs()
            val ok = target.outputStream().use { flat.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            flat.recycle()
            if (!ok) target.delete()
            return ok
        } catch (e: IOException) {
            target.delete()
            return false
        } catch (e: SecurityException) {
            target.delete()
            return false
        } catch (e: OutOfMemoryError) {
            target.delete()
            return false
        } finally {
            temp.delete()
        }
    }

    private companion object {
        const val MAX_DIMENSION = 2000
        const val JPEG_QUALITY = 85
    }
}
