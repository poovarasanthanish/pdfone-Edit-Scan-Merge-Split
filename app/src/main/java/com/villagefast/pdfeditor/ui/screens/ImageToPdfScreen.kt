package com.villagefast.pdfeditor.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.villagefast.pdfeditor.ui.components.GlassCard
import com.villagefast.pdfeditor.ui.components.GlassTopAppBar
import com.villagefast.pdfeditor.ui.components.LiquidScaffold
import com.villagefast.pdfeditor.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToPdfScreen(
    showAds: Boolean = true,
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    val selectedImages = remember { mutableStateListOf<Uri>() }
    var isProcessing by remember { mutableStateOf(false) }
    var imageQuality by remember { mutableFloatStateOf(0.8f) }
    val snackbarHostState = remember { SnackbarHostState() }

    var useCustomLocation by remember { mutableStateOf(false) }
    var customSaveUri by remember { mutableStateOf<Uri?>(null) }
    
    var croppingImageIndex by remember { mutableIntStateOf(-1) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        selectedImages.addAll(uris)
    }

    val cropperLauncher = rememberLauncherForActivityResult(
        contract = CropImageContract(),
    ) { result ->
        if (result.isSuccessful && croppingImageIndex != -1) {
            result.uriContent?.let { uri ->
                selectedImages[croppingImageIndex] = uri
            }
        }
        croppingImageIndex = -1
    }

    val directoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            customSaveUri = uri
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LiquidScaffold(
            showBanner = showAds,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                GlassTopAppBar(
                    title = { Text("Image to PDF") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add Images")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                if (selectedImages.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No images selected", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { 
                                pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) 
                            }) {
                                Text("Select Images")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(selectedImages) { index, uri ->
                            ImageCard(
                                uri = uri,
                                index = index,
                                isFirst = index == 0,
                                isLast = index == selectedImages.size - 1,
                                onMoveUp = {
                                    if (index > 0) {
                                        val temp = selectedImages[index]
                                        selectedImages[index] = selectedImages[index - 1]
                                        selectedImages[index - 1] = temp
                                    }
                                },
                                onMoveDown = {
                                    if (index < selectedImages.size - 1) {
                                        val temp = selectedImages[index]
                                        selectedImages[index] = selectedImages[index + 1]
                                        selectedImages[index + 1] = temp
                                    }
                                },
                                onDelete = {
                                    selectedImages.removeAt(index)
                                },
                                onCrop = {
                                    croppingImageIndex = index
                                    val options = CropImageContractOptions(
                                        uri,
                                        CropImageOptions(
                                            guidelines = CropImageView.Guidelines.ON,
                                            allowRotation = true,
                                            allowCounterRotation = true,
                                            allowFlipping = true,
                                            fixAspectRatio = false,
                                            showProgressBar = true,
                                            activityMenuIconColor = android.graphics.Color.WHITE,
                                            activityTitle = "Crop Image",
                                            // Camera-like options in standard activity
                                            backgroundColor = android.graphics.Color.BLACK
                                        )
                                    )
                                    cropperLauncher.launch(options)
                                }
                            )
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Quality Setting
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Text("Image Quality: ${(imageQuality * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                                    Slider(
                                        value = imageQuality,
                                        onValueChange = { imageQuality = it },
                                        valueRange = 0.1f..1.0f
                                    )
                                    Text("Higher quality results in larger file size.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Destination Selection
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { useCustomLocation = !useCustomLocation }
                                    ) {
                                        Checkbox(checked = useCustomLocation, onCheckedChange = { useCustomLocation = it })
                                        Text("Save to custom folder", style = MaterialTheme.typography.bodyMedium)
                                    }

                                    if (useCustomLocation) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable { directoryLauncher.launch(null) }
                                            .padding(8.dp)
                                        ) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (customSaveUri != null) customSaveUri!!.path ?: "Custom Folder" else "Select Folder...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1
                                                )
                                            }
                                            Icon(Icons.Default.Edit, contentDescription = "Change Folder", modifier = Modifier.size(16.dp))
                                        }
                                    } else {
                                        Text(
                                            text = "Saving to: Documents/pdfone",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(start = 32.dp)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Button(
                                onClick = {
                                    isProcessing = true
                                    scope.launch {
                                        val result = createPdfFromImages(
                                            context = context,
                                            uris = selectedImages.toList(),
                                            quality = imageQuality,
                                            customSaveUri = if (useCustomLocation) customSaveUri else null
                                        )
                                        isProcessing = false
                                        when (result) {
                                            is ConversionResult.Success -> {
                                                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", result.file)
                                                if (showAds && activity != null) {
                                                    com.villagefast.pdfeditor.ads.AdManager.showInterstitialWithInterval(activity) {
                                                        onResult(uri, "Conversion")
                                                    }
                                                } else {
                                                    onResult(uri, "Conversion")
                                                }
                                            }
                                            is ConversionResult.Error -> {
                                                snackbarHostState.showSnackbar("Error: ${result.message}")
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                enabled = !isProcessing && selectedImages.isNotEmpty()
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                } else {
                                    Text("Convert to PDF (${selectedImages.size})")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageCard(
    uri: Uri,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onCrop: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reorder Controls on the Left
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(40.dp)
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move Up",
                        tint = if (isFirst) Color.Gray.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary
                    )
                }
                
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                IconButton(
                    onClick = onMoveDown,
                    enabled = !isLast,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move Down",
                        tint = if (isLast) Color.Gray.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Thumbnail
            AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Actions
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onCrop,
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Crop,
                            contentDescription = "Crop",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    FilledTonalIconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

sealed class ConversionResult {
    data class Success(val file: File) : ConversionResult()
    data class Error(val message: String) : ConversionResult()
}

private suspend fun createPdfFromImages(
    context: android.content.Context,
    uris: List<Uri>,
    quality: Float,
    customSaveUri: Uri?
): ConversionResult = withContext(Dispatchers.IO) {
    val pdfDocument = PdfDocument()
    try {
        uris.forEachIndexed { index, uri ->
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                var bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    // Re-compress to handle quality
                    if (quality < 1.0f) {
                        val stream = java.io.ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, (quality * 100).toInt(), stream)
                        val bytes = stream.toByteArray()
                        bitmap.recycle()
                        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)
                    bitmap.recycle()
                }
            }
        }

        val fileName = "ImagesToPdf_${System.currentTimeMillis()}.pdf"
        val tempFile = PdfUtils.getPublicDocumentsFile(fileName)
        
        FileOutputStream(tempFile).use { out ->
            pdfDocument.writeTo(out)
        }

        if (customSaveUri != null) {
            val parentDir = DocumentFile.fromTreeUri(context, customSaveUri)
            val file = parentDir?.createFile("application/pdf", fileName)
            file?.let {
                context.contentResolver.openOutputStream(it.uri)?.use { out ->
                    pdfDocument.writeTo(out)
                }
            }
        }

        return@withContext ConversionResult.Success(tempFile)
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext ConversionResult.Error(e.message ?: "Unknown error")
    } finally {
        pdfDocument.close()
    }
}
