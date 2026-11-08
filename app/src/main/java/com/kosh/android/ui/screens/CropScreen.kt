package com.kosh.android.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kosh.android.DraftViewModel
import com.kosh.android.R
import com.kosh.android.data.model.CropCorners
import com.kosh.android.data.model.NormalizedPoint
import com.kosh.android.imaging.PageRenderer
import com.kosh.android.util.clampPoint
import com.kosh.android.util.defaultCorners
import com.kosh.android.util.fullImageCorners
import com.kosh.android.util.isFullImage
import com.kosh.android.util.isValidCorners
import kotlin.math.min

private val CornersSaver = Saver<CropCorners, List<Float>>(
    save = { c ->
        listOf(
            c.topLeft.x, c.topLeft.y, c.topRight.x, c.topRight.y,
            c.bottomRight.x, c.bottomRight.y, c.bottomLeft.x, c.bottomLeft.y,
        )
    },
    restore = { v ->
        CropCorners(
            NormalizedPoint(v[0], v[1]), NormalizedPoint(v[2], v[3]),
            NormalizedPoint(v[4], v[5]), NormalizedPoint(v[6], v[7]),
        )
    },
)

private fun CropCorners.asList() = listOf(topLeft, topRight, bottomRight, bottomLeft)

private fun CropCorners.withCorner(index: Int, p: NormalizedPoint): CropCorners = when (index) {
    0 -> copy(topLeft = p)
    1 -> copy(topRight = p)
    2 -> copy(bottomRight = p)
    else -> copy(bottomLeft = p)
}

/** Manual four-corner crop. The page is flattened (perspective-corrected) from these corners when rendered. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(pageId: String, draftVm: DraftViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    val page = draftVm.page(pageId)
    if (page == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val bitmap by produceState<Bitmap?>(null, pageId) { value = PageRenderer.renderBase(page, 1600) }
    var corners by rememberSaveable(pageId, stateSaver = CornersSaver) { mutableStateOf(page.cropCorners ?: defaultCorners()) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var active by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.crop_title)) }, navigationIcon = { ReaderBackButton(onBack) }) },
        bottomBar = {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.crop_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { corners = defaultCorners() },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.crop_reset)) }
                    OutlinedButton(
                        onClick = { corners = fullImageCorners() },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.crop_full)) }
                    Button(
                        onClick = {
                            draftVm.setCrop(pageId, if (corners.isFullImage()) null else corners)
                            onDone()
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.crop_use_page)) }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).onSizeChanged { boxSize = it },
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            if (bmp == null) {
                CircularProgressIndicator()
            } else {
                val image = remember(bmp) { bmp.asImageBitmap() }
                Image(bitmap = image, contentDescription = stringResource(R.string.crop_image_desc), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())

                // Where the image actually sits inside the box (Fit keeps the aspect ratio).
                val scale = min(boxSize.width / bmp.width.toFloat(), boxSize.height / bmp.height.toFloat())
                val imgW = bmp.width * scale
                val imgH = bmp.height * scale
                val left = (boxSize.width - imgW) / 2f
                val top = (boxSize.height - imgH) / 2f
                fun toScreen(p: NormalizedPoint) = Offset(left + p.x * imgW, top + p.y * imgH)

                val handleColor = MaterialTheme.colorScheme.primary
                Canvas(
                    Modifier.fillMaxSize().pointerInput(imgW, imgH, left, top) {
                        val grab = 44.dp.toPx()
                        detectDragGestures(
                            onDragStart = { start ->
                                val nearest = corners.asList().withIndex().minByOrNull { (_, p) ->
                                    (toScreen(p) - start).getDistance()
                                }
                                active = nearest?.takeIf { (toScreen(it.value) - start).getDistance() <= grab }?.index
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val index = active ?: return@detectDragGestures
                                if (imgW <= 0f || imgH <= 0f) return@detectDragGestures
                                val moved = clampPoint(NormalizedPoint((change.position.x - left) / imgW, (change.position.y - top) / imgH))
                                val updated = corners.withCorner(index, moved)
                                if (isValidCorners(updated)) corners = updated // corners may not cross or collapse
                            },
                            onDragEnd = { active = null },
                            onDragCancel = { active = null },
                        )
                    },
                ) {
                    val pts = corners.asList().map { toScreen(it) }
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1..3) lineTo(pts[i].x, pts[i].y)
                        close()
                    }
                    drawPath(path, Color.White, style = Stroke(width = 5.dp.toPx()))
                    drawPath(path, handleColor, style = Stroke(width = 2.5.dp.toPx()))
                    pts.forEachIndexed { i, p ->
                        drawCircle(Color.White, radius = 15.dp.toPx(), center = p)
                        drawCircle(handleColor, radius = 12.dp.toPx(), center = p, style = Stroke(width = 4.dp.toPx()))
                        if (i == active) drawCircle(handleColor.copy(alpha = 0.35f), radius = 26.dp.toPx(), center = p)
                    }
                }
            }
        }
    }
}

