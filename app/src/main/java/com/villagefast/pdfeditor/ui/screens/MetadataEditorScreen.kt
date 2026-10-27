package com.villagefast.pdfeditor.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataEditorScreen(
    showAds: Boolean = true, 
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var creator by remember { mutableStateOf("") }
    var producer by remember { mutableStateOf("") }
    
    // Read-only info
    var creationDate by remember { mutableStateOf("") }
    var modificationDate by remember { mutableStateOf("") }
    var pageCount by remember { mutableIntStateOf(0) }
    var fileSize by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    
    var isProcessing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            scope.launch {
                loadMetadata(context, uri) { info, count ->
                    title = info.title ?: ""
                    author = info.author ?: ""
                    subject = info.subject ?: ""
                    keywords = info.keywords ?: ""
                    creator = info.creator ?: ""
                    producer = info.producer ?: ""
                    
                    creationDate = info.creationDate?.let { dateFormat.format(it.time) } ?: "Unknown"
                    modificationDate = info.modificationDate?.let { dateFormat.format(it.time) } ?: "Unknown"
                    pageCount = count
                    fileSize = PdfUtils.formatFileSize(PdfUtils.getFileSize(context, uri))
                    fileName = PdfUtils.getFileName(context, uri) ?: "Unknown"
                }
            }
        }
    }

    LiquidScaffold(
        showBanner = showAds,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassTopAppBar(
                title = { Text("PDF Details & Metadata") },
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
            if (selectedUri == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))
                        Text("View or Edit PDF details", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { launcher.launch(arrayOf("application/pdf")) }) {
                            Text("Select PDF File")
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // File Info Section
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PdfThumbnail(
                                uri = selectedUri!!,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "File Information",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                InfoRow("Name", fileName)
                                InfoRow("Size", fileSize)
                                InfoRow("Pages", pageCount.toString())
                                InfoRow("Created", creationDate)
                                InfoRow("Modified", modificationDate)
                            }
                        }
                    }

                    if (pageCount > 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Page Previews",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
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
                                        uri = selectedUri!!,
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
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Editable Metadata Section
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Edit Metadata", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            
                            MetadataField("Title", title) { title = it }
                            MetadataField("Author", author) { author = it }
                            MetadataField("Subject", subject) { subject = it }
                            MetadataField("Keywords", keywords) { keywords = it }
                            MetadataField("Creator", creator) { creator = it }
                            MetadataField("Producer", producer) { producer = it }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = {
                            isProcessing = true
                            scope.launch {
                                val file = saveMetadata(context, selectedUri!!, title, author, subject, keywords, creator, producer)
                                isProcessing = false
                                if (file != null) {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                    if (showAds && activity != null) {
                                        com.villagefast.pdfeditor.ads.AdManager.showInterstitial(activity) {
                                            onResult(uri, "Metadata Update")
                                        }
                                    } else {
                                        onResult(uri, "Metadata Update")
                                    }
                                } else {
                                    snackbarHostState.showSnackbar("Failed to update metadata")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Text("Save PDF with New Metadata")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(onClick = { selectedUri = null }) {
                        Text("Select different file")
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MetadataField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        )
    )
}

private suspend fun loadMetadata(context: android.content.Context, uri: Uri, onResult: (PDDocumentInformation, Int) -> Unit) = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val document = PDDocument.load(inputStream)
            val info = document.documentInformation
            val count = document.numberOfPages
            onResult(info, count)
            document.close()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private suspend fun saveMetadata(
    context: android.content.Context,
    uri: Uri,
    title: String,
    author: String,
    subject: String,
    keywords: String,
    creator: String,
    producer: String
): File? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val document = PDDocument.load(inputStream)
            val info = document.documentInformation
            info.title = title
            info.author = author
            info.subject = subject
            info.keywords = keywords
            info.creator = creator
            info.producer = producer
            document.documentInformation = info
            
            val file = PdfUtils.getPublicDocumentsFile("Meta_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { out -> document.save(out) }
            document.close()
            return@withContext file
        }
    } catch (e: Exception) {
        return@withContext null
    }
}
