package com.villagefast.pdfeditor.ui.screens

import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.villagefast.pdfeditor.ui.components.GlassTopAppBar
import com.villagefast.pdfeditor.ui.components.LiquidScaffold
import com.villagefast.pdfeditor.utils.PdfUtils
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

enum class WebToPdfMode {
    URL, HTML
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebToPdfScreen(
    showAds: Boolean = true, 
    onBackClick: () -> Unit,
    onResult: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(WebToPdfMode.URL) }
    
    var urlInput by remember { mutableStateOf("https://www.google.com") }
    var htmlInput by remember { mutableStateOf(HtmlTemplates.BASIC) }
    
    var currentUrl by remember { mutableStateOf("https://www.google.com") }
    var currentHtml by remember { mutableStateOf("") }
    
    var webView: WebView? by remember { mutableStateOf(null) }
    var isProcessing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LiquidScaffold(
        showBanner = showAds,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassTopAppBar(
                title = { Text("Web to PDF", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if ((mode == WebToPdfMode.URL && currentUrl.isNotEmpty()) || (mode == WebToPdfMode.HTML && currentHtml.isNotEmpty())) {
                        IconButton(
                            onClick = {
                                isProcessing = true
                                webView?.let { wv ->
                                    createWebPrintJob(wv) { file ->
                                        isProcessing = false
                                        if (file != null) {
                                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                            if (showAds && activity != null) {
                                                com.villagefast.pdfeditor.ads.AdManager.showInterstitialWithInterval(activity) {
                                                    onResult(uri, "Web to PDF")
                                                }
                                            } else {
                                                onResult(uri, "Web to PDF")
                                            }
                                        } else {
                                            scope.launch { snackbarHostState.showSnackbar("Failed to convert") }
                                        }
                                    }
                                }
                            },
                            enabled = !isProcessing
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "Convert")
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = mode.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = mode == WebToPdfMode.URL,
                    onClick = { mode = WebToPdfMode.URL },
                    text = { Text("From URL") }
                )
                Tab(
                    selected = mode == WebToPdfMode.HTML,
                    onClick = { mode = WebToPdfMode.HTML },
                    text = { Text("From HTML") }
                )
            }

            Crossfade(targetState = mode, label = "mode_switch") { currentMode ->
                when (currentMode) {
                    WebToPdfMode.URL -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("Website URL") },
                                placeholder = { Text("https://example.com") },
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { 
                                    currentUrl = if (urlInput.startsWith("http")) urlInput else "https://$urlInput"
                                },
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text("Go")
                            }
                        }
                    }
                    WebToPdfMode.HTML -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Quick Templates:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                item {
                                    SuggestionChip(
                                        onClick = { htmlInput = HtmlTemplates.RESUME },
                                        label = { Text("Resume") }
                                    )
                                }
                                item {
                                    SuggestionChip(
                                        onClick = { htmlInput = HtmlTemplates.INVOICE },
                                        label = { Text("Invoice") }
                                    )
                                }
                                item {
                                    SuggestionChip(
                                        onClick = { htmlInput = HtmlTemplates.LETTER },
                                        label = { Text("Letter") }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = htmlInput,
                                onValueChange = { htmlInput = it },
                                modifier = Modifier.fillMaxWidth().height(150.dp),
                                label = { Text("HTML Code") },
                                placeholder = { Text("<html>...</html>") }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { 
                                    currentHtml = htmlInput
                                },
                                modifier = Modifier.align(Alignment.End),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text("Preview & Convert")
                            }
                        }
                    }
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.domStorageEnabled = true
                            webView = this
                        }
                    },
                    update = { wv ->
                        if (mode == WebToPdfMode.URL) {
                            if (wv.url != currentUrl) {
                                wv.loadUrl(currentUrl)
                            }
                        } else {
                            wv.loadDataWithBaseURL(null, currentHtml, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                if (mode == WebToPdfMode.HTML && currentHtml.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Enter HTML above and click Preview", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

object HtmlTemplates {
    const val BASIC = """<html>
<head>
<style>
body { font-family: sans-serif; padding: 20px; line-height: 1.6; }
h1 { color: #2979FF; }
</style>
</head>
<body>
<h1>Hello PDF</h1>
<p>This PDF was generated from HTML code. You can edit this text directly.</p>
</body>
</html>"""

    const val RESUME = """<html>
<head>
<style>
body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; color: #333; margin: 40px; }
.header { text-align: center; border-bottom: 2px solid #2979FF; padding-bottom: 10px; }
h1 { margin: 0; color: #2979FF; }
.contact { font-style: italic; color: #666; }
.section-title { font-weight: bold; color: #2979FF; margin-top: 20px; border-bottom: 1px solid #eee; }
.job { margin-bottom: 15px; }
.job-title { font-weight: bold; }
.job-date { float: right; color: #888; }
</style>
</head>
<body>
<div class="header">
    <h1>JOHN DOE</h1>
    <div class="contact">Email: john.doe@example.com | Phone: (555) 012-3456</div>
</div>

<div class="section-title">PROFESSIONAL SUMMARY</div>
<p>Results-driven software developer with 5+ years of experience in building scalable web applications and mobile solutions.</p>

<div class="section-title">WORK EXPERIENCE</div>
<div class="job">
    <span class="job-title">Senior Developer - Tech Corp</span>
    <span class="job-date">2020 - Present</span>
    <ul>
        <li>Led a team of 5 developers to launch a successful fintech app.</li>
        <li>Reduced server costs by 30% through optimized database queries.</li>
    </ul>
</div>

<div class="section-title">EDUCATION</div>
<p>B.S. in Computer Science - State University (2014-2018)</p>
</body>
</html>"""

    const val INVOICE = """<html>
<head>
<style>
body { font-family: Arial, sans-serif; padding: 30px; }
.invoice-box { max-width: 800px; margin: auto; border: 1px solid #eee; padding: 30px; }
.top-row { display: flex; justify-content: space-between; margin-bottom: 20px; }
.title { font-size: 30px; font-weight: bold; color: #2979FF; }
table { width: 100%; border-collapse: collapse; margin-top: 20px; }
th { background: #f8f8f8; text-align: left; padding: 10px; border-bottom: 2px solid #eee; }
td { padding: 10px; border-bottom: 1px solid #eee; }
.total { text-align: right; font-weight: bold; font-size: 18px; margin-top: 20px; }
</style>
</head>
<body>
<div class="invoice-box">
    <div class="top-row">
        <div class="title">INVOICE</div>
        <div>
            Invoice #: 12345<br>
            Date: Oct 25, 2023
        </div>
    </div>
    
    <div style="margin-bottom: 40px;">
        <strong>Bill To:</strong><br>
        Client Name<br>
        123 Client St.<br>
        City, State 12345
    </div>

    <table>
        <tr>
            <th>Description</th>
            <th>Quantity</th>
            <th>Price</th>
        </tr>
        <tr>
            <td>PDF Editor App Customization</td>
            <td>1</td>
            <td>${'$'}500.00</td>
        </tr>
        <tr>
            <td>Hosting (Monthly)</td>
            <td>12</td>
            <td>${'$'}10.00</td>
        </tr>
    </table>

    <div class="total">Total: ${'$'}620.00</div>
</div>
</body>
</html>"""

    const val LETTER = """<html>
<head>
<style>
body { font-family: 'Times New Roman', serif; padding: 50px; line-height: 1.5; font-size: 12pt; }
.date { margin-bottom: 30px; }
.recipient { margin-bottom: 30px; }
.closing { margin-top: 40px; }
</style>
</head>
<body>
<div class="date">October 25, 2023</div>

<div class="recipient">
    Mr. Smith<br>
    Principal Manager<br>
    Global Logistics Ltd.
</div>

<p>Dear Mr. Smith,</p>

<p>I am writing to formally request a copy of the annual financial report for the fiscal year ending 2023. As a shareholder, I am interested in reviewing the company's performance and future growth strategies.</p>

<p>Please let me know if there are any specific procedures I need to follow or if the document can be sent directly to my email address on record.</p>

<p>Thank you for your time and assistance in this matter.</p>

<div class="closing">
    Sincerely,<br><br><br>
    <strong>Jane Williams</strong>
</div>
</body>
</html>"""
}

private fun createWebPrintJob(webView: WebView, onComplete: (File?) -> Unit) {
    val file = PdfUtils.getPublicDocumentsFile("Web_${System.currentTimeMillis()}.pdf")
    
    try {
        val contentWidth = webView.width
        val contentHeight = (webView.contentHeight * webView.resources.displayMetrics.density).toInt()
        
        if (contentWidth <= 0 || contentHeight <= 0) {
            onComplete(null)
            return
        }

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(contentWidth, contentHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        
        webView.draw(page.canvas)
        
        pdfDocument.finishPage(page)
        
        FileOutputStream(file).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        pdfDocument.close()
        
        onComplete(file)
    } catch (e: Exception) {
        e.printStackTrace()
        onComplete(null)
    }
}
