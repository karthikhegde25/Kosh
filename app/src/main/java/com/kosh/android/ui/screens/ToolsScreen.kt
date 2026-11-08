package com.kosh.android.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kosh.android.DraftViewModel
import com.kosh.android.R
import com.kosh.android.tools.PdfTool

internal fun toolTitle(tool: PdfTool): Int = when (tool) {
    PdfTool.IMAGE_TO_PDF -> R.string.tool_image_to_pdf
    PdfTool.PDF_TO_IMAGES -> R.string.tool_pdf_to_images
    PdfTool.DOCX_TO_PDF -> R.string.tool_docx_to_pdf
    PdfTool.PAGES_PER_SHEET -> R.string.tool_pages_per_sheet
    PdfTool.XLSX_TO_PDF -> R.string.tool_xlsx_to_pdf
    PdfTool.EXTRACT_IMAGES -> R.string.tool_extract_images
    PdfTool.MERGE -> R.string.tool_merge
    PdfTool.SPLIT -> R.string.tool_split
    PdfTool.EXTRACT -> R.string.tool_extract
    PdfTool.ROTATE -> R.string.tool_rotate
    PdfTool.DELETE -> R.string.tool_delete
    PdfTool.REORDER -> R.string.tool_reorder
    PdfTool.PAGE_NUMBERS -> R.string.tool_page_numbers
    PdfTool.COMPRESS -> R.string.tool_compress
    PdfTool.ENCRYPT -> R.string.tool_encrypt
    PdfTool.DECRYPT -> R.string.tool_decrypt
}

internal fun toolDescription(tool: PdfTool): Int = when (tool) {
    PdfTool.IMAGE_TO_PDF -> R.string.tool_image_to_pdf_desc
    PdfTool.PDF_TO_IMAGES -> R.string.tool_pdf_to_images_desc
    PdfTool.DOCX_TO_PDF -> R.string.tool_docx_to_pdf_desc
    PdfTool.PAGES_PER_SHEET -> R.string.tool_pages_per_sheet_desc
    PdfTool.XLSX_TO_PDF -> R.string.tool_xlsx_to_pdf_desc
    PdfTool.EXTRACT_IMAGES -> R.string.tool_extract_images_desc
    PdfTool.MERGE -> R.string.tool_merge_desc
    PdfTool.SPLIT -> R.string.tool_split_desc
    PdfTool.EXTRACT -> R.string.tool_extract_desc
    PdfTool.ROTATE -> R.string.tool_rotate_desc
    PdfTool.DELETE -> R.string.tool_delete_desc
    PdfTool.REORDER -> R.string.tool_reorder_desc
    PdfTool.PAGE_NUMBERS -> R.string.tool_page_numbers_desc
    PdfTool.COMPRESS -> R.string.tool_compress_desc
    PdfTool.ENCRYPT -> R.string.tool_encrypt_desc
    PdfTool.DECRYPT -> R.string.tool_decrypt_desc
}

private fun toolIcon(tool: PdfTool): ImageVector = when (tool) {
    PdfTool.IMAGE_TO_PDF -> Icons.Default.PictureAsPdf
    PdfTool.PDF_TO_IMAGES -> Icons.Default.Image
    PdfTool.DOCX_TO_PDF -> Icons.Default.Description
    PdfTool.PAGES_PER_SHEET -> Icons.Default.GridView
    PdfTool.XLSX_TO_PDF -> Icons.Default.TableChart
    PdfTool.EXTRACT_IMAGES -> Icons.Default.PhotoLibrary
    PdfTool.MERGE -> Icons.Default.LibraryAdd
    PdfTool.SPLIT -> Icons.Default.ContentCut
    PdfTool.EXTRACT -> Icons.Default.ContentCopy
    PdfTool.ROTATE -> Icons.AutoMirrored.Filled.RotateRight
    PdfTool.DELETE -> Icons.Default.Delete
    PdfTool.REORDER -> Icons.Default.SwapVert
    PdfTool.PAGE_NUMBERS -> Icons.Default.FormatListNumbered
    PdfTool.COMPRESS -> Icons.Default.Compress
    PdfTool.ENCRYPT -> Icons.Default.Lock
    PdfTool.DECRYPT -> Icons.Default.LockOpen
}

