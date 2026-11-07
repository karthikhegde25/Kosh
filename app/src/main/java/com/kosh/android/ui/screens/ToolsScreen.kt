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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Image
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
@Composable
fun ToolsScreen(
    draftVm: DraftViewModel,
    onOpenCamera: () -> Unit,
    onOpenPdf: (Uri) -> Unit,
    onCrop: (String) -> Unit,
    onEnhance: (String) -> Unit,
    onSaveAsPdf: () -> Unit,
    onOpenTool: (PdfTool) -> Unit,
) {
    // Remembered across the camera / crop screens, so coming back lands in the scanner again.
    var inScanner by rememberSaveable { mutableStateOf(false) }

    if (inScanner) {
        BackHandler { inScanner = false }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { inScanner = false }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.tools_back))
                }
                Text(stringResource(R.string.tool_image_to_pdf), style = MaterialTheme.typography.titleMedium)
            }
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
            ToolCard(tool) { if (tool == PdfTool.IMAGE_TO_PDF) inScanner = true else onOpenTool(tool) }
        }
    }
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
