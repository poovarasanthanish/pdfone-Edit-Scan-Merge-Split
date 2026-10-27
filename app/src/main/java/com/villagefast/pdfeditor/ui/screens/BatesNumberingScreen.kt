package com.villagefast.pdfeditor.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.villagefast.pdfeditor.R
import com.villagefast.pdfeditor.ui.components.*
import com.villagefast.pdfeditor.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class BatesPosition {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatesNumberingScreen(
    initialUri: Uri? = null,
    showAds: Boolean = true,
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val accentColor = Color(0xFF673AB7)

    var currentState by remember { mutableStateOf(ToolState.SELECTING) }
    var selectedUri by remember { mutableStateOf(initialUri) }
    var outputUri by remember { mutableStateOf<Uri?>(null) }
    var unlockPassword by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var pageCount by remember { mutableIntStateOf(0) }
    var isFileLoading by remember { mutableStateOf(false) }
    var processingTime by remember { mutableStateOf("") }
    var fileToUnlock by remember { mutableStateOf<String?>(null) }

    // Bates Configuration
    var prefix by remember { mutableStateOf("DOC-") }
    var startNumber by remember { mutableStateOf("1") }
    var digits by remember { mutableStateOf("6") }
    var position by remember { mutableStateOf(BatesPosition.BOTTOM_RIGHT) }
    var fontSize by remember { mutableFloatStateOf(10f) }

    fun handleFileSelection(uri: Uri) {
        selectedUri = uri
        val details = PdfUtils.getUriDetails(context, uri)
        fileName = details.name
        isFileLoading = true
        scope.launch(Dispatchers.IO) {
            val isEnc = PdfUtils.checkIsEncryptedLocal(context, uri)
            withContext(Dispatchers.Main) {
                if (isEnc) {
                    fileToUnlock = fileName
                    isFileLoading = false
                } else {
                    val count = PdfUtils.getPageCount(context, uri)
                    pageCount = count
                    currentState = ToolState.CONFIGURING
                    isFileLoading = false
                }
            }
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null) {
            handleFileSelection(initialUri)
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleFileSelection(it) }
    }

    fun startProcessing() {
        currentState = ToolState.PROCESSING
        val startTime = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            try {
                val startNum = startNumber.toIntOrNull() ?: 1
                val numDigits = digits.toIntOrNull() ?: 6
                
                val resultFile = performBatesNumbering(
                    context,
                    selectedUri!!,
                    prefix,
                    startNum,
                    numDigits,
                    position,
                    fontSize,
                    unlockPassword.ifEmpty { null }
                )

                if (resultFile != null) {
                    val endTime = System.currentTimeMillis()
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        resultFile
                    )
                    withContext(Dispatchers.Main) {
                        processingTime = java.util.Locale.US.let { locale ->
                            String.format(locale, "%.1fs", (endTime - startTime) / 1000.0)
                        }
                        outputUri = uri
                        currentState = ToolState.SUCCESS
                    }
                } else {
                    throw Exception("Failed to process PDF")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    currentState = ToolState.CONFIGURING
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
                            Text(stringResource(R.string.bates_numbering), fontSize = 16.sp, fontWeight = FontWeight.Black)
                            Text("SEQUENTIAL LEGAL IDENTIFICATION", fontSize = 8.sp, fontWeight = FontWeight.Black, color = accentColor, letterSpacing = 1.sp)
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
                LoadingStateView(accentColor, false, "Preparing document...")
            } else {
                when (currentState) {
                    ToolState.SELECTING -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            SelectionGrid(
                                onSelect = { pickLauncher.launch("application/pdf") },
                                isDark = isDark,
                                icon = Icons.Default.Filter1,
                                title = "Tap to enter file",
                                subtitle = "LEGAL DOCUMENT TRACKING",
                                accentColor = accentColor
                            )
                        }
                    }
                    ToolState.CONFIGURING -> {
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            // Live Preview Header
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .background(accentColor.copy(alpha = 0.05f)),
                                contentAlignment = Alignment.Center
                            ) {
                                BatesPreview(
                                    uri = selectedUri,
                                    password = unlockPassword.ifEmpty { null },
                                    prefix = prefix,
                                    startNum = startNumber.toIntOrNull() ?: 1,
                                    numDigits = digits.toIntOrNull() ?: 6,
                                    position = position,
                                    fontSize = fontSize
                                )
                            }

                            // Configuration Card
                            Surface(
                                modifier = Modifier.padding(16.dp),
                                shape = RoundedCornerShape(24.dp),
                                color = if (isDark) Color(0xFF0A0A0A) else Color.White,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.1f))
                            ) {
                                Column(Modifier.padding(24.dp)) {
                                    Text("Bates Settings", fontSize = 14.sp, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.height(16.dp))

                                    OutlinedTextField(
                                        value = prefix,
                                        onValueChange = { prefix = it },
                                        label = { Text("Prefix (e.g. ABC-)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    Spacer(Modifier.height(12.dp))

                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedTextField(
                                            value = startNumber,
                                            onValueChange = { if (it.all { c -> c.isDigit() }) startNumber = it },
                                            label = { Text("Start No.") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        OutlinedTextField(
                                            value = digits,
                                            onValueChange = { if (it.all { c -> c.isDigit() }) digits = it },
                                            label = { Text("Digits") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }

                                    Spacer(Modifier.height(24.dp))
                                    Text("Position", fontSize = 14.sp, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.height(12.dp))
                                    
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        BatesPosition.entries.forEach { pos ->
                                            FilterChip(
                                                selected = position == pos,
                                                onClick = { position = pos },
                                                label = { Text(pos.name.replace("_", " "), fontSize = 10.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = accentColor,
                                                    selectedLabelColor = Color.White
                                                )
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(24.dp))
                                    Text("Font Size: ${fontSize.toInt()} pt", fontSize = 14.sp, fontWeight = FontWeight.Black)
                                    Slider(
                                        value = fontSize,
                                        onValueChange = { fontSize = it },
                                        valueRange = 6f..24f,
                                        colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                                    )

                                    Spacer(Modifier.height(32.dp))

                                    Button(
                                        onClick = { startProcessing() },
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                    ) {
                                        Icon(Icons.Default.Done, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("APPLY BATES NUMBERING", fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                    ToolState.PROCESSING -> {
                        LoadingStateView(accentColor, true, "Stamping numbers...")
                    }
                    ToolState.SUCCESS -> {
                        SuccessView(
                            message = "Numbering Complete",
                            subMessage = "Bates numbers applied successfully",
                            processingTime = processingTime,
                            onDone = onBackClick,
                            onProcessMore = {
                                selectedUri = null
                                currentState = ToolState.SELECTING
                            },
                            onPreview = {
                                outputUri?.let { uri -> onResult(uri, "Bates Numbering") }
                            },
                            accentColor = accentColor
                        )
                    }
                    else -> {}
                }
            }

            if (fileToUnlock != null) {
                LockedFilePrompt(
                    fileName = fileToUnlock!!,
                    onDismiss = { fileToUnlock = null; selectedUri = null; currentState = ToolState.SELECTING },
                    onUnlocked = { pass ->
                        isFileLoading = true
                        scope.launch(Dispatchers.IO) {
                            val count = PdfUtils.getPageCount(context, selectedUri!!, pass)
                            withContext(Dispatchers.Main) {
                                if (count > 0) {
                                    unlockPassword = pass
                                    pageCount = count
                                    currentState = ToolState.CONFIGURING
                                    fileToUnlock = null
                                } else {
                                    Toast.makeText(context, "Invalid Password", Toast.LENGTH_SHORT).show()
                                }
                                isFileLoading = false
                            }
                        }
                    },
                    accentColor = accentColor,
                    isLoading = isFileLoading
                )
            }
        }
    }
}

@Composable
fun BatesPreview(
    uri: Uri?,
    password: String?,
    prefix: String,
    startNum: Int,
    numDigits: Int,
    position: BatesPosition,
    fontSize: Float
) {
    val context = LocalContext.current
    var thumbnail by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(uri) {
        if (uri != null) {
            thumbnail = PdfUtils.renderPage(context, uri, 0, 0.5f, password)
        }
    }

    val batesText = prefix + startNum.toString().padStart(numDigits, '0')

    Surface(
        modifier = Modifier
            .padding(16.dp)
            .height(180.dp)
            .aspectRatio(0.707f),
        shape = RoundedCornerShape(4.dp),
        shadowElevation = 8.dp,
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.LightGray)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                }
            }

            // Preview Stamping
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = when (position) {
                    BatesPosition.TOP_LEFT -> Alignment.TopStart
                    BatesPosition.TOP_CENTER -> Alignment.TopCenter
                    BatesPosition.TOP_RIGHT -> Alignment.TopEnd
                    BatesPosition.BOTTOM_LEFT -> Alignment.BottomStart
                    BatesPosition.BOTTOM_CENTER -> Alignment.BottomCenter
                    BatesPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                }
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = batesText,
                        color = Color.White,
                        fontSize = (fontSize * 0.5f).sp, // Scale font for preview
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private suspend fun performBatesNumbering(
    context: android.content.Context,
    uri: Uri,
    prefix: String,
    startNum: Int,
    numDigits: Int,
    position: BatesPosition,
    fontSize: Float,
    password: String?
): File? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val doc = if (password != null) PDDocument.load(inputStream, password) else PDDocument.load(inputStream)
            
            for (i in 0 until doc.numberOfPages) {
                val page = doc.getPage(i)
                val pageSize = page.mediaBox
                val batesNum = prefix + (startNum + i).toString().padStart(numDigits, '0')
                
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, fontSize)
                    
                    val textWidth = (PDType1Font.HELVETICA_BOLD.getStringWidth(batesNum) / 1000f) * fontSize
                    val margin = 30f
                    
                    val (x, y) = when (position) {
                        BatesPosition.TOP_LEFT -> margin to (pageSize.height - margin - fontSize)
                        BatesPosition.TOP_CENTER -> (pageSize.width - textWidth) / 2 to (pageSize.height - margin - fontSize)
                        BatesPosition.TOP_RIGHT -> (pageSize.width - textWidth - margin) to (pageSize.height - margin - fontSize)
                        BatesPosition.BOTTOM_LEFT -> margin to margin
                        BatesPosition.BOTTOM_CENTER -> (pageSize.width - textWidth) / 2 to margin
                        BatesPosition.BOTTOM_RIGHT -> (pageSize.width - textWidth - margin) to margin
                    }
                    
                    cs.newLineAtOffset(x, y)
                    cs.showText(batesNum)
                    cs.endText()
                }
            }
            
            val file = PdfUtils.getPublicDocumentsFile("Bates_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { out ->
                doc.save(out)
            }
            doc.close()
            return@withContext file
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}
