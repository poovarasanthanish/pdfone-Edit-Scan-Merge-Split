package com.villagefast.pdfeditor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.villagefast.pdfeditor.R
import com.villagefast.pdfeditor.data.db.AppDatabase
import com.villagefast.pdfeditor.data.db.RecentFile
import com.villagefast.pdfeditor.ui.components.PaperBottomBar
import com.villagefast.pdfeditor.ui.theme.PaperPink
import com.villagefast.pdfeditor.utils.PdfUtils
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun HistoryScreen(onBackClick: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recentFiles by AppDatabase.getDatabase(context).recentFileDao().getRecentFiles()
        .collectAsState(initial = emptyList())

    Scaffold(
        bottomBar = {
            PaperBottomBar(
                currentScreen = "history",
                onNavigate = onNavigate,
                onPlusClick = { /* Handle file picking */ }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    HistoryHeader(onBackClick = onBackClick)
                }

                if (recentFiles.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "NO RECENT ACTIVITY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Gray.copy(alpha = 0.5f),
                                letterSpacing = 1.sp
                            )
                        }
                    }
                } else {
                    items(recentFiles) { file ->
                        PaperHistoryItem(
                            file = file,
                            onClick = { PdfUtils.openPdf(context, File(file.path)) },
                            onDelete = {
                                scope.launch {
                                    AppDatabase.getDatabase(context).recentFileDao().delete(file)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .background(Color.Gray.copy(alpha = 0.1f), CircleShape)
                .size(36.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = "HISTORY",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                color = PaperPink
            )
            Text(
                text = "RECENT OPERATIONS",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun PaperHistoryItem(file: RecentFile, onClick: () -> Unit, onDelete: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val containerColor = if (isDark) Color(0xFF0A0A0A) else Color.White
    val borderColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(0.03f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(PaperPink.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Description, null, tint = PaperPink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "RECENTLY EDITED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray.copy(alpha = 0.5f),
                    letterSpacing = 0.5.sp
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp), tint = Color.Gray.copy(alpha = 0.5f))
            }
        }
    }
}
