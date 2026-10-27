package com.villagefast.pdfeditor.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.villagefast.pdfeditor.ui.components.GlassTopAppBar
import com.villagefast.pdfeditor.ui.components.LiquidScaffold
import com.villagefast.pdfeditor.utils.*
import androidx.compose.ui.tooling.preview.Preview
import com.villagefast.pdfeditor.ui.theme.PdfeditorTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotateScreen(
    initialUri: Uri? = null,
    showAds: Boolean = true,
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit,
    viewModel: AnnotateViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var currentPageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isRendering by remember { mutableStateOf(false) }
    var totalPages by remember { mutableIntStateOf(0) }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    val colorOptions = listOf(
        Color(0xFFFFEB3B) to "Yellow",
        Color(0xFFF44336) to "Red",
        Color(0xFF4CAF50) to "Green",
        Color(0xFF2196F3) to "Blue",
        Color(0xFFE91E63) to "Pink",
        Color(0xFF00BCD4) to "Cyan",
        Color(0xFF000000) to "Black"
    )

    // Load initial PDF if provided
    LaunchedEffect(initialUri) {
        if (initialUri != null && state.sourceUri == null) {
            val name = PdfUtils.getFileName(context, initialUri) ?: "Selected PDF"
            viewModel.setSourcePdf(initialUri, name)
            totalPages = PdfUtils.getPageCount(context, initialUri)
        }
    }

    // Render current page when source or page changes
    LaunchedEffect(state.sourceUri, state.pageIndex) {
        state.sourceUri?.let { uri ->
            isRendering = true
            currentPageBitmap = PdfUtils.renderPage(context, uri, state.pageIndex, 2f)
            viewModel.loadTextBlocks(context) // Load text blocks for word selection
            isRendering = false
            scale = 1f
            offset = androidx.compose.ui.geometry.Offset.Zero
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val name = PdfUtils.getFileName(context, it) ?: "Selected PDF"
            viewModel.setSourcePdf(it, name)
            scope.launch { totalPages = PdfUtils.getPageCount(context, it) }
        }
    }

    LiquidScaffold(
        showBanner = showAds,
        topBar = {
            GlassTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Annotate", style = MaterialTheme.typography.titleMedium)
                        if (state.sourceUri != null) {
                            Text(
                                "Page ${state.pageIndex + 1} of $totalPages",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = state.undoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Undo, null)
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = state.redoStack.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Redo, null)
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                val file = PdfUtils.getPublicDocumentsFile("Annotated_${System.currentTimeMillis()}.pdf")
                                val outputUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                viewModel.applyAnnotations(context, outputUri)
                            }
                        },
                        enabled = state.sourceUri != null && state.annotations.isNotEmpty() && !state.isProcessing
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        bottomBar = {
            if (state.sourceUri != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    tonalElevation = 8.dp
                ) {
                    Column {
                        // Contextual Property Editor
                        AnimatedVisibility(visible = true) {
                            PropertyBar(state, viewModel, colorOptions)
                        }
                        
                        // Tool Selector
                        PrimaryScrollableTabRow(
                            selectedTabIndex = AnnotationTypeOption.entries.indexOf(state.selectedType),
                            containerColor = Color.Transparent,
                            edgePadding = 16.dp,
                            divider = {},
                            indicator = {}
                        ) {
                            AnnotationTypeOption.entries.forEach { type ->
                                Tab(
                                    selected = state.selectedType == type,
                                    onClick = { viewModel.setAnnotationType(type) },
                                    text = {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(type.icon, null, modifier = Modifier.size(20.dp))
                                            Text(type.displayName, style = MaterialTheme.typography.labelSmall)
                                        }
                                    },
                                    selectedContentColor = MaterialTheme.colorScheme.primary,
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            if (state.sourceUri == null) {
                EmptyState(onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) })
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                // Only pan/zoom if there are multiple pointers or we are zoomed in
                                scale *= zoom
                                offset += pan
                            }
                        }
                ) {
                    if (isRendering) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else if (currentPageBitmap != null) {
                        var containerWidth by remember { mutableStateOf(0f) }
                        var containerHeight by remember { mutableStateOf(0f) }

                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .graphicsLayer(
                                    scaleX = maxOf(1f, scale),
                                    scaleY = maxOf(1f, scale),
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                                .shadow(8.dp)
                                .background(Color.White)
                                .aspectRatio(currentPageBitmap!!.width.toFloat() / currentPageBitmap!!.height.toFloat())
                                .onGloballyPositioned {
                                    containerWidth = it.size.width.toFloat()
                                    containerHeight = it.size.height.toFloat()
                                }
                                .pointerInput(state.textBlocks, state.selectedType) {
                                    detectTapGestures { tapOffset ->
                                        // Map tapOffset (screen pixels) to PDF points
                                        val pdfX = (tapOffset.x / containerWidth) * (currentPageBitmap!!.width / 2f)
                                        val pdfY_fromTop = (tapOffset.y / containerHeight) * (currentPageBitmap!!.height / 2f)
                                        val pdfPageHeight = currentPageBitmap!!.height / 2f
                                        val pdfY = pdfPageHeight - pdfY_fromTop
                                        
                                        // Find if tap is within a text block for smart selection
                                        // We use a small tolerance/padding to make selection easier but accurate
                                        val clickedBlock = state.textBlocks.find { block ->
                                            val bounds = block.bounds
                                            // Ensure we are checking within the word boundaries
                                            pdfX >= bounds.x && pdfX <= (bounds.x + bounds.width) &&
                                            pdfY >= bounds.y && pdfY <= (bounds.y + bounds.height)
                                        }

                                        if (clickedBlock != null && state.selectedType in listOf(
                                            AnnotationTypeOption.HIGHLIGHT,
                                            AnnotationTypeOption.UNDERLINE,
                                            AnnotationTypeOption.STRIKETHROUGH
                                        )) {
                                            viewModel.addAnnotationForBlock(clickedBlock)
                                        } else if (state.selectedType !in listOf(
                                            AnnotationTypeOption.HAND,
                                            AnnotationTypeOption.HIGHLIGHT,
                                            AnnotationTypeOption.UNDERLINE,
                                            AnnotationTypeOption.STRIKETHROUGH
                                        )) {
                                            // Only allow adding other annotations (shapes, stamps, etc) 
                                            // if we are NOT using a text-only tool in empty space
                                            viewModel.setPosition(pdfX, pdfY)
                                            viewModel.addAnnotation()
                                        }
                                    }
                                }
                                .pointerInput(state.selectedType) {
                                    // Prevent screen "moving" when using a tool by consuming the drag
                                    if (state.selectedType != AnnotationTypeOption.HAND) {
                                        detectDragGestures(onDrag = { change, _ ->
                                            change.consume()
                                        })
                                    }
                                }
                        ) {
                            Image(
                                bitmap = currentPageBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            // Overlay for drawing annotations
                            AnnotationCanvas(
                                annotations = state.annotations.filter { it.pageIndex == state.pageIndex },
                                containerWidth = containerWidth,
                                containerHeight = containerHeight,
                                pdfPageWidth = currentPageBitmap!!.width / 2f,
                                pdfPageHeight = currentPageBitmap!!.height / 2f
                            )
                        }
                    }
                }

                // Page Navigation Overlay
                PageNavigationOverlay(
                    currentPage = state.pageIndex,
                    totalPages = totalPages,
                    onPageChange = { viewModel.setPageIndex(it) }
                )
            }

            // Processing Overlay
            if (state.isProcessing) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        Text("Applying Annotations...", color = Color.White)
                        Text("${state.progress}%", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
    
    // Result handling
    LaunchedEffect(state.isComplete, state.resultUri) {
        if (state.isComplete && state.resultUri != null) {
            onResult(state.resultUri!!, "Annotate")
        }
    }
}

@Composable
fun PropertyBar(state: AnnotationUiState, viewModel: AnnotateViewModel, colorOptions: List<Pair<Color, String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Color Selector
            if (state.selectedType !in listOf(AnnotationTypeOption.STAMP, AnnotationTypeOption.HAND)) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(colorOptions) { (color, _) ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (state.selectedColor == color.toArgb()) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.setColor(color.toArgb()) }
                        )
                    }
                }
            } else {
                // Stamp Type Selector
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(StampType.entries) { stamp ->
                        AssistChip(
                            onClick = { viewModel.setStampType(stamp) },
                            label = { Text(stamp.text, fontSize = 10.sp) },
                            colors = if (state.stampType == stamp) 
                                AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            else AssistChipDefaults.assistChipColors()
                        )
                    }
                }
            }

            // Size / Text Toggle
            if (state.selectedType in listOf(AnnotationTypeOption.STICKY_NOTE, AnnotationTypeOption.TEXT_BOX)) {
                var showTextDialog by remember { mutableStateOf(false) }
                IconButton(onClick = { showTextDialog = true }) {
                    Icon(Icons.Default.TextFields, "Edit Text")
                }
                if (showTextDialog) {
                    AlertDialog(
                        onDismissRequest = { showTextDialog = false },
                        title = { Text("Annotation Text") },
                        text = {
                            OutlinedTextField(
                                value = state.annotationText,
                                onValueChange = { viewModel.setAnnotationText(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Enter note text...") }
                            )
                        },
                        confirmButton = {
                            Button(onClick = { showTextDialog = false }) { Text("Done") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PageNavigationOverlay(currentPage: Int, totalPages: Int, onPageChange: (Int) -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-80).dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (currentPage > 0) onPageChange(currentPage - 1) }) {
                Icon(Icons.Default.ChevronLeft, null)
            }
            Text(
                "${currentPage + 1} / $totalPages",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = { if (currentPage < totalPages - 1) onPageChange(currentPage + 1) }) {
                Icon(Icons.Default.ChevronRight, null)
            }
        }
    }
}

@Composable
fun EmptyState(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.PictureAsPdf,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        Text("No PDF Selected", style = MaterialTheme.typography.titleMedium)
        Text("Select a file to start annotating", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClick) {
            Icon(Icons.Default.FileOpen, null)
            Spacer(Modifier.width(8.dp))
            Text("Select PDF")
        }
    }
}

@Composable
fun AnnotationCanvas(
    annotations: List<PdfAnnotation>,
    containerWidth: Float,
    containerHeight: Float,
    pdfPageWidth: Float,
    pdfPageHeight: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scaleX = containerWidth / pdfPageWidth
        val scaleY = containerHeight / pdfPageHeight

        annotations.forEach { annotation ->
            when (annotation) {
                is PdfAnnotation.Highlight -> {
                    val rect = annotation.rect
                    drawRect(
                        color = Color(annotation.color).copy(alpha = annotation.opacity),
                        topLeft = androidx.compose.ui.geometry.Offset(
                            rect.x * scaleX,
                            (pdfPageHeight - rect.y - rect.height) * scaleY
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            rect.width * scaleX,
                            rect.height * scaleY
                        )
                    )
                }
                is PdfAnnotation.Underline -> {
                    val y = (pdfPageHeight - annotation.startY) * scaleY
                    drawLine(
                        color = Color(annotation.color),
                        start = androidx.compose.ui.geometry.Offset(annotation.startX * scaleX, y),
                        end = androidx.compose.ui.geometry.Offset(annotation.endX * scaleX, y),
                        strokeWidth = annotation.strokeWidth * scaleX
                    )
                }
                is PdfAnnotation.Strikethrough -> {
                    // Strikethrough is usually in the middle of the text block, but our Underline data might be at the bottom.
                    // If addAnnotationForBlock uses bounds.y, it's at the bottom.
                    val y = (pdfPageHeight - annotation.startY - 5f) * scaleY // Offset slightly up for strikethrough
                    drawLine(
                        color = Color(annotation.color),
                        start = androidx.compose.ui.geometry.Offset(annotation.startX * scaleX, y),
                        end = androidx.compose.ui.geometry.Offset(annotation.endX * scaleX, y),
                        strokeWidth = annotation.strokeWidth * scaleX
                    )
                }
                else -> { /* Other types can be added later */ }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AnnotateScreenPreview() {
    PdfeditorTheme {
        AnnotateScreen(
            onBackClick = {},
            onResult = { _, _ -> }
        )
    }
}
