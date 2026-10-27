package com.villagefast.pdfeditor.ui.screens

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
import com.villagefast.pdfeditor.ui.components.GlassCard
import com.villagefast.pdfeditor.ui.components.GlassTopAppBar
import com.villagefast.pdfeditor.ui.components.LiquidScaffold
import com.villagefast.pdfeditor.ui.components.PdfThumbnail
import com.villagefast.pdfeditor.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToZipScreen(
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
    var zipSize by remember { mutableLongStateOf(0L) }
    var isProcessing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var useCustomLocation by remember { mutableStateOf(false) }
    var customSaveUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            selectedPdf = uri
            originalSize = PdfUtils.getFileSize(context, uri)
            zipSize = 0L
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
                title = { Text("PDF to ZIP") },
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
        ) {
            if (selectedPdf == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Select a PDF to compress into a ZIP archive", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { launcher.launch(arrayOf("application/pdf")) }) {
                            Text("Select PDF File")
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
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
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = PdfUtils.getFileName(context, selectedPdf!!) ?: "Selected PDF",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "PDF Size: ${PdfUtils.formatFileSize(originalSize)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (zipSize > 0) {
                                    Text(
                                        text = "ZIP Size: ${PdfUtils.formatFileSize(zipSize)}",
                                        style = MaterialTheme.typography.bodySmall,
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

                    Text(
                        text = "This tool will wrap your PDF inside a ZIP archive. This is useful for email attachments or reducing storage space marginally.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

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
                                val result = performZipCompression(
                                    context = context,
                                    uri = selectedPdf!!,
                                    customSaveUri = if (useCustomLocation) customSaveUri else null
                                )
                                isProcessing = false
                                when (result) {
                                    is ZipResult.Success -> {
                                        zipSize = result.file.length()
                                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", result.file)
                                        if (showAds && activity != null) {
                                            com.villagefast.pdfeditor.ads.AdManager.showInterstitial(activity) {
                                                onResult(uri, "ZIP Archive")
                                            }
                                        } else {
                                            onResult(uri, "ZIP Archive")
                                        }
                                    }
                                    is ZipResult.Error -> {
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
                            Text("Create ZIP Archive")
                        }
                    }
                }
            }
        }
    }
}

sealed class ZipResult {
    data class Success(val file: File) : ZipResult()
    data class Error(val message: String) : ZipResult()
}

private suspend fun performZipCompression(
    context: android.content.Context,
    uri: Uri,
    customSaveUri: Uri?
): ZipResult = withContext(Dispatchers.IO) {
    try {
        val originalName = PdfUtils.getFileName(context, uri) ?: "Document.pdf"
        val zipFileName = originalName.removeSuffix(".pdf") + ".zip"
        
        val tempZipFile = PdfUtils.getPublicDocumentsFile(zipFileName)
        
        context.contentResolver.openInputStream(uri).use { inputStream ->
            if (inputStream == null) return@withContext ZipResult.Error("Cannot open PDF file")
            
            FileOutputStream(tempZipFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    val zipEntry = ZipEntry(originalName)
                    zos.putNextEntry(zipEntry)
                    
                    val buffer = ByteArray(1024)
                    var len: Int
                    while (inputStream.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }
                    zos.closeEntry()
                }
            }
        }

        if (customSaveUri != null) {
            val parentDir = DocumentFile.fromTreeUri(context, customSaveUri)
            val file = parentDir?.createFile("application/zip", zipFileName)
            file?.let {
                context.contentResolver.openOutputStream(it.uri)?.use { out ->
                    FileInputStream(tempZipFile).use { ins ->
                        ins.copyTo(out)
                    }
                }
            }
        }

        return@withContext ZipResult.Success(tempZipFile)
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext ZipResult.Error(e.message ?: "Unknown error")
    }
}
