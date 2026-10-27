package com.villagefast.pdfeditor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.villagefast.pdfeditor.R
import com.villagefast.pdfeditor.ui.components.PaperBottomBar
import com.villagefast.pdfeditor.ui.components.PaperTool
import com.villagefast.pdfeditor.ui.theme.PaperPink

@Composable
fun AllToolsScreen(showAds: Boolean = true, onBackClick: () -> Unit, onToolClick: (String) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    
    val allTools = listOf(
        PaperTool(stringResource(R.string.merge_pdfs), stringResource(R.string.desc_merge), Icons.Default.Merge, Color(0xFFF43F5E), route = "merge", category = "Structure"),
        PaperTool(stringResource(R.string.split_pdf), stringResource(R.string.desc_split), Icons.Default.ContentCut, Color(0xFF06D6A0), route = "split", category = "Structure"),
        PaperTool(stringResource(R.string.organize_pages), stringResource(R.string.desc_organize), Icons.Default.GridView, Color(0xFF2979FF), route = "organize", category = "Structure"),
        PaperTool(stringResource(R.string.rotate_pages), stringResource(R.string.desc_rotate), Icons.AutoMirrored.Filled.RotateRight, Color(0xFFFF9800), route = "rotate_pages", category = "Structure"),
        PaperTool(stringResource(R.string.delete_pages), stringResource(R.string.desc_delete), Icons.Default.Delete, Color(0xFFE91E63), route = "delete_pages", category = "Structure"),
        PaperTool(stringResource(R.string.extract_pages), stringResource(R.string.desc_extract), Icons.Default.FilterFrames, Color(0xFF00BCD4), route = "extract_pages", category = "Structure"),
        
        PaperTool(stringResource(R.string.image_to_pdf), stringResource(R.string.desc_image_to_pdf), Icons.Default.Add, Color(0xFF7C4DFF), route = "image_to_pdf", category = "Conversion"),
        PaperTool(stringResource(R.string.pdf_to_image), stringResource(R.string.desc_pdf_to_image), Icons.Default.Collections, Color(0xFF4CAF50), route = "pdf_to_image", category = "Conversion"),
        PaperTool(stringResource(R.string.web_to_pdf), stringResource(R.string.desc_web_to_pdf), Icons.Default.Public, Color(0xFF607D8B), route = "web_to_pdf", category = "Conversion"),
        PaperTool(stringResource(R.string.qr_to_pdf), stringResource(R.string.desc_qr_to_pdf), Icons.Default.QrCode, Color(0xFF795548), route = "qr_to_pdf", category = "Conversion"),
        
        PaperTool(stringResource(R.string.fill_sign), stringResource(R.string.desc_fill_sign), Icons.Default.Gesture, Color(0xFF9C27B0), route = "fill_sign", category = "Content"),
        PaperTool(stringResource(R.string.page_numbers), stringResource(R.string.desc_page_no), Icons.Default.FormatListNumbered, Color(0xFFFF9800), route = "page_numbering", category = "Content"),
        PaperTool(stringResource(R.string.bates_numbering), stringResource(R.string.desc_bates), Icons.Default.Filter1, Color(0xFF673AB7), route = "bates", category = "Content"),
        PaperTool(stringResource(R.string.watermark), stringResource(R.string.desc_watermark), Icons.Default.BrandingWatermark, Color(0xFFFF9800), route = "watermark", category = "Content"),
        
        PaperTool(stringResource(R.string.security), stringResource(R.string.desc_security), Icons.Default.Lock, Color(0xFF3F51B5), route = "security", category = "Security"),
        
        PaperTool(stringResource(R.string.compress), stringResource(R.string.desc_compress), Icons.Default.Compress, Color(0xFFFFD166), route = "compress", category = "Utility"),
        PaperTool(stringResource(R.string.scanner), stringResource(R.string.desc_scanner), Icons.Default.CameraAlt, Color(0xFFF44336), route = "scanner", category = "Utility"),
        PaperTool(stringResource(R.string.pdf_viewer), stringResource(R.string.desc_viewer), Icons.Default.Visibility, Color(0xFFFFC107), route = "viewer", category = "Utility"),
        PaperTool(stringResource(R.string.metadata), stringResource(R.string.desc_metadata), Icons.Default.Info, Color(0xFF673AB7), route = "metadata", category = "Utility"),
        PaperTool(stringResource(R.string.repair_pdf), stringResource(R.string.desc_repair), Icons.Default.Build, Color(0xFFF44336), route = "repair_pdf", category = "Utility"),
        PaperTool(stringResource(R.string.voice_reader), stringResource(R.string.desc_voice), Icons.Default.RecordVoiceOver, Color(0xFF4CAF50), route = "voice_reader", category = "Utility"),
        PaperTool(stringResource(R.string.pdf_to_zip), stringResource(R.string.desc_pdf_to_zip), Icons.Default.FolderZip, Color(0xFF9E9E9E), route = "pdf_to_zip", category = "Utility"),
        PaperTool(stringResource(R.string.compare_pdf), stringResource(R.string.desc_compare), Icons.Default.Compare, Color(0xFF2196F3), route = "compare", category = "Utility"),
        PaperTool(stringResource(R.string.nup_pdf), stringResource(R.string.desc_nup), Icons.Default.Layers, Color(0xFF673AB7), route = "nup", category = "Utility"),
        PaperTool(stringResource(R.string.print), stringResource(R.string.desc_print), Icons.Default.Print, Color(0xFF607D8B), route = "print", category = "Utility")
    )

    val filteredGroupedTools = remember(searchQuery) {
        allTools
            .filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.description.contains(searchQuery, ignoreCase = true)
            }
            .groupBy { it.category }
    }

    Scaffold(
        bottomBar = {
            PaperBottomBar(
                currentScreen = "tools",
                onNavigate = onToolClick,
                onPlusClick = { /* File picker if needed */ }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                item(span = { GridItemSpan(3) }) {
                    ToolsViewHeader(onBackClick = onBackClick)
                }
                
                item(span = { GridItemSpan(3) }) {
                    PaperSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }

                filteredGroupedTools.forEach { (category, toolsInCategory) ->
                    item(span = { GridItemSpan(3) }) {
                        Text(
                            text = category.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Gray.copy(alpha = 0.6f),
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }
                    items(toolsInCategory) { tool ->
                        CompactBentoCard(tool = tool, onClick = { onToolClick(tool.route) })
                    }
                }
            }
        }
    }
}

@Composable
fun ToolsViewHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.tools).uppercase(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = ".",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = PaperPink
                )
            }
            Text(
                text = "EVERYTHING YOU NEED",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )
        }
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .background(Color.Gray.copy(alpha = 0.1f), CircleShape)
                .size(36.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun PaperSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color(0xFF0F0F0F) else Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(0.03f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = if (query.isEmpty()) Color.Gray.copy(alpha = 0.5f) else PaperPink,
                modifier = Modifier.size(20.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search utilities...",
                        fontSize = 14.sp,
                        color = Color.Gray.copy(alpha = 0.5f)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(PaperPink),
                    singleLine = true
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CompactBentoCard(tool: PaperTool, onClick: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val containerColor = if (isDark) Color(0xFF0A0A0A) else Color.White
    val borderColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(0.03f)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(if (isDark) tool.color.copy(alpha = 0.12f) else tool.bgColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = tool.color,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = tool.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.3).sp,
                lineHeight = 12.sp,
                maxLines = 1
            )
            Text(
                text = tool.description.uppercase(),
                fontSize = 6.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray.copy(alpha = 0.4f),
                letterSpacing = 0.2.sp,
                maxLines = 1
            )
        }
    }
}
