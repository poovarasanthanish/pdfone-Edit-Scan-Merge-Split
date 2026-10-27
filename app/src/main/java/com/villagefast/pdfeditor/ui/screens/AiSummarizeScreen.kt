package com.villagefast.pdfeditor.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.client.generativeai.GenerativeModel
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.villagefast.pdfeditor.ui.components.GlassTopAppBar
import com.villagefast.pdfeditor.ui.components.LiquidScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.tooling.preview.Preview
import com.villagefast.pdfeditor.ui.theme.PdfeditorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSummarizeScreen(showAds: Boolean = true, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedPdf by remember { mutableStateOf<Uri?>(null) }
    var summaryText by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }

    val generativeModel = remember {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = "YOUR_API_KEY_HERE"
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        selectedPdf = uri
        uri?.let {
            isAnalyzing = true
            scope.launch {
                val fullText = extractTextForSummary(context, it)
                summaryText = generateSummary(generativeModel, fullText)
                isAnalyzing = false
            }
        }
    }

    LiquidScaffold(
        showBanner = showAds,
        topBar = {
            GlassTopAppBar(
                title = { Text("AI Summarize PDF") },
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
                .padding(16.dp)
        ) {
            if (selectedPdf == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(onClick = { launcher.launch(arrayOf("application/pdf")) }) {
                        Text("Select PDF to Summarize")
                    }
                }
            } else {
                Text("File: ${selectedPdf!!.lastPathSegment}", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isAnalyzing) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("AI is reading and summarizing...")
                        }
                    }
                } else {
                    Card(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Box(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                            Text(text = summaryText)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { 
                            selectedPdf = null
                            summaryText = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Summarize Another")
                    }
                }
            }
        }
    }
}

private suspend fun extractTextForSummary(context: android.content.Context, uri: Uri): String = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            document.close()
            return@withContext text
        }
    } catch (e: Exception) {
        return@withContext ""
    }
}

private suspend fun generateSummary(model: GenerativeModel, text: String): String {
    return try {
        val prompt = "Provide a concise summary of the following document text, highlighting key points and main takeaways:\n\n$text"
        val response = model.generateContent(prompt)
        response.text ?: "I couldn't generate a summary."
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}

@Preview(showBackground = true)
@Composable
fun AiSummarizeScreenPreview() {
    PdfeditorTheme {
        AiSummarizeScreen(
            onBackClick = {}
        )
    }
}
