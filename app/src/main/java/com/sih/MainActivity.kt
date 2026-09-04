package com.sih

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sih.ui.screens.auth.LoginScreen
import com.sih.ui.screens.history.HistoryScreen
import com.sih.ui.screens.history.ProductHistoryScreen
import com.sih.ui.screens.history.ReportScreen
import com.sih.ui.screens.home.HomeScreen
import com.sih.ui.screens.inspection.AnalysisScreen
import com.sih.ui.screens.inspection.CameraScreen
import com.sih.ui.screens.inspection.ComparisonScreen
import com.sih.ui.screens.inspection.ComplianceResultScreen
import com.sih.ui.screens.inspection.HumanReviewScreen
import com.sih.ui.screens.inspection.NewInspectionScreen
import com.sih.ui.screens.inspection.ViolationEvidenceScreen
import com.sih.ui.screens.profile.LanguageSelectionScreen
import com.sih.ui.screens.profile.ProfileScreen
import com.sih.ui.theme.Sih_34Theme
import com.sih.util.Localization

import com.sih.network.ApiClient

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            Sih_34Theme {
                Sih_34App()
            }
        }
    }
}

@Composable
fun Sih_34App() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination?.route

    var selectedLanguage by remember { mutableStateOf("English") }
    var activeImageUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var currentCommodityName by remember { mutableStateOf("") }

    // Hide bottom nav for screens outside main flow
    val screensWithBottomNav = listOf(
        AppDestinations.HOME.route,
        AppDestinations.INSPECT.route,
        AppDestinations.HISTORY.route,
        AppDestinations.PROFILE.route
    )
    val showBottomNav = currentDestination in screensWithBottomNav

    val navHostContent = @Composable {
        NavHostMain(
            navController = navController,
            selectedLanguage = selectedLanguage,
            onLanguageChange = { selectedLanguage = it },
            activeImageUris = activeImageUris,
            onActiveImageUrisChange = { activeImageUris = it },
            currentCommodityName = currentCommodityName,
            onCommodityNameChange = { currentCommodityName = it }
        )
    }

    if (showBottomNav) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                AppDestinations.entries.filter { it.showInBottomNav }.forEach { destination ->
                    item(
                        icon = {
                            Icon(
                                destination.icon!!,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(Localization.getString(destination.route, selectedLanguage)) },
                        selected = currentDestination == destination.route,
                        onClick = {
                            if (currentDestination != destination.route) {
                                if (destination.route == AppDestinations.HOME.route) {
                                    navController.navigate(AppDestinations.HOME.route) {
                                        popUpTo(AppDestinations.HOME.route) {
                                            inclusive = false
                                        }
                                        launchSingleTop = true
                                    }
                                } else {
                                    navController.navigate(destination.route) {
                                        popUpTo(AppDestinations.HOME.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                    )
                }
            }
        ) {
            navHostContent()
        }
    } else {
        navHostContent()
    }
}

@Composable
fun NavHostMain(
    navController: NavHostController, 
    selectedLanguage: String, 
    onLanguageChange: (String) -> Unit,
    activeImageUris: List<android.net.Uri>,
    onActiveImageUrisChange: (List<android.net.Uri>) -> Unit,
    currentCommodityName: String,
    onCommodityNameChange: (String) -> Unit
) {
    val context = LocalContext.current
    
    NavHost(
        navController = navController,
        startDestination = AppDestinations.LOGIN.route
    ) {
        composable(AppDestinations.LOGIN.route) {
            LoginScreen(
                selectedLanguage = selectedLanguage,
                onLoginSuccess = {
                    navController.navigate(AppDestinations.HOME.route) {
                        popUpTo(AppDestinations.LOGIN.route) { inclusive = true }
                    }
                }
            )
        }
        composable(AppDestinations.HOME.route) {
            HomeScreen(
                selectedLanguage = selectedLanguage,
                onNewInspection = { 
                    navController.navigate(AppDestinations.INSPECT.route) {
                        popUpTo(AppDestinations.HOME.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onInspectionClick = { 
                    navController.navigate(AppDestinations.PRODUCT_HISTORY.route)
                }
            )
        }
        composable(AppDestinations.INSPECT.route) {
            NewInspectionScreen(
                selectedLanguage = selectedLanguage,
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(AppDestinations.HOME.route) {
                            popUpTo(AppDestinations.HOME.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                onScanCamera = { name -> 
                    com.sih.repository.InspectionRepository.clearCurrentInspection()
                    com.sih.repository.InspectionRepository.activeCommodityName = name
                    onCommodityNameChange(name)
                    navController.navigate(AppDestinations.CAMERA.route) 
                },
                onImagesSelected = { uris, name ->
                    com.sih.repository.InspectionRepository.clearCurrentInspection()
                    com.sih.repository.InspectionRepository.activeImageUris = uris
                    com.sih.repository.InspectionRepository.activeCommodityName = name
                    onCommodityNameChange(name)
                    onActiveImageUrisChange(uris)
                    navController.navigate(AppDestinations.ANALYSIS.route)
                },
                onOnlineListing = { navController.navigate(AppDestinations.COMPARISON.route) }
            )
        }
        composable(AppDestinations.CAMERA.route) {
            CameraScreen(
                selectedLanguage = selectedLanguage,
                onClose = { navController.popBackStack() },
                onCapture = { uri ->
                    com.sih.repository.InspectionRepository.clearCurrentInspection()
                    com.sih.repository.InspectionRepository.activeImageUris = listOf(uri)
                    onActiveImageUrisChange(listOf(uri))
                    navController.navigate(AppDestinations.ANALYSIS.route)
                },
                onCaptureComplete = { uris ->
                    com.sih.repository.InspectionRepository.clearCurrentInspection()
                    com.sih.repository.InspectionRepository.activeImageUris = uris
                    onActiveImageUrisChange(uris)
                    navController.navigate(AppDestinations.ANALYSIS.route)
                }
            )
        }
        composable(AppDestinations.ANALYSIS.route) {
            val effectiveUris = if (activeImageUris.isNotEmpty()) activeImageUris else com.sih.repository.InspectionRepository.activeImageUris
            val effectiveCommodity = if (currentCommodityName.isNotBlank()) currentCommodityName else (com.sih.repository.InspectionRepository.activeCommodityName ?: "")
            AnalysisScreen(
                selectedLanguage = selectedLanguage,
                imageUris = effectiveUris,
                commodityName = effectiveCommodity,
                onAnalysisComplete = {
                    navController.navigate(AppDestinations.RESULT.route) {
                        popUpTo(AppDestinations.ANALYSIS.route) { inclusive = true }
                    }
                }
            )
        }
        composable(AppDestinations.RESULT.route) {
            ComplianceResultScreen(
                selectedLanguage = selectedLanguage,
                onNavigateBack = { navController.navigate(AppDestinations.HOME.route) },
                onViewEvidence = { navController.navigate(AppDestinations.EVIDENCE.route) },
                onViewReport = { navController.navigate(AppDestinations.SIGN_OFF.route) }
            )
        }
        composable(AppDestinations.SIGN_OFF.route) {
            com.sih.ui.screens.inspection.SignOffScreen(
                selectedLanguage = selectedLanguage,
                onNavigateBack = { navController.popBackStack() },
                onFinalized = {
                    navController.navigate(AppDestinations.REPORT.route) {
                        popUpTo(AppDestinations.SIGN_OFF.route) { inclusive = true }
                    }
                }
            )
        }
        composable(AppDestinations.EVIDENCE.route) {
            ViolationEvidenceScreen(
                selectedLanguage = selectedLanguage,
                onNavigateBack = { navController.popBackStack() },
                onConfirm = { navController.navigate(AppDestinations.REVIEW.route) },
                onMarkCompliant = { navController.popBackStack() },
                onRescan = { navController.navigate(AppDestinations.CAMERA.route) }
            )
        }
        composable(AppDestinations.REVIEW.route) {
            HumanReviewScreen(
                selectedLanguage = selectedLanguage,
                onNavigateBack = { navController.popBackStack() },
                onPresent = { navController.popBackStack() },
                onMissing = { navController.popBackStack() },
                onRescan = { navController.navigate(AppDestinations.CAMERA.route) }
            )
        }
        composable(AppDestinations.COMPARISON.route) {
            ComparisonScreen(
                selectedLanguage = selectedLanguage,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(AppDestinations.HISTORY.route) {
            HistoryScreen(
                selectedLanguage = selectedLanguage,
                onInspectionClick = { navController.navigate(AppDestinations.REPORT.route) }
            )
        }
        composable(AppDestinations.PRODUCT_HISTORY.route) {
            ProductHistoryScreen(
                selectedLanguage = selectedLanguage,
                onNavigateBack = { navController.popBackStack() },
                onStartInspection = { navController.navigate(AppDestinations.INSPECT.route) }
            )
        }
        composable(AppDestinations.REPORT.route) {
            ReportScreen(
                selectedLanguage = selectedLanguage,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(AppDestinations.PROFILE.route) {
            ProfileScreen(
                currentLanguage = selectedLanguage,
                onLanguageClick = { navController.navigate(AppDestinations.LANGUAGE.route) },
                onLogout = {
                    navController.navigate(AppDestinations.LOGIN.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(AppDestinations.LANGUAGE.route) {
            LanguageSelectionScreen(
                onNavigateBack = { navController.popBackStack() },
                currentLanguage = selectedLanguage,
                onLanguageSelected = { 
                    onLanguageChange(it)
                    navController.popBackStack()
                }
            )
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector?,
    val route: String,
    val showInBottomNav: Boolean = true
) {
    LOGIN("Login", null, "login", false),
    HOME("Home", Icons.Default.Home, "home"),
    INSPECT("Inspect", Icons.Default.QrCodeScanner, "inspect"),
    HISTORY("History", Icons.Default.History, "history"),
    PROFILE("Profile", Icons.Default.Person, "profile"),
    CAMERA("Camera", null, "camera", false),
    ANALYSIS("Analysis", null, "analysis", false),
    RESULT("Result", null, "result", false),
    EVIDENCE("Evidence", null, "evidence", false),
    REVIEW("Review", null, "review", false),
    COMPARISON("Comparison", null, "comparison", false),
    PRODUCT_HISTORY("Product History", null, "product_history", false),
    REPORT("Report", null, "report", false),
    SIGN_OFF("Sign Off", null, "sign_off", false),
    LANGUAGE("Language", null, "language", false),
}
