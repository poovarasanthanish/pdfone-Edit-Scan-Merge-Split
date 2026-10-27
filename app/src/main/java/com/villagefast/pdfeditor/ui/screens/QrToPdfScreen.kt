package com.villagefast.pdfeditor.ui.screens

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.villagefast.pdfeditor.ui.components.GlassTopAppBar
import com.villagefast.pdfeditor.ui.components.LiquidScaffold
import com.villagefast.pdfeditor.utils.PdfUtils
import androidx.compose.ui.tooling.preview.Preview
import com.villagefast.pdfeditor.ui.theme.PdfeditorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrToPdfScreen(
    showAds: Boolean = false,
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(value = false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LiquidScaffold(
        showBanner = showAds,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassTopAppBar(
                title = { Text("QR to PDF") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { 
                    text = it
                    if (it.isNotEmpty()) {
                        qrBitmap = generateQrCode(it)
                    } else {
                        qrBitmap = null
                    }
                },
                label = { Text("Enter text or URL") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            qrBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR Preview",
                    modifier = Modifier.size(200.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = {
                        isProcessing = true
                        scope.launch {
                            val file = saveQrAsPdf(context, bitmap)
                            isProcessing = false
                            if (file != null) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                if (showAds && activity != null) {
                                    com.villagefast.pdfeditor.ads.AdManager.showInterstitial(activity) {
                                        onResult(uri, "QR Conversion")
                                    }
                                } else {
                                    onResult(uri, "QR Conversion")
                                }
                            } else {
                                snackbarHostState.showSnackbar("Failed to save PDF")
                            }
                        }
                    },
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Text("Save as PDF")
                    }
                }
            }
        }
    }
}

private fun generateQrCode(content: String): Bitmap {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
    val width = bitMatrix.width
    val height = bitMatrix.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap
}

private suspend fun saveQrAsPdf(context: android.content.Context, bitmap: Bitmap): File? = withContext(Dispatchers.IO) {
    val document = PDDocument()
    val page = PDPage()
    document.addPage(page)
    
    try {
        val image = JPEGFactory.createFromImage(document, bitmap, 0.75f)
        val contentStream = PDPageContentStream(document, page)
        
        // Center the QR code on the page
        val pageSize = page.mediaBox
        val x = (pageSize.width - 400) / 2
        val y = (pageSize.height - 400) / 2
        
        contentStream.drawImage(image, x, y, 400f, 400f)
        contentStream.close()
        
        val file = PdfUtils.getPublicDocumentsFile("QR_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out ->
            document.save(out)
        }
        return@withContext file
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    } finally {
        document.close()
    }
}

@Preview(showBackground = true)
@Composable
fun QrToPdfScreenPreview() {
    PdfeditorTheme {
        QrToPdfScreen(
            onBackClick = {},
            onResult = { _, _ -> }
        )
    }
}
