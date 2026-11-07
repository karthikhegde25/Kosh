package com.kosh.android.data.model

/** Page filters. Stored by name, so new ones are only ever appended. */
enum class FilterMode { ORIGINAL, DOCUMENT, GRAYSCALE, BLACK_AND_WHITE, COLOR, LIGHTEN, VIVID, SEPIA, INVERT }

/** A point in normalized image space: both axes are in 0f..1f. */
data class NormalizedPoint(val x: Float, val y: Float)

data class CropCorners(
    val topLeft: NormalizedPoint,
    val topRight: NormalizedPoint,
    val bottomRight: NormalizedPoint,
    val bottomLeft: NormalizedPoint,
)

data class ScannedPage(
    val id: String,
    val documentId: String,
    /** Absolute path of the untouched original, inside app-private storage. */
    val originalPath: String,
    val rotationDegrees: Int = 0,
    val filterMode: FilterMode = FilterMode.ORIGINAL,
    val cropCorners: CropCorners? = null,
    val createdAt: Long,
)

/** A document draft. Page order is the order of [pages]. */
data class Document(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pages: List<ScannedPage> = emptyList(),
)
