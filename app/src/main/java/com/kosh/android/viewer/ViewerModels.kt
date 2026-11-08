package com.kosh.android.viewer

/** Reading theme applied as a colour filter over the rendered pages (the file is never changed). */
enum class ViewerTheme(val js: String) { LIGHT("light"), DARK("dark"), SEPIA("sepia"), BLACK("black") }

/** Order matches pdf.js ScrollMode (0 = vertical, 1 = horizontal). */
enum class ScrollDirection { VERTICAL, HORIZONTAL }

enum class ZoomPreset(val token: String) {
    FIT_WIDTH("page-width"), FIT_PAGE("page-fit"), ACTUAL("page-actual"), AUTO("auto")
}

enum class OrientationLock { AUTO, PORTRAIT, LANDSCAPE }

enum class TapZone { PREVIOUS, CENTER, NEXT }

/** Pen thickness as a fraction of the page width. */
enum class DoodleSize(val fraction: Float) { THIN(0.003f), MEDIUM(0.006f), THICK(0.012f) }

/** A rectangle as fractions (0..1) of the page box, origin top-left, in the page's own unrotated frame. */
data class Quad(val x: Float, val y: Float, val w: Float, val h: Float)

/** Where a selection or highlight sits, in CSS px relative to the viewer (1 CSS px = 1 dp in the WebView). */
data class Anchor(val x: Float, val y: Float, val w: Float, val h: Float)

data class TextSelection(val page: Int, val text: String, val quads: List<Quad>, val anchor: Anchor)

data class OutlineNode(val id: String, val title: String, val page: Int, val children: List<OutlineNode>)

data class AttachmentItem(val id: String, val title: String)

data class DocProperties(
    val title: String = "-", val subject: String = "-", val author: String = "-", val creator: String = "-",
    val producer: String = "-", val creationDate: String = "-", val modDate: String = "-", val keywords: String = "-",
    val language: String = "-", val pdfVersion: String = "-", val sizeBytes: Long = 0L,
    val linearized: Boolean = false, val encryptFilterName: String = "", val acroForm: Boolean = false,
    val signatures: Boolean = false, val xfa: Boolean = false, val custom: Map<String, String> = emptyMap(),
)

/** A highlight shown in the list: either created in Kosh (id > 0) or already stored in the PDF (id < 0, read-only). */
data class HighlightItem(
    val id: Long,
    val page: Int,
    val text: String,
    val quads: List<Quad>,
    val colorArgb: Int,
    val note: String?,
    val label: String?,
    val fromDocument: Boolean,
)

/** Events from the web page. All methods run on the main thread; defaults do nothing. */
interface PdfListener {
    fun onLoadSuccess(pagesCount: Int) {}
    fun onLoadFailed(message: String, name: String) {}
    fun onPasswordDialogChange(open: Boolean) {}
    fun onProgressChange(percent: Int) {}
    fun onPageChange(page: Int) {}
    fun onScaleChange(scale: Float, scaleValue: String) {}
    fun onScroll(offset: Int, total: Int, ratio: Float, horizontal: Boolean) {}
    fun onSingleClick(x: Float, y: Float, width: Float, height: Float) {}
    fun onDoubleClick(x: Float, y: Float) {}
    fun onLongClick() {}
    fun onLinkClick(href: String) {}
    fun onHighlightTapped(id: Long, anchor: Anchor) {}
    fun onTextSelected(selection: TextSelection?) {}
    fun onFindMatchStart() {}
    fun onFindMatchChange(current: Int, total: Int) {}
    fun onFindMatchComplete(found: Boolean) {}
    fun onOutlineLoaded(outline: List<OutlineNode>) {}
    fun onAttachmentsLoaded(items: List<AttachmentItem>) {}
    fun onPropertiesLoaded(properties: DocProperties) {}
    fun onDocumentHighlightsLoaded(items: List<HighlightItem>) {}
    fun onAutoScrollEnd() {}
    fun onDoodleChanged(strokes: Int, undoSteps: Int) {}
    fun onDoodleSaved(file: java.io.File) {}
    fun onDoodleSaveFailed(message: String) {}
    fun onAttachmentData(base64: String, fileName: String) {}
    fun onRendererCrashed() {}
}
