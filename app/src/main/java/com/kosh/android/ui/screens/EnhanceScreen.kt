package com.kosh.android.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kosh.android.DraftViewModel
import com.kosh.android.R
import com.kosh.android.data.model.FilterMode
import com.kosh.android.imaging.PageRenderer
import com.kosh.android.util.FilterMatrices

fun filterLabelRes(mode: FilterMode): Int = when (mode) {
    FilterMode.ORIGINAL -> R.string.filter_original
    FilterMode.DOCUMENT -> R.string.filter_document
    FilterMode.GRAYSCALE -> R.string.filter_grayscale
    FilterMode.BLACK_AND_WHITE -> R.string.filter_bw
    FilterMode.COLOR -> R.string.filter_color
    FilterMode.LIGHTEN -> R.string.filter_lighten
    FilterMode.VIVID -> R.string.filter_vivid
    FilterMode.SEPIA -> R.string.filter_sepia
    FilterMode.INVERT -> R.string.filter_invert
}

@Composable
internal fun ReaderBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
    }
}

/** Pick one of the five enhancement modes. The preview updates instantly and matches what the PDF will contain. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhanceScreen(pageId: String, draftVm: DraftViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    val page = draftVm.page(pageId)
    if (page == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val stage by produceState<Bitmap?>(null, pageId) { value = PageRenderer.renderStage(page, 1400) }
    var selected by rememberSaveable { mutableStateOf(page.filterMode) }
    val filter = remember(selected) { FilterMatrices.forMode(selected)?.let { ColorFilter.colorMatrix(ColorMatrix(it)) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.enhance_title)) }, navigationIcon = { ReaderBackButton(onBack) }) },
        bottomBar = {
            Column(Modifier.padding(16.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    items(FilterMode.entries.toList()) { mode ->
                        FilterChip(
                            selected = mode == selected,
                            onClick = { selected = mode },
                            label = { Text(stringResource(filterLabelRes(mode))) },
                            // Selection is shown with a check mark as well as colour.
                            leadingIcon = if (mode == selected) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = { draftVm.setFilter(pageId, selected); onDone() },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.enhance_done)) }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.Center) {
            val bmp = stage
            if (bmp == null) CircularProgressIndicator()
            else {
                val image = remember(bmp) { bmp.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = stringResource(R.string.crop_image_desc),
                    contentScale = ContentScale.Fit,
                    colorFilter = filter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
