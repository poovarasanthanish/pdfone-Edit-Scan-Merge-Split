package com.villagefast.pdfeditor.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.villagefast.pdfeditor.ui.components.*
import com.villagefast.pdfeditor.ui.theme.PaperPink
import com.villagefast.pdfeditor.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairPdfScreen(
    showAds: Boolean = false,
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val accentColor = PaperPink

    var currentState by remember { mutableStateOf(ToolState.SELECTING) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var outputUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var pageCount by remember { mutableIntStateOf(0) }
    var isFileLoading by remember { mutableStateOf(false) }
    var processingTime by remember { mutableStateOf("") }

    fun handleFileSelection(uri: Uri) {
        selectedUri = uri
        val details = PdfUtils.getUriDetails(context, uri)
        fileName = details.name
        isFileLoading = true
        scope.launch(Dispatchers.IO) {
            val count = PdfUtils.getPageCount(context, uri)
            withContext(Dispatchers.Main) {
                pageCount = count
                currentState = ToolState.CONFIGURING
                isFileLoading = false
            }
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleFileSelection(it) }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { saveUri ->
            currentState = ToolState.PROCESSING
            val startTime = System.currentTimeMillis()
            scope.launch(Dispatchers.IO) {
                try {
                    val resultFile = repairPdf(context, selectedUri!!)
                    if (resultFile != null) {
                        context.contentResolver.openOutputStream(saveUri)?.use { out ->
                            resultFile.inputStream().use { ins ->
                                ins.copyTo(out)
                            }
                        }
                        val endTime = System.currentTimeMillis()
                        withContext(Dispatchers.Main) {
                            processingTime = String.format("%.1fs", (endTime - startTime) / 1000.0)
                            outputUri = saveUri
                            currentState = ToolState.SUCCESS
                        }
                    } else {
                        throw Exception("Failed to repair PDF")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        currentState = ToolState.CONFIGURING
                    }
                }
            }
        }
    }

    LiquidScaffold(
        showBanner = showAds,
        topBar = {
            if (currentState != ToolState.SUCCESS && currentState != ToolState.PROCESSING) {
                GlassTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Repair PDF", fontSize = 16.sp, fontWeight = FontWeight.Black)
                            Text("REBUILD STRUCTURE", fontSize = 8.sp, fontWeight = FontWeight.Black, color = accentColor, letterSpacing = 1.sp)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(22.dp))
                        }
                    },
                    actions = {
                        if (selectedUri != null && currentState == ToolState.CONFIGURING) {
                            TextButton(onClick = { selectedUri = null; currentState = ToolState.SELECTING }) {
                                Text("CHANGE", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isFileLoading) {
                LoadingStateView(accentColor, false, "Analyzing file...")
            } else {
                when (currentState) {
                    ToolState.SELECTING -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            SelectionGrid(
                                onSelect = { pickLauncher.launch("application/pdf") },
                                isDark = isDark,
                                icon = Icons.Filled.Build,
                                title = "Tap to enter file",
                                subtitle = "REPAIR CORRUPTED PDF",
                                accentColor = accentColor
                            )
                        }
                    }
                    ToolState.CONFIGURING -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.height(48.dp))
                            
                            ToolPdfPreview(
                                uri = selectedUri!!,
                                pageCount = pageCount,
                                accentColor = accentColor
                            )

                            Spacer(Modifier.height(32.dp))
                            
                            Text(
                                "This tool attempts to fix structural errors in the PDF by rebuilding its cross-reference table.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(Modifier.weight(1f))

                            Button(
                                onClick = { saveLauncher.launch(fileName.replace(".pdf", "") + "-repaired.pdf") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp).height(60.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White)
                            ) {
                                Text("REPAIR & SAVE", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    ToolState.PROCESSING -> {
                        LoadingStateView(accentColor, true, "Rebuilding index...")
                    }
                    ToolState.SUCCESS -> {
                        SuccessView(
                            message = "Repair Complete",
                            subMessage = "File structure rebuilt successfully",
                            processingTime = processingTime,
                            onDone = onBackClick,
                            onProcessMore = {
                                selectedUri = null
                                currentState = ToolState.SELECTING
                            },
                            onPreview = {
                                outputUri?.let { uri -> onResult(uri, "Repair") }
                            },
                            accentColor = accentColor
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

private suspend fun repairPdf(context: android.content.Context, uri: Uri): File? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val document = PDDocument.load(inputStream)
            val file = PdfUtils.getPublicDocumentsFile("Repaired_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { out -> document.save(out) }
            document.close()
            return@withContext file
        }
    } catch (e: Exception) {
        return@withContext null
    }
}
