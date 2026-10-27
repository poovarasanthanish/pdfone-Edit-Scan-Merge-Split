package com.villagefast.pdfeditor.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.PI
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.graphics.toArgb
import androidx.pdf.PdfDocument
import androidx.pdf.SandboxedPdfLoader
import androidx.pdf.compose.PdfViewer
import androidx.pdf.compose.PdfViewerState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.villagefast.pdfeditor.ui.components.AdBanner
import com.villagefast.pdfeditor.ui.components.GlassTopAppBar
import com.villagefast.pdfeditor.ui.components.LiquidBackground
import com.villagefast.pdfeditor.utils.PdfUtils
import com.villagefast.pdfeditor.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.villagefast.pdfeditor.utils.AnnotationRect
import com.villagefast.pdfeditor.utils.TextBlock

enum class EditorMode {
    SELECT, EDIT_TEXT, ANNOTATE, FILL_SIGN, PAGE_MANAGER
}

data class AnnotationPath(
    val points: List<Offset>,
    val color: Color,
    val width: Float
)

sealed class EditorAction {
    data class TextUpdate(val index: Int, val oldText: String, val newText: String) : EditorAction()
    data class ToggleDelete(val index: Int, val isDeleted: Boolean) : EditorAction()
    data class AddAnnotation(val annotation: AnnotationPath) : EditorAction()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedPdfEditorScreen(
    initialUri: Uri? = null,
    showAds: Boolean = true,
    onBackClick: () -> Unit,
    onToolClick: (String, Uri?) -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var selectedUri by remember { mutableStateOf<Uri?>(initialUri) }
    var pdfDocument by remember { mutableStateOf<PdfDocument?>(null) }
    val pdfViewerState = remember { PdfViewerState() }
    val loader = remember { SandboxedPdfLoader(context) }

    LaunchedEffect(initialUri) {
        if (initialUri != null) {
            try {
                pdfDocument = loader.openDocument(initialUri)
            } catch (e: Exception) {
                Log.e("UnifiedEditor", "Error opening initial document", e)
            }
        }
    }

    var currentMode by remember { mutableStateOf(EditorMode.SELECT) }
    var showToolbar by remember { mutableStateOf(true) }
    var activeTab by remember { mutableStateOf("Text") } // "Text" or "Image"

    val undoStack = remember { mutableStateListOf<EditorAction>() }
    val redoStack = remember { mutableStateListOf<EditorAction>() }

    var currentPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var pdfPageSize by remember { mutableStateOf<android.util.Size?>(null) }
    var currentPageIndex by remember { mutableIntStateOf(0) }

    var textBlocks by remember { mutableStateOf<List<TextBlock>>(emptyList()) }
    var selectedBlockIndex by remember { mutableIntStateOf(-1) }
    var analysisJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val annotationPaths = remember { mutableStateListOf<AnnotationPath>() }
    var selectedColor by remember { mutableStateOf(Color.Red) }
    var strokeWidth by remember { mutableStateOf(5f) }

    var showSignatureDialog by remember { mutableStateOf(false) }
    var signatureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var signatureOffset by remember { mutableStateOf(Offset(100f, 100f)) }
    var screenWidth by remember { mutableIntStateOf(0) }
    var screenHeight by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        selectedUri = uri
        uri?.let {
            scope.launch {
                try {
                    pdfDocument = loader.openDocument(it)
                    currentMode = EditorMode.SELECT
                } catch (e: Exception) {
                    Log.e("UnifiedEditor", "Error opening document", e)
                }
            }
        }
    }

    LiquidBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassTopAppBar(
                    title = {
                        if (selectedUri != null && currentMode == EditorMode.EDIT_TEXT) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Spacer(Modifier.weight(1f))
                                SingleChoiceSegmentedButtonRow {
                                    SegmentedButton(
                                        selected = true,
                                        onClick = { },
                                        shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                                    ) { Text("Edit") }
                                    SegmentedButton(
                                        selected = false,
                                        onClick = { },
                                        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                                    ) { Icon(Icons.Default.UnfoldMore, null) }
                                }
                                Spacer(Modifier.weight(1f))
                            }
                        } else {
                            Text(if (selectedUri == null) "PDF One Editor" else "Editing PDF")
                        }
                    },
                    navigationIcon = {
                        if (selectedUri != null && currentMode == EditorMode.EDIT_TEXT) {
                            TextButton(onClick = { 
                                currentMode = EditorMode.SELECT 
                                selectedBlockIndex = -1
                                keyboardController?.hide()
                            }) {
                                Text("Done", color = Color.Red, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (selectedUri != null) {
                            if (currentMode != EditorMode.EDIT_TEXT) {
                                IconButton(onClick = {
                                    if (undoStack.isNotEmpty()) {
                                        val action = undoStack.removeAt(undoStack.size - 1)
                                        when (action) {
                                            is EditorAction.TextUpdate -> {
                                                val updated = textBlocks.toMutableList()
                                                updated[action.index] = updated[action.index].copy(currentText = action.oldText)
                                                textBlocks = updated
                                            }
                                            is EditorAction.ToggleDelete -> {
                                                val updated = textBlocks.toMutableList()
                                                updated[action.index] = updated[action.index].copy(isDeleted = !action.isDeleted)
                                                textBlocks = updated
                                            }
                                            is EditorAction.AddAnnotation -> {
                                                annotationPaths.remove(action.annotation)
                                            }
                                        }
                                        redoStack.add(action)
                                    }
                                }, enabled = undoStack.isNotEmpty()) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                                }
                                IconButton(onClick = {
                                    if (redoStack.isNotEmpty()) {
                                        val action = redoStack.removeAt(redoStack.size - 1)
                                        when (action) {
                                            is EditorAction.TextUpdate -> {
                                                val updated = textBlocks.toMutableList()
                                                updated[action.index] = updated[action.index].copy(currentText = action.newText)
                                                textBlocks = updated
                                            }
                                            is EditorAction.ToggleDelete -> {
                                                val updated = textBlocks.toMutableList()
                                                updated[action.index] = updated[action.index].copy(isDeleted = action.isDeleted)
                                                textBlocks = updated
                                            }
                                            is EditorAction.AddAnnotation -> {
                                                annotationPaths.add(action.annotation)
                                            }
                                        }
                                        undoStack.add(action)
                                    }
                                }, enabled = redoStack.isNotEmpty()) {
                                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                                }
                                IconButton(onClick = { currentMode = EditorMode.PAGE_MANAGER }) {
                                    Icon(Icons.Default.ViewAgenda, contentDescription = "Pages")
                                }
                            }

                            TextButton(onClick = {
                                scope.launch {
                                    isAnalyzing = true
                                    val file = saveFinalPdf(
                                        context, 
                                        selectedUri!!, 
                                        textBlocks, 
                                        annotationPaths,
                                        signatureBitmap, 
                                        signatureOffset, 
                                        screenWidth, 
                                        screenHeight
                                    )
                                    isAnalyzing = false
                                    if (file != null) {
                                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                        if (showAds && activity != null) {
                                            com.villagefast.pdfeditor.ads.AdManager.showInterstitial(activity) {
                                                onResult(uri, "Edit")
                                            }
                                        } else {
                                            onResult(uri, "Edit")
                                        }
                                    }
                                }
                            }) {
                                Text("Save", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }

                            IconButton(onClick = { /* More menu */ }) {
                                Icon(Icons.Default.MoreHoriz, contentDescription = "More")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                Column {
                    if (currentMode == EditorMode.EDIT_TEXT && selectedBlockIndex != -1) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().imePadding(),
                            tonalElevation = 8.dp,
                            shadowElevation = 8.dp,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            val updated = textBlocks.toMutableList()
                                            updated[selectedBlockIndex] = updated[selectedBlockIndex].copy(fontSize = (updated[selectedBlockIndex].fontSize - 1f).coerceAtLeast(1f))
                                            textBlocks = updated
                                        }) { Icon(Icons.Default.Remove, "Smaller") }
                                        
                                        Text(
                                            "${textBlocks[selectedBlockIndex].fontSize.roundToInt()}pt",
                                            modifier = Modifier.padding(horizontal = 4.dp),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        
                                        IconButton(onClick = {
                                            val updated = textBlocks.toMutableList()
                                            updated[selectedBlockIndex] = updated[selectedBlockIndex].copy(fontSize = updated[selectedBlockIndex].fontSize + 1f)
                                            textBlocks = updated
                                        }) { Icon(Icons.Default.Add, "Larger") }
                                        
                                        Spacer(Modifier.width(16.dp))
                                        
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(textBlocks[selectedBlockIndex].color))
                                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                                .clickable { 
                                                    val updated = textBlocks.toMutableList()
                                                    val colors = listOf(Color.Black, Color.Red, Color.Blue, Color.Green, Color.Yellow)
                                                    val currentColorIndex = colors.indexOfFirst { it.toArgb() == updated[selectedBlockIndex].color }
                                                    val nextColor = colors[(currentColorIndex + 1) % colors.size]
                                                    updated[selectedBlockIndex] = updated[selectedBlockIndex].copy(color = nextColor.toArgb())
                                                    textBlocks = updated
                                                }
                                        )
                                    }

                                    Row {
                                        IconButton(onClick = {
                                            val updated = textBlocks.toMutableList()
                                            updated[selectedBlockIndex] = updated[selectedBlockIndex].copy(isDeleted = true)
                                            textBlocks = updated
                                            selectedBlockIndex = -1
                                            keyboardController?.hide()
                                        }) {
                                            Icon(Icons.Default.Delete, "Delete", tint = Color.Red)
                                        }
                                        TextButton(onClick = { 
                                            selectedBlockIndex = -1 
                                            keyboardController?.hide()
                                        }) {
                                            Text("Done", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (selectedUri != null && showToolbar && currentMode != EditorMode.EDIT_TEXT) {
                        Surface(
                            color = (if (LocalIsDarkTheme.current) Color.Black else Color.White).copy(alpha = 0.2f),
                            tonalElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                EditorToolButton(Icons.Default.TouchApp, "Select", currentMode == EditorMode.SELECT) { currentMode = EditorMode.SELECT }

                                EditorToolButton(Icons.Default.Gesture, "Sign", currentMode == EditorMode.FILL_SIGN) {
                                    currentMode = EditorMode.FILL_SIGN
                                    preparePageBitmap(context, selectedUri, currentPageIndex, scope) { currentPageBitmap = it }
                                }
                                EditorToolButton(Icons.Default.Dashboard, "OnlyOffice", false) {
                                    onToolClick("onlyoffice", selectedUri)
                                }
                                EditorToolButton(Icons.Default.FormatListNumbered, "Numbers", false) {
                                    onToolClick("page_numbering", selectedUri)
                                }
                            }
                        }
                    }
                    if (showAds) {
                        AdBanner()
                    }
                }
            }
        ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (selectedUri != null && currentMode == EditorMode.EDIT_TEXT && selectedBlockIndex == -1) {
                Column {
                    TabRow(
                        selectedTabIndex = if (activeTab == "Text") 0 else 1,
                        containerColor = MaterialTheme.colorScheme.surface,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[if (activeTab == "Text") 0 else 1]),
                                color = Color.Red
                            )
                        }
                    ) {
                        Tab(selected = activeTab == "Text", onClick = { activeTab = "Text" }) {
                            Text("Text", modifier = Modifier.padding(16.dp))
                        }
                        Tab(selected = activeTab == "Image", onClick = { activeTab = "Image" }) {
                            Text("Image", modifier = Modifier.padding(16.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val newBlock = TextBlock(
                                text = "",
                                bounds = AnnotationRect(100f, 100f, 200f, 40f),
                                pageIndex = currentPageIndex,
                                currentText = "New Text"
                            )
                            textBlocks = textBlocks + newBlock
                            selectedBlockIndex = textBlocks.size - 1
                        }.padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(24.dp).border(1.dp, Color.Gray, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("T", color = Color.Red, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Add Text")
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { coordinates ->
                    screenWidth = coordinates.size.width
                    screenHeight = coordinates.size.height
                },
                contentAlignment = Alignment.Center
            ) {
                if (selectedUri == null) {
                    EmptyStateView { launcher.launch(arrayOf("application/pdf")) }
                } else {
                    when (currentMode) {
                        EditorMode.SELECT -> PdfViewer(pdfDocument = pdfDocument, state = pdfViewerState, modifier = Modifier.fillMaxSize())
                        EditorMode.EDIT_TEXT -> PdfTextEditor(
                            bitmap = currentPageBitmap,
                            textBlocks = textBlocks,
                            isAnalyzing = isAnalyzing,
                            pdfPageSize = pdfPageSize,
                            selectedIndex = selectedBlockIndex,
                            onBlockClick = { selectedBlockIndex = it },
                            onTextChanged = { index, newText ->
                                val updated = textBlocks.toMutableList()
                                updated[index] = updated[index].copy(currentText = newText)
                                textBlocks = updated
                            },
                            onPositionChanged = { index, dx, dy ->
                                val updated = textBlocks.toMutableList()
                                updated[index] = updated[index].copy(
                                    xOffset = updated[index].xOffset + dx,
                                    yOffset = updated[index].yOffset + dy
                                )
                                textBlocks = updated
                            }
                        )
                        EditorMode.ANNOTATE -> AnnotateLayer(
                            bitmap = currentPageBitmap, 
                            paths = annotationPaths, 
                            currentColor = selectedColor, 
                            currentWidth = strokeWidth
                        ) { 
                            annotationPaths.add(it)
                            undoStack.add(EditorAction.AddAnnotation(it))
                            redoStack.clear()
                        }
                        EditorMode.FILL_SIGN -> FillSignLayer(bitmap = currentPageBitmap, signature = signatureBitmap, offset = signatureOffset, onOffsetChange = { signatureOffset = it }, onAddSignature = { showSignatureDialog = true })
                        EditorMode.PAGE_MANAGER -> PageManagerView(onDismiss = { currentMode = EditorMode.SELECT })
                    }
                }

                if (!showToolbar && selectedUri != null && currentMode != EditorMode.EDIT_TEXT) {
                    FloatingActionButton(
                        onClick = { showToolbar = true },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Edit, "Show Tools")
                    }
                }
            }
        }

        if (showSignatureDialog) {
            UnifiedSignatureDialog(onDismiss = { showSignatureDialog = false }, onSave = { signatureBitmap = it; showSignatureDialog = false })
        }
    }
}
}

@Composable
fun UnifiedSignatureDialog(onDismiss: () -> Unit, onSave: (Bitmap) -> Unit) {
    val paths = remember { mutableStateListOf<ComposePath>() }
    var currentPath by remember { mutableStateOf<ComposePath?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize().padding(16.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Draw your signature", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> currentPath = ComposePath().apply { moveTo(offset.x, offset.y) } },
                                onDrag = { change, _ ->
                                    currentPath?.lineTo(change.position.x, change.position.y)
                                    val temp = currentPath; currentPath = null; currentPath = temp
                                },
                                onDragEnd = { currentPath?.let { paths.add(it) }; currentPath = null }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        paths.forEach { drawPath(it, Color.Black, style = Stroke(width = 5f)) }
                        currentPath?.let { drawPath(it, Color.Black, style = Stroke(width = 5f)) }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { paths.clear() }) { Text("Clear") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        if (paths.isNotEmpty()) {
                            val bitmap = Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)
                            canvas.drawColor(android.graphics.Color.WHITE)
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.BLACK; style = android.graphics.Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true; strokeJoin = android.graphics.Paint.Join.ROUND; strokeCap = android.graphics.Paint.Cap.ROUND
                            }
                            paths.forEach { canvas.drawPath(it.asAndroidPath(), paint) }
                            onSave(bitmap)
                        }
                    }) { Text("Save Signature") }
                }
            }
        }
    }
}

