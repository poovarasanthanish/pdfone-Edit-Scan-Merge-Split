package com.villagefast.pdfeditor.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.villagefast.pdfeditor.ui.theme.PaperPink
import com.villagefast.pdfeditor.utils.PdfUtils

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer

enum class ToolState {
    SELECTING,
    UNLOCKING,
    CONFIGURING,
    PROCESSING,
    SUCCESS
}

enum class PreviewMode {
    GRID,
    ROTATE,
    DELETE
}

@Composable
fun SelectionGrid(
    onSelect: () -> Unit,
    isDark: Boolean,
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color = Color.Gray,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(if (isDark) Color(0xFF09090B) else Color.White)
            .border(BorderStroke(1.dp, Color.Gray.copy(0.1f)), RoundedCornerShape(32.dp))
            .clickable { onSelect() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 48.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp).alpha(0.1f),
                tint = accentColor
            )
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = if (isDark) Color.LightGray else Color.DarkGray
            )
            Text(
                subtitle,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = Color.Gray.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun LoadingStateView(
    accentColor: Color,
    showWarning: Boolean,
    text: String
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = accentColor)
        Spacer(Modifier.height(16.dp))
        Text(text, fontWeight = FontWeight.Medium, color = Color.Gray)
        
        if (showWarning) {
            Spacer(Modifier.height(24.dp))
            Surface(
                color = accentColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = accentColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Processing may take a moment depending on the device.",
                        fontSize = 10.sp,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SuccessView(
    message: String = "Task Complete",
    subMessage: String = "Saved successfully",
    processingTime: String = "",
    onDone: () -> Unit,
    onProcessMore: () -> Unit,
    onPreview: (() -> Unit)? = null,
    showPreviewButton: Boolean = true,
    accentColor: Color = PaperPink
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            color = Color(0xFF06D6A0).copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Check,
                    null,
                    tint = Color(0xFF06D6A0),
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(message, fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = (-0.5).sp)

        if (subMessage.isNotBlank()) {
            Text(subMessage, fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }

        if (processingTime.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = Color(0xFF06D6A0).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "DONE IN $processingTime",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF06D6A0),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(64.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onProcessMore,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White)
            ) {
                Text("PROCESS MORE FILES", fontWeight = FontWeight.Black)
            }

            if (showPreviewButton) {
                OutlinedButton(
                    onClick = { onPreview?.invoke() },
                    enabled = onPreview != null,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, if (onPreview != null) accentColor.copy(alpha = 0.3f) else Color.Gray.copy(0.2f))
                ) {
                    Icon(
                        Icons.Filled.Visibility,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = if (onPreview != null) accentColor else Color.Gray
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (onPreview != null) "OPEN PREVIEW" else "PREVIEW (UNAVAILABLE)",
                        fontWeight = FontWeight.Bold,
                        color = if (onPreview != null) accentColor else Color.Gray
                    )
                }
            }

            TextButton(
                onClick = onDone,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("BACK TO TOOLS CATALOG", fontWeight = FontWeight.Black, color = Color.Gray, fontSize = 11.sp, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
fun LockedFilePrompt(
    fileName: String,
    onDismiss: () -> Unit,
    onUnlocked: (String) -> Unit,
    accentColor: Color = Color(0xFF6366F1),
    isLoading: Boolean = false
) {
    var password by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Lock, null, tint = accentColor, modifier = Modifier.size(28.dp))
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    "Password Required",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    fileName,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        isError = false
                    },
                    label = { Text("PDF Password") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    isError = isError,
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        focusedLabelColor = accentColor,
                        cursorColor = accentColor
                    )
                )

                if (isError) {
                    Text(
                        "Incorrect password, try again.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.Start).padding(start = 8.dp, top = 4.dp)
                    )
                }

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = { onUnlocked(password) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    } else {
                        Text("UNLOCK FILE", fontWeight = FontWeight.Black, color = Color.White)
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    enabled = !isLoading,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("CANCEL", color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
fun ToolPdfPreview(
    uri: Uri,
    pageCount: Int,
    password: String? = null,
    accentColor: Color = PaperPink
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uri, password) {
        isLoading = true
        bitmap = PdfUtils.renderPage(context, uri, 0, scale = 1.5f, password = password)
        isLoading = false
    }

    Card(
        modifier = Modifier
            .width(160.dp)
            .height(226.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(color = accentColor)
            } else {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } ?: Icon(Icons.Default.Error, null, tint = Color.Gray)
            }
            
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "$pageCount Pages",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun CompareFileCard(
    label: String,
    name: String,
    isSelected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(24.dp),
        color = if (isSelected) color.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, if (isSelected) color.copy(alpha = 0.3f) else Color.Transparent)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                if (isSelected) Icons.Default.CheckCircle else Icons.Default.AddCircle,
                contentDescription = null,
                tint = if (isSelected) color else Color.Gray.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                label,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                color = if (isSelected) color else Color.Gray,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (isSelected) name else "Select File",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ComparisonViewer(
    uriA: Uri,
    uriB: Uri
) {
    val context = LocalContext.current
    var pageCountA by remember { mutableIntStateOf(0) }
    var pageCountB by remember { mutableIntStateOf(0) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(uriA, uriB) {
        pageCountA = PdfUtils.getPageCount(context, uriA)
        pageCountB = PdfUtils.getPageCount(context, uriB)
    }

    val maxPages = remember(pageCountA, pageCountB) { kotlin.math.max(pageCountA, pageCountB) }

    androidx.compose.foundation.lazy.LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        items(maxPages) { index ->
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                // File A Column
                Box(modifier = Modifier.weight(1f).padding(8.dp)) {
                    if (index < pageCountA) {
                        PdfPageItem(uri = uriA, index = index, onClick = {})
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.707f).background(Color.Gray.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("End of File", fontSize = 8.sp, color = Color.Gray)
                        }
                    }
                }

                // Vertical Divider
                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.Gray.copy(alpha = 0.1f)))

                // File B Column
                Box(modifier = Modifier.weight(1f).padding(8.dp)) {
                    if (index < pageCountB) {
                        PdfPageItem(uri = uriB, index = index, onClick = {})
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.707f).background(Color.Gray.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("End of File", fontSize = 8.sp, color = Color.Gray)
                        }
                    }
                }
            }
            if (index < maxPages - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = Color.Gray.copy(alpha = 0.05f)
                )
            }
        }
    }
}

