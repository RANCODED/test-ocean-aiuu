package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.config.CountryPack
import com.example.data.config.LocalizedLabels
import com.example.data.model.LicenseStatus
import com.example.data.model.Trip
import com.example.data.model.Vessel
import com.example.ui.components.EmptyWorkspaceCard
import com.example.ui.components.ErrorRetryBanner
import com.example.ui.components.MetricTile
import com.example.ui.components.OceanStatusPill
import com.example.ui.components.licensePillColors
import com.example.ui.theme.DeepOceanNavy
import com.example.ui.theme.MaritimeAmber
import com.example.ui.theme.SeafoamMint
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.viewmodel.UiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetScreen(
    countryPack: CountryPack,
    labels: LocalizedLabels,
    vesselsState: UiState<List<Vessel>>,
    tripsState: UiState<List<Trip>>,
    onRegisterVessel: (String, String, String, String, LicenseStatus, String, Int) -> Unit,
    onSeedPilotData: () -> Unit
) {
    var showAddVesselSheet by remember { mutableStateOf(false) }

    val vessels = (vesselsState as? UiState.Success)?.data.orEmpty()
    val trips = (tripsState as? UiState.Success)?.data.orEmpty()
    val totalLandedKg = trips.sumOf { it.unloadedWeightKg ?: it.declaredWeightKg }
    val alertCount = vessels.count { it.licenseStatusEnum() != LicenseStatus.ACTIVE }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    title = "Active Vessels",
                    value = "${vessels.size}",
                    accent = SeafoamMint,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "Permit Alerts",
                    value = "$alertCount",
                    accent = MaritimeAmber,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "Fleet Catch",
                    value = String.format(Locale.US, "%.0f kg", totalLandedKg),
                    accent = DeepOceanNavy,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Button(
                onClick = { showAddVesselSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("register_vessel_button"),
                colors = ButtonDefaults.buttonColors(containerColor = DeepOceanNavy)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(labels.registerVessel)
            }
        }

        when (vesselsState) {
            is UiState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> item {
                ErrorRetryBanner(message = vesselsState.message)
            }
            is UiState.Success -> {
                if (vessels.isEmpty()) {
                    item {
                        EmptyWorkspaceCard(
                            icon = Icons.Default.DirectionsBoat,
                            title = "Fleet Registry Empty",
                            subtitle = "Add your fishing vessels, permits, and co-ownership splits, or load the ${countryPack.name} pilot fleet.",
                            primaryButtonLabel = labels.seedPilotData,
                            onPrimaryClick = onSeedPilotData
                        )
                    }
                } else {
                    items(vessels, key = { it.id }) { vessel ->
                        val vesselTrips = trips.filter { it.vesselId == vessel.id }
                        val vesselKg = vesselTrips.sumOf { it.unloadedWeightKg ?: it.declaredWeightKg }
                        VesselRegistryCard(vessel = vessel, tripCount = vesselTrips.size, totalKg = vesselKg)
                    }
                }
            }
        }
    }

    if (showAddVesselSheet) {
        RegisterVesselBottomSheet(
            countryPack = countryPack,
            onDismiss = { showAddVesselSheet = false },
            onSubmit = { name, reg, gear, status, expiry, split ->
                onRegisterVessel(name, reg, countryPack.code, gear, status, expiry, split)
                showAddVesselSheet = false
            }
        )
    }
}

@Composable
private fun VesselRegistryCard(
    vessel: Vessel,
    tripCount: Int,
    totalKg: Double
) {
    val status = vessel.licenseStatusEnum()
    val (bg, fg) = licensePillColors(status)

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
                    Text(vessel.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${vessel.registrationNo} · ${vessel.countryCode} · ${vessel.gearType}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OceanStatusPill(label = status.name, containerColor = bg, contentColor = fg)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Permit Expiry: ${vessel.licenseExpiry}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "$tripCount trips · ${String.format(Locale.US, "%.0f kg", totalKg)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = DeepOceanNavy
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Co-Owner Catch Share", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${vessel.coOwnerSplitPct}% Owner / ${100 - vessel.coOwnerSplitPct}% Crew", style = MaterialTheme.typography.labelSmall)
                }
                LinearProgressIndicator(
                    progress = { (vessel.coOwnerSplitPct / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = VerifiedEmerald,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterVesselBottomSheet(
    countryPack: CountryPack,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, LicenseStatus, String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var regNo by remember { mutableStateOf("${countryPack.code}-") }
    var gear by remember { mutableStateOf(countryPack.gearTypes.first()) }
    var licenseStatus by remember { mutableStateOf(LicenseStatus.ACTIVE) }
    var expiry by remember { mutableStateOf("2028-06-30") }
    var splitText by remember { mutableStateOf("60") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Register Vessel (${countryPack.name})", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Vessel Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = regNo,
                onValueChange = { regNo = it },
                label = { Text("National / IMO Registration No.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Authorized Gear", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                countryPack.gearTypes.forEach { g ->
                    FilterChip(selected = g == gear, onClick = { gear = g }, label = { Text(g) })
                }
            }

            Text("License Status", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LicenseStatus.entries.forEach { st ->
                    FilterChip(selected = licenseStatus == st, onClick = { licenseStatus = st }, label = { Text(st.name) })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = expiry,
                    onValueChange = { expiry = it },
                    label = { Text("Expiry (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = splitText,
                    onValueChange = { splitText = it.filter { c -> c.isDigit() } },
                    label = { Text("Owner Share %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = {
                    val split = splitText.toIntOrNull()?.coerceIn(0, 100) ?: 50
                    onSubmit(name.trim(), regNo.trim(), gear, licenseStatus, expiry.trim(), split)
                },
                enabled = name.trim().length >= 2 && regNo.trim().length >= 3 && expiry.trim().length >= 4,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("confirm_register_vessel_button"),
                colors = ButtonDefaults.buttonColors(containerColor = DeepOceanNavy)
            ) {
                Text("Save to Fleet Registry")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
