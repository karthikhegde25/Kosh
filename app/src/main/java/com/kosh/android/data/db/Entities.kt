package com.kosh.android.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks", indices = [Index("documentKey")])
data class Bookmark(
    @PrimaryKey val id: String,
    val documentKey: String,
    val pageIndex: Int,
    val label: String?,
    val createdAt: Long,
)

enum class AnnotationType { PEN, HIGHLIGHTER, TEXT }

/**
 * A user annotation, stored separately from the PDF (the original file is never modified).
 * Coordinates are normalised to the page (0..1), so they stay aligned at any zoom or screen size.
 * [points] is the encoded list "x,y;x,y;..." (see AnnotationCodec). For TEXT it holds one anchor point,
 * [strokeWidth] is the text size and [note] the text. For strokes, [strokeWidth] is a fraction of page width.
 */
@Entity(tableName = "annotations", indices = [Index("documentKey")])
data class PdfAnnotation(
    @PrimaryKey val id: String,
    val documentKey: String,
    val pageIndex: Int,
    val type: String,
    val colorArgb: Long,
    val strokeWidth: Float,
    val points: String,
    val note: String?,
    val createdAt: Long,
) {
    val annotationType: AnnotationType
        get() = AnnotationType.entries.firstOrNull { it.name == type } ?: AnnotationType.PEN
}

/** A finished scan that was exported as a PDF (shown under Recent Scans). */
@Entity(tableName = "scan_records")
data class ScanRecord(
    @PrimaryKey val id: String,
    val name: String,
    val uri: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val createdAt: Long,
)
