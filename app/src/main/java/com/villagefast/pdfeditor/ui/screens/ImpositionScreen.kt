package com.villagefast.pdfeditor.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.util.Matrix
import com.villagefast.pdfeditor.ui.components.*
import com.villagefast.pdfeditor.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class ImpositionMode {
    N_UP,
    BOOKLET,
    SIGNATURE
}

enum class NUpLayout(val label: String, val pages: Int, val rows: Int, val cols: Int) {
    TWO_UP("2-Up", 2, 2, 1),
    FOUR_UP("4-Up", 4, 2, 2),
    SIX_UP("6-Up", 6, 3, 2),
    EIGHT_UP("8-Up", 8, 4, 2),
    NINE_UP("9-Up", 9, 3, 3),
    SIXTEEN_UP("16-Up", 16, 4, 4)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImpositionScreen(
    initialUri: Uri? = null,
    showAds: Boolean = true,
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val accentColor = Color(0xFF673AB7)

    var currentState by remember { mutableStateOf(ToolState.SELECTING) }
    var selectedUri by remember { mutableStateOf(initialUri) }
    var outputUri by remember { mutableStateOf<Uri?>(null) }
    var unlockPassword by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var pageCount by remember { mutableIntStateOf(0) }
    var isFileLoading by remember { mutableStateOf(false) }
    var processingTime by remember { mutableStateOf("") }
    var fileToUnlock by remember { mutableStateOf<String?>(null) }

    // Configuration state
    var mode by remember { mutableStateOf(ImpositionMode.N_UP) }
    var selectedLayout by remember { mutableStateOf(NUpLayout.FOUR_UP) }
    var isLandscape by remember { mutableStateOf(false) }
    var margin by remember { mutableFloatStateOf(20f) }
    var spacing by remember { mutableFloatStateOf(10f) }
    var gutter by remember { mutableFloatStateOf(0f) }
    var showCropMarks by remember { mutableStateOf(false) }

    fun handleFileSelection(uri: Uri) {
        selectedUri = uri
        val details = PdfUtils.getUriDetails(context, uri)
        fileName = details.name
        isFileLoading = true
        scope.launch(Dispatchers.IO) {
            val isEnc = PdfUtils.checkIsEncryptedLocal(context, uri)
            withContext(Dispatchers.Main) {
                if (isEnc) {
                    fileToUnlock = fileName
                    isFileLoading = false
                } else {
                    val count = PdfUtils.getPageCount(context, uri)
                    pageCount = count
                    currentState = ToolState.CONFIGURING
                    isFileLoading = false
                }
            }
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null) {
            handleFileSelection(initialUri)
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleFileSelection(it) }
    }

    fun startProcessing() {
        currentState = ToolState.PROCESSING
        val startTime = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            try {
                val resultFile = performImposition(
                    context,
                    selectedUri!!,
                    mode,
                    selectedLayout,
                    isLandscape,
                    margin,
                    spacing,
                    gutter,
                    showCropMarks,
                    unlockPassword.ifEmpty { null }
                )

                if (resultFile != null) {
                    val endTime = System.currentTimeMillis()
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        resultFile
                    )
                    withContext(Dispatchers.Main) {
                        processingTime = java.util.Locale.US.let { locale ->
                            String.format(locale, "%.1fs", (endTime - startTime) / 1000.0)
                        }
                        outputUri = uri
                        currentState = ToolState.SUCCESS
                    }
                } else {
                    throw Exception("Failed to process PDF")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    currentState = ToolState.CONFIGURING
                }
            }
        }
    }

    LiquidScaffold(
        showBanner = showAds,
        topBar = {
            if (currentState != ToolState.SUCCESS && currentState != ToolState.PROCESSING) {
                GlassTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PDF Imposition", fontSize = 16.sp, fontWeight = FontWeight.Black)
                            Text("PRINT-READY LAYOUTS", fontSize = 8.sp, fontWeight = FontWeight.Black, color = accentColor, letterSpacing = 1.sp)
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(20.dp))
                        }
                    },
                    actions = {
                        if (selectedUri != null && currentState == ToolState.CONFIGURING) {
                            TextButton(onClick = { selectedUri = null; currentState = ToolState.SELECTING }) {
                                Text("CHANGE", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isFileLoading) {
                LoadingStateView(accentColor, false, "Analyzing pages...")
            } else {
                when (currentState) {
                    ToolState.SELECTING -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            SelectionGrid(
                                onSelect = { pickLauncher.launch("application/pdf") },
                                isDark = isDark,
                                icon = Icons.Default.Layers,
                                title = "Tap to enter file",
                                subtitle = "PROFESSIONAL PRINT LAYOUTS",
                                accentColor = accentColor
                            )
                        }
                    }
                    ToolState.CONFIGURING -> {
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .background(accentColor.copy(alpha = 0.05f)),
                                contentAlignment = Alignment.Center
                            ) {
                                ImpositionPreview(
                                    mode = mode,
                                    layout = selectedLayout,
                                    isLandscape = isLandscape,
                                    margin = margin,
                                    spacing = spacing,
                                    gutter = gutter,
                                    uri = selectedUri,
                                    password = unlockPassword.ifEmpty { null },
                                    accentColor = accentColor,
                                    pageCount = pageCount
                                )
                            }

                            Column(Modifier.padding(24.dp)) {
                                Text("Imposition Type", fontSize = 14.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ImpositionMode.entries.forEach { m ->
                                        FilterChip(
                                            selected = mode == m,
                                            onClick = { mode = m },
                                            label = { Text(m.name.replace("_", "-")) },
                                            modifier = Modifier.weight(1f),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = accentColor,
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                }

                                if (mode == ImpositionMode.N_UP) {
                                    Spacer(Modifier.height(24.dp))
                                    Text("Select N-Up Layout", fontSize = 14.sp, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.height(12.dp))
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        NUpLayout.entries.forEach { layout ->
                                            FilterChip(
                                                selected = selectedLayout == layout,
                                                onClick = { selectedLayout = layout },
                                                label = { Text(layout.label) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = accentColor,
                                                    selectedLabelColor = Color.White
                                                )
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(24.dp))
                                Text("Sheet Orientation", fontSize = 14.sp, fontWeight = FontWeight.Black)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = !isLandscape, onClick = { isLandscape = false })
                                    Text("Portrait", fontSize = 14.sp)
                                    Spacer(Modifier.width(16.dp))
                                    RadioButton(selected = isLandscape, onClick = { isLandscape = true })
                                    Text("Landscape", fontSize = 14.sp)
                                }

                                Spacer(Modifier.height(24.dp))
                                Text("Margin: ${margin.toInt()} pt", fontSize = 14.sp, fontWeight = FontWeight.Black)
                                Slider(value = margin, onValueChange = { margin = it }, valueRange = 0f..100f, colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor))

                                if (mode == ImpositionMode.N_UP) {
                                    Spacer(Modifier.height(16.dp))
                                    Text("Spacing: ${spacing.toInt()} pt", fontSize = 14.sp, fontWeight = FontWeight.Black)
                                    Slider(value = spacing, onValueChange = { spacing = it }, valueRange = 0f..50f, colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor))
                                } else {
                                    Spacer(Modifier.height(16.dp))
                                    Text("Gutter: ${gutter.toInt()} pt", fontSize = 14.sp, fontWeight = FontWeight.Black)
                                    Slider(value = gutter, onValueChange = { gutter = it }, valueRange = 0f..50f, colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor))
                                }

                                Spacer(Modifier.height(16.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = showCropMarks, onCheckedChange = { showCropMarks = it }, colors = CheckboxDefaults.colors(checkedColor = accentColor))
                                    Text("Show Crop Marks", fontSize = 14.sp)
                                }

                                Spacer(Modifier.height(24.dp))
                                Text(
                                    text = "Saving to: Documents/pdfone",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                                Spacer(Modifier.height(8.dp))

                                Button(
                                    onClick = { startProcessing() },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                ) {
                                    Icon(Icons.Default.Done, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("GENERATE IMPOSITION", fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                    ToolState.PROCESSING -> {
                        LoadingStateView(accentColor, true, "Imposing pages...")
                    }
                    ToolState.SUCCESS -> {
                        SuccessView(
                            message = "Imposition Complete",
                            subMessage = "Document saved successfully",
                            processingTime = processingTime,
                            onDone = onBackClick,
                            onProcessMore = {
                                selectedUri = null
                                currentState = ToolState.SELECTING
                            },
                            onPreview = {
                                outputUri?.let { uri -> onResult(uri, "PDF Imposition") }
                            },
                            accentColor = accentColor
                        )
                    }
                    else -> {}
                }
            }

            if (fileToUnlock != null) {
                LockedFilePrompt(
                    fileName = fileToUnlock!!,
                    onDismiss = { fileToUnlock = null; selectedUri = null; currentState = ToolState.SELECTING },
                    onUnlocked = { pass ->
                        isFileLoading = true
                        scope.launch(Dispatchers.IO) {
                            val count = PdfUtils.getPageCount(context, selectedUri!!, pass)
                            withContext(Dispatchers.Main) {
                                if (count > 0) {
                                    unlockPassword = pass
                                    pageCount = count
                                    currentState = ToolState.CONFIGURING
                                    fileToUnlock = null
                                } else {
                                    Toast.makeText(context, "Invalid Password", Toast.LENGTH_SHORT).show()
                                }
                                isFileLoading = false
                            }
                        }
                    },
                    accentColor = accentColor,
                    isLoading = isFileLoading
                )
            }
        }
    }
}

@Composable
fun ImpositionPreview(
    mode: ImpositionMode,
    layout: NUpLayout,
    isLandscape: Boolean,
    margin: Float,
    spacing: Float,
    gutter: Float,
    uri: Uri?,
    password: String?,
    accentColor: Color,
    pageCount: Int
) {
    val context = LocalContext.current
    val thumbnails = remember(uri) { mutableStateMapOf<Int, android.graphics.Bitmap?>() }

    LaunchedEffect(uri) {
        if (uri != null) {
            val count = minOf(16, PdfUtils.getPageCount(context, uri, password))
            for (i in 0 until count) {
                if (!thumbnails.containsKey(i)) {
                    val bitmap = PdfUtils.renderPage(context, uri, i, 0.5f, password)
                    thumbnails[i] = bitmap
                }
            }
        }
    }

    var rows = if (mode == ImpositionMode.N_UP) layout.rows else 1
    var cols = if (mode == ImpositionMode.N_UP) layout.cols else 2
    if (isLandscape && mode == ImpositionMode.N_UP) {
        rows = layout.cols
        cols = layout.rows
    }
    
    // For Booklet/Signature, we usually want landscape sheet if we're putting 2 pages side-by-side
    // But we'll follow the user's landscape selection.

    val scale = 160f / 842f
    val previewMargin = (margin * scale).dp
    val previewSpacing = (if (mode == ImpositionMode.N_UP) spacing else gutter) * scale

    val displayPages = remember(mode, layout, pageCount) {
        when (mode) {
            ImpositionMode.N_UP -> (0 until (rows * cols)).toList()
            ImpositionMode.BOOKLET -> {
                val total = if (pageCount % 4 == 0) pageCount else pageCount + (4 - pageCount % 4)
                listOf(total - 1, 0) // Just showing the first front sheet for preview
            }
            ImpositionMode.SIGNATURE -> listOf(0, 1, 2, 3)
        }
    }

    Surface(
        modifier = Modifier
            .padding(16.dp)
            .height(160.dp)
            .aspectRatio(if (isLandscape) 1.414f else 0.707f),
        shape = RoundedCornerShape(4.dp),
        shadowElevation = 8.dp,
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.LightGray)
    ) {
        Column(
            modifier = Modifier.padding(previewMargin),
            verticalArrangement = Arrangement.spacedBy(previewSpacing.dp)
        ) {
            var pageIdx = 0
            repeat(rows) { rowIndex ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(previewSpacing.dp),
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(cols) { colIndex ->
                        if (pageIdx < displayPages.size) {
                            val sourcePage = displayPages[pageIdx++]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(accentColor.copy(alpha = 0.05f), RoundedCornerShape(1.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val bitmap = thumbnails[sourcePage]
                                if (bitmap != null) {
                                    val rotation = if (mode == ImpositionMode.SIGNATURE && rowIndex == 0) 180f else 0f
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().rotate(rotation),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                Surface(
                                    color = accentColor.copy(alpha = 0.8f),
                                    shape = CircleShape,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).size(10.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("${sourcePage + 1}", color = Color.White, fontSize = 6.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Box(Modifier.weight(1f))
                        }
                        
                        // Vertical Fold Line for Booklet
                        if (colIndex == 0 && cols > 1 && mode == ImpositionMode.BOOKLET) {
                            VerticalDivider(
                                modifier = Modifier.padding(horizontal = (previewSpacing / 2).dp),
                                thickness = 0.5.dp,
                                color = Color.LightGray.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                
                // Fold Line for Signature/Booklet
                if (rowIndex == 0 && rows > 1 && (mode == ImpositionMode.SIGNATURE)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = (previewSpacing / 2).dp),
                        thickness = 0.5.dp,
                        color = Color.LightGray.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

private suspend fun performImposition(
    context: android.content.Context,
    uri: Uri,
    mode: ImpositionMode,
    layout: NUpLayout,
    isLandscape: Boolean,
    margin: Float,
    spacing: Float,
    gutter: Float,
    showMarks: Boolean,
    password: String?
): File? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val sourceDoc = if (password != null) PDDocument.load(inputStream, password) else PDDocument.load(inputStream)
            val targetDoc = PDDocument()
            val layerUtility = LayerUtility(targetDoc)
            
            val outputPageSize = if (isLandscape) PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width) else PDRectangle.A4
            
            val totalSourcePages = sourceDoc.numberOfPages
            
            when (mode) {
                ImpositionMode.N_UP -> {
                    var sourcePageIndex = 0
                    var rows = layout.rows
                    var cols = layout.cols
                    if (isLandscape) {
                        rows = layout.cols
                        cols = layout.rows
                    }
                    
                    while (sourcePageIndex < totalSourcePages) {
                        val newPage = PDPage(outputPageSize)
                        targetDoc.addPage(newPage)
                        PDPageContentStream(targetDoc, newPage).use { contentStream ->
                            val availableWidth = outputPageSize.width - (2 * margin) - ((cols - 1) * spacing)
                            val availableHeight = outputPageSize.height - (2 * margin) - ((rows - 1) * spacing)
                            val cellWidth = availableWidth / cols
                            val cellHeight = availableHeight / rows
                            
                            for (r in 0 until rows) {
                                for (c in 0 until cols) {
                                    if (sourcePageIndex < totalSourcePages) {
                                        drawPageInCell(sourceDoc, layerUtility, contentStream, sourcePageIndex++, 
                                            margin + (c * (cellWidth + spacing)), 
                                            outputPageSize.height - (margin + ((r + 1) * cellHeight) + (r * spacing)),
                                            cellWidth, cellHeight)
                                        
                                        if (showMarks) drawCropMarks(contentStream,
                                            margin + (c * (cellWidth + spacing)), 
                                            outputPageSize.height - (margin + ((r + 1) * cellHeight) + (r * spacing)),
                                            cellWidth, cellHeight)
                                    }
                                }
                            }
                        }
                    }
                }
                ImpositionMode.BOOKLET -> {
                    // Total pages must be multiple of 4 for booklet
                    val bookletPages = if (totalSourcePages % 4 == 0) totalSourcePages else totalSourcePages + (4 - totalSourcePages % 4)
                    val sheets = bookletPages / 4
                    
                    for (i in 0 until sheets) {
                        // Front Sheet
                        val frontPage = PDPage(outputPageSize)
                        targetDoc.addPage(frontPage)
                        PDPageContentStream(targetDoc, frontPage).use { cs ->
                            val leftPage = bookletPages - (2 * i) - 1
                            val rightPage = 2 * i
                            
                            drawBookletHalf(sourceDoc, layerUtility, cs, outputPageSize, leftPage, rightPage, margin, gutter, totalSourcePages, showMarks)
                        }
                        
                        // Back Sheet
                        val backPage = PDPage(outputPageSize)
                        targetDoc.addPage(backPage)
                        PDPageContentStream(targetDoc, backPage).use { cs ->
                            val leftPage = 2 * i + 1
                            val rightPage = bookletPages - (2 * i) - 2
                            
                            drawBookletHalf(sourceDoc, layerUtility, cs, outputPageSize, leftPage, rightPage, margin, gutter, totalSourcePages, showMarks)
                        }
                    }
                }
                ImpositionMode.SIGNATURE -> {
                    // 4-page signature per sheet (Top row rotated 180 for folding)
                    for (i in 0 until totalSourcePages step 4) {
                        val newPage = PDPage(outputPageSize)
                        targetDoc.addPage(newPage)
                        PDPageContentStream(targetDoc, newPage).use { cs ->
                            val cellW = outputPageSize.width / 2 - margin
                            val cellH = outputPageSize.height / 2 - margin
                            
                            // Top Row (Rotated 180)
                            drawPageInCell(sourceDoc, layerUtility, cs, i, margin, outputPageSize.height / 2, cellW, cellH, 180f)
                            if (showMarks) drawCropMarks(cs, margin, outputPageSize.height / 2, cellW, cellH)
                            
                            if (i + 1 < totalSourcePages) {
                                drawPageInCell(sourceDoc, layerUtility, cs, i + 1, outputPageSize.width / 2, outputPageSize.height / 2, cellW, cellH, 180f)
                                if (showMarks) drawCropMarks(cs, outputPageSize.width / 2, outputPageSize.height / 2, cellW, cellH)
                            }
                            
                            // Bottom Row (Normal)
                            if (i + 2 < totalSourcePages) {
                                drawPageInCell(sourceDoc, layerUtility, cs, i + 2, margin, margin, cellW, cellH)
                                if (showMarks) drawCropMarks(cs, margin, margin, cellW, cellH)
                            }
                            if (i + 3 < totalSourcePages) {
                                drawPageInCell(sourceDoc, layerUtility, cs, i + 3, outputPageSize.width / 2, margin, cellW, cellH)
                                if (showMarks) drawCropMarks(cs, outputPageSize.width / 2, margin, cellW, cellH)
                            }
                        }
                    }
                }
            }
            
            val file = PdfUtils.getPublicDocumentsFile("Imposition_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { out ->
                targetDoc.save(out)
            }
            sourceDoc.close()
            targetDoc.close()
            return@withContext file
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}

private fun drawBookletHalf(
    sourceDoc: PDDocument,
    layerUtility: LayerUtility,
    cs: PDPageContentStream,
    pageSize: PDRectangle,
    leftIdx: Int,
    rightIdx: Int,
    margin: Float,
    gutter: Float,
    maxPages: Int,
    showMarks: Boolean
) {
    val cellWidth = (pageSize.width - 2 * margin - gutter) / 2
    val cellHeight = pageSize.height - 2 * margin
    
    if (leftIdx < maxPages) {
        drawPageInCell(sourceDoc, layerUtility, cs, leftIdx, margin, margin, cellWidth, cellHeight)
        if (showMarks) drawCropMarks(cs, margin, margin, cellWidth, cellHeight)
    }
    if (rightIdx < maxPages) {
        drawPageInCell(sourceDoc, layerUtility, cs, rightIdx, margin + cellWidth + gutter, margin, cellWidth, cellHeight)
        if (showMarks) drawCropMarks(cs, margin + cellWidth + gutter, margin, cellWidth, cellHeight)
    }
}

private fun drawCropMarks(cs: PDPageContentStream, x: Float, y: Float, w: Float, h: Float) {
    val len = 10f
    val gap = 5f
    cs.setLineWidth(0.5f)
    cs.setStrokingColor(Color.Black.red, Color.Black.green, Color.Black.blue)
    
    // Top Left
    cs.moveTo(x - gap, y + h)
    cs.lineTo(x - gap - len, y + h)
    cs.moveTo(x, y + h + gap)
    cs.lineTo(x, y + h + gap + len)
    
    // Top Right
    cs.moveTo(x + w + gap, y + h)
    cs.lineTo(x + w + gap + len, y + h)
    cs.moveTo(x + w, y + h + gap)
    cs.lineTo(x + w, y + h + gap + len)
    
    // Bottom Left
    cs.moveTo(x - gap, y)
    cs.lineTo(x - gap - len, y)
    cs.moveTo(x, y - gap)
    cs.lineTo(x, y - gap - len)
    
    // Bottom Right
    cs.moveTo(x + w + gap, y)
    cs.lineTo(x + w + gap + len, y)
    cs.moveTo(x + w, y - gap)
    cs.lineTo(x + w, y - gap - len)
    
    cs.stroke()
}

private fun drawPageInCell(
    sourceDoc: PDDocument,
    layerUtility: LayerUtility,
    cs: PDPageContentStream,
    pageIdx: Int,
    x: Float,
    y: Float,
    cellW: Float,
    cellH: Float,
    rotation: Float = 0f
) {
    val sourcePage = sourceDoc.getPage(pageIdx)
    val sourceForm = layerUtility.importPageAsForm(sourceDoc, sourcePage)
    val sW = sourceForm.bBox.width
    val sH = sourceForm.bBox.height
    val scale = minOf(cellW / sW, cellH / sH)
    
    val xOff = x + (cellW - sW * scale) / 2
    val yOff = y + (cellH - sH * scale) / 2
    
    cs.saveGraphicsState()
    val matrix = Matrix()
    
    if (rotation != 0f) {
        // Rotate around cell center
        val centerX = x + cellW / 2
        val centerY = y + cellH / 2
        matrix.translate(centerX, centerY)
        matrix.rotate(Math.toRadians(rotation.toDouble()))
        matrix.translate(-sW * scale / 2, -sH * scale / 2)
    } else {
        matrix.translate(xOff, yOff)
    }

    matrix.scale(scale, scale)
    cs.transform(matrix)
    cs.drawForm(sourceForm)
    cs.restoreGraphicsState()
}
