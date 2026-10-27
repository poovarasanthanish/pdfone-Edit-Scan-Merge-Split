package com.villagefast.pdfeditor.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.villagefast.pdfeditor.ui.components.GlassTopAppBar
import com.villagefast.pdfeditor.ui.components.LiquidScaffold
import com.villagefast.pdfeditor.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

// Constants to ensure consistency between UI and PDF
private const val SIGN_BOX_ASPECT_RATIO = 2.0f
private const val PREVIEW_SIGN_WIDTH_DP = 120f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillSignScreen(
    initialUri: Uri? = null, 
    showAds: Boolean = true, 
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    
    var selectedPdf by remember { mutableStateOf(initialUri) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(0) }
    
    // Page metadata for coordinate mapping
    var pageAspectRatio by remember { mutableFloatStateOf(0.707f) }

    // Signature Drawing State
    val paths = remember { mutableStateListOf<Path>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var drawBoxSize by remember { mutableStateOf(IntSize.Zero) }

    // Info State
    var fullName by remember { mutableStateOf("") }
    var signDate by remember { mutableStateOf("") }
    var signPlace by remember { mutableStateOf("") }
    var customText by remember { mutableStateOf("") }
    
    // Placement as percentage (0.0 to 1.0) - Center of the signature box
    var posX by remember { mutableFloatStateOf(0.5f) }
    var posY by remember { mutableFloatStateOf(0.5f) }
    var signatureScale by remember { mutableFloatStateOf(1.0f) }

    var isProcessing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> 
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            selectedPdf = uri
            currentPageIndex = 0
        }
    }

    LaunchedEffect(selectedPdf) {
        selectedPdf?.let { uri ->
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val doc = PDDocument.load(inputStream)
                        totalPages = doc.numberOfPages
                        if (totalPages > 0) {
                            val page = doc.getPage(0)
                            val rot = page.rotation
                            val box = page.mediaBox
                            val vw = if (rot == 90 || rot == 270) box.height else box.width
                            val vh = if (rot == 90 || rot == 270) box.width else box.height
                            pageAspectRatio = if (vh > 0) vw / vh else 0.707f
                        }
                        doc.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    LiquidScaffold(
        showBanner = showAds,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassTopAppBar(
                title = { Text("Fill & Sign") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (selectedPdf == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { pdfLauncher.launch(arrayOf("application/pdf")) }) {
                    Text("Select PDF File")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Sign Drawing Area
                Text("1. Create your Signature", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(SIGN_BOX_ASPECT_RATIO)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .onGloballyPositioned { drawBoxSize = it.size }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> currentPath = Path().apply { moveTo(offset.x, offset.y) } },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentPath?.lineTo(change.position.x, change.position.y)
                                    val temp = currentPath
                                    currentPath = null
                                    currentPath = temp
                                },
                                onDragEnd = { currentPath?.let { paths.add(it) }; currentPath = null }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        paths.forEach { drawPath(it, Color.Black, style = Stroke(width = 5f)) }
                        currentPath?.let { drawPath(it, Color.Black, style = Stroke(width = 5f)) }
                    }
                    if (paths.isEmpty() && currentPath == null) {
                        Text("Draw here", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                    } else {
                        IconButton(onClick = { paths.clear() }, modifier = Modifier.align(Alignment.TopEnd)) {
                            Icon(Icons.Default.Refresh, null)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. Details
                Text("2. Fill Details (Optional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = customText, onValueChange = { customText = it }, label = { Text("Custom Text") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Name") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = signDate, onValueChange = { signDate = it }, label = { Text("Date") }, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. Placement
                Text("3. Position on Page", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                
                SignPlacementPreview(
                    selectedUri = selectedPdf,
                    pageIndex = currentPageIndex,
                    aspectRatio = pageAspectRatio,
                    paths = paths,
                    drawBoxSize = drawBoxSize,
                    text = customText,
                    name = fullName,
                    date = signDate,
                    scale = signatureScale,
                    posX = posX,
                    posY = posY,
                    onPositionChange = { x, y -> posX = x; posY = y }
                )

                if (totalPages > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        IconButton(onClick = { if (currentPageIndex > 0) currentPageIndex-- }, enabled = currentPageIndex > 0) {
                            Icon(Icons.Default.ChevronLeft, null)
                        }
                        Text("Page ${currentPageIndex + 1} of $totalPages", style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { if (currentPageIndex < totalPages - 1) currentPageIndex++ }, enabled = currentPageIndex < totalPages - 1) {
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Slider(value = signatureScale, onValueChange = { signatureScale = it }, valueRange = 0.5f..2.5f)
                Text("Sign Size: ${String.format(Locale.US, "%.1f", signatureScale)}x", style = MaterialTheme.typography.labelMedium)

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        isProcessing = true
                        scope.launch {
                            val file = saveSignedPdf(
                                context, selectedPdf!!, paths, drawBoxSize,
                                fullName, signDate, signPlace, customText, 
                                currentPageIndex, signatureScale, posX, posY
                            )
                            isProcessing = false
                            if (file != null) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                if (showAds && activity != null) {
                                    com.villagefast.pdfeditor.ads.AdManager.showInterstitial(activity) {
                                        onResult(uri, "Fill & Sign")
                                    }
                                } else {
                                    onResult(uri, "Fill & Sign")
                                }
                            } else {
                                snackbarHostState.showSnackbar("Failed to save PDF")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !isProcessing && (paths.isNotEmpty() || customText.isNotEmpty() || fullName.isNotEmpty()),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("Apply to This Page")
                    }
                }
                
                TextButton(onClick = { selectedPdf = null }) {
                    Text("Change PDF File")
                }
            }
        }
    }
}

@Composable
fun SignPlacementPreview(
    selectedUri: Uri?,
    pageIndex: Int,
    aspectRatio: Float,
    paths: List<Path>,
    drawBoxSize: IntSize,
    text: String,
    name: String,
    date: String,
    scale: Float,
    posX: Float,
    posY: Float,
    onPositionChange: (Float, Float) -> Unit
) {
    val context = LocalContext.current
    
    val currentPosX by rememberUpdatedState(posX)
    val currentPosY by rememberUpdatedState(posY)
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)

    val pdfBitmap by produceState<Bitmap?>(initialValue = null, selectedUri, pageIndex) {
        if (selectedUri != null) {
            value = withContext(Dispatchers.IO) {
                try {
                    var rot = 0
                    context.contentResolver.openInputStream(selectedUri)?.use { inputStream ->
                        val doc = PDDocument.load(inputStream)
                        if (doc.numberOfPages > pageIndex) {
                            rot = doc.getPage(pageIndex).rotation
                        }
                        doc.close()
                    }

                    context.contentResolver.openFileDescriptor(selectedUri, "r")?.use { pfd ->
                        val renderer = PdfRenderer(pfd)
                        if (renderer.pageCount > pageIndex) {
                            renderer.openPage(pageIndex).use { page ->
                                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bitmap)
                                canvas.drawColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                
                                if (rot != 0) {
                                    val matrix = android.graphics.Matrix()
                                    matrix.postRotate(rot.toFloat())
                                    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                    if (rotated != bitmap) bitmap.recycle()
                                    rotated
                                } else bitmap
                            }
                        } else null
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .aspectRatio(aspectRatio)
            .fillMaxWidth(0.9f)
            .background(Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = if (aspectRatio > 0) width / aspectRatio else width / 0.707f

        if (pdfBitmap != null) {
            Image(bitmap = pdfBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(width, height) {
                    detectDragGestures { change, dragAmount ->
                        if (width > 0 && height > 0) {
                            change.consume()
                            val newX = (currentPosX + dragAmount.x / width).coerceIn(0f, 1f)
                            val newY = (currentPosY + dragAmount.y / height).coerceIn(0f, 1f)
                            currentOnPositionChange(newX, newY)
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = posX * width - size.width / 2
                        translationY = posY * height - size.height / 2
                        scaleX = scale
                        scaleY = scale
                    }
                    .wrapContentSize()
                    .background(Color.White.copy(alpha = 0.5f))
                    .border(0.5.dp, Color.Blue.copy(alpha = 0.3f))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (paths.isNotEmpty() && drawBoxSize.width > 0) {
                        Canvas(modifier = Modifier.size(PREVIEW_SIGN_WIDTH_DP.dp, (PREVIEW_SIGN_WIDTH_DP / SIGN_BOX_ASPECT_RATIO).dp)) {
                            // Scale drawing to fit the preview box
                            val s = PREVIEW_SIGN_WIDTH_DP.dp.toPx() / drawBoxSize.width
                            scale(s, pivot = Offset(0f, 0f)) {
                                paths.forEach { drawPath(it, Color.Black, style = Stroke(width = 5f / s)) }
                            }
                        }
                    }
                    if (text.isNotEmpty()) Text(text, fontSize = 12.sp, color = Color.Black)
                    if (name.isNotEmpty()) Text(name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    if (date.isNotEmpty()) Text(date, fontSize = 8.sp, color = Color.Gray)
                }
            }
        }
    }
}

private suspend fun saveSignedPdf(
    context: android.content.Context,
    uri: Uri,
    paths: List<Path>,
    drawBoxSize: IntSize,
    name: String,
    date: String,
    place: String,
    customText: String,
    pageIndex: Int,
    scale: Float,
    posX: Float,
    posY: Float
): File? = withContext(Dispatchers.IO) {
    try {
        val originalName = PdfUtils.getFileName(context, uri)?.removeSuffix(".pdf") ?: "Document"
        
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val document = PDDocument.load(inputStream)
            val page = document.getPage(pageIndex)
            val mediaBox = page.mediaBox
            val pageRotation = page.rotation
            
            val contentStream = PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)
            
            val vw = if (pageRotation == 90 || pageRotation == 270) mediaBox.height else mediaBox.width
            val vh = if (pageRotation == 90 || pageRotation == 270) mediaBox.width else mediaBox.height

            contentStream.saveGraphicsState()
            
            // Coordinate transformation to match visual orientation
            contentStream.transform(com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(mediaBox.lowerLeftX, mediaBox.lowerLeftY))
            when (pageRotation) {
                90 -> contentStream.transform(com.tom_roush.pdfbox.util.Matrix(0f, 1f, -1f, 0f, mediaBox.width, 0f))
                180 -> contentStream.transform(com.tom_roush.pdfbox.util.Matrix(-1f, 0f, 0f, -1f, mediaBox.width, mediaBox.height))
                270 -> contentStream.transform(com.tom_roush.pdfbox.util.Matrix(0f, -1f, 1f, 0f, 0f, mediaBox.height))
            }
            
            val tx = vw * posX
            val ty = vh * (1f - posY)

            // 1. Draw Signature
            if (paths.isNotEmpty() && drawBoxSize.width > 0) {
                val signatureBitmap = createSignatureBitmap(paths, drawBoxSize)
                if (signatureBitmap != null) {
                    val pdImage = LosslessFactory.createFromImage(document, signatureBitmap)
                    val sigW = 120f * scale
                    val sigH = (120f / SIGN_BOX_ASPECT_RATIO) * scale
                    
                    val matrix = com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(tx, ty)
                    contentStream.transform(matrix)
                    contentStream.drawImage(pdImage, -sigW/2, -sigH/2, sigW, sigH)
                    // Reset matrix for text
                    contentStream.transform(com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(-tx, -ty))
                }
            }
            
            // 2. Draw Text
            var currentTy = ty - (35f * scale)
            if (paths.isEmpty()) currentTy = ty

            if (customText.isNotEmpty()) {
                drawCenteredText(contentStream, customText, PDType1Font.HELVETICA, 12f * scale, tx, currentTy + 15f*scale)
            }
            if (name.isNotEmpty()) {
                drawCenteredText(contentStream, name, PDType1Font.HELVETICA_BOLD, 10f * scale, tx, currentTy)
                currentTy -= 12f * scale
            }
            if (date.isNotEmpty()) {
                drawCenteredText(contentStream, date, PDType1Font.HELVETICA, 8f * scale, tx, currentTy)
            }
            
            contentStream.restoreGraphicsState()
            contentStream.close()
            
            val fileName = "Signed_${originalName}_${System.currentTimeMillis()}.pdf"
            val outputFile = File(context.cacheDir, fileName)
            document.save(outputFile)
            document.close()

            // Try to move to public documents if possible
            try {
                val publicFile = PdfUtils.getPublicDocumentsFile(fileName)
                val parent = publicFile.parentFile
                if (parent != null && (parent.exists() || parent.mkdirs())) {
                    outputFile.copyTo(publicFile, overwrite = true)
                    return@withContext publicFile
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return@withContext outputFile
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun drawCenteredText(stream: PDPageContentStream, text: String, font: PDType1Font, size: Float, x: Float, y: Float) {
    try {
        val width = font.getStringWidth(text) / 1000f * size
        stream.beginText()
        stream.setFont(font, size)
        stream.newLineAtOffset(x - width/2, y)
        stream.showText(text)
        stream.endText()
    } catch (e: Exception) {}
}

private fun createSignatureBitmap(paths: List<Path>, size: IntSize): Bitmap? {
    if (paths.isEmpty() || size.width <= 0 || size.height <= 0) return null
    
    // We create a bitmap for the entire drawing area to preserve relative positions
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 10f
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    
    paths.forEach { canvas.drawPath(it.asAndroidPath(), paint) }
    
    // Now crop the bitmap to remove excessive empty space, but keep it centered or just return as is?
    // Actually, to guarantee match with preview, let's keep the full aspect ratio box.
    // If the user draws a small signature, the bitmap will have transparent edges, 
    // which is fine because we draw it into a matched box in the PDF.

    return bitmap
}
