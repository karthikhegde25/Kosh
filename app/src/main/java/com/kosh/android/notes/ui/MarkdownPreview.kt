package com.kosh.android.notes.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.kosh.android.imaging.ThumbnailLoader
import com.kosh.android.notes.format.ImagePiece
import com.kosh.android.notes.format.NoteImageTokens
import com.kosh.android.notes.format.TextPiece
import java.io.File
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kosh.android.notes.format.MdBlank
import com.kosh.android.notes.format.MdBlock
import com.kosh.android.notes.format.MdBullet
import com.kosh.android.notes.format.MdChecklist
import com.kosh.android.notes.format.MdHeading
import com.kosh.android.notes.format.MdNumbered
import com.kosh.android.notes.format.MdParagraph
import com.kosh.android.notes.format.Span
import com.kosh.android.notes.format.parseMarkdown

/** Read-only rendering of the Markdown source, with pictures where they sit in the note. Checkboxes can be ticked. */
@Composable
fun MarkdownPreview(
    content: String,
    images: List<File>,
    onOpenImage: (File) -> Unit,
    onToggleChecklist: (lineIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pieces = remember(content) { NoteImageTokens.split(content) }
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    LazyColumn(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        itemsIndexed(pieces, key = { index, _ -> index }) { _, piece ->
            when (piece) {
                is TextPiece -> {
                    val blocks = remember(piece.text) { parseMarkdown(piece.text) }
                    Column {
                        blocks.forEach { block ->
                            PreviewBlock(block, codeBackground) { line -> onToggleChecklist(piece.firstLine + line) }
                        }
                    }
                }
                is ImagePiece -> {
                    val file = images.firstOrNull { it.name == piece.name }
                    if (file != null) PreviewImage(file, onOpenImage)
                }
            }
        }
    }
}

@Composable
private fun PreviewImage(file: File, onOpen: (File) -> Unit) {
    val bmp by produceState<Bitmap?>(null, file.path) { value = ThumbnailLoader.load(file.path, 1200) }
    val bitmap = bmp ?: return
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(10.dp)).clickable { onOpen(file) },
    )
}

@Composable
private fun PreviewBlock(block: MdBlock, codeBackground: Color, onToggle: (Int) -> Unit) {
    val body = MaterialTheme.typography.bodyLarge
    when (block) {
        is MdBlank -> Spacer(Modifier.height(10.dp))
        is MdHeading -> Text(
            text = styled(block.spans, codeBackground),
            style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            }.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        is MdParagraph -> Text(styled(block.spans, codeBackground), style = body)
        is MdBullet -> ListRow(block.level, marker = { Text("•", style = body) }) {
            Text(styled(block.spans, codeBackground), style = body)
        }
        is MdNumbered -> ListRow(block.level, marker = { Text("${block.number}.", style = body) }) {
            Text(styled(block.spans, codeBackground), style = body)
        }
        is MdChecklist -> ListRow(block.level, marker = {
            Checkbox(checked = block.checked, onCheckedChange = { onToggle(block.line) })
        }) {
            val textStyle = if (block.checked) body.copy(textDecoration = TextDecoration.LineThrough) else body
            Text(styled(block.spans, codeBackground), style = textStyle)
        }
    }
}

@Composable
private fun ListRow(level: Int, marker: @Composable () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = (level * 20).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(36.dp)) { marker() }
        Column(Modifier.weight(1f)) { content() }
    }
}

private fun styled(spans: List<Span>, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    for (s in spans) {
        withStyle(
            SpanStyle(
                fontWeight = if (s.bold) FontWeight.Bold else null,
                fontStyle = if (s.italic) FontStyle.Italic else null,
                textDecoration = if (s.strike) TextDecoration.LineThrough else null,
                fontFamily = if (s.code) FontFamily.Monospace else null,
                background = if (s.code) codeBackground else Color.Unspecified,
            ),
        ) { append(s.text) }
    }
}

