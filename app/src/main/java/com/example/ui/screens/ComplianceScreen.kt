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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.config.CountryPack
import com.example.data.config.LocalizedLabels
import com.example.data.model.Haul
import com.example.data.model.IuuAuditResult
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.ui.components.EmptyWorkspaceCard
import com.example.ui.components.ErrorRetryBanner
import com.example.ui.components.OceanStatusPill
import com.example.ui.components.riskPillColors
import com.example.ui.components.tripStatusPillColors
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.DeepOceanNavy
import com.example.ui.theme.MaritimeAmber
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.UiState
import java.util.Locale

@Composable
fun ComplianceScreen(
    countryPack: CountryPack,
    labels: LocalizedLabels,
    tripsState: UiState<List<Trip>>,
    selectedTripHaulsState: UiState<List<Haul>>,
    iuuAiState: AiTaskState<IuuAuditResult>,
    onSelectTripForAudit: (Trip) -> Unit,
    onRunAiIuuAudit: (Trip, List<Haul>) -> Unit,
    onIssueCertificate: (Trip, String) -> Unit,
    onFlagIuuCase: (Trip, String) -> Unit
) {
    var statusFilter by remember { mutableStateOf<TripStatus?>(null) }
    var activeAuditTripId by remember { mutableStateOf<String?>(null) }
    var inspectorNote by remember { mutableStateOf("Port scale verified & GDST compliance confirmed.") }

    val trips = (tripsState as? UiState.Success)?.data.orEmpty()
    val filteredTrips = if (statusFilter == null) trips else trips.filter { it.statusEnum() == statusFilter }
    val haulsForSelected = (selectedTripHaulsState as? UiState.Success)?.data.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DeepOceanNavy),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        Text(
                            text = countryPack.regulatorName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Text(
                        text = "Coast Guard Integration: ${countryPack.coastGuardAgency} · Active",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = statusFilter == null,
                    onClick = { statusFilter = null },
                    label = { Text("All (${trips.size})") }
                )
                TripStatus.entries.forEach { st ->
                    val count = trips.count { it.statusEnum() == st }
                    FilterChip(
                        selected = statusFilter == st,
                        onClick = { statusFilter = st },
                        label = { Text("${st.name} ($count)") }
                    )
                }
            }
        }

        when (tripsState) {
            is UiState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> item {
                ErrorRetryBanner(message = tripsState.message)
            }
            is UiState.Success -> {
                if (filteredTrips.isEmpty()) {
                    item {
                        EmptyWorkspaceCard(
                            icon = Icons.Default.Gavel,
                            title = "No Voyages Matching Filter",
                            subtitle = "Landed and active voyages appear here for ministry weight verification, AI IUU risk auditing, and catch certification."
                        )
                    }
                } else {
                    items(filteredTrips, key = { it.id }) { trip ->
                        InspectionTripCard(
                            trip = trip,
                            labels = labels,
                            isAuditTarget = activeAuditTripId == trip.id,
                            iuuAiState = if (activeAuditTripId == trip.id) iuuAiState else AiTaskState.Idle,
                            inspectorNote = inspectorNote,
                            onInspectorNoteChange = { inspectorNote = it },
                            onRunAudit = {
                                activeAuditTripId = trip.id
                                onSelectTripForAudit(trip)
                                onRunAiIuuAudit(trip, haulsForSelected.filter { it.tripId == trip.id })
                            },
                            onIssueCert = { onIssueCertificate(trip, inspectorNote) },
                            onFlagCase = { onFlagIuuCase(trip, inspectorNote) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectionTripCard(
    trip: Trip,
    labels: LocalizedLabels,
    isAuditTarget: Boolean,
    iuuAiState: AiTaskState<IuuAuditResult>,
    inspectorNote: String,
    onInspectorNoteChange: (String) -> Unit,
    onRunAudit: () -> Unit,
    onIssueCert: () -> Unit,
    onFlagCase: () -> Unit
) {
    val status = trip.statusEnum()
    val risk = trip.riskEnum()
    val (stBg, stFg) = tripStatusPillColors(status)
    val (rkBg, rkFg) = riskPillColors(risk)

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
                    Text(trip.vesselName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${trip.countryCode} · ${trip.departurePort} -> ${trip.unloadedPort ?: "At Sea"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OceanStatusPill(label = status.name, containerColor = stBg, contentColor = stFg)
                    OceanStatusPill(label = "IUU: ${risk.name}", containerColor = rkBg, contentColor = rkFg)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Declared At Sea", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(String.format(Locale.US, "%.1f kg", trip.declaredWeightKg), style = MaterialTheme.typography.labelLarge)
                }
                Column {
                    Text("Unloaded Scale", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        trip.unloadedWeightKg?.let { String.format(Locale.US, "%.1f kg", it) } ?: "Pending",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Variance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        trip.discrepancyPct?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—",
                        style = MaterialTheme.typography.labelLarge,
                        color = if ((trip.discrepancyPct ?: 0.0) > 10.0) CoralAlert else VerifiedEmerald
                    )
                }
            }

            if (!trip.iuuSummary.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = trip.iuuSummary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (isAuditTarget) {
                when (iuuAiState) {
                    is AiTaskState.Loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Running Gemini IUU Risk & Coast Guard cross-check...", style = MaterialTheme.typography.bodySmall)
                    }
                    is AiTaskState.Success -> {
                        val res = iuuAiState.data
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("AI Audit Result · Risk: ${res.riskLevel.name}", style = MaterialTheme.typography.titleSmall)
                                Text(res.summary, style = MaterialTheme.typography.bodySmall)
                                res.anomalyFlags.forEach { flag ->
                                    Text("• $flag", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "Coast Guard Action: ${res.coastGuardSyncRecommendation}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DeepOceanNavy
                                )
                            }
                        }
                    }
                    is AiTaskState.Error -> ErrorRetryBanner(message = iuuAiState.message)
                    AiTaskState.Idle -> Unit
                }
            }

            if (status == TripStatus.VERIFIED) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = VerifiedEmerald)
                        Column {
                            Text("Certified & Locked · ${trip.certificateCode.orEmpty()}", style = MaterialTheme.typography.titleSmall, color = VerifiedEmerald)
                            if (!trip.auditNotes.isNullOrBlank()) {
                                Text(trip.auditNotes, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = inspectorNote,
                    onValueChange = onInspectorNoteChange,
                    label = { Text("Inspector Decision Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = onRunAudit,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("run_ai_iuu_audit_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(labels.runIuuAudit)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onFlagCase,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("flag_iuu_button"),
                        border = BorderStroke(1.dp, CoralAlert)
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null, tint = CoralAlert, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(labels.flagIuuCase, color = CoralAlert)
                    }
                    Button(
                        onClick = onIssueCert,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("issue_cert_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = VerifiedEmerald)
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(labels.issueCertificate)
                    }
                }
            }
        }
    }
}
