package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.config.CountryPack
import com.example.data.config.LocalizedLabels
import com.example.data.model.ProcessorLot
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.ui.components.EmptyWorkspaceCard
import com.example.ui.components.ErrorRetryBanner
import com.example.ui.components.OceanStatusPill
import com.example.ui.theme.DeepOceanNavy
import com.example.ui.theme.MaritimeAmberContainer
import com.example.ui.theme.SeafoamMint
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldContainer
import com.example.ui.viewmodel.UiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraceabilityScreen(
    countryPack: CountryPack,
    labels: LocalizedLabels,
    tripsState: UiState<List<Trip>>,
    lotsState: UiState<List<ProcessorLot>>,
    onCreateLot: (Trip, String, String, Double, Double, Double, String, String) -> Unit
) {
    var searchLotQuery by remember { mutableStateOf("") }
    var showCreateLotSheet by remember { mutableStateOf(false) }

    val trips = (tripsState as? UiState.Success)?.data.orEmpty()
    val eligibleTrips = trips.filter {
        it.statusEnum() == TripStatus.VERIFIED || it.statusEnum() == TripStatus.LANDED
    }
    val lots = (lotsState as? UiState.Success)?.data.orEmpty()
    val filteredLots = if (searchLotQuery.isBlank()) {
        lots
    } else {
        lots.filter {
            it.lotCode.contains(searchLotQuery, ignoreCase = true) ||
                it.certificateCode.contains(searchLotQuery, ignoreCase = true) ||
                it.speciesName.contains(searchLotQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_traceability_port),
                    contentDescription = "Cold-chain port receiving and export traceability banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    color = Color(0xB8071E2E),
                    modifier = Modifier.fillMaxSize()
                ) {}
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${countryPack.processorName} · Export Pack",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "GDST Critical Tracking Events & Buyer Lot Verification Portal",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD0E7F5)
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchLotQuery,
                onValueChange = { searchLotQuery = it },
                label = { Text(labels.verifyLotCode) },
                placeholder = { Text("Search LOT-IDN-..., CERT-..., or species") },
                leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("buyer_lot_search_input")
            )
        }

        if (eligibleTrips.isNotEmpty()) {
            item {
                Button(
                    onClick = { showCreateLotSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("create_lot_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepOceanNavy)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(labels.createLot)
                }
            }
        }

        when (lotsState) {
            is UiState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> item {
                ErrorRetryBanner(message = lotsState.message)
            }
            is UiState.Success -> {
                if (filteredLots.isEmpty()) {
                    item {
                        EmptyWorkspaceCard(
                            icon = Icons.Default.Inventory2,
                            title = if (lots.isEmpty()) "No Export Lots Created Yet" else "No Matching Lot Found",
                            subtitle = if (eligibleTrips.isEmpty()) {
                                "Land and verify a voyage in At-Sea / Ministry panels first to receive it into cold-chain processing."
                            } else {
                                "Convert a landed or verified voyage into a cold-chain export lot with GDST origin verification."
                            },
                            primaryButtonLabel = if (eligibleTrips.isNotEmpty()) labels.createLot else null,
                            onPrimaryClick = { showCreateLotSheet = true }
                        )
                    }
                } else {
                    items(filteredLots, key = { it.id }) { lot ->
                        BuyerLotCard(lot = lot)
                    }
                }
            }
        }
    }

    if (showCreateLotSheet && eligibleTrips.isNotEmpty()) {
        CreateLotBottomSheet(
            eligibleTrips = eligibleTrips,
            countryPack = countryPack,
            onDismiss = { showCreateLotSheet = false },
            onSubmit = { trip, spCode, spName, inKg, yieldKg, tempC, dest ->
                onCreateLot(
                    trip,
                    spCode,
                    spName,
                    inKg,
                    yieldKg,
                    tempC,
                    countryPack.processorName,
                    dest
                )
                showCreateLotSheet = false
            }
        )
    }
}