/**
 * Enhanced detectTransformGestures that allows clicks to pass through.
 */
suspend fun PointerInputScope.detectTransformGestures(
    panZoomLock: Boolean = false,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        var lockedToPanZoom = false

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val rotationMotion = abs(rotation) * atan(touchSlop / centroidSize) * (180f / PI.toFloat())
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop ||
                        rotationMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                        lockedToPanZoom = panZoomLock && zoomMotion > touchSlop
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val effectiveRotation = if (lockedToPanZoom) 0f else rotationChange
                    if (effectiveRotation != 0f ||
                        zoomChange != 1f ||
                        panChange != Offset.Zero
                    ) {
                        onGesture(centroid, panChange, zoomChange, effectiveRotation)
                    }
                    event.changes.forEach {
                        if (it.positionChanged()) {
                            it.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}

@Composable
fun PdfTextEditor(
    bitmap: Bitmap?,
    textBlocks: List<TextBlock>,
    isAnalyzing: Boolean,
    pdfPageSize: android.util.Size?,
    selectedIndex: Int,
    onBlockClick: (Int) -> Unit,
    onTextChanged: (Int, String) -> Unit,
    onPositionChanged: (Int, Float, Float) -> Unit
) {
    if (bitmap == null || isAnalyzing || pdfPageSize == null) {
        LoadingScreen("Analyzing Document Structure...")
    } else {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var viewSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

        // Fit page to viewport initially
        val baseScale = remember(viewSize, pdfPageSize) {
            if (viewSize.width > 0 && viewSize.height > 0 && pdfPageSize.width > 0) {
                val sW = viewSize.width.toFloat() / pdfPageSize.width.toFloat()
                val sH = viewSize.height.toFloat() / pdfPageSize.height.toFloat()
                minOf(sW, sH)
            } else 1f
        }

        val animOffset = animateOffsetAsState(targetValue = offset, label = "offset")

        LaunchedEffect(selectedIndex, viewSize) {
            if (selectedIndex != -1 && pdfPageSize != null && viewSize.height > 0) {
                val block = textBlocks[selectedIndex]
                // Centering logic relative to viewport center
                val blockY = (block.bounds.top + block.yOffset + block.bounds.height() / 2) * baseScale
                val targetY = (viewSize.height / 2f - blockY * scale)
                offset = Offset(offset.x, targetY / scale)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF2B2B2B))
                .onGloballyPositioned { viewSize = it.size }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 15f)
                        offset += pan / scale
                    }
                }
        ) {
            val density = LocalDensity.current
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = animOffset.value.x * scale
                        translationY = animOffset.value.y * scale
                    }
                    .size(
                        width = with(density) { (pdfPageSize.width * baseScale).toDp() },
                        height = with(density) { (pdfPageSize.height * baseScale).toDp() }
                    )
                    .background(Color.White)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                
                textBlocks.forEachIndexed { index, block -> 
                    key(index) {
                        EditableBlock(
                            block = block,
                            isSelected = selectedIndex == index,
                            scaleFactor = baseScale,
                            density = density,
                            onClick = { onBlockClick(index) },
                            onValueChange = { onTextChanged(index, it) },
                            onPositionChange = { dx, dy -> onPositionChanged(index, dx, dy) },
                            onDone = { onBlockClick(-1) }
                        )
                    }
                }
            }
            
            if (scale != 1f || offset != Offset.Zero) {
                ResetZoomButton(modifier = Modifier.align(Alignment.BottomEnd)) { scale = 1f; offset = Offset.Zero }
            }
        }
    }
}

