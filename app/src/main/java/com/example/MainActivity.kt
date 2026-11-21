package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ai.OceanAiRepository
import com.example.data.config.CountryRegistry
import com.example.data.config.PreferencesRepository
import com.example.data.firebase.configureAppCheck
import com.example.data.repository.OceanRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.OceanStatusPill
import com.example.ui.screens.AdminConfigScreen
import com.example.ui.screens.AtSeaScreen
import com.example.ui.screens.ComplianceScreen
import com.example.ui.screens.FleetScreen
import com.example.ui.screens.TraceabilityScreen
import com.example.ui.theme.DeepOceanNavy
import com.example.ui.theme.OceanAiUUTheme
import com.example.ui.viewmodel.OceanViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_AT_SEA = "at_sea"
const val ROUTE_FLEET = "fleet"
const val ROUTE_COMPLIANCE = "compliance"
const val ROUTE_EXPORT = "export"
const val ROUTE_ADMIN = "admin"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Step 1: Configure Firebase App Check with launch intent before UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            OceanAiUUTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        val uid = user.uid
        val displayName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: user.phoneNumber?.takeIf { it.isNotBlank() }
            ?: "OceanAiUU Captain"
        val contactLabel = user.email?.takeIf { it.isNotBlank() }
            ?: user.phoneNumber?.takeIf { it.isNotBlank() }
            ?: "Authenticated Operator"
        key(uid) {
            OceanWorkspaceShell(
                currentUserId = uid,
                operatorName = displayName,
                operatorEmail = contactLabel
            )
        }
    }
}

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OceanWorkspaceShell(
    currentUserId: String,
    operatorName: String,
    operatorEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: OceanViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                OceanViewModel(
                    repository = OceanRepository(db),
                    aiRepository = OceanAiRepository(),
                    prefsRepository = PreferencesRepository(app),
                    currentUserId = currentUserId,
                    actorDisplayName = operatorName
                )
            }
        }
    )

    val countryCode by viewModel.countryCode.collectAsStateWithLifecycle()
    val activeLanguage by viewModel.language.collectAsStateWithLifecycle()
    val countryPack = remember(countryCode) { CountryRegistry.byCode(countryCode) }
    val labels = remember(activeLanguage) { CountryRegistry.labels(activeLanguage) }

    val vesselsState by viewModel.vesselsState.collectAsStateWithLifecycle()
    val tripsState by viewModel.tripsState.collectAsStateWithLifecycle()
    val selectedTripId by viewModel.selectedTripId.collectAsStateWithLifecycle()
    val haulsState by viewModel.selectedTripHaulsState.collectAsStateWithLifecycle()
    val lotsState by viewModel.lotsState.collectAsStateWithLifecycle()
    val auditLogsState by viewModel.auditLogsState.collectAsStateWithLifecycle()
    val speciesAiState by viewModel.speciesAiState.collectAsStateWithLifecycle()
    val iuuAiState by viewModel.iuuAiState.collectAsStateWithLifecycle()
    val gpsFix by viewModel.gpsFix.collectAsStateWithLifecycle()
    val isSimulatedOffline by viewModel.simulatedAtSeaOffline.collectAsStateWithLifecycle()
    val feedback by viewModel.actionFeedback.collectAsStateWithLifecycle()
    val isNetworkOnline by remember(context) {
        viewModel.observeNetworkConnected(context)
    }.collectAsStateWithLifecycle(initialValue = true)

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        val msg = feedback
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_AT_SEA

    val navItems = listOf(
        NavDestinationItem(ROUTE_AT_SEA, labels.tabAtSea, Icons.Default.Sailing),
        NavDestinationItem(ROUTE_FLEET, labels.tabFleet, Icons.Default.DirectionsBoat),
        NavDestinationItem(ROUTE_COMPLIANCE, labels.tabCompliance, Icons.Default.Gavel),
        NavDestinationItem(ROUTE_EXPORT, labels.tabExport, Icons.Default.Inventory2),
        NavDestinationItem(ROUTE_ADMIN, labels.tabAdmin, Icons.Default.AdminPanelSettings)
    )

    val layoutDirection = if (activeLanguage.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 700.dp

            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = DeepOceanNavy,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("OceanAiUU", style = MaterialTheme.typography.titleLarge)
                                OceanStatusPill(
                                    label = "${countryPack.code} · ${activeLanguage.code.uppercase()}",
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar {
                            navItems.forEach { item ->
                                NavigationBarItem(
                                    selected = currentRoute == item.route,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_${item.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail {
                            navItems.forEach { item ->
                                NavigationRailItem(
                                    selected = currentRoute == item.route,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) }
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_AT_SEA,
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 840.dp)
                        ) {
                            composable(ROUTE_AT_SEA) {
                                AtSeaScreen(
                                    countryPack = countryPack,
                                    labels = labels,
                                    vesselsState = vesselsState,
                                    tripsState = tripsState,
                                    selectedTripId = selectedTripId,
                                    haulsState = haulsState,
                                    speciesAiState = speciesAiState,
                                    gpsFix = gpsFix,
                                    isNetworkOnline = isNetworkOnline,
                                    isSimulatedOffline = isSimulatedOffline,
                                    actorDisplayName = operatorName,
                                    onSelectTrip = viewModel::selectTrip,
                                    onToggleSimulatedOffline = viewModel::toggleSimulatedAtSeaOffline,
                                    onSyncPendingTrips = { pending ->
                                        pending.forEach { viewModel.syncTripAtPort(it.id) }
                                    },
                                    onRefreshGps = { viewModel.refreshDeviceGps(context, countryPack) },
                                    onStartTrip = viewModel::startTrip,
                                    onLogHaul = viewModel::logHaul,
                                    onIdentifySpecies = { bmp -> viewModel.identifySpecies(bmp, countryPack) },
                                    onClearSpeciesAi = viewModel::clearSpeciesAi,
                                    onLandAndCloseTrip = viewModel::landAndCloseTrip,
                                    onSeedPilotData = { viewModel.seedPilotWorkspace(countryPack) },
                                    onNavigateToFleet = {
                                        navController.navigate(ROUTE_FLEET)
                                    }
                                )
                            }
                            composable(ROUTE_FLEET) {
                                FleetScreen(
                                    countryPack = countryPack,
                                    labels = labels,
                                    vesselsState = vesselsState,
                                    tripsState = tripsState,
                                    onRegisterVessel = viewModel::addVessel,
                                    onSeedPilotData = { viewModel.seedPilotWorkspace(countryPack) }
                                )
                            }
                            composable(ROUTE_COMPLIANCE) {
                                ComplianceScreen(
                                    countryPack = countryPack,
                                    labels = labels,
                                    tripsState = tripsState,
                                    selectedTripHaulsState = haulsState,
                                    iuuAiState = iuuAiState,
                                    onSelectTripForAudit = { trip -> viewModel.selectTrip(trip.id) },
                                    onRunAiIuuAudit = { trip, hauls -> viewModel.runAiIuuAudit(trip, hauls, countryPack) },
                                    onIssueCertificate = viewModel::verifyTripAndIssueCertificate,
                                    onFlagIuuCase = viewModel::flagTripIuuCase
                                )
                            }
                            composable(ROUTE_EXPORT) {
                                TraceabilityScreen(
                                    countryPack = countryPack,
                                    labels = labels,
                                    tripsState = tripsState,
                                    lotsState = lotsState,
                                    onCreateLot = viewModel::createProcessorLot
                                )
                            }
                            composable(ROUTE_ADMIN) {
                                AdminConfigScreen(
                                    countryPack = countryPack,
                                    activeLanguage = activeLanguage,
                                    labels = labels,
                                    operatorName = operatorName,
                                    operatorEmail = operatorEmail,
                                    auditLogsState = auditLogsState,
                                    onSelectCountry = viewModel::setCountry,
                                    onSelectLanguage = viewModel::setLanguage,
                                    onSeedPilotData = { viewModel.seedPilotWorkspace(countryPack) },
                                    onSignOut = {
                                        signOutUser(credentialManager, scope)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
