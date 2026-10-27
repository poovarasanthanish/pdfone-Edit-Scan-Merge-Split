package com.villagefast.pdfeditor.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.villagefast.pdfeditor.ui.components.GlassCard
import com.villagefast.pdfeditor.ui.components.GlassTopAppBar
import com.villagefast.pdfeditor.ui.components.LiquidScaffold
import com.villagefast.pdfeditor.ui.components.PdfThumbnail
import com.villagefast.pdfeditor.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressPdfScreen(
    showAds: Boolean = false,
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    var selectedPdf by remember { mutableStateOf<Uri?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var originalSize by remember { mutableLongStateOf(0L) }
    var compressedSize by remember { mutableLongStateOf(0L) }
    var isProcessing by remember { mutableStateOf(false) }
    var compressionQuality by remember { mutableFloatStateOf(0.5f) }
    val snackbarHostState = remember { SnackbarHostState() }

    var useCustomLocation by remember { mutableStateOf(false) }
    var customSaveUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
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
            originalSize = PdfUtils.getFileSize(context, uri)
            compressedSize = 0L
            scope.launch {
                pageCount = PdfUtils.getPageCount(context, uri)
            }
        } else {
            pageCount = 0
        }
    }

    val directoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            customSaveUri = uri
        }
    }

    LiquidScaffold(
        showBanner = showAds,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassTopAppBar(
                title = { Text("Compress PDF") },
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select a PDF to reduce its size", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { launcher.launch(arrayOf("application/pdf")) }) {
                        Text("Select PDF File")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // File Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PdfThumbnail(
                                uri = selectedPdf!!,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = PdfUtils.getFileName(context, selectedPdf!!) ?: "Selected PDF",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Original Size: ${PdfUtils.formatFileSize(originalSize)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (compressedSize > 0) {
                                    Text(
                                        text = "Compressed Size: ${PdfUtils.formatFileSize(compressedSize)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    val reduction = ((originalSize - compressedSize).toDouble() / originalSize * 100).toInt()
                                    Text(
                                        text = "Reduction: $reduction%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            TextButton(onClick = { 
                                selectedPdf = null
                                pageCount = 0
                            }) {
                                Text("Change")
                            }
                        }
                    }

                    if (pageCount > 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Page Previews",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(pageCount) { index ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    PdfThumbnail(
                                        uri = selectedPdf!!,
                                        pageIndex = index,
                                        modifier = Modifier
                                            .size(80.dp, 110.dp)
                                            .clip(MaterialTheme.shapes.small)
                                    )
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Compression Controls
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("Compression Level", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("High Quality", style = MaterialTheme.typography.labelSmall)
                                Text("Small File", style = MaterialTheme.typography.labelSmall)
                            }
                            
                            Slider(
                                value = compressionQuality,
                                onValueChange = { compressionQuality = it },
                                valueRange = 0.1f..0.9f,
                                steps = 8
                            )
                            
                            val qualityLabel = when {
                                compressionQuality > 0.7f -> "Low Compression (Best Quality)"
                                compressionQuality > 0.4f -> "Recommended Compression"
                                else -> "High Compression (Smallest Size)"
                            }
                            
                            Text(
                                text = qualityLabel,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Quality Estimation Info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val minReductionPercent = (1 - compressionQuality) * 0.6f
                        val maxReductionPercent = (1 - compressionQuality) * 0.9f
                        val estMinSize = (originalSize * (1 - maxReductionPercent)).toLong()
                        val estMaxSize = (originalSize * (1 - minReductionPercent)).toLong()

                        Text(
                            text = "Estimated size: ${PdfUtils.formatFileSize(estMinSize)} - ${PdfUtils.formatFileSize(estMaxSize)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))

                    // Destination Selection
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { useCustomLocation = !useCustomLocation }
                            ) {
                                Checkbox(
                                    checked = useCustomLocation,
                                    onCheckedChange = { useCustomLocation = it }
                                )
                                Text("Save to custom folder", style = MaterialTheme.typography.bodyMedium)
                            }

                            if (useCustomLocation) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .clickable { directoryLauncher.launch(null) }
                                        .padding(8.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (customSaveUri != null) customSaveUri!!.path ?: "Custom Folder" else "Select Folder...",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1
                                        )
                                    }
                                    Icon(Icons.Default.FolderOpen, contentDescription = "Choose Folder", modifier = Modifier.size(20.dp))
                                }
                            } else {
                                Text(
                                    text = "Saving to: Documents/pdfone",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 32.dp, bottom = 4.dp)
                                )
                            }
                        }
                    }

                    // Action Button
                    Button(
                        onClick = {
                            isProcessing = true
                            scope.launch {
                                val result = performCompression(
                                    context = context,
                                    uri = selectedPdf!!,
                                    quality = compressionQuality,
                                    customSaveUri = if (useCustomLocation) customSaveUri else null
                                )
                                isProcessing = false
                                when (result) {
                                    is CompressionResult.Success -> {
                                        compressedSize = result.file.length()
                                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", result.file)
                                        if (showAds && activity != null) {
                                            com.villagefast.pdfeditor.ads.AdManager.showInterstitial(activity) {
                                                onResult(uri, "Compression")
                                            }
                                        } else {
                                            onResult(uri, "Compression")
                                        }
                                    }
                                    is CompressionResult.Error -> {
                                        snackbarHostState.showSnackbar("Error: ${result.message}")
                                    }
                                }
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text("Compress PDF")
                        }
                    }
                }
            }
        }
    }


sealed class CompressionResult {
    data class Success(val file: File) : CompressionResult()
    data class Error(val message: String) : CompressionResult()
}

private suspend fun performCompression(
    context: android.content.Context,
    uri: Uri,
    quality: Float,
    customSaveUri: Uri?
): CompressionResult = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val document = PDDocument.load(inputStream)
            
            for (page in document.pages) {
                val resources = page.resources
                for (name in resources.xObjectNames) {
                    if (resources.isImageXObject(name)) {
                        val image = resources.getXObject(name) as PDImageXObject
                        val bitmap = image.image
                        
                        // We use a lower quality for the JPEG compression
                        val compressedImage = JPEGFactory.createFromImage(document, bitmap, quality)
                        resources.put(name, compressedImage)
                    }
                }
            }
            
            val originalName = PdfUtils.getFileName(context, uri)?.removeSuffix(".pdf") ?: "Document"
            val fileName = "Compressed_${originalName}_${System.currentTimeMillis()}.pdf"
            
            val outputFile = File(context.cacheDir, fileName)
            FileOutputStream(outputFile).use { out ->
                document.save(out)
            }
            
            // If custom location was requested, save there too
            if (customSaveUri != null) {
                val parentDir = DocumentFile.fromTreeUri(context, customSaveUri)
                val file = parentDir?.createFile("application/pdf", fileName)
                file?.let {
                    context.contentResolver.openOutputStream(it.uri)?.use { out ->
                        document.save(out)
                    }
                }
            }

            // Try to move/copy to public documents if possible
            val finalFile = try {
                val publicFile = PdfUtils.getPublicDocumentsFile(fileName)
                val parent = publicFile.parentFile
                if (parent != null && (parent.exists() || parent.mkdirs())) {
                    outputFile.copyTo(publicFile, overwrite = true)
                    publicFile
                } else {
                    outputFile
                }
            } catch (e: Exception) {
                outputFile
            }

            document.close()
            return@withContext CompressionResult.Success(finalFile)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext CompressionResult.Error(e.message ?: "Unknown error")
    }
}