@Composable
fun EditableBlock(
    block: TextBlock,
    isSelected: Boolean,
    scaleFactor: Float,
    density: androidx.compose.ui.unit.Density,
    onClick: () -> Unit,
    onValueChange: (String) -> Unit,
    onPositionChange: (Float, Float) -> Unit,
    onDone: () -> Unit
) {
    val left = (block.bounds.left + block.xOffset) * scaleFactor
    val top = (block.bounds.top + block.yOffset) * scaleFactor
    val width = block.bounds.width * scaleFactor
    val height = block.bounds.height * scaleFactor
    
    val focusRequester = remember { FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val textStyle = remember(height, block.color, block.fontSize, scaleFactor) {
        androidx.compose.ui.text.TextStyle(
            color = Color(block.color),
            fontSize = with(density) { (block.fontSize * scaleFactor).toSp() },
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
    }

    LaunchedEffect(isSelected) {
        if (isSelected) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .offset(x = with(density) { left.toDp() }, y = with(density) { top.toDp() })
            .size(width = with(density) { width.toDp() }, height = with(density) { height.toDp() })
            .pointerInput(isSelected) {
                if (isSelected) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onPositionChange(dragAmount.x / scaleFactor, dragAmount.y / scaleFactor)
                    }
                }
            }
            .drawBehind {
                // Seamless edit: Only white out if text is modified or being edited
                // This makes edited text look like it's part of the original PDF
                if (isSelected || block.currentText != block.text || block.isDeleted) {
                    drawRect(color = Color.White)
                }

                if (isSelected) {
                    // Professional selection border with corner handles
                    drawRect(
                        color = Color(0xFF2196F3),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    
                    val hS = 4.dp.toPx()
                    val hSize = androidx.compose.ui.geometry.Size(hS, hS)
                    drawRect(color = Color.White, topLeft = Offset(-hS/2, -hS/2), size = hSize)
                    drawRect(color = Color(0xFF2196F3), topLeft = Offset(-hS/2, -hS/2), size = hSize, style = Stroke(width = 1.dp.toPx()))
                    
                    drawRect(color = Color.White, topLeft = Offset(size.width - hS/2, -hS/2), size = hSize)
                    drawRect(color = Color(0xFF2196F3), topLeft = Offset(size.width - hS/2, -hS/2), size = hSize, style = Stroke(width = 1.dp.toPx()))

                    drawRect(color = Color.White, topLeft = Offset(-hS/2, size.height - hS/2), size = hSize)
                    drawRect(color = Color(0xFF2196F3), topLeft = Offset(-hS/2, size.height - hS/2), size = hSize, style = Stroke(width = 1.dp.toPx()))

                    drawRect(color = Color.White, topLeft = Offset(size.width - hS/2, size.height - hS/2), size = hSize)
                    drawRect(color = Color(0xFF2196F3), topLeft = Offset(size.width - hS/2, size.height - hS/2), size = hSize, style = Stroke(width = 1.dp.toPx()))
                }
            }
            .clickable(enabled = !isSelected) { onClick() }
    ) {
        val showText = isSelected || (block.currentText != block.text && !block.isDeleted)
        if (showText) {
            if (isSelected) {
                BasicTextField(
                    value = if (block.isDeleted) "" else block.currentText,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester),
                    textStyle = textStyle,
                    cursorBrush = SolidColor(Color(0xFF2196F3)),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { 
                            onDone()
                            keyboardController?.hide()
                        }
                    )
                )
            } else {
                Text(
                    text = block.currentText,
                    modifier = Modifier.fillMaxSize(),
                    style = textStyle,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun FillSignLayer(
    bitmap: Bitmap?,
    signature: Bitmap?,
    offset: Offset,
    onOffsetChange: (Offset) -> Unit,
    onAddSignature: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
        
        if (signature == null) {
            Button(onClick = onAddSignature) { Text("Add Signature") }
        } else {
            Box(
                modifier = Modifier
                    .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onOffsetChange(offset + dragAmount)
                        }
                    }
                    .border(1.dp, Color(0xFF2196F3), RoundedCornerShape(4.dp))
                    .padding(4.dp)
            ) {
                Image(bitmap = signature.asImageBitmap(), contentDescription = "Signature", modifier = Modifier.size(150.dp, 80.dp))
                
                val handleSize = 12.dp
                val handleColor = Color(0xFF2196F3)
                
                Box(modifier = Modifier.align(Alignment.TopStart).offset((-4).dp, (-4).dp).size(handleSize).background(Color.White, CircleShape).border(1.dp, handleColor, CircleShape))
                Box(modifier = Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).size(handleSize).background(Color.White, CircleShape).border(1.dp, handleColor, CircleShape))
                Box(modifier = Modifier.align(Alignment.BottomStart).offset((-4).dp, 4.dp).size(handleSize).background(Color.White, CircleShape).border(1.dp, handleColor, CircleShape))
                Box(modifier = Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(handleSize).background(Color.White, CircleShape).border(1.dp, handleColor, CircleShape))

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 16.dp)
                        .size(24.dp)
                        .background(handleColor, CircleShape)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowDropDown, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun LoadingScreen(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(); Spacer(modifier = Modifier.height(16.dp)); Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ResetZoomButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick, modifier = modifier.padding(16.dp), containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Icon(Icons.Default.ZoomOutMap, contentDescription = "Reset Zoom")
    }
}

@Composable
fun AnnotateLayer(bitmap: Bitmap?, paths: List<AnnotationPath>, currentColor: Color, currentWidth: Float, onPathAdded: (AnnotationPath) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
        var currentPoints = remember { mutableStateListOf<Offset>() }
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> 
                        currentPoints.clear()
                        currentPoints.add(offset) 
                    },
                    onDrag = { change, _ ->
                        currentPoints.add(change.position)
                        change.consume()
                    },
                    onDragEnd = { 
                        if (currentPoints.isNotEmpty()) {
                            onPathAdded(AnnotationPath(currentPoints.toList(), currentColor, currentWidth))
                        }
                        currentPoints.clear()
                    }
                )
            }
        ) {
            paths.forEach { ann ->
                if (ann.points.size > 1) {
                    val path = ComposePath().apply {
                        moveTo(ann.points[0].x, ann.points[0].y)
                        for (i in 1 until ann.points.size) {
                            lineTo(ann.points[i].x, ann.points[i].y)
                        }
                    }
                    drawPath(path, ann.color, style = Stroke(width = ann.width))
                }
            }
            if (currentPoints.size > 1) {
                val path = ComposePath().apply {
                    moveTo(currentPoints[0].x, currentPoints[0].y)
                    for (i in 1 until currentPoints.size) {
                        lineTo(currentPoints[i].x, currentPoints[i].y)
                    }
                }
                drawPath(path, currentColor, style = Stroke(width = currentWidth))
            }
        }
    }
}

