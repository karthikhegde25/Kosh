package com.kosh.android.notes.export

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.kosh.android.notes.format.NoteFormatConverter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class NoteExportFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    MARKDOWN("md", "text/markdown"),
    PLAIN_TEXT("txt", "text/plain"),
}

/** A copy of the note taken when the user chose Export, so later edits can't change the exported file. */
data class NoteSnapshot(
    val title: String,
    val content: String,
    /** Attached images. Only the PDF export uses them; Markdown and plain text never include images. */
    val images: List<File> = emptyList(),
)

interface NoteExporter {
    suspend fun export(snapshot: NoteSnapshot, format: NoteExportFormat, destination: Uri): Result<Unit>
}

/** Writes to a URI chosen with the system document picker. Success is reported only after the stream is closed. */
class AndroidNoteExporter(private val resolver: ContentResolver) : NoteExporter {

    override suspend fun export(snapshot: NoteSnapshot, format: NoteExportFormat, destination: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val stream = resolver.openOutputStream(destination, "w") ?: throw IOException("Can't open destination")
                stream.use { out ->
                    when (format) {
                        NoteExportFormat.PDF -> PdfNoteWriter().write(snapshot.title, snapshot.content, snapshot.images, out)
                        NoteExportFormat.MARKDOWN ->
                            out.write(NoteFormatConverter.toMarkdown(snapshot.title, snapshot.content).toByteArray(Charsets.UTF_8))
                        NoteExportFormat.PLAIN_TEXT ->
                            out.write(NoteFormatConverter.toPlainText(snapshot.title, snapshot.content).toByteArray(Charsets.UTF_8))
                    }
                    out.flush()
                }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Don't leave a half-written file behind in the document the picker just created.
                runCatching { DocumentsContract.deleteDocument(resolver, destination) }
                Result.failure(e)
            }
        }
}
