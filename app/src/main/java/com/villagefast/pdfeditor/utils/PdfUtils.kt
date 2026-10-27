package com.villagefast.pdfeditor.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import com.villagefast.pdfeditor.data.db.AppDatabase
import com.villagefast.pdfeditor.data.db.RecentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import android.util.Log

data class UriDetails(
    val name: String,
    val size: String,
    val sizeBytes: Long = 0
)

object PdfUtils {
    fun getPublicDocumentsFile(fileName: String): File {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val appDir = File(documentsDir, "pdfone")
        try {
            if (!appDir.exists()) {
                val created = appDir.mkdirs()
                android.util.Log.d("PdfUtils", "Public Documents app directory created: $created at ${appDir.absolutePath}")
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfUtils", "Failed to create public documents directory", e)
        }
        return File(appDir, fileName)
    }

    fun getUriDetails(context: Context, uri: Uri): UriDetails {
        var name = "Document.pdf"
        var size = "Unknown size"
        var bytes: Long = 0
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) {
                        val rawName = cursor.getString(nameIndex)
                        if (rawName != null) name = rawName
                    }
                    if (sizeIndex != -1) {
                        bytes = cursor.getLong(sizeIndex)
                        size = formatFileSize(bytes)
                    }
                }
            }
        } catch (e: Exception) {
            uri.lastPathSegment?.let { name = it }
        }
        if (!name.contains(".")) name += ".pdf"
        return UriDetails(name, size, bytes)
    }

    suspend fun checkIsEncryptedLocal(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
                val isEnc = doc.isEncrypted
                doc.close()
                isEnc
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun getPublicPicturesFile(fileName: String): File {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val appDir = File(picturesDir, "pdfone")
        if (!appDir.exists()) appDir.mkdirs()
        return File(appDir, fileName)
    }

    fun openPdf(context: Context, file: File) {
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        GlobalScope.launch {
            AppDatabase.getDatabase(context).recentFileDao().insert(
                RecentFile(file.absolutePath, file.name)
            )
        }

        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            openPdf(context, uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No PDF viewer found", Toast.LENGTH_SHORT).show()
        }
    }

    fun openPdf(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/pdf")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No PDF viewer found", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun showSaveToast(context: Context, file: File) {
        Toast.makeText(context, "Saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    }

    fun openFile(context: Context, uri: Uri) {
        val fileName = getFileName(context, uri) ?: "Document"
        if (fileName.lowercase().endsWith(".pdf")) {
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch {
                AppDatabase.getDatabase(context).recentFileDao().insert(
                    RecentFile(uri.toString(), fileName)
                )
            }
        }

        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: getMimeTypeFromUri(uri)
            
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeTypeFromUri(uri: Uri): String {
        val extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    fun shareFile(context: Context, uri: Uri, title: String = "Share File") {
        val mimeType = context.contentResolver.getType(uri) ?: getMimeTypeFromUri(uri)
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = mimeType
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun printPdf(context: Context, uri: Uri) {
        val activity = findActivity(context)
        if (activity == null) {
            Toast.makeText(context, "Activity not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val printManager = activity.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
            val jobName = "${activity.getString(com.villagefast.pdfeditor.R.string.app_name)} Print"
            
            printManager.print(jobName, object : android.print.PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: android.print.PrintAttributes?,
                    newAttributes: android.print.PrintAttributes?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: android.os.Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val pdi = android.print.PrintDocumentInfo.Builder("document.pdf")
                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .build()
                    callback?.onLayoutFinished(pdi, true)
                }

                override fun onWrite(
                    pages: Array<out android.print.PageRange>?,
                    destination: android.os.ParcelFileDescriptor?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                android.os.ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (cancellationSignal?.isCanceled == true) {
                                callback?.onWriteCancelled()
                            } else {
                                callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                            }
                        } catch (e: Exception) {
                            callback?.onWriteFailed(e.message)
                        }
                    }
                }
            }, null)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(activity, "Print failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun findActivity(context: Context): android.app.Activity? {
        var currentContext = context
        while (currentContext is android.content.ContextWrapper) {
            if (currentContext is android.app.Activity) {
                return currentContext
            }
            currentContext = currentContext.baseContext
        }
        return null
    }

    fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    return cursor.getLong(sizeIndex)
                }
            }
        } else if (uri.scheme == "file") {
            return File(uri.path!!).length()
        }
        return 0
    }

    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(java.util.Locale.US, "%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    suspend fun getPageCount(context: android.content.Context, uri: android.net.Uri, password: String? = null): Int = withContext(
        kotlinx.coroutines.Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri).use { inputStream ->
                val document = if (password != null) 
                    com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream, password)
                else 
                    com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
                val count = document.numberOfPages
                document.close()
                return@withContext count
            }
        } catch (e: Exception) {
            return@withContext 0
        }
    }

    suspend fun renderPage(context: android.content.Context, uri: Uri, pageIndex: Int, scale: Float = 2f, password: String? = null): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri).use { inputStream ->
                val document = if (password != null)
                    com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream, password)
                else
                    com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
                val renderer = com.tom_roush.pdfbox.rendering.PDFRenderer(document)
                val bitmap = renderer.renderImage(pageIndex, scale)
                document.close()
                return@withContext bitmap
            }
        } catch (e: Exception) {
            return@withContext null
        }
    }

    suspend fun getTextBlocks(context: Context, uri: Uri, pageIndex: Int): List<TextBlock> = withContext(Dispatchers.IO) {
        val blocks = mutableListOf<TextBlock>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
                val stripper = object : com.tom_roush.pdfbox.text.PDFTextStripper() {
                    init {
                        startPage = pageIndex + 1
                        endPage = pageIndex + 1
                    }

                    override fun writeString(text: String, textPositions: List<TextPosition>) {
                        if (text.trim().isEmpty()) return
                        
                        var minX = Float.MAX_VALUE
                        var minY = Float.MAX_VALUE
                        var maxX = Float.MIN_VALUE
                        var maxY = Float.MIN_VALUE
                        var totalHeight = 0f
                        
                        for (pos in textPositions) {
                            minX = minOf(minX, pos.xDirAdj)
                            minY = minOf(minY, pos.yDirAdj - pos.heightDir)
                            maxX = maxOf(maxX, pos.xDirAdj + pos.widthDirAdj)
                            maxY = maxOf(maxY, pos.yDirAdj)
                            totalHeight += pos.heightDir
                        }
                        
                        val avgFontSize = if (textPositions.isNotEmpty()) totalHeight / textPositions.size else 12f
                        val fontName = textPositions.firstOrNull()?.font?.name ?: "Helvetica"

                        blocks.add(
                            TextBlock(
                                text = text.trim(),
                                bounds = AnnotationRect(minX, minY, maxX - minX, maxY - minY),
                                pageIndex = pageIndex,
                                fontSize = avgFontSize,
                                fontName = fontName
                            )
                        )
                    }
                }
                stripper.getText(document)
                document.close()
            }
        } catch (e: Exception) {
            Log.e("PdfUtils", "Error getting text blocks with PDFBox", e)
        }
        return@withContext blocks
    }

    /**
     * Copies a URI to a local cache file. This is crucial for remote URIs (like Google Drive)
     * where direct stream access might fail due to authentication or sync issues in 3rd party processes.
     */
    suspend fun localiseUri(context: Context, uri: Uri): Uri = withContext(Dispatchers.IO) {
        if (uri.scheme == "file") return@withContext uri
        
        try {
            val fileName = getFileName(context, uri) ?: "temp_${System.currentTimeMillis()}.pdf"
            val cacheDir = File(context.cacheDir, "pdf_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            
            val tempFile = File(cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext uri // Fallback to original if stream is null
            
            return@withContext Uri.fromFile(tempFile)
        } catch (e: Exception) {
            Log.e("PdfUtils", "Failed to localise URI: $uri", e)
            return@withContext uri // Fallback to original
        }
    }
}
