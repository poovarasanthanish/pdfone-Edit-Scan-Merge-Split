package com.villagefast.pdfeditor.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.pdf.PdfDocument
import androidx.pdf.SandboxedPdfLoader
import androidx.pdf.compose.PdfViewer
import androidx.pdf.compose.PdfViewerState
import com.villagefast.pdfeditor.ui.components.GlassTopAppBar
import com.villagefast.pdfeditor.ui.components.LiquidScaffold
import com.villagefast.pdfeditor.ui.components.SelectionGrid
import com.villagefast.pdfeditor.ui.theme.PaperPink
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(initialUri: Uri? = null, showAds: Boolean = true, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val accentColor = PaperPink
    
    var selectedUri by remember { mutableStateOf<Uri?>(initialUri) }
    var pdfDocument by remember { mutableStateOf<PdfDocument?>(null) }
    val pdfViewerState = remember { PdfViewerState() }
    var isLoading by remember { mutableStateOf(false) }
    
    val loader = remember { SandboxedPdfLoader(context) }

    fun loadPdf(uri: Uri) {
        selectedUri = uri
        isLoading = true
        scope.launch {
            try {
                // Pre-process URI to avoid Auth errors with remote providers (e.g. Google Drive)
                // during sandboxed loading.
                val localUri = com.villagefast.pdfeditor.utils.PdfUtils.localiseUri(context, uri)
                pdfDocument = loader.openDocument(localUri)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error loading PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null) {
            loadPdf(initialUri)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { loadPdf(it) }
    }

    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpPageInput by remember { mutableStateOf("") }

    LiquidScaffold(
        showBanner = showAds,
        topBar = {
            GlassTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (pdfDocument != null) "Ultra Viewer" else "PDF Viewer", fontSize = 16.sp, fontWeight = FontWeight.Black)
                        if (pdfDocument != null) {
                            Text("READ MODE ACTIVE", fontSize = 8.sp, fontWeight = FontWeight.Black, color = accentColor, letterSpacing = 1.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(20.dp))
                    }
                },
                actions = {
                    if (pdfDocument != null) {
                        IconButton(onClick = { showJumpDialog = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Jump to page")
                        }
                    }
                    IconButton(onClick = { launcher.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Open PDF")
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
            if (pdfDocument == null) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = accentColor)
                            Spacer(Modifier.height(16.dp))
                            Text("Initializing engine...", color = Color.Gray, fontSize = 12.sp)
                        }
                    } else {
                        SelectionGrid(
                            onSelect = { launcher.launch(arrayOf("application/pdf")) },
                            isDark = isDark,
                            icon = Icons.Default.Visibility,
                            title = "Tap to view",
                            subtitle = "ULTRA PDF READER",
                            accentColor = accentColor
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = accentColor)
                    }
                    PdfViewer(
                        pdfDocument = pdfDocument,
                        state = pdfViewerState,
                        modifier = Modifier.fillMaxSize().background(Color.Black)
                    )
                }
            }

            if (showJumpDialog) {
                ModalBottomSheet(onDismissRequest = { showJumpDialog = false }) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("JUMP TO PAGE", fontSize = 12.sp, fontWeight = FontWeight.Black, color = accentColor, letterSpacing = 1.sp)
                        Spacer(Modifier.height(24.dp))
                        
                        OutlinedTextField(
                            value = jumpPageInput,
                            onValueChange = { if (it.all { char -> char.isDigit() }) jumpPageInput = it },
                            label = { Text("Page Number (1 - ${pdfDocument?.pageCount ?: "?"})") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                        
                        Spacer(Modifier.height(24.dp))
                        
                        Button(
                            onClick = {
                                val page = jumpPageInput.toIntOrNull()
                                if (page != null && page > 0 && page <= (pdfDocument?.pageCount ?: 0)) {
                                    // PdfViewerState.scrollToPage might be what we want, but it's internal or missing in some versions
                                    // For now, we close the dialog.
                                    showJumpDialog = false
                                } else {
                                    Toast.makeText(context, "Invalid page number", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Text("GO TO PAGE", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}
