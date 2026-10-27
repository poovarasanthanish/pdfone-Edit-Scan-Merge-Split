package com.villagefast.pdfeditor.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.villagefast.pdfeditor.ui.components.GlassTopAppBar
import com.villagefast.pdfeditor.ui.components.LiquidScaffold
import com.villagefast.pdfeditor.ui.components.PdfThumbnail
import com.villagefast.pdfeditor.utils.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    showAds: Boolean = true, 
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLocking by remember { mutableStateOf(true) } // true for Lock, false for Unlock
    var isProcessing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        selectedUri = uri
        uri?.let {
            fileName = PdfUtils.getFileName(context, it)
            scope.launch {
                pageCount = PdfUtils.getPageCount(context, it)
            }
        } ?: run {
            pageCount = 0
        }
    }

    LiquidScaffold(
        showBanner = showAds,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassTopAppBar(
                title = { Text("PDF Security") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TabRow(selectedTabIndex = if (isLocking) 0 else 1) {
                Tab(selected = isLocking, onClick = { 
                    isLocking = true
                    if (selectedUri != null) {
                        scope.launch {
                            pageCount = PdfUtils.getPageCount(context, selectedUri!!)
                        }
                    }
                }) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Lock PDF")
                    }
                }
                Tab(selected = !isLocking, onClick = { 
                    isLocking = false 
                    if (selectedUri != null) {
                        scope.launch {
                            pageCount = PdfUtils.getPageCount(context, selectedUri!!, password)
                        }
                    }
                }) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LockOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Unlock PDF")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (selectedUri == null) {
                Card(
                    onClick = { launcher.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Tap to Select PDF File", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PdfThumbnail(
                            uri = selectedUri!!,
                            modifier = Modifier.size(48.dp),
                            password = if (!isLocking) password else null
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fileName ?: "Selected PDF",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (pageCount > 0) {
                                Text(
                                    text = "$pageCount Pages",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                if (pageCount > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Page Previews",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(pageCount) { index ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PdfThumbnail(
                                    uri = selectedUri!!,
                                    pageIndex = index,
                                    password = if (!isLocking) password else null,
                                    modifier = Modifier
                                        .size(80.dp, 110.dp)
                                        .clip(MaterialTheme.shapes.small)
                                )
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        if (!isLocking && selectedUri != null) {
                            scope.launch {
                                val count = PdfUtils.getPageCount(context, selectedUri!!, it)
                                if (count > 0) pageCount = count
                            }
                        }
                    },
                    label = { Text(if (isLocking) "Set Password" else "Enter Current Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                if (isLocking) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        if (password.isEmpty()) {
                            scope.launch { snackbarHostState.showSnackbar("Please enter a password") }
                            return@Button
                        }
                        if (isLocking && password != confirmPassword) {
                            scope.launch { snackbarHostState.showSnackbar("Passwords do not match") }
                            return@Button
                        }
                        isProcessing = true
                        scope.launch {
                            val resultFile = if (isLocking) {
                                lockPdf(context, selectedUri!!, password, fileName ?: "Locked")
                            } else {
                                unlockPdf(context, selectedUri!!, password, fileName ?: "Unlocked")
                            }
                            isProcessing = false
                            if (resultFile != null) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", resultFile)
                                if (showAds && activity != null) {
                                    com.villagefast.pdfeditor.ads.AdManager.showInterstitial(activity) {
                                        onResult(uri, if (isLocking) "Lock" else "Unlock")
                                    }
                                } else {
                                    onResult(uri, if (isLocking) "Lock" else "Unlock")
                                }
                            } else {
                                snackbarHostState.showSnackbar("Operation failed. Check your password.")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(size = 24.dp)
                    } else {
                        Text(if (isLocking) "Lock and Save" else "Unlock and Save")
                    }
                }
                
                TextButton(onClick = { selectedUri = null; fileName = null; password = ""; confirmPassword = "" }) {
                    Text("Choose different file")
                }
            }
        }
    }
}

private suspend fun lockPdf(context: android.content.Context, uri: Uri, pass: String, originalName: String): File? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val document = PDDocument.load(inputStream)
            val ap = AccessPermission()
            val spp = StandardProtectionPolicy(pass, pass, ap)
            spp.encryptionKeyLength = 128
            spp.permissions = ap
            document.protect(spp)
            
            val baseName = originalName.substringBeforeLast(".")
            val file = PdfUtils.getPublicDocumentsFile("${baseName}_locked.pdf")
            FileOutputStream(file).use { out -> document.save(out) }
            document.close()
            return@withContext file
        }
    } catch (e: Exception) {
        return@withContext null
    }
}

private suspend fun unlockPdf(context: android.content.Context, uri: Uri, pass: String, originalName: String): File? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri).use { inputStream ->
            val document = PDDocument.load(inputStream, pass)
            if (document.isEncrypted) {
                document.setAllSecurityToBeRemoved(true)
            }
            val baseName = originalName.substringBeforeLast(".")
            val file = PdfUtils.getPublicDocumentsFile("${baseName}_unlocked.pdf")
            FileOutputStream(file).use { out -> document.save(out) }
            document.close()
            return@withContext file
        }
    } catch (e: Exception) {
        return@withContext null
    }
}

@Composable
fun CircularProgressIndicator(size: androidx.compose.ui.unit.Dp) {
    androidx.compose.material3.CircularProgressIndicator(
        modifier = Modifier.size(size),
        strokeWidth = 2.dp
    )
}
