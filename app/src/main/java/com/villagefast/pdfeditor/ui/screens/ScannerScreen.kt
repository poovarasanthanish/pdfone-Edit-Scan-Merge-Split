package com.villagefast.pdfeditor.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.villagefast.pdfeditor.ads.AdManager
import com.villagefast.pdfeditor.ui.components.GlassCard
import com.villagefast.pdfeditor.ui.components.GlassTopAppBar
import com.villagefast.pdfeditor.ui.components.LiquidScaffold
import com.villagefast.pdfeditor.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    showAds: Boolean = true,
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var recognizedText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(value = false) }
    
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfPageCount by remember { mutableIntStateOf(0) }
    val selectedPagesForOcr = remember { mutableStateListOf<Int>() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedPdfUri = it
            scope.launch {
                pdfPageCount = PdfUtils.getPageCount(context, it)
                selectedPagesForOcr.clear()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LiquidScaffold(
        showBanner = showAds,
        topBar = {
            GlassTopAppBar(
                title = { Text(if (selectedPdfUri != null && recognizedText.isEmpty()) "Select Pages to Extract" else "Scanner & OCR") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedPdfUri != null && recognizedText.isEmpty()) {
                            selectedPdfUri = null
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedPdfUri == null && recognizedText.isEmpty()) {
                        IconButton(onClick = { pdfLauncher.launch(arrayOf("application/pdf")) }) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Select PDF")
                        }
                    } else if (selectedPdfUri != null && recognizedText.isEmpty() && selectedPagesForOcr.isNotEmpty()) {
                        TextButton(onClick = {
                            isProcessing = true
                            scope.launch {
                                recognizedText = extractSpecificPagesText(context, selectedPdfUri!!, selectedPagesForOcr.toList())
                                isProcessing = false
                            }
                        }) {
                            Text("Extract", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (selectedPdfUri != null && recognizedText.isEmpty() && !isProcessing) {
                // Visual Page Selector for PDF
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                selectedPagesForOcr.clear()
                                for (i in 0 until pdfPageCount) selectedPagesForOcr.add(i)
                            },
                            label = { Text("All") }
                        )
                        AssistChip(
                            onClick = { selectedPagesForOcr.clear() },
                            label = { Text("Clear") }
                        )
                    }
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(pdfPageCount) { index ->
                            ScannerPageItem(
                                uri = selectedPdfUri!!,
                                pageIndex = index,
                                isSelected = selectedPagesForOcr.contains(index),
                                onClick = {
                                    if (selectedPagesForOcr.contains(index)) {
                                        selectedPagesForOcr.remove(index)
                                    } else {
                                        selectedPagesForOcr.add(index)
                                    }
                                }
                            )
                        }
                    }
                }
            } else if (capturedBitmap == null && recognizedText.isEmpty() && !isProcessing) {
                if (hasCameraPermission) {
                    CameraPreview(
                        onImageCaptured = { bitmap ->
                            capturedBitmap = bitmap
                            isProcessing = true
                            scope.launch {
                                recognizeText(bitmap) { text ->
                                    recognizedText = text
                                    isProcessing = false
                                }
                            }
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Camera permission required")
                    }
                }
            } else {
                // Show captured image (if any) and recognized text
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (capturedBitmap != null) {
                        GlassCard {
                            Image(
                                bitmap = capturedBitmap!!.asImageBitmap(),
                                contentDescription = "Captured Image",
                                modifier = Modifier
                                    .height(200.dp)
                                    .fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    if (isProcessing) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        GlassCard(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = recognizedText,
                                onValueChange = { recognizedText = it },
                                modifier = Modifier.fillMaxSize(),
                                label = { Text("Extracted Text") },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val clipboardManager = LocalClipboardManager.current
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    capturedBitmap = null
                                    recognizedText = ""
                                    selectedPdfUri = null
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clear")
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        val file = saveTextAsPdf(context, recognizedText)
                                        if (file != null) {
                                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                            if (showAds && activity != null) {
                                                AdManager.showInterstitialWithInterval(activity) {
                                                    onResult(uri, "OCR Scanner")
                                                }
                                            } else {
                                                onResult(uri, "OCR Scanner")
                                            }
                                        }
                                    }
                                },
                                enabled = recognizedText.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save PDF")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(recognizedText))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                enabled = recognizedText.isNotEmpty()
                            ) {
                                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Copy")
                            }
                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, recognizedText)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Text"))
                                },
                                modifier = Modifier.weight(1f),
                                enabled = recognizedText.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Share")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(onImageCaptured: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("ScannerScreen", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        
        IconButton(
            onClick = {
                imageCapture.takePicture(
                    cameraExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = image.toBitmap()
                            onImageCaptured(bitmap)
                            image.close()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e("ScannerScreen", "Capture failed", exception)
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(72.dp)
        ) {
            Icon(
                Icons.Default.Camera,
                contentDescription = "Capture",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ScannerPageItem(
    uri: Uri,
    pageIndex: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri, pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val renderer = PdfRenderer(pfd)
                    if (pageIndex < renderer.pageCount) {
                        val page = renderer.openPage(pageIndex)
                        val b = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
                        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap = b
                        page.close()
                    }
                    renderer.close()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            )
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )
        }

        Text(
            text = (pageIndex + 1).toString(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun recognizeText(bitmap: Bitmap, onResult: (String) -> Unit) {
    val image = InputImage.fromBitmap(bitmap, 0)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer.process(image)
        .addOnSuccessListener { visionText ->
            onResult(visionText.text)
        }
        .addOnFailureListener { e ->
            Log.e("ScannerScreen", "Text recognition failed", e)
            onResult("")
        }
}

private suspend fun recognizeTextSuspend(bitmap: Bitmap): String = suspendCoroutine { continuation ->
    val image = InputImage.fromBitmap(bitmap, 0)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer.process(image)
        .addOnSuccessListener { visionText ->
            continuation.resume(visionText.text)
        }
        .addOnFailureListener { e ->
            Log.e("ScannerScreen", "Text recognition failed", e)
            continuation.resume("")
        }
}

private suspend fun extractSpecificPagesText(context: Context, uri: Uri, pageIndices: List<Int>): String = withContext(Dispatchers.IO) {
    val textBuilder = StringBuilder()
    try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val renderer = PdfRenderer(pfd)
            
            pageIndices.sorted().forEach { index ->
                if (index < renderer.pageCount) {
                    val page = renderer.openPage(index)
                    // Render page at a high enough resolution for OCR
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    val pageText = recognizeTextSuspend(bitmap)
                    textBuilder.append(pageText)
                    
                    if (pageIndices.size > 1) {
                        textBuilder.append("\n--- Page ${index + 1} ---\n")
                    }
                    
                    page.close()
                }
            }
            renderer.close()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext textBuilder.toString()
}

private suspend fun saveTextAsPdf(context: Context, text: String): File? = withContext(Dispatchers.IO) {
    val document = PDDocument()
    val page = PDPage()
    document.addPage(page)
    
    try {
        val contentStream = PDPageContentStream(document, page)
        contentStream.beginText()
        contentStream.setFont(PDType1Font.HELVETICA, 12f)
        contentStream.newLineAtOffset(50f, 750f)
        
        val lines = text.split("\n")
        lines.forEach { line ->
            val safeLine = line.filter { it.code in 32..126 }
            if (safeLine.isNotEmpty()) {
                contentStream.showText(safeLine)
            }
            contentStream.newLineAtOffset(0f, -15f)
        }
        
        contentStream.endText()
        contentStream.close()
        
        val file = PdfUtils.getPublicDocumentsFile("Scanned_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { outputStream ->
            document.save(outputStream)
        }
        return@withContext file
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    } finally {
        document.close()
    }
}
