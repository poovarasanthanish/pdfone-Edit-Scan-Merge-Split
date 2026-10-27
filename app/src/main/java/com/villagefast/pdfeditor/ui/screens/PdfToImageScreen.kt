package com.villagefast.pdfeditor.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
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
fun PdfToImageScreen(
    showAds: Boolean = true,
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    var selectedPdf by remember { mutableStateOf<Uri?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var isProcessing by remember { mutableStateOf(value = false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val formats = listOf("JPEG", "PNG", "WEBP")
    var selectedFormat by remember { mutableStateOf(formats[0]) }

    var selectedDpi by remember { mutableFloatStateOf(150f) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        selectedPdf = uri
        if (uri != null) {
            scope.launch {
                pageCount = PdfUtils.getPageCount(context, uri)
            }
        } else {
            pageCount = 0
        }
    }

    LiquidScaffold(
        showBanner = showAds,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassTopAppBar(
                title = { Text("PDF to Image") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (selectedPdf == null) {
                OutlinedCard(
                    onClick = { launcher.launch(arrayOf("application/pdf")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Select PDF File",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Convert pages to high-quality images",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val fileName = remember(selectedPdf) { PdfUtils.getFileName(context, selectedPdf!!) ?: "Document.pdf" }
                
                // Selected File Card
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PdfThumbnail(
                            uri = selectedPdf!!,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (pageCount > 0) {
                                Text(
                                    text = "$pageCount Pages",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                        IconButton(onClick = { 
                            selectedPdf = null
                            pageCount = 0
                        }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (pageCount > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Page Previews",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.fillMaxWidth(),
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
                                        .size(100.dp, 140.dp)
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

                // Settings Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Conversion Settings", style = MaterialTheme.typography.titleSmall)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        var formatExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = formatExpanded,
                            onExpandedChange = { formatExpanded = !formatExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedFormat,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Output Format") },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = formatExpanded,
                                onDismissRequest = { formatExpanded = false }
                            ) {
                                formats.forEach { format ->
                                    DropdownMenuItem(
                                        text = { Text(format) },
                                        onClick = {
                                            selectedFormat = format
                                            formatExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Resolution: ${selectedDpi.toInt()} DPI",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = selectedDpi,
                            onValueChange = { selectedDpi = it },
                            valueRange = 72f..600f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("72 DPI", style = MaterialTheme.typography.labelSmall)
                            Text("600 DPI", style = MaterialTheme.typography.labelSmall)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Save Location",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Pictures/pdfone",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                Button(
                    onClick = {
                        isProcessing = true
                        scope.launch {
                            val resultFile = convertPdfToImages(
                                context = context,
                                uri = selectedPdf!!,
                                format = selectedFormat,
                                dpi = selectedDpi,
                                fileName = fileName
                            )
                            isProcessing = false
                            if (resultFile != null) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", resultFile)
                                if (showAds && activity != null) {
                                    com.villagefast.pdfeditor.ads.AdManager.showInterstitial(activity) {
                                        onResult(uri, "PDF to Image")
                                    }
                                } else {
                                    onResult(uri, "PDF to Image")
                                }
                            } else {
                                snackbarHostState.showSnackbar("Failed to convert PDF")
                            }
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Start Conversion", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

private suspend fun convertPdfToImages(
    context: android.content.Context,
    uri: Uri,
    format: String,
    dpi: Float,
    fileName: String
): File? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val document = PDDocument.load(inputStream)
            val renderer = PDFRenderer(document)

            val compressFormat = when (format) {
                "PNG" -> Bitmap.CompressFormat.PNG
                "WEBP" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                else -> Bitmap.CompressFormat.JPEG
            }

            val extension = when (format) {
                "PNG" -> "png"
                "WEBP" -> "webp"
                else -> "jpg"
            }

            val baseName = fileName.substringBeforeLast(".")
            val timestamp = System.currentTimeMillis()
            
            if (document.numberOfPages == 1) {
                // Single page: Save as direct image file
                val outputFile = PdfUtils.getPublicPicturesFile("${baseName}_$timestamp.$extension")
                val bitmap = renderer.renderImage(0, dpi / 72f)
                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(compressFormat, 95, out)
                }
                document.close()
                return@withContext outputFile
            } else {
                // Multiple pages: Save as ZIP
                val outputZip = PdfUtils.getPublicDocumentsFile("${baseName}_images_$timestamp.zip")
                java.util.zip.ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
                    val scale = dpi / 72f
                    for (i in 0 until document.numberOfPages) {
                        val bitmap = renderer.renderImage(i, scale)
                        val entry = java.util.zip.ZipEntry("${baseName}_page_${i + 1}.$extension")
                        zos.putNextEntry(entry)
                        bitmap.compress(compressFormat, 90, zos)
                        zos.closeEntry()
                        bitmap.recycle()
                    }
                }
                document.close()
                return@withContext outputZip
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}
