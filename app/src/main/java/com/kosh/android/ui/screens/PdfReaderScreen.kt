package com.kosh.android.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.print.PrintManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kosh.android.KoshViewModelFactory
import com.kosh.android.R
import com.kosh.android.data.model.NormalizedPoint
import com.kosh.android.reader.AnnotatedExportState
import com.kosh.android.reader.PageSize
import com.kosh.android.reader.ReaderError
import com.kosh.android.reader.ReaderEvent
import com.kosh.android.reader.ReaderLayout
import com.kosh.android.reader.ReaderPreferences
import com.kosh.android.reader.ReaderTheme
import com.kosh.android.reader.ReaderUiState
import com.kosh.android.reader.ReaderViewModel
import com.kosh.android.reader.UriPrintAdapter
import com.kosh.android.reader.annotation.AnnotationLayer
import com.kosh.android.reader.annotation.AnnotationStyle
import com.kosh.android.reader.annotation.AnnotationTool
import com.kosh.android.reader.annotation.toDrawItem
import com.kosh.android.ui.components.sharePdf
import com.kosh.android.ui.theme.KoshTheme
import com.kosh.android.util.DOUBLE_TAP_ZOOM
import com.kosh.android.util.DrawItem
import com.kosh.android.util.MAX_RENDER_WIDTH_PX
import com.kosh.android.util.bucketWidth
import com.kosh.android.util.clampPage
import com.kosh.android.util.clampZoom
import com.kosh.android.util.fitPageZoom
import com.kosh.android.util.formatFileSize
import com.kosh.android.util.maxPanX
import com.kosh.android.util.parsePageInput
import com.kosh.android.util.scrollAfterZoom
import com.kosh.android.util.suggestedExportName
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun PdfReaderScreen(onBack: () -> Unit) {
    val vm: ReaderViewModel = viewModel(factory = KoshViewModelFactory.Factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val dark = when (prefs.theme) {
        ReaderTheme.SYSTEM -> isSystemInDarkTheme()
        ReaderTheme.LIGHT -> false
        ReaderTheme.DARK -> true
    }
    // The reader can have its own theme without touching the rest of the app.
    KoshTheme(darkTheme = dark) {
        Surface(Modifier.fillMaxSize()) {
            when (val s = state) {
                ReaderUiState.Loading -> ReaderLoading(onBack)
                is ReaderUiState.Error -> ReaderErrorContent(s.kind, onBack, onRetry = vm::retry)
                is ReaderUiState.Ready -> ReaderContent(vm, s, prefs, onBack)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderLoading(onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = {}, navigationIcon = { ReaderBackButton(onBack) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.reader_opening), modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderErrorContent(kind: ReaderError, onBack: () -> Unit, onRetry: () -> Unit) {
    val message = when (kind) {
        ReaderError.NOT_FOUND -> R.string.error_not_found
        ReaderError.ACCESS_DENIED -> R.string.error_access_denied
        ReaderError.PASSWORD_PROTECTED -> R.string.error_password
        ReaderError.CORRUPT -> R.string.error_corrupt
        ReaderError.EMPTY -> R.string.error_empty
        ReaderError.UNSUPPORTED -> R.string.error_unsupported
    }
    Scaffold(topBar = { TopAppBar(title = {}, navigationIcon = { ReaderBackButton(onBack) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(message), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Button(onClick = onBack, modifier = Modifier.padding(top = 24.dp).height(48.dp)) { Text(stringResource(R.string.reader_back_to_files)) }
            if (kind != ReaderError.EMPTY && kind != ReaderError.PASSWORD_PROTECTED) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp).height(48.dp)) { Text(stringResource(R.string.reader_try_again)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class, ExperimentalFoundationApi::class)
@Composable
private fun ReaderContent(vm: ReaderViewModel, ready: ReaderUiState.Ready, prefs: ReaderPreferences, onBack: () -> Unit) {
    val doc = ready.doc
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ready.initialPage)
    val snackbar = remember { SnackbarHostState() }

    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val annotations by vm.annotations.collectAsStateWithLifecycle()
    val canUndo by vm.canUndoAnnotation.collectAsStateWithLifecycle()
    val exportState by vm.annotatedExport.collectAsStateWithLifecycle()
    val itemsByPage = remember(annotations) { annotations.groupBy({ it.pageIndex }, { it.toDrawItem() }) }

    // Zoom is reader-wide (one value for the whole document), relative to fit-to-width = 1.0.
    var zoom by rememberSaveable { mutableStateOf(1f) }
    var offsetX by rememberSaveable { mutableStateOf(0f) }
    var rotation by rememberSaveable { mutableIntStateOf(0) } // view-only rotation: 0, 90, 180, 270
    var settledZoom by remember { mutableStateOf(zoom) }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var menuOpen by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showDisplay by remember { mutableStateOf(false) }
    var showAutoScrollDialog by remember { mutableStateOf(false) }
    var showExportConfirm by remember { mutableStateOf(false) }
    var navJob by remember { mutableStateOf<Job?>(null) }
    var autoScrolling by remember { mutableStateOf(false) }

    // Annotation mode
    var annotating by rememberSaveable { mutableStateOf(false) }
    var tool by rememberSaveable { mutableStateOf(AnnotationTool.PEN) }
    var colorIndex by rememberSaveable { mutableIntStateOf(0) }
    var widthIndex by rememberSaveable { mutableIntStateOf(1) }
    var pendingNote by remember { mutableStateOf<Pair<Int, NormalizedPoint>?>(null) }

    val single = prefs.layout == ReaderLayout.SINGLE_PAGE
    val odd = rotation % 180 != 0
    fun effective(i: Int): PageSize = doc.pageSizes[i].let { if (odd) PageSize(it.heightPts, it.widthPts) else it }

    val currentPage by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(center - (it.offset + it.size / 2)) }?.index ?: 0
        }
    }

    val insets = WindowInsets.systemBars.asPaddingValues()
    val pageGapPx = with(density) { 8.dp.toPx() }

    fun pageHeightPx(i: Int, z: Float): Float {
        val s = effective(i)
        return viewport.width * z * s.heightPts / s.widthPts
    }

    /** Scrolls so the spot under the pinch (or double-tap) stays where it is while the pages grow or shrink. */
    fun anchorScrollToFocal(newZoom: Float, focalY: Float) {
        val info = listState.layoutInfo
        val focalInContent = focalY + info.viewportStartOffset // relative to the start of the pages, past the top padding
        val visible = info.visibleItemsInfo
        val target = visible.firstOrNull { focalInContent >= it.offset && focalInContent < it.offset + it.size }
            ?: visible.minByOrNull { abs(focalInContent - (it.offset + it.size / 2f)) }
            ?: return
        val fraction = ((focalInContent - target.offset) / target.size.toFloat()).coerceIn(0f, 1f)
        val heights = List(doc.pageCount) { pageHeightPx(it, newZoom) }
        val pos = scrollAfterZoom(heights, pageGapPx, target.index, fraction, focalInContent)
        listState.requestScrollToItem(pos.index, pos.offset)
    }

    /** Free zoom: both axes follow the focal point (the pinch centre, or the tapped spot). */
    fun applyZoom(target: Float, focalX: Float, focalY: Float, panX: Float) {
        val w = viewport.width.toFloat()
        val z = clampZoom(target)
        val ratio = z / zoom
        if (ratio != 1f) anchorScrollToFocal(z, focalY)
        val fx = focalX - w / 2f
        val limit = maxPanX(w, z)
        offsetX = ((offsetX - fx) * ratio + fx + panX).coerceIn(-limit, limit)
        zoom = z
    }

    fun goTo(page: Int) {
        if (navJob?.isActive == true) return // ignore taps while a jump is still animating
        navJob = scope.launch { listState.animateScrollToItem(clampPage(page, doc.pageCount)) }
    }

    fun fitWidth() {
        applyZoom(1f, viewport.width / 2f, viewport.height / 2f, 0f)
        offsetX = 0f
    }

    fun fitPage() {
        val idx = currentPage
        val s = effective(idx)
        val usableH = viewport.height - with(density) { 16.dp.toPx() }
        applyZoom(
            fitPageZoom(viewport.width.toFloat(), usableH, s.widthPts.toFloat(), s.heightPts.toFloat()),
            viewport.width / 2f,
            viewport.height / 2f,
            0f,
        )
        offsetX = 0f
        scope.launch { listState.scrollToItem(idx) }
    }

    fun rotateView() {
        rotation = (rotation + 90) % 360
        offsetX = 0f
        zoom = 1f
        settledZoom = 1f
    }

    // Single-page mode shows one whole page at a time; switching back returns to fit-to-width.
    var lastLayout by remember { mutableStateOf<ReaderLayout?>(null) }
    LaunchedEffect(prefs.layout, viewport) {
        if (viewport.width == 0) return@LaunchedEffect
        if (lastLayout != prefs.layout) {
            if (prefs.layout == ReaderLayout.SINGLE_PAGE) fitPage() else if (lastLayout != null) fitWidth()
            lastLayout = prefs.layout
        }
    }

    // Re-render at higher resolution only after zooming settles, so pinching never thrashes the renderer.
    LaunchedEffect(zoom) {
        delay(250)
        settledZoom = zoom
    }
    val renderWidth = min(bucketWidth(viewport.width * settledZoom), MAX_RENDER_WIDTH_PX)

    LaunchedEffect(currentPage, renderWidth) {
        if (viewport.width > 0) vm.prefetch(currentPage + 1, renderWidth)
    }

    // Auto-scroll: pauses on any touch and stops at the end of the document.
    LaunchedEffect(autoScrolling, prefs.autoScrollSpeed) {
        if (!autoScrolling) return@LaunchedEffect
        val pxPerSecond = with(density) { 48.dp.toPx() } * prefs.autoScrollSpeed
        var last = androidx.compose.runtime.withFrameNanos { it }
        while (isActive) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            if (listState.scrollBy(pxPerSecond * dt) == 0f) {
                autoScrolling = false
                break
            }
        }
    }

    // Debounced so scrolling doesn't write to storage constantly; also saved when leaving.
    val latestPage by rememberUpdatedState(currentPage)
    LaunchedEffect(Unit) {
        snapshotFlow { currentPage }.debounce(600).collect { vm.saveProgress(it) }
    }
    DisposableEffect(Unit) {
        onDispose { vm.saveProgress(latestPage) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { autoScrolling = false }
    BackHandler(enabled = annotating) { annotating = false }

    // Messages from the ViewModel (failed saves, export results).
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            val msg = when (event) {
                ReaderEvent.BookmarkFailed -> R.string.bookmark_failed
                ReaderEvent.AnnotationFailed -> R.string.annotation_failed
                ReaderEvent.AnnotatedExported -> R.string.annotated_exported
                ReaderEvent.AnnotatedExportFailed -> R.string.annotated_export_failed
            }
            snackbar.showSnackbar(context.getString(msg))
        }
    }

    val annotatedLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.exportAnnotatedCopy(uri)
    }

    fun share() {
        if (!sharePdf(context, Uri.parse(vm.uriString))) {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.reader_share_failed)) }
        }
    }

    fun print() {
        val uri = vm.uriString?.let { Uri.parse(it) } ?: return
        runCatching {
            val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            pm.print(doc.name, UriPrintAdapter(context, uri, doc.name, doc.pageCount), null)
        }.onFailure { scope.launch { snackbar.showSnackbar(context.getString(R.string.print_failed)) } }
    }

    val showChrome = chromeVisible || annotating
    val bookmarkedHere = bookmarks.any { it.pageIndex == currentPage }
    val colorArgb = AnnotationStyle.COLORS[colorIndex.coerceIn(0, AnnotationStyle.COLORS.lastIndex)]

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { viewport = it }
            .clipToBounds()
            .pointerInput(annotating) {
                if (!annotating) {
                    detectTapGestures(
                        onTap = { chromeVisible = !chromeVisible },
                        onDoubleTap = { p -> applyZoom(if (zoom < 1.5f) DOUBLE_TAP_ZOOM else 1f, p.x, p.y, 0f) },
                    )
                }
            }
            .pointerInput(Unit) {
                // Pinch-zoom and pan observed in the Initial pass. Two fingers are consumed here; a single
                // finger is only observed (never consumed), so vertical scrolling and drawing keep working.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    autoScrolling = false // any touch pauses auto-scroll
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            val z = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (z != 1f || pan != Offset.Zero) {
                                val centroid = event.calculateCentroid(useCurrent = false)
                                applyZoom(zoom * z, centroid.x, centroid.y, pan.x)
                                listState.dispatchRawDelta(-pan.y)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } else if (pressed.size == 1 && zoom > 1f && !annotating) {
                            val d = pressed[0].positionChange()
                            val limit = maxPanX(viewport.width.toFloat(), zoom)
                            offsetX = (offsetX + d.x).coerceIn(-limit, limit) // vertical movement scrolls the list
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        if (viewport.width > 0) {
            val contentWidthPx = viewport.width * zoom
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !annotating, // one finger draws; two fingers scroll and zoom
                flingBehavior = if (single) rememberSnapFlingBehavior(listState) else ScrollableDefaults.flingBehavior(),
                contentPadding = PaddingValues(
                    top = insets.calculateTopPadding() + 64.dp,
                    bottom = insets.calculateBottomPadding() + if (annotating) 150.dp else 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(doc.pageCount, key = { it }) { index ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        PdfPageItem(
                            index = index,
                            size = doc.pageSizes[index],
                            rotation = rotation,
                            contentWidthPx = contentWidthPx,
                            renderWidthPx = renderWidth,
                            offsetXPx = offsetX.roundToInt(),
                            invert = prefs.invertPages,
                            render = vm::renderPage,
                            annotations = itemsByPage[index].orEmpty(),
                            interactive = annotating,
                            tool = tool,
                            colorArgb = colorArgb,
                            strokeSize = AnnotationStyle.size(tool, widthIndex),
                            onStroke = { points ->
                                AnnotationStyle.type(tool)?.let { type ->
                                    vm.addAnnotation(index, type, colorArgb, AnnotationStyle.size(tool, widthIndex), points)
                                }
                            },
                            onErase = vm::deleteAnnotation,
                            onAddText = { point -> pendingNote = index to point },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showChrome,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            TopAppBar(
                title = {
                    Text(
                        if (annotating) stringResource(R.string.annotate_title) else doc.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { ReaderBackButton { if (annotating) annotating = false else onBack() } },
                actions = {
                    if (!annotating) {
                        IconButton(onClick = { showSheet = true }) {
                            Icon(Icons.Default.Bookmarks, contentDescription = stringResource(R.string.panel_open))
                        }
                        IconButton(onClick = { annotating = true; autoScrolling = false }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.annotate_start))
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.reader_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.reader_share)) }, onClick = { menuOpen = false; share() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.reader_print)) }, onClick = { menuOpen = false; print() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.reader_info)) }, onClick = { menuOpen = false; showInfo = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.display_title)) }, onClick = { menuOpen = false; showDisplay = true })
                            DropdownMenuItem(
                                text = { Text(stringResource(if (autoScrolling) R.string.autoscroll_stop else R.string.autoscroll_title)) },
                                onClick = { menuOpen = false; if (autoScrolling) autoScrolling = false else showAutoScrollDialog = true },
                            )
                            DropdownMenuItem(text = { Text(stringResource(R.string.reader_fit_width)) }, onClick = { menuOpen = false; fitWidth() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.reader_fit_page)) }, onClick = { menuOpen = false; fitPage() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.reader_rotate)) }, onClick = { menuOpen = false; rotateView() })
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.annotated_export_menu)) },
                                enabled = annotations.isNotEmpty(),
                                onClick = { menuOpen = false; showExportConfirm = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
            )
        }

        AnimatedVisibility(
            visible = showChrome,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            if (annotating) {
                AnnotationToolbar(
                    tool = tool,
                    onTool = { tool = it },
                    colorIndex = colorIndex,
                    onColor = { colorIndex = it },
                    widthIndex = widthIndex,
                    onWidth = { widthIndex = it },
                    canUndo = canUndo,
                    onUndo = vm::undoAnnotation,
                    onDone = { annotating = false },
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier.navigationBarsPadding().padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { goTo(currentPage - 1) }, enabled = currentPage > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.reader_prev))
                        }
                        val counterDesc = stringResource(R.string.reader_page_counter_desc, currentPage + 1, doc.pageCount)
                        val goToDesc = stringResource(R.string.reader_go_to_page)
                        TextButton(onClick = { showJump = true }, modifier = Modifier.semantics { contentDescription = "$counterDesc. $goToDesc" }) {
                            Text(stringResource(R.string.reader_page_counter, currentPage + 1, doc.pageCount))
                        }
                        IconButton(onClick = { goTo(currentPage + 1) }, enabled = currentPage < doc.pageCount - 1) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.reader_next))
                        }
                        IconButton(onClick = {
                            if (bookmarkedHere) {
                                bookmarks.filter { it.pageIndex == currentPage }.forEach { vm.removeBookmark(it.id) }
                                scope.launch { snackbar.showSnackbar(context.getString(R.string.bookmark_removed, currentPage + 1)) }
                            } else {
                                showBookmarkDialog = true
                            }
                        }) {
                            Icon(
                                if (bookmarkedHere) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = stringResource(if (bookmarkedHere) R.string.bookmark_remove_here else R.string.bookmark_add_here),
                                tint = if (bookmarkedHere) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        if (autoScrolling) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 16.dp, bottom = 96.dp),
            ) {
                TextButton(onClick = { autoScrolling = false }) { Text(stringResource(R.string.autoscroll_pause)) }
            }
        }

        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (annotating) 170.dp else 96.dp))
    }

    // ---- dialogs and sheets ------------------------------------------------------------------------

    if (showSheet) {
        BookmarksSheet(
            bookmarks = bookmarks,
            annotations = annotations,
            currentPage = currentPage,
            onAddBookmark = { showSheet = false; showBookmarkDialog = true },
            onGoTo = { goTo(it) },
            onDeleteBookmark = vm::removeBookmark,
            onDeleteAnnotation = vm::deleteAnnotation,
            onDismiss = { showSheet = false },
        )
    }
    if (showBookmarkDialog) {
        BookmarkDialog(
            page = currentPage,
            onDismiss = { showBookmarkDialog = false },
            onSave = { label -> showBookmarkDialog = false; vm.addBookmark(currentPage, label) },
        )
    }
    pendingNote?.let { (page, point) ->
        TextNoteDialog(
            onDismiss = { pendingNote = null },
            onSave = { text ->
                pendingNote = null
                vm.addAnnotation(page, com.kosh.android.data.db.AnnotationType.TEXT, colorArgb, AnnotationStyle.size(AnnotationTool.TEXT, widthIndex), listOf(point), text)
            },
        )
    }
    if (showJump) {
        JumpDialog(pageCount = doc.pageCount, onDismiss = { showJump = false }, onGo = { index -> showJump = false; goTo(index) })
    }
    if (showDisplay) {
        DisplaySettingsDialog(
            prefs = prefs,
            onTheme = vm::setTheme,
            onInvert = vm::setInvert,
            onLayout = vm::setLayout,
            onDismiss = { showDisplay = false },
        )
    }
    if (showAutoScrollDialog) {
        AutoScrollDialog(
            speed = prefs.autoScrollSpeed,
            running = autoScrolling,
            onSpeed = vm::setAutoScrollSpeed,
            onStart = { autoScrolling = true; chromeVisible = false },
            onStop = { autoScrolling = false },
            onDismiss = { showAutoScrollDialog = false },
        )
    }
    if (showExportConfirm) {
        AlertDialog(
            onDismissRequest = { showExportConfirm = false },
            title = { Text(stringResource(R.string.annotated_export_title)) },
            text = { Text(stringResource(R.string.annotated_export_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showExportConfirm = false
                    annotatedLauncher.launch(suggestedExportName(doc.name.removeSuffix(".pdf") + " (annotated)", "pdf"))
                }) { Text(stringResource(R.string.annotated_export_choose)) }
            },
            dismissButton = { TextButton(onClick = { showExportConfirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    (exportState as? AnnotatedExportState.Running)?.let { running ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.annotated_exporting)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.export_progress, (running.done + 1).coerceAtMost(running.total), running.total))
                    LinearProgressIndicator(progress = { running.done / running.total.coerceAtLeast(1).toFloat() }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = vm::cancelAnnotatedExport) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showInfo) {
        val na = stringResource(R.string.info_not_available)
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(stringResource(R.string.info_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow(stringResource(R.string.info_file_name), doc.name)
                    InfoRow(stringResource(R.string.info_pages), doc.pageCount.toString())
                    InfoRow(stringResource(R.string.info_size), doc.sizeBytes?.let(::formatFileSize) ?: na)
                    InfoRow(stringResource(R.string.info_current_page), "${currentPage + 1} / ${doc.pageCount}")
                    InfoRow(stringResource(R.string.info_bookmarks), bookmarks.size.toString())
                    InfoRow(stringResource(R.string.info_annotations), annotations.size.toString())
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun AnnotationToolbar(
    tool: AnnotationTool,
    onTool: (AnnotationTool) -> Unit,
    colorIndex: Int,
    onColor: (Int) -> Unit,
    widthIndex: Int,
    onWidth: (Int) -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(tonalElevation = 6.dp, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.annotate_undo))
                }
                AnnotationTool.entries.forEach { t ->
                    FilterChip(
                        selected = tool == t,
                        onClick = { onTool(t) },
                        label = { Text(stringResource(toolLabel(t))) },
                        leadingIcon = if (tool == t) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null,
                    )
                }
                TextButton(onClick = onDone) { Text(stringResource(R.string.annotate_done)) }
            }
            if (tool == AnnotationTool.ERASER) {
                Text(stringResource(R.string.annotate_erase_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnnotationStyle.COLORS.forEachIndexed { i, argb ->
                        val name = stringResource(colorName(i))
                        val selected = i == colorIndex
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { onColor(i) })
                                .semantics { contentDescription = name },
                            contentAlignment = Alignment.Center,
                        ) {
                            // Selection is shown with a check mark as well as the border.
                            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = if (i == 0) Color.Black else Color.White)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    listOf(R.string.width_thin, R.string.width_medium, R.string.width_thick).forEachIndexed { i, res ->
                        FilterChip(selected = widthIndex == i, onClick = { onWidth(i) }, label = { Text(stringResource(res)) })
                    }
                }
                if (tool == AnnotationTool.TEXT) {
                    Text(stringResource(R.string.annotate_note_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
        }
    }
}

private fun toolLabel(t: AnnotationTool) = when (t) {
    AnnotationTool.PEN -> R.string.tool_pen
    AnnotationTool.HIGHLIGHTER -> R.string.tool_highlighter
    AnnotationTool.TEXT -> R.string.tool_note
    AnnotationTool.ERASER -> R.string.tool_eraser
}

private fun colorName(i: Int) = when (i) {
    0 -> R.string.color_yellow
    1 -> R.string.color_green
    2 -> R.string.color_blue
    3 -> R.string.color_red
    else -> R.string.color_black
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun JumpDialog(pageCount: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    fun submit() {
        val index = parsePageInput(text, pageCount)
        if (index == null) error = true else onGo(index)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_jump_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(6); error = false },
                label = { Text(stringResource(R.string.reader_jump_label, pageCount)) },
                isError = error,
                supportingText = if (error) {
                    { Text(stringResource(R.string.reader_jump_invalid, pageCount)) }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
            )
        },
        confirmButton = { TextButton(onClick = ::submit) { Text(stringResource(R.string.reader_jump_go)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val InvertFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

@Composable
private fun PdfPageItem(
    index: Int,
    size: PageSize,
    rotation: Int,
    contentWidthPx: Float,
    renderWidthPx: Int,
    offsetXPx: Int,
    invert: Boolean,
    render: suspend (Int, Int) -> Bitmap?,
    annotations: List<DrawItem>,
    interactive: Boolean,
    tool: AnnotationTool,
    colorArgb: Long,
    strokeSize: Float,
    onStroke: (List<NormalizedPoint>) -> Unit,
    onErase: (String) -> Unit,
    onAddText: (NormalizedPoint) -> Unit,
) {
    val density = LocalDensity.current
    val odd = rotation % 180 != 0
    // The page is laid out unrotated (w0 x h0) and then rotated; its displayed box swaps width and height at 90/270.
    val w0 = if (odd) contentWidthPx * size.widthPts / size.heightPts else contentWidthPx
    val h0 = if (odd) contentWidthPx else contentWidthPx * size.heightPts / size.widthPts
    val dispW = if (odd) h0 else w0
    val dispH = if (odd) w0 else h0
    val bitmapWidth = if (odd) (renderWidthPx * size.widthPts / size.heightPts.toFloat()).roundToInt() else renderWidthPx

    // The previous bitmap stays visible while a sharper one renders. The effect is cancelled when the
    // page scrolls out of composition, which drops obsolete work.
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index, bitmapWidth) {
        val result = render(index, bitmapWidth)
        if (result != null) {
            bitmap = result
            failed = false
        } else if (bitmap == null) {
            failed = true
        }
    }

    val label = stringResource(R.string.reader_page_label, index + 1)
    Box(
        modifier = Modifier
            .requiredSize(with(density) { dispW.toDp() }, with(density) { dispH.toDp() })
            .offset { IntOffset(offsetXPx, 0) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .requiredSize(with(density) { w0.toDp() }, with(density) { h0.toDp() })
                .graphicsLayer { rotationZ = rotation.toFloat() }
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            when {
                bmp != null -> {
                    val image = remember(bmp) { bmp.asImageBitmap() }
                    Image(
                        bitmap = image,
                        contentDescription = label,
                        contentScale = ContentScale.FillBounds, // same aspect ratio as the page, so no distortion
                        colorFilter = if (invert) InvertFilter else null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                failed -> Text(
                    stringResource(R.string.reader_page_failed, index + 1),
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                )
                else -> CircularProgressIndicator()
            }
            AnnotationLayer(
                items = annotations,
                interactive = interactive,
                tool = tool,
                colorArgb = colorArgb,
                strokeSize = strokeSize,
                onStroke = onStroke,
                onErase = onErase,
                onAddText = onAddText,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
