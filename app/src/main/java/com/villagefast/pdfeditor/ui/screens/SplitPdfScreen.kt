package com.villagefast.pdfeditor.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.multipdf.Splitter
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
import androidx.compose.ui.tooling.preview.Preview
import com.villagefast.pdfeditor.ui.theme.PdfeditorTheme

enum class SplitMode {
    ALL_PAGES,
    EXTRACT_RANGE,
    SPECIFIC_PAGES,
    VISUAL_SELECTION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPdfScreen(
    showAds: Boolean = true,
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val finalShowAds = showAds && com.villagefast.pdfeditor.data.RemoteConfigManager.showBannerAds()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    var selectedPdf by remember { mutableStateOf<Uri?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var splitMode by remember { mutableStateOf(SplitMode.ALL_PAGES) }
    var isProcessing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Input states
    var rangeStart by remember { mutableStateOf("1") }
    var rangeEnd by remember { mutableStateOf("1") }
    var specificPages by remember { mutableStateOf("") }
    val visualSelectedPages = remember { mutableStateListOf<Int>() }
    var customSaveUri by remember { mutableStateOf<Uri?>(null) }
    var useCustomLocation by remember { mutableStateOf(false) }

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
            scope.launch {
                pageCount = getPageCount(context, uri)
                rangeEnd = pageCount.toString()
                visualSelectedPages.clear()
            }
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
        showBanner = finalShowAds,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassTopAppBar(
                title = { Text("Split PDF") },
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
                    Text("Select a PDF file to split", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { launcher.launch(arrayOf("application/pdf")) }) {
                        Text("Select PDF")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                // Selection Summary
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = PdfUtils.getFileName(context, selectedPdf!!) ?: "Selected PDF",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$pageCount Pages",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { selectedPdf = null }) {
                                Text("Change")
                            }
                        }
                    }

                    // Tabs for modes
                    SecondaryScrollableTabRow(
                        selectedTabIndex = splitMode.ordinal,
                        edgePadding = 16.dp,
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        SplitMode.entries.forEach { mode ->
                            Tab(
                                selected = splitMode == mode,
                                onClick = { splitMode = mode },
                                text = {
                                    Text(
                                        when (mode) {
                                            SplitMode.ALL_PAGES -> "All Pages"
                                            SplitMode.EXTRACT_RANGE -> "Range"
                                            SplitMode.SPECIFIC_PAGES -> "Specific"
                                            SplitMode.VISUAL_SELECTION -> "Visual"
                                        }
                                    )
                                }
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (splitMode) {
                            SplitMode.ALL_PAGES -> {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text("Every page will be saved as a separate PDF file.")
                                }
                            }
                            SplitMode.EXTRACT_RANGE -> {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = rangeStart,
                                            onValueChange = { rangeStart = it.filter { c -> c.isDigit() } },
                                            label = { Text("From") },
                                            modifier = Modifier.width(100.dp)
                                        )
                                        Text(" to ", modifier = Modifier.padding(horizontal = 8.dp))
                                        OutlinedTextField(
                                            value = rangeEnd,
                                            onValueChange = { rangeEnd = it.filter { c -> c.isDigit() } },
                                            label = { Text("To") },
                                            modifier = Modifier.width(100.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Example: 1 to 5 will extract first 5 pages.")
                                }
                            }
                            SplitMode.SPECIFIC_PAGES -> {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    OutlinedTextField(
                                        value = specificPages,
                                        onValueChange = { specificPages = it },
                                        label = { Text("Page numbers") },
                                        placeholder = { Text("e.g. 1, 3, 5-7") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Enter comma separated page numbers or ranges.")
                                }
                            }
                            SplitMode.VISUAL_SELECTION -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        AssistChip(
                                            onClick = {
                                                visualSelectedPages.clear()
                                                for (i in 0 until pageCount) visualSelectedPages.add(i)
                                            },
                                            label = { Text("All") }
                                        )
                                        AssistChip(
                                            onClick = {
                                                visualSelectedPages.clear()
                                                for (i in 0 until pageCount step 2) visualSelectedPages.add(i)
                                            },
                                            label = { Text("Odd") }
                                        )
                                        AssistChip(
                                            onClick = {
                                                visualSelectedPages.clear()
                                                for (i in 1 until pageCount step 2) visualSelectedPages.add(i)
                                            },
                                            label = { Text("Even") }
                                        )
                                        AssistChip(
                                            onClick = { visualSelectedPages.clear() },
                                            label = { Text("Clear") }
                                        )
                                    }
                                    VisualSelectionGrid(
                                        uri = selectedPdf!!,
                                        pageCount = pageCount,
                                        selectedPages = visualSelectedPages,
                                        onTogglePage = { index ->
                                            if (visualSelectedPages.contains(index)) {
                                                visualSelectedPages.remove(index)
                                            } else {
                                                visualSelectedPages.add(index)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Destination Selection
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                                val result = performSplit(
                                    context = context,
                                    uri = selectedPdf!!,
                                    mode = splitMode,
                                    rangeStart = rangeStart.toIntOrNull() ?: 1,
                                    rangeEnd = rangeEnd.toIntOrNull() ?: pageCount,
                                    specificPages = specificPages,
                                    visualSelectedIndices = visualSelectedPages.toList(),
                                    customSaveUri = if (useCustomLocation) customSaveUri else null
                                )
                                isProcessing = false
                                when (result) {
                                    is SplitResult.Success -> {
                                        if (result.files.isNotEmpty()) {
                                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", result.files.first())
                                            if (showAds && activity != null) {
                                                AdManager.showInterstitial(activity) {
                                                    onResult(uri, "Split")
                                                }
                                            } else {
                                                onResult(uri, "Split")
                                            }
                                        } else {
                                             snackbarHostState.showSnackbar("Split successful!")
                                        }
                                    }
                                    is SplitResult.Error -> {
                                        snackbarHostState.showSnackbar("Error: ${result.message}")
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text("Process Split")
                        }
                    }
            }
        }
    }
}

@Composable
fun VisualSelectionGrid(
    uri: Uri,
    pageCount: Int,
    selectedPages: List<Int>,
    onTogglePage: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(pageCount) { index ->
            PageThumbnailItem(
                uri = uri,
                pageIndex = index,
                isSelected = selectedPages.contains(index),
                onClick = { onTogglePage(index) }
            )
        }
    }
}

@Composable
fun PageThumbnailItem(
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

        // Overlay for selection
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

sealed class SplitResult {
    data class Success(val files: List<File>) : SplitResult()
    data class Error(val message: String) : SplitResult()
}

private suspend fun getPageCount(context: android.content.Context, uri: Uri): Int = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val document = PDDocument.load(inputStream)
            val count = document.numberOfPages
            document.close()
            return@withContext count
        }
    } catch (e: Exception) {
        return@withContext 0
    }
}

private suspend fun performSplit(
    context: android.content.Context,
    uri: Uri,
    mode: SplitMode,
    rangeStart: Int,
    rangeEnd: Int,
    specificPages: String,
    visualSelectedIndices: List<Int>,
    customSaveUri: Uri?
): SplitResult = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val document = PDDocument.load(inputStream)
            val totalPages = document.numberOfPages
            
