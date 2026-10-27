package com.villagefast.pdfeditor.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.villagefast.pdfeditor.R
import com.villagefast.pdfeditor.data.db.AppDatabase
import com.villagefast.pdfeditor.data.db.RecentFile
import com.villagefast.pdfeditor.ui.components.GlassCard
import com.villagefast.pdfeditor.ui.components.GlassTopAppBar
import com.villagefast.pdfeditor.ui.components.LiquidScaffold
import com.villagefast.pdfeditor.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    fileUri: Uri,
    operationName: String,
    showAds: Boolean,
    onBackToHome: () -> Unit
) {
    val context = LocalContext.current
    val fileName = PdfUtils.getFileName(context, fileUri) ?: "Result"
    val isImage = fileName.lowercase().let { it.endsWith(".jpg") || it.endsWith(".png") || it.endsWith(".webp") || it.endsWith(".jpeg") }
    val isZip = fileName.lowercase().endsWith(".zip")

    LaunchedEffect(fileUri) {
        if (!isImage && !isZip) {
            withContext(Dispatchers.IO) {
                // Determine absolute path if possible, or use URI string
                val path = if (fileUri.scheme == "file") {
                    fileUri.path
                } else {
                    // For content URIs, we might not have a direct file path that the app can use later
                    fileUri.toString()
                }
                
                if (path != null) {
                    AppDatabase.getDatabase(context).recentFileDao().insert(
                        RecentFile(path, fileName)
                    )
                }
            }
        }
    }

    LiquidScaffold(
        showBanner = showAds && com.villagefast.pdfeditor.data.RemoteConfigManager.showBannerAds(),
        topBar = {
            GlassTopAppBar(
                title = { Text(stringResource(R.string.result), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackToHome) {
                        Icon(Icons.Default.Home, contentDescription = stringResource(R.string.back_to_home))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(80.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.operation_successful, operationName),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isImage -> Icons.Default.Image
                            isZip -> Icons.Default.FolderZip
                            else -> Icons.Default.PictureAsPdf
                        },
                        contentDescription = null,
                        tint = when {
                            isImage -> Color(0xFF2196F3)
                            isZip -> Color(0xFFFFC107)
                            else -> Color(0xFFF44336)
                        },
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = fileName,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (isImage) stringResource(R.string.saved_to_pictures) else stringResource(R.string.saved_to_documents),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { PdfUtils.openFile(context, fileUri) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Visibility, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.view))
                }
                
                OutlinedButton(
                    onClick = { PdfUtils.shareFile(context, fileUri) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(onClick = onBackToHome) {
                Text(stringResource(R.string.back_to_home))
            }
        }
    }
}