@Composable
private fun BuyerLotCard(lot: ProcessorLot) {
    val yieldPct = if (lot.inputWeightKg > 0) (lot.yieldWeightKg / lot.inputWeightKg) * 100.0 else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(lot.lotCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${lot.speciesName} (${lot.speciesCode}) · ${lot.vesselName}", style = MaterialTheme.typography.bodySmall)
                }
                OceanStatusPill(
                    label = if (lot.gdstCompliant) "GDST VERIFIED" else "PROVISIONAL",
                    containerColor = if (lot.gdstCompliant) VerifiedEmeraldContainer else MaritimeAmberContainer,
                    contentColor = if (lot.gdstCompliant) VerifiedEmerald else Color(0xFF6B3E00)
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Receiving -> Yield", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        String.format(Locale.US, "%.0f -> %.0f kg (%.0f%%)", lot.inputWeightKg, lot.yieldWeightKg, yieldPct),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Cold Chain", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        String.format(Locale.US, "%.1f °C", lot.coldChainTempC),
                        style = MaterialTheme.typography.labelLarge,
                        color = SeafoamMint
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = DeepOceanNavy, modifier = Modifier.size(18.dp))
                    Column {
                        Text("Catch Cert: ${lot.certificateCode} · Zone ${lot.faoZone}", style = MaterialTheme.typography.labelSmall)
                        Text("Processor: ${lot.processorName} -> ${lot.exportDestination}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateLotBottomSheet(
    eligibleTrips: List<Trip>,
    countryPack: CountryPack,
    onDismiss: () -> Unit,
    onSubmit: (Trip, String, String, Double, Double, Double, String) -> Unit
) {
    var selectedTrip by remember { mutableStateOf(eligibleTrips.first()) }
    var selectedSpecies by remember { mutableStateOf(countryPack.speciesCatalog.first()) }
    var inputWeightText by remember {
        val base = selectedTrip.unloadedWeightKg ?: selectedTrip.declaredWeightKg
        mutableStateOf(String.format(Locale.US, "%.0f", base.coerceAtLeast(100.0)))
    }
    var yieldWeightText by remember {
        val base = (selectedTrip.unloadedWeightKg ?: selectedTrip.declaredWeightKg) * 0.68
        mutableStateOf(String.format(Locale.US, "%.0f", base.coerceAtLeast(65.0)))
    }
    var tempText by remember { mutableStateOf("-18.5") }
    var selectedDest by remember { mutableStateOf(countryPack.exportDestinations.first()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Receive & Create Export Lot", style = MaterialTheme.typography.headlineSmall)

            Text("Source Voyage", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                eligibleTrips.forEach { t ->
                    FilterChip(
                        selected = t.id == selectedTrip.id,
                        onClick = {
                            selectedTrip = t
                            val base = (t.unloadedWeightKg ?: t.declaredWeightKg).coerceAtLeast(100.0)
                            inputWeightText = String.format(Locale.US, "%.0f", base)
                            yieldWeightText = String.format(Locale.US, "%.0f", base * 0.68)
                        },
                        label = { Text("${t.vesselName} (${t.status})") }
                    )
                }
            }

            Text("Lot Species", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                countryPack.speciesCatalog.forEach { sp ->
                    FilterChip(
                        selected = sp.faoCode == selectedSpecies.faoCode,
                        onClick = { selectedSpecies = sp },
                        label = { Text("${sp.faoCode} · ${sp.internationalName}") }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = inputWeightText,
                    onValueChange = { inputWeightText = it },
                    label = { Text("Input (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = yieldWeightText,
                    onValueChange = { yieldWeightText = it },
                    label = { Text("Yield (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = tempText,
                    onValueChange = { tempText = it },
                    label = { Text("Temp °C") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(0.8f)
                )
            }

            Text("Export Destination", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                countryPack.exportDestinations.forEach { dest ->
                    FilterChip(selected = dest == selectedDest, onClick = { selectedDest = dest }, label = { Text(dest) })
                }
            }

            Button(
                onClick = {
                    val inKg = inputWeightText.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 100.0
                    val yKg = yieldWeightText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 65.0
                    val tempC = tempText.toDoubleOrNull()?.coerceIn(-60.0, 25.0) ?: -18.0
                    onSubmit(selectedTrip, selectedSpecies.faoCode, selectedSpecies.internationalName, inKg, yKg, tempC, selectedDest)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("confirm_create_lot_button"),
                colors = ButtonDefaults.buttonColors(containerColor = DeepOceanNavy)
            ) {
                Text("Generate GDST Lot & Export Pack")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
