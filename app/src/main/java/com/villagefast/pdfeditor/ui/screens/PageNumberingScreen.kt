package com.villagefast.pdfeditor.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.villagefast.pdfeditor.ui.components.*
import com.villagefast.pdfeditor.ui.theme.PaperPink
import com.villagefast.pdfeditor.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageNumberingScreen(
    initialUri: Uri? = null,
    showAds: Boolean = false,
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val accentColor = PaperPink

    var currentState by remember { mutableStateOf(ToolState.SELECTING) }
    var selectedUri by remember { mutableStateOf(initialUri) }
    var outputUri by remember { mutableStateOf<Uri?>(null) }
    var unlockPassword by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var pageCount by remember { mutableIntStateOf(0) }
    var isFileLoading by remember { mutableStateOf(false) }
    var processingTime by remember { mutableStateOf("") }
    var fileToUnlock by remember { mutableStateOf<String?>(null) }

    // Settings
    var position by remember { mutableStateOf("Bottom Right") }
    var fontSize by remember { mutableFloatStateOf(14f) }
    var format by remember { mutableStateOf("Page {n}") }
    var numberColor by remember { mutableStateOf(Color.Black) }
    var showPreviewOverlay by remember { mutableStateOf(true) }
    
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPositionSheet by remember { mutableStateOf(false) }
    var showColorSheet by remember { mutableStateOf(false) }

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

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { saveUri ->
            currentState = ToolState.PROCESSING
            val startTime = System.currentTimeMillis()
            scope.launch(Dispatchers.IO) {
                try {
                    val resultFile = addPageNumbers(
                        context,
                        selectedUri!!,
                        position,
                        format,
                        fontSize,
                        numberColor,
                        unlockPassword.ifEmpty { null }
                    )
                    
                    if (resultFile != null) {
                        context.contentResolver.openOutputStream(saveUri)?.use { out ->
                            resultFile.inputStream().use { ins ->
                                ins.copyTo(out)
                            }
                        }
                        val endTime = System.currentTimeMillis()
                        withContext(Dispatchers.Main) {
                            processingTime = String.format(Locale.US, "%.1fs", (endTime - startTime) / 1000.0)
                            outputUri = saveUri
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
    }

    LiquidScaffold(
        showBanner = showAds,
        topBar = {
            if (currentState != ToolState.SUCCESS && currentState != ToolState.PROCESSING) {
                GlassTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Page Numbers", fontSize = 16.sp, fontWeight = FontWeight.Black)
                            Text("CUSTOM PAGINATION", fontSize = 8.sp, fontWeight = FontWeight.Black, color = accentColor, letterSpacing = 1.sp)
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
                LoadingStateView(accentColor, false, "Preparing document...")
            } else {
                when (currentState) {
                    ToolState.SELECTING -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            SelectionGrid(
                                onSelect = { pickLauncher.launch("application/pdf") },
                                isDark = isDark,
                                icon = Icons.Filled.FormatListNumbered,
                                title = "Tap to enter file",
                                subtitle = "ADD PAGE NUMBERS",
                                accentColor = accentColor
                            )
                        }
                    }
                    ToolState.CONFIGURING -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.height(12.dp))
                            
                            Box(modifier = Modifier.weight(1f)) {
                                val numberOverlay: @Composable (BoxScope.(Int) -> Unit) = { index ->
                                    if (showPreviewOverlay) {
                                        val text = format.replace("{n}", (index + 1).toString()).replace("{total}", pageCount.toString())
                                        Box(Modifier.fillMaxSize(), contentAlignment = when {
                                            position.contains("Bottom") && position.contains("Left") -> Alignment.BottomStart
                                            position.contains("Bottom") && position.contains("Center") -> Alignment.BottomCenter
                                            position.contains("Bottom") && position.contains("Right") -> Alignment.BottomEnd
                                            position.contains("Top") && position.contains("Left") -> Alignment.TopStart
                                            position.contains("Top") && position.contains("Center") -> Alignment.TopCenter
                                            position.contains("Top") && position.contains("Right") -> Alignment.TopEnd
                                            else -> Alignment.BottomEnd
                                        }) {
                                            Text(
                                                text = text,
                                                color = if (numberColor == Color.White && !isDark) Color.LightGray else numberColor,
                                                fontSize = (fontSize / 2).sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(6.dp)
                                            )
                                        }
                                    }
                                }

                                PdfGridPreview(
                                    uri = selectedUri!!,
                                    pageCount = pageCount,
                                    password = unlockPassword.ifEmpty { null },
                                    accentColor = accentColor,
                                    itemOverlay = numberOverlay,
                                    onPageClick = {}
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Surface(
                                onClick = { showSettingsSheet = true },
                                color = accentColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.width(80.dp).height(24.dp)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.KeyboardArrowUp, null, tint = accentColor, modifier = Modifier.size(20.dp))
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Button(
                                onClick = { saveLauncher.launch(fileName.replace(".pdf", "") + "-numbered.pdf") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).height(60.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White)
                            ) {
                                Text("SAVE DOCUMENT", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    ToolState.PROCESSING -> {
                        LoadingStateView(accentColor, true, "Applying numbering...")
                    }
                    ToolState.SUCCESS -> {
                        SuccessView(
                            message = "Numbering Complete",
                            subMessage = "Page numbers added successfully",
                            processingTime = processingTime,
                            onDone = onBackClick,
                            onProcessMore = {
                                selectedUri = null
                                currentState = ToolState.SELECTING
                            },
                            onPreview = {
                                outputUri?.let { uri -> onResult(uri, "Page Numbering") }
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

            if (showSettingsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSettingsSheet = false }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp).navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("PAGE NUMBER SETTINGS", fontSize = 12.sp, fontWeight = FontWeight.Black, color = accentColor, letterSpacing = 1.sp)
                        Spacer(Modifier.height(24.dp))

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("LIVE PREVIEW", fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                            Switch(checked = showPreviewOverlay, onCheckedChange = { showPreviewOverlay = it }, colors = SwitchDefaults.colors(checkedTrackColor = accentColor))
                        }
                        
                        Spacer(Modifier.height(16.dp))

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = format,
                                onValueChange = { format = it },
                                label = { Text("Format ({n})", fontSize = 9.sp) },
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, focusedLabelColor = accentColor)
                            )
                            
                            Surface(
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f),
                                onClick = { showColorSheet = true }
                            ) {
                                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                    Box(Modifier.size(16.dp).clip(CircleShape).background(numberColor).border(1.dp, Color.Gray.copy(0.3f), CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                    Text("COLOR", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("FONT SIZE: ${fontSize.toInt()} PT", fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                            Slider(
                                value = fontSize,
                                onValueChange = { fontSize = it },
                                valueRange = 8f..36f,
                                modifier = Modifier.weight(2f),
                                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f),
                            onClick = { showPositionSheet = true }
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Place, null, tint = accentColor, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text("POSITION", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                                    Text(position.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Filled.ArrowDropDown, null, tint = Color.Gray)
                            }
                        }
                    }
                }
            }

            if (showPositionSheet) {
                ModalBottomSheet(onDismissRequest = { showPositionSheet = false }) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 32.dp).navigationBarsPadding()) {
                        Text("SELECT POSITION", modifier = Modifier.padding(16.dp), fontSize = 12.sp, fontWeight = FontWeight.Black, color = accentColor)
                        listOf("Top Left", "Top Center", "Top Right", "Bottom Left", "Bottom Center", "Bottom Right").forEach { pos ->
                            ListItem(
                                headlineContent = { Text(pos, fontWeight = if (position == pos) FontWeight.Bold else FontWeight.Normal) },
                                leadingContent = { RadioButton(selected = position == pos, onClick = null) },
                                modifier = Modifier.clickable { position = pos; showPositionSheet = false }
                            )
                        }
                    }
                }
            }

            if (showColorSheet) {
                ModalBottomSheet(onDismissRequest = { showColorSheet = false }) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 32.dp).navigationBarsPadding()) {
                        Text("SELECT COLOR", modifier = Modifier.padding(16.dp), fontSize = 12.sp, fontWeight = FontWeight.Black, color = accentColor)
                        val colors = listOf(Color.Black, Color.White, Color.Red, Color.Blue, Color(0xFF8B5CF6), Color(0xFF10B981))
                        val colorNames = listOf("Black", "White", "Red", "Blue", "Indigo", "Emerald")
                        colors.forEachIndexed { idx, col ->
                            ListItem(
                                headlineContent = { Text(colorNames[idx], fontWeight = if (numberColor == col) FontWeight.Bold else FontWeight.Normal) },
                                leadingContent = { Box(Modifier.size(24.dp).clip(CircleShape).background(col).border(1.dp, Color.Gray.copy(0.3f), CircleShape)) },
                                modifier = Modifier.clickable { numberColor = col; showColorSheet = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun addPageNumbers(
    context: android.content.Context,
    uri: Uri,
    position: String,
    format: String,
    fontSize: Float,
    numberColor: Color,
    password: String?
): File? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val document = if (password != null) PDDocument.load(inputStream, password) else PDDocument.load(inputStream)
            val totalPages = document.numberOfPages
            val font = PDType1Font.HELVETICA_BOLD
            
            for (i in 0 until totalPages) {
                val page = document.getPage(i)
                PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    val rect = page.mediaBox
                    val text = format.replace("{n}", (i + 1).toString()).replace("{total}", totalPages.toString())
                    val textWidth = font.getStringWidth(text) / 1000 * fontSize
                    val margin = 30f

                    val x = when {
                        position.contains("Left") -> margin
                        position.contains("Center") -> (rect.width - textWidth) / 2
                        else -> rect.width - textWidth - margin
                    }
                    val y = when {
                        position.contains("Top") -> rect.height - margin - fontSize
                        else -> margin
                    }

                    cs.beginText()
                    cs.setFont(font, fontSize)
                    cs.setNonStrokingColor(
                        (numberColor.red * 255).toInt(),
                        (numberColor.green * 255).toInt(),
                        (numberColor.blue * 255).toInt()
                    )
                    cs.newLineAtOffset(x, y)
                    cs.showText(text)
                    cs.endText()
                }
            }
            
            val fileName = "Numbered_${System.currentTimeMillis()}.pdf"
            val outputFile = File(context.cacheDir, fileName)
            FileOutputStream(outputFile).use { out ->
                document.save(out)
            }
            document.close()

            // Try to move to public documents if possible
            try {
                val publicFile = PdfUtils.getPublicDocumentsFile(fileName)
                val parent = publicFile.parentFile
                if (parent != null && (parent.exists() || parent.mkdirs())) {
                    outputFile.copyTo(publicFile, overwrite = true)
                    return@withContext publicFile
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return@withContext outputFile
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}
