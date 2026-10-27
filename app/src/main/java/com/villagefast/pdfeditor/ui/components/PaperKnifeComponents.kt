package com.villagefast.pdfeditor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.villagefast.pdfeditor.ui.theme.PaperPink

@Composable
fun PaperBottomBar(
    modifier: Modifier = Modifier,
    currentScreen: String,
    onNavigate: (String) -> Unit,
    onPlusClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(84.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .offset(y = (-32).dp)
                    .size(40.dp)
                    .background(Brush.radialGradient(listOf(PaperPink.copy(alpha = 0.4f), Color.Transparent)))
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                color = Color.Transparent
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            PaperNavItem(Icons.Default.Home, androidx.compose.ui.res.stringResource(com.villagefast.pdfeditor.R.string.home_label), currentScreen == "home") { onNavigate("home") }
                            PaperNavItem(Icons.Default.GridView, androidx.compose.ui.res.stringResource(com.villagefast.pdfeditor.R.string.tools), currentScreen == "tools") { onNavigate("tools") }
                        }
                        Spacer(modifier = Modifier.width(72.dp))
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            PaperNavItem(Icons.Default.History, androidx.compose.ui.res.stringResource(com.villagefast.pdfeditor.R.string.history_label), currentScreen == "history") { onNavigate("history") }
                            PaperNavItem(Icons.Default.Settings, androidx.compose.ui.res.stringResource(com.villagefast.pdfeditor.R.string.settings), currentScreen == "settings") { onNavigate("settings") }
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .offset(y = (-34).dp)
                    .size(58.dp)
                    .border(2.5.dp, if (MaterialTheme.colorScheme.background == Color.Black) Color.Black else Color.White, RoundedCornerShape(20.dp))
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(20.dp), spotColor = PaperPink)
                    .clickable { onPlusClick() },
                shape = RoundedCornerShape(20.dp),
                color = PaperPink
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
fun PaperNavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) PaperPink else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
            color = if (selected) PaperPink else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            letterSpacing = 0.2.sp
        )
    }
}

@Composable
fun HeroCard(onSelectPdf: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val containerColor = if (isDark) Color.White else Color(0xFF121212)
    val textColor = if (isDark) Color.Black else Color.White
    val subTextColor = if (isDark) Color.DarkGray else Color.Gray

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(28.dp), spotColor = PaperPink.copy(alpha = 0.15f))
            .clickable(onClick = onSelectPdf),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = if (isDark) {
                            listOf(Color.White, Color.White, PaperPink.copy(alpha = 0.1f))
                        } else {
                            listOf(Color(0xFF121212), Color(0xFF121212), PaperPink.copy(alpha = 0.2f))
                        },
                        start = Offset(0f, 1000f),
                        end = Offset(1000f, 0f)
                    )
                )
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.villagefast.pdfeditor.R.string.select_pdf),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = textColor,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 28.sp
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(com.villagefast.pdfeditor.R.string.tap_to_load),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = subTextColor,
                    letterSpacing = 0.5.sp
                )
            }
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.TopStart),
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, if (isDark) Color.Black.copy(alpha = 0.05f) else Color.White.copy(0.05f))
            ) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = if (isDark) Color.Black else Color.White,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .clickable { onSelectPdf() },
                color = PaperPink,
                shape = CircleShape
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.villagefast.pdfeditor.R.string.start_session),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

data class PaperTool(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val bgColor: Color = color.copy(alpha = 0.1f),
    val route: String,
    val category: String = "Other"
)

@Composable
fun BentoCard(tool: PaperTool, onClick: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val containerColor = remember(isDark) { if (isDark) Color(0xFF0A0A0A) else Color.White }
    val borderColor = remember(isDark) { if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(0.03f) }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.2f),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
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
            Column {
                Text(
                    text = tool.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.3).sp,
                    lineHeight = 14.sp
                )
                Text(
                    text = tool.description.uppercase(),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray.copy(alpha = 0.5f),
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}
