package com.villagefast.pdfeditor.ui.screens

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.canhub.cropper.CropImageView
import com.villagefast.pdfeditor.ui.components.GlassTopAppBar
import com.villagefast.pdfeditor.ui.components.LiquidScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    initialUri: Uri? = null,
    showAds: Boolean = true,
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    var cropImageView by remember { mutableStateOf<CropImageView?>(null) }
    var currentAspectRatio by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var selectedUri by remember { mutableStateOf(initialUri) }
    var lastLoadedUri by remember { mutableStateOf<Uri?>(null) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
        }
    }

    LiquidScaffold(
        showBanner = showAds,
        topBar = {
            GlassTopAppBar(
                title = { Text("Crop Image") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedUri == null) {
                        IconButton(onClick = { 
                            pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) 
                        }) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Select Image")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (selectedUri == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Select an image to crop", color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { 
                        pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) 
                    }) {
                        Text("Pick Image")
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Crop Area
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AndroidView(
                            factory = { ctx ->
                                Log.d("CropScreen", "Creating CropImageView for URI: $selectedUri")
                                CropImageView(ctx).apply {
                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    
                                    guidelines = CropImageView.Guidelines.ON
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                    setFixedAspectRatio(false)
                                    setMultiTouchEnabled(true)
                                    
                                    setOnCropImageCompleteListener { _, result ->
                                        if (result.isSuccessful) {
                                            Log.d("CropScreen", "Crop success: ${result.uriContent}")
                                            result.uriContent?.let { onResult(it, "Crop") }
                                        } else {
                                            Log.e("CropScreen", "Crop failed", result.error)
                                        }
                                    }

                                    setOnSetImageUriCompleteListener { _, uri, error ->
                                        if (error != null) {
                                            Log.e("CropScreen", "Failed to load image: $uri", error)
                                        } else {
                                            Log.d("CropScreen", "Successfully loaded image: $uri")
                                        }
                                    }
                                    
                                    selectedUri?.let { 
                                        setImageUriAsync(it)
                                        lastLoadedUri = it
                                    }
                                    cropImageView = this
                                }
                            },
                            update = { view ->
                                if (selectedUri != null && selectedUri != lastLoadedUri) {
                                    view.setImageUriAsync(selectedUri)
                                    lastLoadedUri = selectedUri
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Bottom Controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .padding(bottom = 32.dp, top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Aspect Ratio Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            AspectRatioItem("Free", currentAspectRatio == null) {
                                currentAspectRatio = null
                                cropImageView?.setFixedAspectRatio(false)
                            }
                            AspectRatioItem("1:1", currentAspectRatio?.first == 1 && currentAspectRatio?.second == 1) {
                                currentAspectRatio = 1 to 1
                                cropImageView?.setAspectRatio(1, 1)
                            }
                            AspectRatioItem("4:3", currentAspectRatio?.first == 4 && currentAspectRatio?.second == 3) {
                                currentAspectRatio = 4 to 3
                                cropImageView?.setAspectRatio(4, 3)
                            }
                            AspectRatioItem("16:9", currentAspectRatio?.first == 16 && currentAspectRatio?.second == 9) {
                                currentAspectRatio = 16 to 9
                                cropImageView?.setAspectRatio(16, 9)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Main Actions
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }

                            ControlIconButton(Icons.AutoMirrored.Filled.RotateRight, "Rotate") {
                                cropImageView?.rotateImage(90)
                            }

                            // Shutter/Check Button
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    border = androidx.compose.foundation.BorderStroke(4.dp, Color.White),
                                    modifier = Modifier.fillMaxSize()
                                ) {}
                                
                                Button(
                                    onClick = { 
                                        Log.d("CropScreen", "Requesting crop...")
                                        cropImageView?.croppedImageAsync() 
                                    },
                                    modifier = Modifier.size(54.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(28.dp))
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
fun AspectRatioItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ControlIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}
