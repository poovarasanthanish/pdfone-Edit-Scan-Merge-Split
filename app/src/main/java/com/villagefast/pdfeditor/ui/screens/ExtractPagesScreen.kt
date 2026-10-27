package com.villagefast.pdfeditor.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
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
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.villagefast.pdfeditor.ads.AdManager
import com.villagefast.pdfeditor.ui.components.GlassTopAppBar
import com.villagefast.pdfeditor.ui.components.LiquidScaffold
import com.villagefast.pdfeditor.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractPagesScreen(
    initialUri: Uri? = null,
    showAds: Boolean = true,
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    var selectedPdf by remember { mutableStateOf<Uri?>(initialUri) }
    var pageCount by remember { mutableIntStateOf(0) }
    val selectedPages = remember { mutableStateListOf<Int>() }
    var isProcessing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

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
                selectedPages.clear()
            }
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null) {
            pageCount = getPageCount(context, initialUri)
            selectedPages.clear()
        }
    }

    LiquidScaffold(
        showBanner = showAds,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassTopAppBar(
                title = { Text("Extract Pages") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedPdf != null && selectedPages.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                isProcessing = true
                                scope.launch {
                                    val file = performExtraction(context, selectedPdf!!, selectedPages.toList())
                                    isProcessing = false
                                    if (file != null) {
                                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                        if (showAds && activity != null) {
                                            AdManager.showInterstitial(activity) {
                                                onResult(uri, "Extraction")
                                            }
                                        } else {
                                            onResult(uri, "Extraction")
                                        }
                                    } else {
                                        snackbarHostState.showSnackbar("Failed to extract pages")
                                    }
                                }
                            },
                            enabled = !isProcessing
                        ) {
                            Text("Extract (${selectedPages.size})")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (selectedPdf == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Select a PDF file to extract pages", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { launcher.launch(arrayOf("application/pdf")) }) {
                        Text("Select PDF")
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Selection Summary & Quick Select
                    Surface(
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = PdfUtils.getFileName(context, selectedPdf!!) ?: "Selected PDF",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = {
                                        selectedPages.clear()
                                        for (i in 0 until pageCount) selectedPages.add(i)
                                    },
                                    label = { Text("All") }
                                )
                                AssistChip(
                                    onClick = {
                                        selectedPages.clear()
                                        for (i in 0 until pageCount step 2) selectedPages.add(i)
                                    },
                                    label = { Text("Odd") }
                                )
                                AssistChip(
                                    onClick = {
                                        selectedPages.clear()
                                        for (i in 1 until pageCount step 2) selectedPages.add(i)
                                    },
                                    label = { Text("Even") }
                                )
                                AssistChip(
                                    onClick = { selectedPages.clear() },
                                    label = { Text("Clear") }
                                )
                            }
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(pageCount) { index ->
                            VisualPageItem(
                                uri = selectedPdf!!,
                                pageIndex = index,
                                isSelected = selectedPages.contains(index),
                                onClick = {
                                    if (selectedPages.contains(index)) {
                                        selectedPages.remove(index)
                                    } else {
                                        selectedPages.add(index)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun VisualPageItem(
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

private suspend fun getPageCount(context: android.content.Context, uri: Uri): Int = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val document = PDDocument.load(inputStream)
            val count = document.numberOfPages
            document.close()
            return@withContext count
        }
    } catch (e: Exception) {
        0
    }
}

private suspend fun performExtraction(
    context: android.content.Context,
    uri: Uri,
    selectedIndices: List<Int>
): File? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val sourceDoc = PDDocument.load(inputStream)
            val targetDoc = PDDocument()
            
            selectedIndices.sorted().forEach { index ->
                targetDoc.importPage(sourceDoc.getPage(index))
            }
            
            val file = PdfUtils.getPublicDocumentsFile("Extracted_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { out ->
                targetDoc.save(out)
            }
            sourceDoc.close()
            targetDoc.close()
            return@withContext file
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
