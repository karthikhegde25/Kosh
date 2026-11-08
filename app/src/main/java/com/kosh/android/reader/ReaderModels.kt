package com.kosh.android.reader

data class PageSize(val widthPts: Int, val heightPts: Int)

data class PdfDocumentInfo(
    val name: String,
    val pageCount: Int,
    val pageSizes: List<PageSize>,
    val sizeBytes: Long?,
)

enum class ReaderError { NOT_FOUND, ACCESS_DENIED, PASSWORD_PROTECTED, CORRUPT, EMPTY, UNSUPPORTED }

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Ready(val doc: PdfDocumentInfo, val initialPage: Int) : ReaderUiState
    data class Error(val kind: ReaderError) : ReaderUiState
}
