package com.villagefast.pdfeditor.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.villagefast.pdfeditor.ui.components.GlassTopAppBar
import com.villagefast.pdfeditor.ui.components.LiquidScaffold
import com.villagefast.pdfeditor.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

enum class WatermarkType {
    TEXT, IMAGE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkScreen(
    showAds: Boolean = false, 
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var watermarkType by remember { mutableStateOf(WatermarkType.TEXT) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var watermarkImageUri by remember { mutableStateOf<Uri?>(null) }
    
    var opacity by remember { mutableFloatStateOf(0.5f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    
    // Position as percentage (0.0 to 1.0)
    var posX by remember { mutableFloatStateOf(0.5f) }
    var posY by remember { mutableFloatStateOf(0.5f) }
    
    var isProcessing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
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
            selectedUri = it
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri -> watermarkImageUri = uri }

    LiquidScaffold(
        showBanner = showAds,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassTopAppBar(
                title = { Text("Watermark Editor", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (selectedUri == null) {
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
                Text("Drag to position, pinch to rotate/scale", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                // Live Preview
                WatermarkPreview(
                    selectedUri = selectedUri,
                    type = watermarkType,
                    text = watermarkText,
                    imageUri = watermarkImageUri,
                    opacity = opacity,
                    rotation = rotation,
                    scale = scale,
                    posX = posX,
                    posY = posY,
                    onPositionChange = { x, y ->
                        posX = x
                        posY = y
                    },
                    onRotationChange = { rotation = it },
                    onScaleChange = { scale = it }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        TabRow(selectedTabIndex = watermarkType.ordinal, containerColor = Color.Transparent) {
                            Tab(
                                selected = watermarkType == WatermarkType.TEXT,
                                onClick = { watermarkType = WatermarkType.TEXT },
                                icon = { Icon(Icons.Default.TextFields, null) },
                                text = { Text("Text") }
                            )
                            Tab(
                                selected = watermarkType == WatermarkType.IMAGE,
                                onClick = { watermarkType = WatermarkType.IMAGE },
                                icon = { Icon(Icons.Default.Image, null) },
                                text = { Text("Image") }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (watermarkType == WatermarkType.TEXT) {
                            OutlinedTextField(
                                value = watermarkText,
                                onValueChange = { watermarkText = it },
                                label = { Text("Watermark Text") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = { imageLauncher.launch("image/*") }) {
                                    Text("Choose Image")
                                }
                                if (watermarkImageUri != null) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    AsyncImage(
                                        model = watermarkImageUri,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Opacity: ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                        Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.1f..1.0f)

                        Text("Rotation: ${rotation.toInt()}°", style = MaterialTheme.typography.labelLarge)
                        Slider(value = rotation, onValueChange = { rotation = it }, valueRange = 0f..360f)

                        Text("Size Scale: ${String.format(Locale.US, "%.1f", scale)}x", style = MaterialTheme.typography.labelLarge)
                        Slider(value = scale, onValueChange = { scale = it }, valueRange = 0.5f..3.0f)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        isProcessing = true
                        scope.launch {
                            val file = addWatermark(
                                context, selectedUri!!, watermarkType,
                                watermarkText, watermarkImageUri,
                                opacity, rotation, scale, posX, posY
                            )
                            isProcessing = false
                            if (file != null) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                if (showAds && activity != null) {
                                    com.villagefast.pdfeditor.ads.AdManager.showInterstitial(activity) {
                                        onResult(uri, "Watermark")
                                    }
                                } else {
                                    onResult(uri, "Watermark")
                                }
                            } else {
                                snackbarHostState.showSnackbar("Failed to add watermark")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !isProcessing && (watermarkType == WatermarkType.TEXT || watermarkImageUri != null),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("Apply to All Pages")
                    }
                }
                
                TextButton(onClick = { selectedUri = null }) {
                    Text("Change PDF File")
                }
            }
        }
    }
}

@Composable
fun WatermarkPreview(
    selectedUri: Uri?,
    type: WatermarkType,
    text: String,
    imageUri: Uri?,
    opacity: Float,
    rotation: Float,
    scale: Float,
    posX: Float,
    posY: Float,
    onPositionChange: (Float, Float) -> Unit,
    onRotationChange: (Float) -> Unit,
    onScaleChange: (Float) -> Unit
) {
    val context = LocalContext.current
    
    // Track page info to handle rotation and aspect ratio correctly
    var pageAspectRatio by remember { mutableFloatStateOf(0.707f) }

    val pdfBitmap by produceState<Bitmap?>(initialValue = null, selectedUri) {
        if (selectedUri != null) {
            value = withContext(Dispatchers.IO) {
                try {
                    var rot = 0
                    var ratio = 0.707f
                    
                    // 1. Get rotation and dimensions using PDFBox
                    context.contentResolver.openInputStream(selectedUri)?.use { inputStream ->
                        val doc = PDDocument.load(inputStream)
                        if (doc.numberOfPages > 0) {
                            val page = doc.getPage(0)
                            rot = page.rotation
                            val box = page.mediaBox
                            val vw = if (rot == 90 || rot == 270) box.height else box.width
                            val vh = if (rot == 90 || rot == 270) box.width else box.height
                            ratio = if (vh > 0) vw / vh else 0.707f
                        }
                        doc.close()
                    }
                    
                    pageAspectRatio = ratio

                    // 2. Render bitmap using PdfRenderer
                    context.contentResolver.openFileDescriptor(selectedUri, "r")?.use { pfd ->
                        val renderer = PdfRenderer(pfd)
                        if (renderer.pageCount > 0) {
                            renderer.openPage(0).use { page ->
                                val bitmap = Bitmap.createBitmap(
                                    page.width, page.height, Bitmap.Config.ARGB_8888
                                )
                                val canvas = android.graphics.Canvas(bitmap)
                                canvas.drawColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                
                                // Rotate bitmap to match visual orientation if needed
                                if (rot != 0) {
                                    val matrix = android.graphics.Matrix()
                                    matrix.postRotate(rot.toFloat())
                                    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                    if (rotated != bitmap) bitmap.recycle()
                                    rotated
                                } else {
                                    bitmap
                                }
                            }
                        } else null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .aspectRatio(pageAspectRatio)
            .fillMaxWidth(0.85f)
            .background(Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = if (pageAspectRatio > 0) width / pageAspectRatio else width / 0.707f

        if (pdfBitmap != null) {
            Image(
                bitmap = pdfBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(modifier = Modifier.padding(20.dp).alpha(0.05f)) {
                repeat(15) {
                    Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(Color.Gray))
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        val currentPosX by rememberUpdatedState(posX)
        val currentPosY by rememberUpdatedState(posY)
        val currentRotation by rememberUpdatedState(rotation)
        val currentScale by rememberUpdatedState(scale)
        val currentOnPositionChange by rememberUpdatedState(onPositionChange)
        val currentOnRotationChange by rememberUpdatedState(onRotationChange)
        val currentOnScaleChange by rememberUpdatedState(onScaleChange)

        // Transparent overlay to capture gestures across the entire preview area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(width, height) {
                    detectTransformGestures { _, pan, zoom, rotationChange ->
                        if (width > 0 && height > 0) {
                            val newX = (currentPosX + pan.x / width).coerceIn(0f, 1f)
                            val newY = (currentPosY + pan.y / height).coerceIn(0f, 1f)
                            currentOnPositionChange(newX, newY)

                            val newRotation = (currentRotation + rotationChange) % 360f
                            currentOnRotationChange(if (newRotation < 0) newRotation + 360f else newRotation)

                            val newScale = (currentScale * zoom).coerceIn(0.5f, 3.0f)
                            currentOnScaleChange(newScale)
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = posX * width - size.width / 2
                        translationY = posY * height - size.height / 2
                        rotationZ = rotation
                        alpha = opacity
                        scaleX = scale
                        scaleY = scale
                    }
                    .wrapContentSize(),
                contentAlignment = Alignment.Center
            ) {
                if (type == WatermarkType.TEXT) {
                    Text(
                        text = if (text.isEmpty()) "WATERMARK" else text,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(12.dp)
                    )
                } else {
                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = null,
                            modifier = Modifier.size(120.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .border(2.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Image, null, tint = Color.LightGray)
                        }
                    }
                }
            }
        }
    }
}

private suspend fun addWatermark(
    context: android.content.Context,
    uri: Uri,
    type: WatermarkType,
    text: String,
    imageUri: Uri?,
    opacity: Float,
    watermarkRotation: Float,
    scale: Float,
    posX: Float,
    posY: Float
): File? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val document = PDDocument.load(inputStream)
            
            val gs = PDExtendedGraphicsState()
            gs.nonStrokingAlphaConstant = opacity
            
            var watermarkImage: com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject? = null
            if (type == WatermarkType.IMAGE && imageUri != null) {
                context.contentResolver.openInputStream(imageUri).use { imgIn ->
                    val bitmap = BitmapFactory.decodeStream(imgIn)
                    if (bitmap != null) {
                        watermarkImage = LosslessFactory.createFromImage(document, bitmap)
                    }
                }
            }

            val font = PDType1Font.HELVETICA_BOLD
            val fontSize = 60f * scale

            for (i in 0 until document.numberOfPages) {
                val page = document.getPage(i)
                val mediaBox = page.mediaBox
                val pageRotation = page.rotation
                
                val contentStream = PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)
                contentStream.setGraphicsStateParameters(gs)
                
                // Calculate visual dimensions
                val visualWidth = if (pageRotation == 90 || pageRotation == 270) mediaBox.height else mediaBox.width
                val visualHeight = if (pageRotation == 90 || pageRotation == 270) mediaBox.width else mediaBox.height

                contentStream.saveGraphicsState()
                
                // 1. Move to mediaBox origin
                contentStream.transform(com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(mediaBox.lowerLeftX, mediaBox.lowerLeftY))
                
                // 2. Transform coordinate system to match visual orientation (visual bottom-left origin)
                when (pageRotation) {
                    90 -> contentStream.transform(com.tom_roush.pdfbox.util.Matrix(0f, 1f, -1f, 0f, mediaBox.width, 0f))
                    180 -> contentStream.transform(com.tom_roush.pdfbox.util.Matrix(-1f, 0f, 0f, -1f, mediaBox.width, mediaBox.height))
                    270 -> contentStream.transform(com.tom_roush.pdfbox.util.Matrix(0f, -1f, 1f, 0f, 0f, mediaBox.height))
                }
                
                // Calculate target position in visual space
                val tx = visualWidth * posX
                val ty = visualHeight * (1f - posY)

                if (type == WatermarkType.TEXT) {
                    val textToDraw = if (text.isEmpty()) "WATERMARK" else text
                    val textWidth = font.getStringWidth(textToDraw) / 1000f * fontSize
                    val textHeight = font.fontDescriptor.capHeight / 1000f * fontSize
                    
                    contentStream.beginText()
                    contentStream.setFont(font, fontSize)
                    contentStream.setNonStrokingColor(200, 200, 200)
                    
                    // Rotate and center text
                    val matrix = com.tom_roush.pdfbox.util.Matrix.getRotateInstance(Math.toRadians(watermarkRotation.toDouble()), tx, ty)
                    matrix.translate(-textWidth / 2f, -textHeight / 2f)
                    contentStream.setTextMatrix(matrix)
                    contentStream.showText(textToDraw)
                    contentStream.endText()
                } else {
                    val currentImg = watermarkImage
                    if (currentImg != null) {
                        val imgWidth = 200f * scale
                        val imgHeight = (imgWidth * currentImg.height) / currentImg.width
                        
                        // Rotate and center image
                        val matrix = com.tom_roush.pdfbox.util.Matrix.getRotateInstance(Math.toRadians(watermarkRotation.toDouble()), tx, ty)
                        contentStream.transform(matrix)
                        contentStream.drawImage(currentImg, -imgWidth / 2, -imgHeight / 2, imgWidth, imgHeight)
                    }
                }
                
                contentStream.restoreGraphicsState()
                contentStream.close()
            }
            
            val originalName = PdfUtils.getFileName(context, uri)?.removeSuffix(".pdf") ?: "Document"
            val fileName = "Watermarked_${originalName}_${System.currentTimeMillis()}.pdf"
            val outputFile = File(context.cacheDir, fileName)
            FileOutputStream(outputFile).use { out -> document.save(out) }
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
        return@withContext null
    }
}
