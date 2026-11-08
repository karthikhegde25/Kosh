package com.kosh.android.reader.annotation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import com.kosh.android.data.db.AnnotationType
import com.kosh.android.data.model.NormalizedPoint
import com.kosh.android.util.DrawItem
import com.kosh.android.util.hitTest
import com.kosh.android.util.normalizePoint
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

enum class AnnotationTool { PEN, HIGHLIGHTER, TEXT, ERASER }

/** Sizes and colours offered in the annotation toolbar. Stroke sizes are fractions of the page width. */
object AnnotationStyle {
    val COLORS = longArrayOf(0xFFFFEB3B, 0xFF4CAF50, 0xFF2196F3, 0xFFF44336, 0xFF212121)

    fun size(tool: AnnotationTool, level: Int): Float {
        val l = level.coerceIn(0, 2)
        return when (tool) {
            AnnotationTool.HIGHLIGHTER -> floatArrayOf(0.014f, 0.024f, 0.036f)[l]
            AnnotationTool.TEXT -> floatArrayOf(0.022f, 0.030f, 0.042f)[l]
            else -> floatArrayOf(0.0025f, 0.005f, 0.009f)[l]
        }
    }

    fun type(tool: AnnotationTool): AnnotationType? = when (tool) {
        AnnotationTool.PEN -> AnnotationType.PEN
        AnnotationTool.HIGHLIGHTER -> AnnotationType.HIGHLIGHTER
        AnnotationTool.TEXT -> AnnotationType.TEXT
        AnnotationTool.ERASER -> null
    }
}

/**
 * Draws a page's annotations and, when [interactive], lets the user draw, tap to place a note or tap to erase.
 * Everything is in normalised page coordinates, so annotations stay aligned at any zoom level or rotation.
 */
@Composable
fun AnnotationLayer(
    items: List<DrawItem>,
    interactive: Boolean,
    tool: AnnotationTool,
    colorArgb: Long,
    strokeSize: Float,
    onStroke: (List<NormalizedPoint>) -> Unit,
    onErase: (String) -> Unit,
    onAddText: (NormalizedPoint) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val live = remember { mutableStateListOf<NormalizedPoint>() }
    val liveItem = if (live.isNotEmpty() && (tool == AnnotationTool.PEN || tool == AnnotationTool.HIGHLIGHTER)) {
        DrawItem("live", AnnotationStyle.type(tool) ?: AnnotationType.PEN, colorArgb, strokeSize, live.toList(), null)
    } else null

    val input = if (interactive) {
        Modifier.pointerInput(tool, items) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                if (tool == AnnotationTool.PEN || tool == AnnotationTool.HIGHLIGHTER) {
                    val points = mutableListOf(normalizePoint(down.position.x, down.position.y, w, h))
                    live.clear()
                    live.addAll(points)
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        // A second finger means the user is pinching or panning: drop the stroke.
                        if (event.changes.count { it.pressed } >= 2) { cancelled = true; break }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.positionChanged()) {
                            val p = normalizePoint(change.position.x, change.position.y, w, h)
                            points += p
                            live.add(p)
                            change.consume()
                        }
                        if (!change.pressed) break
                    }
                    live.clear()
                    if (!cancelled) onStroke(points)
                } else {
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    // Only a real tap counts, not the end of a drag.
                    if (hypot(up.position.x - down.position.x, up.position.y - down.position.y) > 24.dp.toPx()) return@awaitEachGesture
                    if (tool == AnnotationTool.ERASER) {
                        hitTest(items, up.position.x, up.position.y, w, h, 16.dp.toPx())?.let { onErase(it.id) }
                    } else {
                        onAddText(normalizePoint(up.position.x, up.position.y, w, h))
                    }
                }
            }
        }
    } else Modifier

    Canvas(modifier.then(input)) {
        drawIntoCanvas { canvas ->
            AnnotationPainter.draw(canvas.nativeCanvas, if (liveItem != null) items + liveItem else items, size.width, size.height)
        }
    }
}
