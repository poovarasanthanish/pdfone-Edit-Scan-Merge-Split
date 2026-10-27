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
import androidx.compose.material.icons.filled.*
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

enum class OrganizeMode {
    ORGANIZE,
    DELETE,
    ROTATE,
    REORDER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizePagesScreen(
    initialUri: Uri? = null,
    mode: OrganizeMode = OrganizeMode.ORGANIZE,
    showAds: Boolean = true,
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val accentColor = when(mode) {
        OrganizeMode.DELETE -> Color(0xFFF43F5E)
        OrganizeMode.ROTATE -> Color(0xFFFF9800)
        else -> Color(0xFF2979FF)
    }

    var currentState by remember { mutableStateOf(ToolState.SELECTING) }
    var selectedUri by remember { mutableStateOf(initialUri) }
    var outputUri by remember { mutableStateOf<Uri?>(null) }
    var unlockPassword by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var pageCount by remember { mutableIntStateOf(0) }
    var isFileLoading by remember { mutableStateOf(false) }
    var processingTime by remember { mutableStateOf("") }
    var fileToUnlock by remember { mutableStateOf<String?>(null) }

    // State for operations
    var pageRotations by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var pagesToDelete by remember { mutableStateOf<Set<Int>>(emptySet()) }

    val title = when (mode) {
        OrganizeMode.ORGANIZE -> "Organize Pages"
        OrganizeMode.DELETE -> "Delete Pages"
        OrganizeMode.ROTATE -> "Rotate Pages"
        OrganizeMode.REORDER -> "Reorder Pages"
    }

    val subtitle = when (mode) {
        OrganizeMode.DELETE -> "REMOVE SPECIFIC PAGES"
        OrganizeMode.ROTATE -> "FIX PAGE ORIENTATION"
        else -> "MANAGE DOCUMENT PAGES"
    }

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
                    val resultFile = performOrganizeOperation(
                        context,
                        selectedUri!!,
                        mode,
                        pageRotations,
                        pagesToDelete,
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
                            processingTime = String.format("%.1fs", (endTime - startTime) / 1000.0)
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
                            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Black)
                            Text(subtitle, fontSize = 8.sp, fontWeight = FontWeight.Black, color = accentColor, letterSpacing = 1.sp)
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
                LoadingStateView(accentColor, false, "Analyzing pages...")
            } else {
                when (currentState) {
                    ToolState.SELECTING -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            SelectionGrid(
                                onSelect = { pickLauncher.launch("application/pdf") },
                                isDark = isDark,
                                icon = when(mode) {
                                    OrganizeMode.ROTATE -> Icons.Default.Refresh
                                    OrganizeMode.DELETE -> Icons.Default.Delete
                                    else -> Icons.Default.GridView
                                },
                                title = "Tap to enter file",
                                subtitle = subtitle,
                                accentColor = accentColor
                            )
                        }
                    }
                    ToolState.CONFIGURING -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Instruction Banner
                            Surface(
                                color = accentColor.copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, null, tint = accentColor, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        when(mode) {
                                            OrganizeMode.ROTATE -> "Tap page to rotate 90°"
                                            OrganizeMode.DELETE -> "Select pages to remove"
                                            else -> "Configure page layout"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor
                                    )
                                }
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                PdfGridPreview(
                                    uri = selectedUri!!,
                                    pageCount = pageCount,
                                    password = unlockPassword.ifEmpty { null },
                                    pageRotations = pageRotations,
                                    selectedPages = pagesToDelete,
                                    mode = when(mode) {
                                        OrganizeMode.ROTATE -> PreviewMode.ROTATE
                                        OrganizeMode.DELETE -> PreviewMode.DELETE
                                        else -> PreviewMode.GRID
                                    },
                                    onPageClick = { index ->
                                        when(mode) {
                                            OrganizeMode.ROTATE -> {
                                                val current = pageRotations[index] ?: 0
                                                pageRotations = pageRotations + (index to (current + 90) % 360)
                                            }
                                            OrganizeMode.DELETE -> {
                                                pagesToDelete = if (pagesToDelete.contains(index)) pagesToDelete - index else pagesToDelete + index
                                            }
                                            else -> {}
                                        }
                                    },
                                    accentColor = accentColor
                                )
                            }

                            // Action Bar
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                tonalElevation = 8.dp,
                                shadowElevation = 16.dp
                            ) {
                                Column(Modifier.padding(16.dp).navigationBarsPadding()) {
                                    if (mode == OrganizeMode.ROTATE) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(
                                                "${pageRotations.size} / $pageCount PAGES MODIFIED",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.Gray,
                                                letterSpacing = 1.sp
                                            )
                                            TextButton(
                                                onClick = {
                                                    pageRotations = (0 until pageCount).associateWith { idx ->
                                                        ((pageRotations[idx] ?: 0) + 90) % 360
                                                    }
                                                },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("ROTATE ALL 90°", fontSize = 10.sp, fontWeight = FontWeight.Black, color = accentColor)
                                            }
                                        }
                                    } else if (mode == OrganizeMode.DELETE) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(
                                                "${pagesToDelete.size} / $pageCount PAGES SELECTED",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (pagesToDelete.isEmpty()) Color.Gray else Color.Red,
                                                letterSpacing = 1.sp
                                            )
                                            Row {
                                                TextButton(onClick = { pagesToDelete = (0 until pageCount).toSet() }) {
                                                    Text("SELECT ALL", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                                                }
                                                TextButton(onClick = { pagesToDelete = emptySet() }) {
                                                    Text("CLEAR", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    Button(
                                        onClick = { 
                                            val outName = fileName.replace(".pdf", "") + when(mode) {
                                                OrganizeMode.ROTATE -> "-rotated.pdf"
                                                OrganizeMode.DELETE -> "-edited.pdf"
                                                else -> "-organized.pdf"
                                            }
                                            saveLauncher.launch(outName) 
                                        },
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                        enabled = when(mode) {
                                            OrganizeMode.DELETE -> pagesToDelete.size < pageCount
                                            else -> true
                                        }
                                    ) {
                                        Text(
                                            when(mode) {
                                                OrganizeMode.ROTATE -> "SAVE ROTATED PDF"
                                                OrganizeMode.DELETE -> "DELETE SELECTED PAGES"
                                                else -> "SAVE CHANGES"
                                            },
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                    ToolState.PROCESSING -> {
                        LoadingStateView(accentColor, true, "Processing document...")
                    }
                    ToolState.SUCCESS -> {
                        SuccessView(
                            message = when(mode) {
                                OrganizeMode.ROTATE -> "Rotation Complete"
                                OrganizeMode.DELETE -> "Pages Deleted"
                                else -> "Task Complete"
                            },
                            subMessage = "Document saved successfully",
                            processingTime = processingTime,
                            onDone = onBackClick,
                            onProcessMore = {
                                selectedUri = null
                                currentState = ToolState.SELECTING
                                pageRotations = emptyMap()
                                pagesToDelete = emptySet()
                            },
                            onPreview = {
                                outputUri?.let { uri -> onResult(uri, title) }
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

private suspend fun performOrganizeOperation(
    context: android.content.Context,
    uri: Uri,
    mode: OrganizeMode,
    rotations: Map<Int, Int>,
    toDelete: Set<Int>,
    password: String?
): File? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val sourceDoc = if (password != null) PDDocument.load(inputStream, password) else PDDocument.load(inputStream)
            val targetDoc = PDDocument()
            
            for (i in 0 until sourceDoc.numberOfPages) {
                if (mode == OrganizeMode.DELETE && toDelete.contains(i)) continue
                
                val page = sourceDoc.getPage(i)
                val importedPage = targetDoc.importPage(page)
                
                if (mode == OrganizeMode.ROTATE) {
                    val extraRotation = rotations[i] ?: 0
                    if (extraRotation != 0) {
                        importedPage.rotation = (importedPage.rotation + extraRotation) % 360
                    }
                }
            }
            
            val prefix = when (mode) {
                OrganizeMode.ROTATE -> "Rotated"
                OrganizeMode.DELETE -> "Edited"
                else -> "Organized"
            }
            
            val file = PdfUtils.getPublicDocumentsFile("${prefix}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { out ->
                targetDoc.save(out)
            }
            sourceDoc.close()
            targetDoc.close()
            return@withContext file
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}
