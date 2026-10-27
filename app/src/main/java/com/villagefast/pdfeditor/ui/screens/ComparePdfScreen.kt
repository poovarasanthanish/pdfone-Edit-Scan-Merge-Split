package com.villagefast.pdfeditor.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.villagefast.pdfeditor.ui.components.*
import com.villagefast.pdfeditor.ui.theme.PaperPink
import com.villagefast.pdfeditor.utils.PdfUtils
import kotlinx.coroutines.launch
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparePdfScreen(showAds: Boolean = true, onBackClick: () -> Unit) {
    val context = LocalContext.current
    
    var uriA by remember { mutableStateOf<Uri?>(null) }
    var uriB by remember { mutableStateOf<Uri?>(null) }
    var nameA by remember { mutableStateOf("") }
    var nameB by remember { mutableStateOf("") }
    
    var isSynchronizedMode by remember { mutableStateOf(false) }

    val pickALauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            uriA = it
            nameA = PdfUtils.getFileName(context, it) ?: "File A"
        }
    }

    val pickBLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            uriB = it
            nameB = PdfUtils.getFileName(context, it) ?: "File B"
        }
    }

    LiquidScaffold(
        showBanner = showAds,
        topBar = {
            GlassTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isSynchronizedMode) "Synchronized View" else "Compare PDF", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        if (isSynchronizedMode) {
                            Text("BEFORE (LEFT) VS AFTER (RIGHT)", fontSize = 8.sp, fontWeight = FontWeight.Black, color = PaperPink, letterSpacing = 1.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (isSynchronizedMode) isSynchronizedMode = false else onBackClick() },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            if (isSynchronizedMode) Icons.Default.Edit else Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (!isSynchronizedMode) {
                // Selection UI
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Select two documents to compare side-by-side",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CompareFileCard(
                            label = "ORIGINAL",
                            name = nameA,
                            isSelected = uriA != null,
                            color = PaperPink,
                            modifier = Modifier.weight(1f),
                            onClick = { pickALauncher.launch(arrayOf("application/pdf")) }
                        )
                        CompareFileCard(
                            label = "MODIFIED",
                            name = nameB,
                            isSelected = uriB != null,
                            color = Color(0xFF00BCD4),
                            modifier = Modifier.weight(1f),
                            onClick = { pickBLauncher.launch(arrayOf("application/pdf")) }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    Button(
                        onClick = { isSynchronizedMode = true },
                        enabled = uriA != null && uriB != null,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PaperPink)
                    ) {
                        Icon(Icons.Default.Compare, null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Start Comparison", fontWeight = FontWeight.Black)
                    }
                }
            } else {
                // Synchronized Comparison View
                ComparisonViewer(
                    uriA = uriA!!,
                    uriB = uriB!!
                )
            }
        }
    }
}
