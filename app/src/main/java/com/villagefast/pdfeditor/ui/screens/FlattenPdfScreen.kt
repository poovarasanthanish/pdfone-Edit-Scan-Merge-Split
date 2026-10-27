package com.villagefast.pdfeditor.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.rendering.PDFRenderer
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
fun FlattenPdfScreen(
    showAds: Boolean = false, 
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        selectedUri = uri
    }

    LiquidScaffold(
        showBanner = showAds,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassTopAppBar(
                title = { Text("Flatten PDF") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (selectedUri == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Flattening prevents editing of forms and annotations.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { launcher.launch(arrayOf("application/pdf")) }) {
                        Text("Select PDF File")
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Selected: ${PdfUtils.getFileName(context, selectedUri!!)}")
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = {
                            isProcessing = true
                            scope.launch {
                                val file = flattenPdf(context, selectedUri!!)
                                isProcessing = false
                                if (file != null) {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                    if (showAds && activity != null) {
                                        com.villagefast.pdfeditor.ads.AdManager.showInterstitial(activity) {
                                            onResult(uri, "Flatten")
                                        }
                                    } else {
                                        onResult(uri, "Flatten")
                                    }
                                } else {
                                    snackbarHostState.showSnackbar("Failed to flatten PDF")
                                }
                            }
                        },
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Text("Flatten & Save")
                        }
                    }
                }
            }
        }
    }
}

private suspend fun flattenPdf(context: android.content.Context, uri: Uri): File? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val sourceDoc = PDDocument.load(inputStream)
            val renderer = PDFRenderer(sourceDoc)
            val targetDoc = PDDocument()
            
            for (i in 0 until sourceDoc.numberOfPages) {
                // Render page to high-quality image
                val bitmap = renderer.renderImage(i, 2f) 
                val page = PDPage(PDRectangle(bitmap.width.toFloat() / 2, bitmap.height.toFloat() / 2))
                targetDoc.addPage(page)
                
                val pdImage = LosslessFactory.createFromImage(targetDoc, bitmap)
                val contentStream = PDPageContentStream(targetDoc, page)
                contentStream.drawImage(pdImage, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
                contentStream.close()
            }
            
            val file = PdfUtils.getPublicDocumentsFile("Flattened_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { out -> targetDoc.save(out) }
            sourceDoc.close()
            targetDoc.close()
            return@withContext file
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}
