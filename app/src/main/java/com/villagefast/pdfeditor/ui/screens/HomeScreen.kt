package com.villagefast.pdfeditor.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.villagefast.pdfeditor.R
import com.villagefast.pdfeditor.ui.components.BentoCard
import com.villagefast.pdfeditor.ui.components.HeroCard
import com.villagefast.pdfeditor.ui.components.PaperBottomBar
import com.villagefast.pdfeditor.ui.components.PaperTool
import com.villagefast.pdfeditor.ui.theme.PaperPink

@Composable
fun HomeScreen(isPremium: Boolean, onFeatureClick: (String) -> Unit) {
    val context = LocalContext.current
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val encodedUri = android.net.Uri.encode(it.toString())
            onFeatureClick("viewer?uri=$encodedUri")
        }
    }

    val tools = listOf(
        PaperTool(stringResource(R.string.merge_pdfs), stringResource(R.string.desc_merge), Icons.Default.Merge, Color(0xFFF43F5E), route = "merge", category = "Structure"),
        PaperTool(stringResource(R.string.split_pdf), stringResource(R.string.desc_split), Icons.Default.ContentCut, Color(0xFF06D6A0), route = "split", category = "Structure"),
        PaperTool(stringResource(R.string.organize_pages), stringResource(R.string.desc_organize), Icons.Default.GridView, Color(0xFF2979FF), route = "organize", category = "Structure"),

        PaperTool(stringResource(R.string.image_to_pdf), stringResource(R.string.desc_image_to_pdf), Icons.Default.Add, Color(0xFF7C4DFF), route = "image_to_pdf", category = "Conversion"),
        PaperTool(stringResource(R.string.pdf_to_image), stringResource(R.string.desc_pdf_to_image), Icons.Default.Collections, Color(0xFF4CAF50), route = "pdf_to_image", category = "Conversion"),

        PaperTool(stringResource(R.string.nup_pdf), stringResource(R.string.desc_nup), Icons.Default.Layers, Color(0xFF673AB7), route = "nup", category = "Utility"),
        PaperTool(stringResource(R.string.print), stringResource(R.string.desc_print), Icons.Default.Print, Color(0xFF607D8B), route = "print", category = "Utility"),
    )

    val groupedTools = remember(tools) {
        tools.groupBy { it.category }
    }

    Scaffold(
        bottomBar = {
            PaperBottomBar(
                currentScreen = "home",
                onNavigate = { route -> onFeatureClick(route) },
                onPlusClick = { launcher.launch(arrayOf("application/pdf")) }
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
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                item(span = { GridItemSpan(2) }) {
                    HomeHeader(onPremiumClick = { onFeatureClick("premium") })
                }
                item(span = { GridItemSpan(2) }) {
                    HeroCard(onSelectPdf = { launcher.launch(arrayOf("application/pdf")) })
                }

                groupedTools.forEach { (category, toolsInCategory) ->
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            text = category.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Gray.copy(alpha = 0.6f),
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                    items(toolsInCategory) { tool ->
                        BentoCard(tool = tool, onClick = { onFeatureClick(tool.route) })
                    }
                }

                item(span = { GridItemSpan(2) }) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clickable { onFeatureClick("tools") },
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.view_all_tools),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeHeader(onPremiumClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "PDF ONE",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                color = PaperPink
            )
            Text(
                text = "PDF UTILITY HUB",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )
        }
        IconButton(
            onClick = onPremiumClick,
            modifier = Modifier
                .background(Color.Gray.copy(alpha = 0.1f), CircleShape)
                .size(36.dp)
        ) {
            Icon(
                Icons.Default.WorkspacePremium,
                contentDescription = "Premium",
                modifier = Modifier.size(18.dp),
                tint = PaperPink
            )
        }
    }
}
