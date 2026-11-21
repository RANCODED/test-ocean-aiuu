package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.config.CountryPack
import com.example.data.config.LocalizedLabels
import com.example.data.model.Haul
import com.example.data.model.HaulEventType
import com.example.data.model.SpeciesIdentificationResult
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.data.model.Vessel
import com.example.ui.components.EmptyWorkspaceCard
import com.example.ui.components.ErrorRetryBanner
import com.example.ui.components.OceanStatusPill
import com.example.ui.components.OfflineSyncStrip
import com.example.ui.components.tripStatusPillColors
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CoralAlertContainer
import com.example.ui.theme.DeepOceanNavy
import com.example.ui.theme.MaritimeAmber
import com.example.ui.theme.SeafoamMint
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.GpsFix
import com.example.ui.viewmodel.UiState
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtSeaScreen(
    countryPack: CountryPack,
    labels: LocalizedLabels,
    vesselsState: UiState<List<Vessel>>,
    tripsState: UiState<List<Trip>>,
    selectedTripId: String?,
    haulsState: UiState<List<Haul>>,
    speciesAiState: AiTaskState<SpeciesIdentificationResult>,
    gpsFix: GpsFix?,
    isNetworkOnline: Boolean,
    isSimulatedOffline: Boolean,
    actorDisplayName: String,
    onSelectTrip: (String?) -> Unit,
    onToggleSimulatedOffline: () -> Unit,
    onSyncPendingTrips: (List<Trip>) -> Unit,
    onRefreshGps: () -> Unit,
    onStartTrip: (Vessel, String, Int, String, String, String, Boolean) -> Unit,
    onLogHaul: (Trip, Int, String, String, String, Int, Double, Double, Double, String, Boolean, String, HaulEventType, Boolean, String?, Boolean) -> Unit,
    onIdentifySpecies: (Bitmap) -> Unit,
    onClearSpeciesAi: () -> Unit,
    onLandAndCloseTrip: (Trip, String, Double) -> Unit,
    onSeedPilotData: () -> Unit,
    onNavigateToFleet: () -> Unit
) {
    val context = LocalContext.current
    var showStartTripSheet by remember { mutableStateOf(false) }
    var showLogHaulSheet by remember { mutableStateOf(false) }
    var showLandTripSheet by remember { mutableStateOf(false) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onRefreshGps()
        }
    }

    LaunchedEffect(countryPack.code) {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            onRefreshGps()
        }
    }

    val vessels = (vesselsState as? UiState.Success)?.data.orEmpty()
    val trips = (tripsState as? UiState.Success)?.data.orEmpty()
    val effectiveTrip = trips.firstOrNull { it.id == selectedTripId }
        ?: trips.firstOrNull { it.statusEnum() == TripStatus.AT_SEA }
        ?: trips.firstOrNull()

    LaunchedEffect(effectiveTrip?.id) {
        if (effectiveTrip != null && effectiveTrip.id != selectedTripId) {
            onSelectTrip(effectiveTrip.id)
        }
    }

    val pendingSyncTrips = trips.filter { it.syncPending }
    val effectiveOffline = !isNetworkOnline || isSimulatedOffline

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OfflineSyncStrip(
                labels = labels,
                isNetworkOnline = isNetworkOnline,
                isSimulatedOffline = isSimulatedOffline,
                pendingSyncCount = pendingSyncTrips.size,
                onToggleSimulatedOffline = onToggleSimulatedOffline,
                onSyncPending = { onSyncPendingTrips(pendingSyncTrips) }
            )
        }

        if (tripsState is UiState.Error) {
            item { ErrorRetryBanner(message = tripsState.message) }
        }

        if (tripsState is UiState.Loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (vessels.isEmpty()) {
            item {
                EmptyWorkspaceCard(
                    icon = Icons.Default.DirectionsBoat,
                    title = "No Registered Vessels in ${countryPack.name}",
                    subtitle = "Register a vessel in Fleet or load a regional pilot workspace to begin logging at-sea voyages.",
                    primaryButtonLabel = labels.seedPilotData,
                    onPrimaryClick = onSeedPilotData,
                    secondaryButtonLabel = labels.registerVessel,
                    onSecondaryClick = onNavigateToFleet
                )
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showStartTripSheet = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("start_trip_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepOceanNavy)
                    ) {
                        Icon(Icons.Default.Sailing, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(labels.startTrip)
                    }
                    if (effectiveTrip != null && effectiveTrip.statusEnum() == TripStatus.AT_SEA) {
                        Button(
                            onClick = { showLogHaulSheet = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("log_haul_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = SeafoamMint)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(labels.logHaul)
                        }
                    }
                }
            }

            if (trips.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        trips.forEach { trip ->
                            val selected = trip.id == effectiveTrip?.id
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectTrip(trip.id) },
                                label = {
                                    Text("${trip.vesselName} · ${trip.statusEnum().name}")
                                }
                            )
                        }
                    }
                }
            }

            if (effectiveTrip == null) {
                item {
                    EmptyWorkspaceCard(
                        icon = Icons.Default.Anchor,
                        title = "Ready to Depart",
                        subtitle = "Open a new voyage for one of your ${vessels.size} registered vessels to record hauls offline.",
                        primaryButtonLabel = labels.startTrip,
                        onPrimaryClick = { showStartTripSheet = true }
                    )
                }
            } else {
                item {
                    ActiveTripSummaryCard(
                        trip = effectiveTrip,
                        labels = labels,
                        onOpenLandSheet = { showLandTripSheet = true },
                        onRequestGpsPermission = {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                onRefreshGps()
                            } else {
                                locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        gpsFix = gpsFix
                    )
                }

                item {
                    Text(
                        text = "Haul & At-Sea Activity Log",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                when (haulsState) {
                    is UiState.Loading -> item {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    }
                    is UiState.Error -> item {
                        ErrorRetryBanner(message = haulsState.message)
                    }
                    is UiState.Success -> {
                        val hauls = haulsState.data
                        if (hauls.isEmpty()) {
                            item {
                                EmptyWorkspaceCard(
                                    icon = Icons.Default.GpsFixed,
                                    title = "No Hauls Recorded Yet",
                                    subtitle = "Tap '${labels.logHaul}' to lock GPS coordinates, scan a catch photo with Gemini AI, and log crates.",
                                    primaryButtonLabel = if (effectiveTrip.statusEnum() == TripStatus.AT_SEA) labels.logHaul else null,
                                    onPrimaryClick = { showLogHaulSheet = true }
                                )
                            }
                        } else {
                            items(hauls, key = { it.id }) { haul ->
                                HaulEntryCard(haul = haul)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStartTripSheet) {
        StartTripBottomSheet(
            vessels = vessels,
            countryPack = countryPack,
            defaultCaptain = actorDisplayName,
            isOffline = effectiveOffline,
            onDismiss = { showStartTripSheet = false },
            onSubmit = { vessel, captain, crewCount, port, zone, gear ->
                onStartTrip(vessel, captain, crewCount, port, zone, gear, effectiveOffline)
                showStartTripSheet = false
            }
        )
    }

    if (showLogHaulSheet && effectiveTrip != null) {
        val currentHauls = (haulsState as? UiState.Success)?.data.orEmpty()
        val nextNum = (currentHauls.maxOfOrNull { it.haulNumber } ?: 0L).toInt() + 1
        LogHaulBottomSheet(
            trip = effectiveTrip,
            nextHaulNumber = nextNum,
            countryPack = countryPack,
            labels = labels,
            gpsFix = gpsFix,
            defaultLogger = actorDisplayName,
            speciesAiState = speciesAiState,
            onDismiss = {
                onClearSpeciesAi()
                showLogHaulSheet = false
            },
            onRefreshGps = {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) onRefreshGps() else locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            onIdentifySpecies = onIdentifySpecies,
            onSubmit = { sCode, sName, sciName, crates, weight, lat, lon, zone, zoneAlert, logger, eventType, aiVerified, notes ->
                onLogHaul(
                    effectiveTrip,
                    nextNum,
                    sCode,
                    sName,
                    sciName,
                    crates,
                    weight,
                    lat,
                    lon,
                    zone,
                    zoneAlert,
                    logger,
                    eventType,
                    aiVerified,
                    notes,
                    effectiveOffline
                )
                onClearSpeciesAi()
                showLogHaulSheet = false
            }
        )
    }

    if (showLandTripSheet && effectiveTrip != null) {
        LandTripBottomSheet(
            trip = effectiveTrip,
            countryPack = countryPack,
            labels = labels,
            onDismiss = { showLandTripSheet = false },
            onSubmit = { port, unloadedKg ->
                onLandAndCloseTrip(effectiveTrip, port, unloadedKg)
                showLandTripSheet = false
            }
        )
    }
}

@Composable
private fun ActiveTripSummaryCard(
    trip: Trip,
    labels: LocalizedLabels,
    gpsFix: GpsFix?,
    onOpenLandSheet: () -> Unit,
    onRequestGpsPermission: () -> Unit
) {
    val status = trip.statusEnum()
    val (bg, fg) = tripStatusPillColors(status)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trip.vesselName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${trip.departurePort} · ${trip.faoZone} · ${trip.gearType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OceanStatusPill(label = status.name, containerColor = bg, contentColor = fg)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Captain & Crew", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${trip.captainName} (${trip.crewCount})", style = MaterialTheme.typography.titleSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Declared Catch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = String.format(Locale.US, "%.1f kg", trip.declaredWeightKg),
                        style = MaterialTheme.typography.titleMedium,
                        color = DeepOceanNavy,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRequestGpsPermission() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.GpsFixed, contentDescription = null, tint = SeafoamMint, modifier = Modifier.size(18.dp))
                        val coordText = if (gpsFix != null) {
                            val tag = if (gpsFix.isLiveDeviceFix) "GPS LOCK" else "PORT REF"
                            String.format(Locale.US, "%s %.4f, %.4f", tag, gpsFix.latitude, gpsFix.longitude)
                        } else {
                            "Tap to lock device GPS"
                        }
                        Text(coordText, style = MaterialTheme.typography.labelMedium)
                    }
                    Text("Refresh", style = MaterialTheme.typography.labelSmall, color = DeepOceanNavy)
                }
            }

            if (status == TripStatus.AT_SEA) {
                OutlinedButton(
                    onClick = onOpenLandSheet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("land_close_trip_button")
                ) {
                    Icon(Icons.Default.Anchor, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(labels.closeAndLandTrip)
                }
            } else if (!trip.certificateCode.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = VerifiedEmerald)
                        Text(
                            text = "Verified Certificate: ${trip.certificateCode}",
                            style = MaterialTheme.typography.labelMedium,
                            color = VerifiedEmerald
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HaulEntryCard(haul: Haul) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (haul.zoneAlert) CoralAlert else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OceanStatusPill(
                        label = "#${haul.haulNumber} · ${haul.speciesCode}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                    if (haul.aiVerified) {
                        OceanStatusPill(
                            label = "AI PHOTO ID",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = VerifiedEmerald
                        )
                    }
                    if (haul.zoneAlert) {
                        OceanStatusPill(
                            label = "RESTRICTED ZONE",
                            containerColor = CoralAlertContainer,
                            contentColor = CoralAlert
                        )
                    }
                }
                Text(
                    text = String.format(Locale.US, "%.1f kg", haul.weightKg),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "${haul.speciesName} (${haul.scientificName})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${haul.cratesCount} crates · ${haul.eventTypeEnum().name} · By ${haul.loggedBy}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(Locale.US, "GPS: %.4f, %.4f · %s", haul.latitude, haul.longitude, haul.faoZone),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!haul.notes.isNullOrBlank()) {
                Text(
                    text = haul.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartTripBottomSheet(
    vessels: List<Vessel>,
    countryPack: CountryPack,
    defaultCaptain: String,
    isOffline: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Vessel, String, Int, String, String, String) -> Unit
) {
    var selectedVessel by remember { mutableStateOf(vessels.first()) }
    var captainName by remember { mutableStateOf(defaultCaptain) }
    var crewText by remember { mutableStateOf("10") }
    var selectedPort by remember { mutableStateOf(countryPack.ports.first()) }
    var selectedZone by remember { mutableStateOf(countryPack.zones.first()) }
    var selectedGear by remember { mutableStateOf(selectedVessel.gearType.ifBlank { countryPack.gearTypes.first() }) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Start At-Sea Voyage", style = MaterialTheme.typography.headlineSmall)
            if (isOffline) {
                Text(
                    "Offline mode active — departure declaration will queue locally for port sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaritimeAmber
                )
            }

            Text("Select Vessel", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vessels.forEach { v ->
                    FilterChip(
                        selected = v.id == selectedVessel.id,
                        onClick = {
                            selectedVessel = v
                            selectedGear = v.gearType
                        },
                        label = { Text("${v.name} (${v.licenseStatus})") }
                    )
                }
            }

            OutlinedTextField(
                value = captainName,
                onValueChange = { captainName = it },
                label = { Text("Captain / Master") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = crewText,
                onValueChange = { crewText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Active Crew Count") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Departure Port", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                countryPack.ports.forEach { port ->
                    FilterChip(
                        selected = port == selectedPort,
                        onClick = { selectedPort = port },
                        label = { Text(port) }
                    )
                }
            }

            Text("Target FAO Zone", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                countryPack.zones.forEach { zone ->
                    FilterChip(
                        selected = zone.code == selectedZone.code,
                        onClick = { selectedZone = zone },
                        label = { Text(zone.code) }
                    )
                }
            }
            if (selectedZone.restricted) {
                Surface(color = CoralAlertContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = CoralAlert)
                        Text(selectedZone.seasonalRule, style = MaterialTheme.typography.bodySmall, color = CoralAlert)
                    }
                }
            }

            Button(
                onClick = {
                    val crew = crewText.toIntOrNull()?.coerceIn(1, 100) ?: 6
                    onSubmit(selectedVessel, captainName.ifBlank { "Capt. Crew" }, crew, selectedPort, selectedZone.code, selectedGear)
                },
                enabled = captainName.trim().length >= 2,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("confirm_start_trip_button"),
                colors = ButtonDefaults.buttonColors(containerColor = DeepOceanNavy)
            ) {
                Text("Confirm Departure")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogHaulBottomSheet(
    trip: Trip,
    nextHaulNumber: Int,
    countryPack: CountryPack,
    labels: LocalizedLabels,
    gpsFix: GpsFix?,
    defaultLogger: String,
    speciesAiState: AiTaskState<SpeciesIdentificationResult>,
    onDismiss: () -> Unit,
    onRefreshGps: () -> Unit,
    onIdentifySpecies: (Bitmap) -> Unit,
    onSubmit: (String, String, String, Int, Double, Double, Double, String, Boolean, String, HaulEventType, Boolean, String?) -> Unit
) {
    val context = LocalContext.current
    var selectedSpecies by remember { mutableStateOf(countryPack.speciesCatalog.first()) }
    var customSpeciesCode by remember { mutableStateOf(selectedSpecies.faoCode) }
    var customSpeciesName by remember { mutableStateOf(selectedSpecies.internationalName) }
    var customScientific by remember { mutableStateOf(selectedSpecies.scientificName) }
    var aiVerified by remember { mutableStateOf(false) }

    var selectedZone by remember {
        mutableStateOf(countryPack.zones.firstOrNull { it.code == trip.faoZone } ?: countryPack.zones.first())
    }
    var eventType by remember { mutableStateOf(HaulEventType.CATCH) }
    var cratesText by remember { mutableStateOf("8") }
    var weightText by remember { mutableStateOf("320") }
    var loggerName by remember { mutableStateOf(defaultLogger) }
    var notes by remember { mutableStateOf("") }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = decodeBitmapFromUri(context, uri)
            if (bmp != null) {
                previewBitmap = bmp
                onIdentifySpecies(bmp)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            val bmp = decodeBitmapFromUri(context, uri)
            if (bmp != null) {
                previewBitmap = bmp
                onIdentifySpecies(bmp)
            }
        }
    }

    LaunchedEffect(speciesAiState) {
        if (speciesAiState is AiTaskState.Success) {
            val res = speciesAiState.data
            customSpeciesCode = res.faoAlphaCode
            customSpeciesName = res.commonName
            customScientific = res.scientificName
            aiVerified = true
        }
    }

    val lat = gpsFix?.latitude ?: countryPack.defaultLat
    val lon = gpsFix?.longitude ?: countryPack.defaultLon

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Log Haul #$nextHaulNumber · ${trip.vesselName}", style = MaterialTheme.typography.headlineSmall)

            // AI Photo Species Identifier Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = "Captured catch photo",
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.img_species_ai_scan),
                                contentDescription = "AI species identification reticle illustration",
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DeepOceanNavy, modifier = Modifier.size(16.dp))
                                Text(labels.aiIdentifySpecies, style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                "Snap or pick a catch photo for Gemini FAO taxonomy & protected species check.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("pick_catch_photo_button")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Gallery")
                        }
                        OutlinedButton(
                            onClick = {
                                val uri = createTempPhotoUri(context)
                                pendingCameraUri = uri
                                cameraLauncher.launch(uri)
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("camera_catch_photo_button")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Camera")
                        }
                    }

                    when (speciesAiState) {
                        is AiTaskState.Loading -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Analyzing catch photo with Gemini Vision...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is AiTaskState.Success -> {
                            val res = speciesAiState.data
                            Surface(
                                color = if (res.protectedSpeciesWarning) CoralAlertContainer else MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "${res.commonName} (${res.faoAlphaCode}) · ${res.confidencePercent}% confidence",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (res.protectedSpeciesWarning) CoralAlert else VerifiedEmerald
                                    )
                                    Text(res.scientificName, style = MaterialTheme.typography.labelSmall)
                                    if (res.protectedSpeciesWarning) {
                                        Text(
                                            "PROTECTED SPECIES ALERT — Log as BYCATCH_RELEASE!",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CoralAlert,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (res.inspectionNote.isNotBlank()) {
                                        Text(res.inspectionNote, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        is AiTaskState.Error -> {
                            ErrorRetryBanner(message = speciesAiState.message)
                        }
                        AiTaskState.Idle -> Unit
                    }
                }
            }

            Text("Event Type", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HaulEventType.entries.forEach { type ->
                    FilterChip(
                        selected = eventType == type,
                        onClick = { eventType = type },
                        label = { Text(type.name) }
                    )
                }
            }

            Text("Species (${countryPack.name} FAO Catalog)", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                countryPack.speciesCatalog.forEach { sp ->
                    FilterChip(
                        selected = customSpeciesCode == sp.faoCode && !aiVerified,
                        onClick = {
                            selectedSpecies = sp
                            customSpeciesCode = sp.faoCode
                            customSpeciesName = sp.internationalName
                            customScientific = sp.scientificName
                            aiVerified = false
                        },
                        label = { Text("${sp.faoCode} · ${sp.internationalName}") }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = customSpeciesCode,
                    onValueChange = { customSpeciesCode = it.uppercase().take(16) },
                    label = { Text("FAO Code") },
                    singleLine = true,
                    modifier = Modifier.weight(0.35f)
                )
                OutlinedTextField(
                    value = customSpeciesName,
                    onValueChange = { customSpeciesName = it },
                    label = { Text("Species Name") },
                    singleLine = true,
                    modifier = Modifier.weight(0.65f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = cratesText,
                    onValueChange = { cratesText = it.filter { c -> c.isDigit() } },
                    label = { Text("Crates") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Text("Fishing Zone & GPS Lock", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                countryPack.zones.forEach { z ->
                    FilterChip(
                        selected = selectedZone.code == z.code,
                        onClick = { selectedZone = z },
                        label = { Text(if (z.restricted) "${z.code} (RESTRICTED)" else z.code) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    String.format(Locale.US, "Locked GPS: %.4f, %.4f", lat, lon),
                    style = MaterialTheme.typography.labelMedium
                )
                OutlinedButton(onClick = onRefreshGps) {
                    Text("Lock GPS")
                }
            }

            OutlinedTextField(
                value = loggerName,
                onValueChange = { loggerName = it },
                label = { Text("Attributed Crew / Technical Assistant") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Activity / Cold-Hold Notes (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val crates = cratesText.toIntOrNull()?.coerceIn(0, 10000) ?: 0
                    val weight = weightText.toDoubleOrNull()?.coerceIn(0.0, 200000.0) ?: 0.0
                    onSubmit(
                        customSpeciesCode.ifBlank { "UNK" },
                        customSpeciesName.ifBlank { "Unknown Catch" },
                        customScientific.ifBlank { "Pisces sp." },
                        crates,
                        weight,
                        lat,
                        lon,
                        selectedZone.code,
                        selectedZone.restricted,
                        loggerName.ifBlank { "Crew" },
                        eventType,
                        aiVerified,
                        notes.takeIf { it.isNotBlank() }
                    )
                },
                enabled = customSpeciesName.trim().length >= 2 && loggerName.trim().length >= 2,
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("submit_haul_button"),
                colors = ButtonDefaults.buttonColors(containerColor = DeepOceanNavy)
            ) {
                Text("Save Haul #$nextHaulNumber")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LandTripBottomSheet(
    trip: Trip,
    countryPack: CountryPack,
    labels: LocalizedLabels,
    onDismiss: () -> Unit,
    onSubmit: (String, Double) -> Unit
) {
    var selectedPort by remember { mutableStateOf(countryPack.ports.first()) }
    var unloadedWeightText by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", trip.declaredWeightKg))
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(labels.closeAndLandTrip, style = MaterialTheme.typography.headlineSmall)
            Text(
                "At-Sea Declared Total: ${String.format(Locale.US, "%.1f kg", trip.declaredWeightKg)}",
                style = MaterialTheme.typography.titleSmall,
                color = DeepOceanNavy
            )

            Text("Landing Port", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                countryPack.ports.forEach { port ->
                    FilterChip(
                        selected = port == selectedPort,
                        onClick = { selectedPort = port },
                        label = { Text(port) }
                    )
                }
            }

            OutlinedTextField(
                value = unloadedWeightText,
                onValueChange = { unloadedWeightText = it },
                label = { Text("Port Scale Unloaded Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val kg = unloadedWeightText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: trip.declaredWeightKg
                    onSubmit(selectedPort, kg)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("confirm_land_trip_button"),
                colors = ButtonDefaults.buttonColors(containerColor = DeepOceanNavy)
            ) {
                Text("Finalize Landing & Submit to Ministry")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
} catch (_: Exception) {
    null
}

private fun createTempPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "catch_photos").apply { mkdirs() }
    val file = File(dir, "catch_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