/**
 * The Tools tab: a grid of tools. "Image to PDF" is the scanner that used to be this tab; the others open their own screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    inScanner: Boolean,
    onScannerChange: (Boolean) -> Unit,
    draftVm: DraftViewModel,
    onOpenCamera: () -> Unit,
    onOpenPdf: (Uri) -> Unit,
    onCrop: (String) -> Unit,
    onEnhance: (String) -> Unit,
    onSaveAsPdf: () -> Unit,
    onOpenTool: (PdfTool) -> Unit,
    onOpenImageTool: (com.kosh.android.imagetools.ImageTool) -> Unit,
) {
    if (inScanner) {
        BackHandler { onScannerChange(false) }
        Column(Modifier.fillMaxSize()) {
            // Same title bar as every other tool.
            TopAppBar(
                title = { Text(stringResource(R.string.tool_image_to_pdf)) },
                navigationIcon = {
                    IconButton(onClick = { onScannerChange(false) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.tools_back))
                    }
                },
            )
            Box(Modifier.weight(1f)) {
                ScannerScreen(
                    draftVm = draftVm,
                    onOpenCamera = onOpenCamera,
                    onOpenPdf = onOpenPdf,
                    onCrop = onCrop,
                    onEnhance = onEnhance,
                    onSaveAsPdf = onSaveAsPdf,
                )
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.padding(bottom = 4.dp)) {
                Text(stringResource(R.string.tools_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.tools_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        items(PdfTool.entries.toList(), key = { it.id }) { tool ->
            ToolCard(tool) { if (tool == PdfTool.IMAGE_TO_PDF) onScannerChange(true) else onOpenTool(tool) }
        }
        item(span = { GridItemSpan(2) }, key = "image-tools-header") {
            Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                Text(stringResource(R.string.img_tools_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.img_tools_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        items(com.kosh.android.imagetools.ImageTool.entries.toList(), key = { it.id }) { tool ->
            ImageToolCard(tool) { onOpenImageTool(tool) }
        }
    }
}

@Composable
private fun ImageToolCard(tool: com.kosh.android.imagetools.ImageTool, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().height(148.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageToolIcon(tool), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Text(stringResource(com.kosh.android.imagetools.imageToolTitle(tool)), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(com.kosh.android.imagetools.imageToolDescription(tool)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
        }
    }
}

private fun imageToolIcon(tool: com.kosh.android.imagetools.ImageTool) = when (tool) {
    com.kosh.android.imagetools.ImageTool.CONVERT -> Icons.Default.SwapHoriz
    com.kosh.android.imagetools.ImageTool.COMPRESS -> Icons.Default.Compress
    com.kosh.android.imagetools.ImageTool.RESIZE -> Icons.Default.PhotoSizeSelectLarge
    com.kosh.android.imagetools.ImageTool.CROP -> Icons.Default.Crop
    com.kosh.android.imagetools.ImageTool.ROTATE -> Icons.Default.RotateRight
    com.kosh.android.imagetools.ImageTool.COLOR -> Icons.Default.Tune
    com.kosh.android.imagetools.ImageTool.FILTERS -> Icons.Default.Filter
    com.kosh.android.imagetools.ImageTool.BORDERS -> Icons.Default.BorderOuter
    com.kosh.android.imagetools.ImageTool.ROUNDED -> Icons.Default.RoundedCorner
    com.kosh.android.imagetools.ImageTool.WATERMARK -> Icons.Default.BrandingWatermark
    com.kosh.android.imagetools.ImageTool.STAMP -> Icons.Default.Layers
    com.kosh.android.imagetools.ImageTool.SOCIAL -> Icons.Default.Share
    com.kosh.android.imagetools.ImageTool.PALETTE -> Icons.Default.Palette
    com.kosh.android.imagetools.ImageTool.COLLAGE -> Icons.Default.Dashboard
    com.kosh.android.imagetools.ImageTool.INCREASE -> Icons.Default.Storage
    com.kosh.android.imagetools.ImageTool.STRIP -> Icons.Default.CleaningServices
}

@Composable
private fun ToolCard(tool: PdfTool, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().height(148.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(toolIcon(tool), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Text(stringResource(toolTitle(tool)), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(toolDescription(tool)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
        }
    }
}
