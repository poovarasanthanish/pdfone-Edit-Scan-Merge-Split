package com.villagefast.pdfeditor.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.villagefast.pdfeditor.ads.AdManager
import com.villagefast.pdfeditor.ui.components.GlassCard
import com.villagefast.pdfeditor.ui.components.GlassTopAppBar
import com.villagefast.pdfeditor.ui.components.LiquidScaffold
import com.villagefast.pdfeditor.ui.components.PdfThumbnail
import com.villagefast.pdfeditor.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import com.villagefast.pdfeditor.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

import androidx.compose.ui.tooling.preview.Preview
import com.villagefast.pdfeditor.ui.theme.PdfeditorTheme

data class SelectedPdf(val uri: Uri, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergePdfScreen(
    showAds: Boolean = true,
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    var selectedPdfs by remember { mutableStateOf<List<SelectedPdf>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(value = false) }
    var useCustomLocation by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val defaultPath = "Documents/pdfone"

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val newPdfs = uris.map { uri ->
            SelectedPdf(uri, PdfUtils.getFileName(context, uri) ?: "Unknown PDF")
        }
        selectedPdfs += newPdfs
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null) {
            isProcessing = true
            scope.launch {
                val success = mergePdfsToUri(context, selectedPdfs.map { it.uri }, uri)
                isProcessing = false
                if (success) {
                    if (showAds && activity != null) {
                        AdManager.showInterstitialWithInterval(activity) {
                            onResult(uri, "Merge")
                        }
                    } else {
                        onResult(uri, "Merge")
                    }
                } else {
                    snackbarHostState.showSnackbar("Failed to merge PDFs")
                }
            }
        }
    }

    LiquidScaffold(
        showBanner = showAds,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassTopAppBar(
                title = { Text("Merge PDFs") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            launcher.launch(arrayOf("application/pdf"))
                        },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add PDF")
                    }
                }
            )
        },
        bottomBar = {
            if (selectedPdfs.size >= 2) {
                Surface(
                    color = (if (LocalIsDarkTheme.current) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White).copy(alpha = 0.2f),
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Checkbox(
                                checked = useCustomLocation,
                                onCheckedChange = { useCustomLocation = it }
                            )
                            Text("Custom Save Location", style = MaterialTheme.typography.bodyMedium)
                        }

                        if (!useCustomLocation) {
                            Text(
                                text = "Default: $defaultPath",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        Button(
                            onClick = {
                                if (useCustomLocation) {
                                    createDocumentLauncher.launch("Merged_${System.currentTimeMillis()}.pdf")
                                } else {
                                    isProcessing = true
                                    scope.launch {
                                        val file = mergePdfs(context, selectedPdfs.map { it.uri })
                                        isProcessing = false
                                        if (file != null) {
                                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                            if (showAds && activity != null) {
                                                AdManager.showInterstitialWithInterval(activity) {
                                                    onResult(uri, "Merge")
                                                }
                                            } else {
                                                onResult(uri, "Merge")
                                            }
                                        } else {
                                            snackbarHostState.showSnackbar("Failed to merge PDFs")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isProcessing
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Text("Merge ${selectedPdfs.size} PDFs")
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (selectedPdfs.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select at least 2 PDFs to merge")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { launcher.launch(arrayOf("application/pdf")) }) {
                        Text("Select PDF Files")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(selectedPdfs) { index, pdf ->
                    GlassCard {
                        ListItem(
                            headlineContent = { 
                                Text(
                                    text = pdf.name,
                                    maxLines = 1,
                                    fontSize = 14.sp
                                ) 
                            },
                            leadingContent = { 
                                PdfThumbnail(
                                    uri = pdf.uri,
                                    modifier = Modifier.size(40.dp)
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val newList = selectedPdfs.toMutableList()
                                                val item = newList.removeAt(index)
                                                newList.add(index - 1, item)
                                                selectedPdfs = newList
                                            }
                                        },
                                        enabled = index > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Up", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            if (index < selectedPdfs.size - 1) {
                                                val newList = selectedPdfs.toMutableList()
                                                val item = newList.removeAt(index)
                                                newList.add(index + 1, item)
                                                selectedPdfs = newList
                                            }
                                        },
                                        enabled = index < selectedPdfs.size - 1,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Down", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { selectedPdfs = selectedPdfs - pdf },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

private suspend fun mergePdfs(context: android.content.Context, uris: List<Uri>): File? = withContext(Dispatchers.IO) {
    val destination = com.tom_roush.pdfbox.pdmodel.PDDocument()
    try {
        val merger = PDFMergerUtility()
        for (uri in uris) {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val source = com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
                merger.appendDocument(destination, source)
                source.close()
            }
        }
        
        val firstFileName = uris.firstOrNull()?.let { PdfUtils.getFileName(context, it)?.removeSuffix(".pdf") } ?: "Document"
        val fileName = "Merged_${firstFileName}_${System.currentTimeMillis()}.pdf"
        
        val outputFile = File(context.cacheDir, fileName)
        FileOutputStream(outputFile).use { outputStream ->
            destination.save(outputStream)
        }
        
        // Try to move to public documents if possible
        try {
            val publicFile = PdfUtils.getPublicDocumentsFile(fileName)
            val parent = publicFile.parentFile
            if (parent != null && (parent.exists() || parent.mkdirs())) {
                outputFile.copyTo(publicFile, overwrite = true)
                destination.close()
                return@withContext publicFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        destination.close()
        return@withContext outputFile
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    } finally {
        try { destination.close() } catch (e: Exception) {}
    }
}

private suspend fun mergePdfsToUri(context: android.content.Context, uris: List<Uri>, outputUri: Uri): Boolean = withContext(Dispatchers.IO) {
    val destination = com.tom_roush.pdfbox.pdmodel.PDDocument()
    try {
        val merger = PDFMergerUtility()
        for (uri in uris) {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val source = com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
                merger.appendDocument(destination, source)
                source.close()
            }
        }
        
        context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
            destination.save(outputStream)
        }
        destination.close()
        return@withContext true
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext false
    } finally {
        try { destination.close() } catch (e: Exception) {}
    }
}

@Preview(showBackground = true)
@Composable
fun MergePdfScreenPreview() {
    PdfeditorTheme {
        MergePdfScreen(
            onBackClick = {},
            onResult = { _, _ -> }
        )
    }
}