private suspend fun renderPage(context: android.content.Context, uri: Uri, pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
    PdfUtils.renderPage(context, uri, pageIndex, 2.0f)
}

private suspend fun analyzePdfText(context: android.content.Context, uri: Uri, pageIndex: Int, onResult: (List<TextBlock>, android.util.Size) -> Unit) = withContext(Dispatchers.IO) {
    try {
        val blocks = PdfUtils.getTextBlocks(context, uri, pageIndex)
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
            val page = document.getPage(pageIndex)
            val mediaBox = page.mediaBox
            val pageSize = android.util.Size(mediaBox.width.toInt(), mediaBox.height.toInt())
            document.close()
            onResult(blocks, pageSize)
        }
    } catch (e: Exception) {
        Log.e("UnifiedEditor", "Analysis error", e)
        onResult(emptyList(), android.util.Size(0, 0))
    }
}

@Composable
fun EditorToolButton(icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable { onClick() }.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

fun preparePageBitmap(context: android.content.Context, uri: Uri?, pageIndex: Int, scope: kotlinx.coroutines.CoroutineScope, onBitmapReady: (Bitmap?) -> Unit) {
    if (uri == null) return
    scope.launch {
        val bitmap = renderPage(context, uri, pageIndex)
        onBitmapReady(bitmap)
    }
}

@Composable
fun EmptyStateView(onSelectClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onSelectClick) { Text("Select PDF to Edit") }
    }
}

