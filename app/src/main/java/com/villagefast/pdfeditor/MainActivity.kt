package com.villagefast.pdfeditor

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.gms.ads.MobileAds
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.villagefast.pdfeditor.ads.AdManager
import com.villagefast.pdfeditor.data.PremiumManager
import com.villagefast.pdfeditor.data.RemoteConfigManager
import com.villagefast.pdfeditor.data.SettingsManager
import com.villagefast.pdfeditor.ui.screens.*
import com.villagefast.pdfeditor.ui.theme.PdfeditorTheme

class MainActivity : ComponentActivity() {
    private lateinit var premiumManager: PremiumManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var appUpdateManager: AppUpdateManager
    private val UPDATE_REQUEST_CODE = 123
    private var initialIntentUri = mutableStateOf<android.net.Uri?>(null)

    override fun attachBaseContext(newBase: Context) {
        val sm = SettingsManager(newBase)
        val locale = sm.getLocale(sm.language.value)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        // We use the original newBase but update its configuration
        // This avoids creating a new ContextImpl that breaks PrintManager's Activity check
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        settingsManager = SettingsManager(this)
        
        // Ensure Locale.setDefault is also called for components that rely on it
        val locale = settingsManager.getLocale(settingsManager.language.value)
        java.util.Locale.setDefault(locale)

        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)

        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)

        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForUpdates()

        RemoteConfigManager.fetchAndActivate {
            MobileAds.initialize(this) {
                AdManager.loadInterstitial(this)
            }
        }

        premiumManager = PremiumManager(this)
        
        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            val themeMode by settingsManager.themeMode
            PdfeditorTheme(themeMode = themeMode) {
                PdfApp(premiumManager, settingsManager, initialIntentUri.value)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent == null) return
        when (intent.action) {
            android.content.Intent.ACTION_VIEW -> {
                initialIntentUri.value = intent.data
            }
            android.content.Intent.ACTION_SEND -> {
                if (intent.type == "application/pdf") {
                    val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
                    }
                    initialIntentUri.value = uri
                }
            }
        }
    }

    private fun checkForUpdates() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.IMMEDIATE,
                        this,
                        UPDATE_REQUEST_CODE
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.IMMEDIATE,
                        this,
                        UPDATE_REQUEST_CODE
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UPDATE_REQUEST_CODE) {
            if (resultCode != RESULT_OK) {
                Toast.makeText(this, "Update failed or cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun PdfApp(premiumManager: PremiumManager, settingsManager: SettingsManager, initialUri: android.net.Uri? = null) {
    val navController = rememberNavController()
    val isPremium by premiumManager.isPremium
    val showAds = !isPremium
    
    LaunchedEffect(showAds) {
        android.util.Log.d("PdfApp", "showAds value: $showAds")
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null) {
            val encodedUri = android.net.Uri.encode(initialUri.toString())
            navController.navigate("viewer?uri=$encodedUri")
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                isPremium = isPremium,
                onFeatureClick = { route ->
                    val isTab = route in listOf("home", "tools", "history", "settings")
                    if (route == "ai_tools" && !isPremium) {
                        navController.navigate("premium")
                    } else {
                        navController.navigate(route) {
                            if (isTab) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                }
            )
        }
        composable("tools") {
            AllToolsScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onToolClick = { route -> navController.navigate(route) }
            )
        }
        composable(
            route = "viewer?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType; nullable = true })
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri")
            val uri = uriString?.let { android.net.Uri.parse(it) }
            PdfViewerScreen(initialUri = uri, showAds = showAds, onBackClick = { navController.popBackStack() })
        }
        composable("image_to_pdf") {
            ImageToPdfScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { uri, op ->
                    val encodedUri = android.net.Uri.encode(uri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable("merge") {
            MergePdfScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { uri, op ->
                    val encodedUri = android.net.Uri.encode(uri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable("split") {
            SplitPdfScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { uri, op ->
                    val encodedUri = android.net.Uri.encode(uri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable("crop") {
            CropScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { uri, op ->
                    val encodedUri = android.net.Uri.encode(uri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable(
            route = "extract_pages?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType; nullable = true })
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri")
            val uri = uriString?.let { android.net.Uri.parse(it) }
            ExtractPagesScreen(
                initialUri = uri,
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { resUri, op ->
                    val encodedUri = android.net.Uri.encode(resUri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable("compress") {
            CompressPdfScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { uri, op ->
                    val encodedUri = android.net.Uri.encode(uri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable("scanner") {
            ScannerScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { uri, op ->
                    val encodedUri = android.net.Uri.encode(uri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable(
            route = "editor?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType; nullable = true })
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri")
            val uri = uriString?.let { android.net.Uri.parse(it) }
            UnifiedPdfEditorScreen(
                initialUri = uri,
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onToolClick = { route, toolUri ->
                    val encodedUri = toolUri?.let { android.net.Uri.encode(it.toString()) }
                    val finalRoute = if (encodedUri != null) "$route?uri=$encodedUri" else route
                    navController.navigate(finalRoute)
                },
                onResult = { resUri, op ->
                    val encodedUri = android.net.Uri.encode(resUri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable(
            route = "annotate?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType; nullable = true })
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri")
            val uri = uriString?.let { android.net.Uri.parse(it) }
            AnnotateScreen(
                initialUri = uri,
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { resUri, op ->
                    val encodedUri = android.net.Uri.encode(resUri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable(
            route = "fill_sign?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType; nullable = true })
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri")
            val uri = uriString?.let { android.net.Uri.parse(it) }
            FillSignScreen(
                initialUri = uri,
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { resUri, op ->
                    val encodedUri = android.net.Uri.encode(resUri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable(
            route = "organize?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType; nullable = true })
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri")
            val uri = uriString?.let { android.net.Uri.parse(it) }
            OrganizePagesScreen(
                initialUri = uri,
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { resUri, op ->
                    val encodedUri = android.net.Uri.encode(resUri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable(
            route = "delete_pages?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType; nullable = true })
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri")
            val uri = uriString?.let { android.net.Uri.parse(it) }
            OrganizePagesScreen(
                initialUri = uri,
                mode = OrganizeMode.DELETE,
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { resUri, op ->
                    val encodedUri = android.net.Uri.encode(resUri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable(
            route = "rotate_pages?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType; nullable = true })
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri")
            val uri = uriString?.let { android.net.Uri.parse(it) }
            OrganizePagesScreen(
                initialUri = uri,
                mode = OrganizeMode.ROTATE,
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { resUri, op ->
                    val encodedUri = android.net.Uri.encode(resUri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable(
            route = "reorder_pages?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType; nullable = true })
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri")
            val uri = uriString?.let { android.net.Uri.parse(it) }
            OrganizePagesScreen(
                initialUri = uri,
                mode = OrganizeMode.ORGANIZE,
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { resUri, op ->
                    val encodedUri = android.net.Uri.encode(resUri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable("pdf_to_image") {
            PdfToImageScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { uri, op ->
                    val encodedUri = android.net.Uri.encode(uri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable("pdf_to_zip") {
            PdfToZipScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { uri, op ->
                    val encodedUri = android.net.Uri.encode(uri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable("compare") {
            ComparePdfScreen(showAds = showAds, onBackClick = { navController.popBackStack() })
        }
        composable("print") {
            PrintScreen(showAds = showAds, onBackClick = { navController.popBackStack() })
        }
        composable("nup") {
            ImpositionScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { resUri, op ->
                    val encodedUri = android.net.Uri.encode(resUri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable("voice_reader") {
            VoiceReaderScreen(showAds = showAds, onBackClick = { navController.popBackStack() })
        }
        composable("web_to_pdf") {
            WebToPdfScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { resUri, op ->
                    val encodedUri = android.net.Uri.encode(resUri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable("qr_to_pdf") {
            QrToPdfScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { uri, op ->
                    val encodedUri = android.net.Uri.encode(uri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable("ai_chat") {
            AiChatScreen(showAds = showAds, onBackClick = { navController.popBackStack() })
        }
        composable("summarize") {
            AiSummarizeScreen(showAds = showAds, onBackClick = { navController.popBackStack() })
        }
        composable(
            route = "edit_details?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType; nullable = true })
        ) {
            PlaceholderScreen("Edit PDF", "Advanced PDF editing tools coming soon!") {
                navController.popBackStack()
            }
        }
        composable("mark_details") {
            PlaceholderScreen("Mark/Annotate", "PDF annotation and drawing tools coming soon!") {
                navController.popBackStack()
            }
        }
        composable("metadata") {
            MetadataEditorScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { resUri, op ->
                    val encodedUri = android.net.Uri.encode(resUri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable("bates") {
            BatesNumberingScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { resUri, op ->
                    val encodedUri = android.net.Uri.encode(resUri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable(
            route = "page_numbering?uri={uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType; nullable = true })
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri")
            val uri = uriString?.let { android.net.Uri.parse(it) }
            PageNumberingScreen(
                initialUri = uri,
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { resUri, op ->
                    val encodedUri = android.net.Uri.encode(resUri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable("flatten") {
            FlattenPdfScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { resUri, op ->
                    val encodedUri = android.net.Uri.encode(resUri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable("watermark") {
            WatermarkScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { resUri, op ->
                    val encodedUri = android.net.Uri.encode(resUri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable("repair_pdf") {
            RepairPdfScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { resUri, op ->
                    val encodedUri = android.net.Uri.encode(resUri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable("security") {
            SecurityScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onResult = { resUri, op ->
                    val encodedUri = android.net.Uri.encode(resUri.toString())
                    navController.navigate("result?uri=$encodedUri&op=$op")
                }
            )
        }
        composable("ai_tools") {
            AiToolsScreen(
                showAds = showAds,
                onBackClick = { navController.popBackStack() },
                onToolClick = { route -> navController.navigate(route) }
            )
        }
        composable("premium") {
            PremiumScreen(
                onBackClick = { navController.popBackStack() },
                onUpgradeClick = {
                    premiumManager.setPremium(true)
                    navController.popBackStack()
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                settingsManager = settingsManager,
                onBackClick = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        composable("history") {
            HistoryScreen(
                onBackClick = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        composable(
            route = "result?uri={uri}&op={op}",
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("op") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri") ?: ""
            val uri = android.net.Uri.parse(android.net.Uri.decode(uriString))
            val op = backStackEntry.arguments?.getString("op") ?: "Operation"
            ResultScreen(
                fileUri = uri,
                operationName = op,
                showAds = showAds,
                onBackToHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
    }
}