@Composable
fun PdfPageItem(
    uri: Uri,
    index: Int,
    password: String? = null,
    rotation: Int = 0,
    isSelected: Boolean = false,
    mode: PreviewMode = PreviewMode.GRID,
    onClick: () -> Unit,
    accentColor: Color = PaperPink,
    overlay: @Composable (BoxScope.(Int) -> Unit)? = null
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uri, index, password) {
        isLoading = true
        bitmap = PdfUtils.renderPage(context, uri, index, scale = 1.5f, password = password)
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (rotation % 180 == 0) 0.707f else 1.414f)
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = accentColor)
        } else {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Page ${index + 1}",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(rotationZ = rotation.toFloat()),
                    contentScale = ContentScale.Fit
                )
            } ?: Text("Error", fontSize = 8.sp, color = Color.Red)
        }

        // Selection Overlay (for DELETE mode)
        if (mode == PreviewMode.DELETE && isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red.copy(alpha = 0.3f))
                    .border(2.dp, Color.Red, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }

        // Page Number Badge
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            color = Color.Black.copy(alpha = 0.4f),
            shape = CircleShape
        ) {
            Text(
                (index + 1).toString(),
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        
        // Rotation indicator for ROTATE mode
        if (mode == PreviewMode.ROTATE && rotation != 0) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                color = accentColor.copy(alpha = 0.9f),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(12.dp).padding(2.dp))
            }
        }

        // Custom Overlay
        overlay?.invoke(this, index)
    }
}

@Composable
fun PdfGridPreview(
    uri: Uri,
    pageCount: Int,
    password: String? = null,
    pageRotations: Map<Int, Int> = emptyMap(),
    selectedPages: Set<Int> = emptySet(),
    mode: PreviewMode = PreviewMode.GRID,
    onPageClick: (Int) -> Unit,
    accentColor: Color = PaperPink,
    itemOverlay: @Composable (BoxScope.(Int) -> Unit)? = null
) {
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(pageCount) { index ->
            PdfPageItem(
                uri = uri,
                index = index,
                password = password,
                rotation = pageRotations[index] ?: 0,
                isSelected = selectedPages.contains(index),
                mode = mode,
                onClick = { onPageClick(index) },
                accentColor = accentColor,
                overlay = itemOverlay
            )
        }
    }
}