@Composable
fun PageManagerView(onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
        Text("Page Manager (Not Implemented)", color = Color.White)
    }
}

private suspend fun saveFinalPdf(
    context: android.content.Context,
    uri: Uri,
    blocks: List<TextBlock>,
    annotations: List<AnnotationPath>,
    signature: Bitmap?,
    signatureOffset: Offset,
    screenWidth: Int,
    screenHeight: Int
): File? = withContext(Dispatchers.IO) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
        inputStream.close()
        
        try {
            val blocksByPage = blocks.groupBy { it.pageIndex }
            
            for (pageIdx in 0 until document.numberOfPages) {
                val page = document.getPage(pageIdx)
                val mediaBox = page.mediaBox
                val pageBlocks = blocksByPage[pageIdx] ?: emptyList()
                
                val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                    document, 
                    page, 
                    com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, 
                    true, 
                    true
                )
                
                // Save Text Edits
                pageBlocks.forEach { block ->
                    if (block.currentText != block.text || block.isDeleted || block.xOffset != 0f || block.yOffset != 0f) {
                        contentStream.setNonStrokingColor(255, 255, 255)
                        val y = mediaBox.height - block.bounds.bottom
                        contentStream.addRect(block.bounds.left, y, block.bounds.width, block.bounds.height)
                        contentStream.fill()
                        
                        if (!block.isDeleted) {
                            contentStream.beginText()
                            contentStream.setNonStrokingColor(android.graphics.Color.red(block.color), android.graphics.Color.green(block.color), android.graphics.Color.blue(block.color))
                            contentStream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, block.fontSize)
                            
                            val newX = block.bounds.left + block.xOffset
                            val newY = mediaBox.height - (block.bounds.top + block.yOffset + block.fontSize)
                            contentStream.newLineAtOffset(newX, newY)
                            contentStream.showText(block.currentText)
                            contentStream.endText()
                        }
                    }
                }

                // Save Annotations (Drawings)
                if (pageIdx == 0) { // Currently simplified to first page or current page
                    val scaleX = mediaBox.width / screenWidth.toFloat()
                    val scaleY = mediaBox.height / screenHeight.toFloat()
                    
                    annotations.forEach { ann ->
                        contentStream.setStrokingColor(
                            android.graphics.Color.red(ann.color.toArgb()),
                            android.graphics.Color.green(ann.color.toArgb()),
                            android.graphics.Color.blue(ann.color.toArgb())
                        )
                        contentStream.setLineWidth(ann.width * scaleX)
                        contentStream.setLineCapStyle(1) // Round
                        contentStream.setLineJoinStyle(1) // Round
                        
                        if (ann.points.isNotEmpty()) {
                            val first = ann.points[0]
                            contentStream.moveTo(first.x * scaleX, mediaBox.height - (first.y * scaleY))
                            for (i in 1 until ann.points.size) {
                                val p = ann.points[i]
                                contentStream.lineTo(p.x * scaleX, mediaBox.height - (p.y * scaleY))
                            }
                            contentStream.stroke()
                        }
                    }
                }
                
                // Save Signature
                if (signature != null && pageIdx == 0) {
                    val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(document, signature)
                    val scaleX = mediaBox.width / screenWidth.toFloat()
                    val scaleY = mediaBox.height / screenHeight.toFloat()
                    
                    val sigX = signatureOffset.x * scaleX
                    val sigY = mediaBox.height - (signatureOffset.y * scaleY + 80f * context.resources.displayMetrics.density * scaleY)
                    
                    contentStream.drawImage(pdImage, sigX, sigY, 150f * context.resources.displayMetrics.density * scaleX, 80f * context.resources.displayMetrics.density * scaleY)
                }
                
                contentStream.close()
            }
            
            val outputFile = PdfUtils.getPublicDocumentsFile("Edited_${System.currentTimeMillis()}.pdf")
            document.save(outputFile)
            document.close()
            return@withContext outputFile
        } finally {
            document.close()
        }
    } catch (e: Exception) {
        Log.e("UnifiedEditor", "PDFBox save error", e)
        return@withContext null
    }
}