            val resultFiles = mutableListOf<File>()
            val parentDir: Any = if (customSaveUri != null) {
                DocumentFile.fromTreeUri(context, customSaveUri) ?: return@withContext SplitResult.Error("Cannot access folder")
            } else {
                val dir = PdfUtils.getPublicDocumentsFile("Split_${System.currentTimeMillis()}")
                dir.mkdirs()
                dir
            }

            fun saveDoc(doc: PDDocument, fileName: String) {
                if (parentDir is DocumentFile) {
                    val file = parentDir.createFile("application/pdf", fileName)
                    file?.let {
                        context.contentResolver.openOutputStream(it.uri)?.use { out ->
                            doc.save(out)
                        }
                    }
                } else if (parentDir is File) {
                    val file = File(parentDir, fileName)
                    doc.save(file)
                    resultFiles.add(file)
                }
            }

            when (mode) {
                SplitMode.ALL_PAGES -> {
                    val splitter = Splitter()
                    val pages = splitter.split(document)
                    pages.forEachIndexed { index, doc ->
                        saveDoc(doc, "Page_${index + 1}.pdf")
                        doc.close()
                    }
                }
                SplitMode.EXTRACT_RANGE -> {
                    val start = (rangeStart - 1).coerceIn(0, totalPages - 1)
                    val end = (rangeEnd - 1).coerceIn(start, totalPages - 1)
                    
                    val newDoc = PDDocument()
                    for (i in start..end) {
                        newDoc.importPage(document.getPage(i))
                    }
                    saveDoc(newDoc, "Range_${rangeStart}_to_${rangeEnd}.pdf")
                    newDoc.close()
                }
                SplitMode.SPECIFIC_PAGES -> {
                    val indices = parsePageIndices(specificPages, totalPages)
                    if (indices.isEmpty()) return@withContext SplitResult.Error("Invalid page selection")
                    
                    val newDoc = PDDocument()
                    indices.forEach { idx ->
                        newDoc.importPage(document.getPage(idx))
                    }
                    saveDoc(newDoc, "Extracted_Pages.pdf")
                    newDoc.close()
                }
                SplitMode.VISUAL_SELECTION -> {
                    if (visualSelectedIndices.isEmpty()) return@withContext SplitResult.Error("No pages selected")
                    
                    val newDoc = PDDocument()
                    visualSelectedIndices.sorted().forEach { idx ->
                        newDoc.importPage(document.getPage(idx))
                    }
                    saveDoc(newDoc, "Selected_Pages.pdf")
                    newDoc.close()
                }
            }
            
            document.close()
            return@withContext SplitResult.Success(resultFiles)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext SplitResult.Error(e.message ?: "Unknown error")
    }
}

private fun parsePageIndices(input: String, maxPages: Int): List<Int> {
    val result = mutableSetOf<Int>()
    val parts = input.split(",")
    for (part in parts) {
        val trimmed = part.trim()
        if (trimmed.contains("-")) {
            val range = trimmed.split("-")
            if (range.size == 2) {
                val start = range[0].trim().toIntOrNull()
                val end = range[1].trim().toIntOrNull()
                if (start != null && end != null) {
                    for (i in start..end) {
                        if (i in 1..maxPages) result.add(i - 1)
                    }
                }
            }
        } else {
            val page = trimmed.toIntOrNull()
            if (page != null && page in 1..maxPages) {
                result.add(page - 1)
            }
        }
    }
    return result.toList().sorted()
}

@Preview(showBackground = true)
@Composable
fun SplitPdfScreenPreview() {
    PdfeditorTheme {
        SplitPdfScreen(
            onBackClick = {},
            onResult = { _, _ -> }
        )
    }
}
