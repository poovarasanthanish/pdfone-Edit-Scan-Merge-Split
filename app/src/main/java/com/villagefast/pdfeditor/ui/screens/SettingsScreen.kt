package com.villagefast.pdfeditor.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.villagefast.pdfeditor.R
import com.villagefast.pdfeditor.data.SettingsManager
import com.villagefast.pdfeditor.data.db.AppDatabase
import com.villagefast.pdfeditor.ui.components.PaperBottomBar
import com.villagefast.pdfeditor.ui.theme.PaperPink
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onBackClick: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showRetentionDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val themeMode by settingsManager.themeMode
    val language by settingsManager.language
    val author by settingsManager.defaultAuthor
    val retention by settingsManager.historyRetention
    var cacheSize by remember { mutableStateOf("0.0 B") }

    LaunchedEffect(Unit) {
        cacheSize = getCacheSize(context)
    }

    Scaffold(
        bottomBar = {
            PaperBottomBar(
                currentScreen = "settings",
                onNavigate = onNavigate,
                onPlusClick = { /* File pick if needed */ }
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    SettingsViewHeader(onBackClick = onBackClick)
                }

                // 1. APPEARANCE
                item {
                    SettingsGroup(title = stringResource(R.string.appearance)) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ThemeOption(
                                    name = stringResource(R.string.theme_system),
                                    icon = Icons.Outlined.SettingsSuggest,
                                    selected = themeMode == "System Default",
                                    onClick = { settingsManager.setThemeMode("System Default") },
                                    modifier = Modifier.weight(1f)
                                )
                                ThemeOption(
                                    name = stringResource(R.string.theme_light),
                                    icon = Icons.Outlined.LightMode,
                                    selected = themeMode == "Light",
                                    onClick = { settingsManager.setThemeMode("Light") },
                                    modifier = Modifier.weight(1f)
                                )
                                ThemeOption(
                                    name = stringResource(R.string.theme_dark),
                                    icon = Icons.Outlined.DarkMode,
                                    selected = themeMode == "Dark",
                                    onClick = { settingsManager.setThemeMode("Dark") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
                            SettingsItem(
                                icon = Icons.Default.Language,
                                title = stringResource(R.string.app_language),
                                subtitle = language,
                                onClick = { showLanguageDialog = true }
                            )
                        }
                    }
                }

                // 2. PREFERENCES
                item {
                    SettingsGroup(title = stringResource(R.string.preferences)) {
                        Column {
                            Text(
                                text = stringResource(R.string.default_author),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedTextField(
                                value = author,
                                onValueChange = { settingsManager.setDefaultAuthor(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.default_author), fontSize = 13.sp) },
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PaperPink,
                                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.2f)
                                ),
                                singleLine = true
                            )
                            Text(
                                text = stringResource(R.string.author_metadata_desc),
                                fontSize = 10.sp,
                                color = Color.Gray.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                // 3. PRIVACY & STORAGE
                item {
                    SettingsGroup(title = stringResource(R.string.privacy_storage)) {
                        Column {
                            SettingsItem(
                                icon = Icons.Default.History,
                                title = stringResource(R.string.auto_delete_history),
                                subtitle = retention,
                                onClick = { showRetentionDialog = true }
                            )
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
                            SettingsItem(
                                icon = Icons.Default.Storage,
                                title = stringResource(R.string.cache_size),
                                subtitle = cacheSize,
                                onClick = { /* View only */ }
                            )
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
                            SettingsItem(
                                icon = Icons.Default.Cached,
                                title = stringResource(R.string.clear_cache),
                                subtitle = stringResource(R.string.clear_cache_desc),
                                onClick = { 
                                    clearAppCache(context)
                                    scope.launch { cacheSize = getCacheSize(context) }
                                }
                            )
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
                            SettingsItem(
                                icon = Icons.Default.DeleteSweep,
                                title = stringResource(R.string.clear_history),
                                subtitle = stringResource(R.string.wipe_history_desc),
                                onClick = { showClearHistoryDialog = true }
                            )
                        }
                    }
                }

                // 4. COMMUNITY & LEGAL
                item {
                    SettingsGroup(title = stringResource(R.string.community_legal)) {
                        Column {
                            SettingsItem(
                                icon = Icons.Default.Info,
                                title = stringResource(R.string.about_title),
                                subtitle = stringResource(R.string.about_desc),
                                onClick = { /* Show About Info */ }
                            )
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
                            SettingsItem(
                                icon = Icons.Default.Star,
                                title = stringResource(R.string.rate_us),
                                subtitle = stringResource(R.string.rate_us_desc),
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, "market://details?id=${context.packageName}".toUri())
                                    context.startActivity(intent)
                                }
                            )
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
                            SettingsItem(
                                icon = Icons.Default.Policy,
                                title = stringResource(R.string.privacy_policy),
                                subtitle = stringResource(R.string.privacy_policy_desc),
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, "https://poovarasanthanish.github.io/pdfoneprivacypolicy/".toUri())
                                    context.startActivity(intent)
                                }
                            )
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))
                            SettingsItem(
                                icon = Icons.Default.Favorite,
                                title = stringResource(R.string.support_dev),
                                subtitle = stringResource(R.string.support_dev_desc),
                                color = PaperPink,
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, "https://www.buymeacoffee.com/".toUri())
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "PDF ONE V1.1",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = Color.Gray.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }

    if (showRetentionDialog) {
        AlertDialog(
            onDismissRequest = { showRetentionDialog = false },
            title = { Text(stringResource(R.string.auto_delete_history), fontWeight = FontWeight.Black) },
            text = {
                val scrollState = rememberScrollState()
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    listOf("Unlimited", "24 Hours", "7 Days", "30 Days").forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                settingsManager.setHistoryRetention(option)
                                showRetentionDialog = false
                            }.padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = option == retention,
                                onClick = {
                                    settingsManager.setHistoryRetention(option)
                                    showRetentionDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = PaperPink)
                            )
                            Text(
                                text = option,
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.select_language), fontWeight = FontWeight.Black) },
            text = {
                val scrollState = rememberScrollState()
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    listOf("English", "Spanish", "French", "German", "Hindi", "Tamil", "Portuguese", "Japanese", "Arabic", "Indonesian", "Korean", "Vietnamese").forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                settingsManager.setLanguage(option)
                                showLanguageDialog = false
                            }.padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = option == language,
                                onClick = {
                                    settingsManager.setLanguage(option)
                                    showLanguageDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = PaperPink)
                            )
                            Text(
                                text = option,
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.clear_history) + "?", fontWeight = FontWeight.Black) },
            text = { Text("This will permanently delete all session history. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        AppDatabase.getDatabase(context).recentFileDao().clearAll()
                        showClearHistoryDialog = false
                    }
                }) {
                    Text(stringResource(R.string.wipe_all), color = PaperPink, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun SettingsViewHeader(onBackClick: () -> Unit) {
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
                    text = "SETTINGS",
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
                text = "APP CONFIGURATION",
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
fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = Color.Gray.copy(alpha = 0.6f),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = if (isDark) Color(0xFF0A0A0A) else Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(0.03f))
        ) {
            Box(modifier = Modifier.padding(20.dp)) {
                content()
            }
        }
    }
}

@Composable
fun ThemeOption(
    name: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.background == Color.Black
    Surface(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) PaperPink else (if (isDarkTheme) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.02f)),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = name,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else Color.Gray
            )
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color = PaperPink,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(text = subtitle, fontSize = 10.sp, color = Color.Gray.copy(alpha = 0.6f))
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.Gray.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun clearAppCache(context: Context) {
    try {
        context.cacheDir.deleteRecursively()
        context.externalCacheDir?.deleteRecursively()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getCacheSize(context: Context): String {
    var size: Long = 0
    size += getDirSize(context.cacheDir)
    size += getDirSize(context.externalCacheDir)
    size += getDirSize(context.filesDir)
    size += getDirSize(context.getExternalFilesDir(null))
    return formatSize(size)
}

private fun getDirSize(dir: File?): Long {
    var size: Long = 0
    if (dir != null && dir.isDirectory) {
        val files = dir.listFiles()
        if (files != null) {
            for (file in files) {
                size += if (file.isFile) file.length() else getDirSize(file)
            }
        }
    } else if (dir != null && dir.isFile) {
        size += dir.length()
    }
    return size
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0.0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(java.util.Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
