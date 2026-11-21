package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.config.AppLanguage
import com.example.data.config.CountryPack
import com.example.data.config.CountryRegistry
import com.example.data.config.LocalizedLabels
import com.example.data.model.AuditLog
import com.example.ui.components.OceanStatusPill
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CoralAlertContainer
import com.example.ui.theme.DeepOceanNavy
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldContainer
import com.example.ui.viewmodel.UiState

@Composable
fun AdminConfigScreen(
    countryPack: CountryPack,
    activeLanguage: AppLanguage,
    labels: LocalizedLabels,
    operatorName: String,
    operatorEmail: String,
    auditLogsState: UiState<List<AuditLog>>,
    onSelectCountry: (String) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSeedPilotData: () -> Unit,
    onSignOut: () -> Unit
) {
    val logs = (auditLogsState as? UiState.Success)?.data.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Public, contentDescription = null, tint = DeepOceanNavy)
                        Text("Country Configuration Pack", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Switch national regulator, FAO zones, authorized ports, and species catalog without code changes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CountryRegistry.packs.forEach { pack ->
                            FilterChip(
                                selected = pack.code == countryPack.code,
                                onClick = { onSelectCountry(pack.code) },
                                label = { Text("${pack.name} (${pack.code})") }
                            )
                        }
                    }
                    Text(
                        "${countryPack.regulatorName} · ${countryPack.coastGuardAgency} · ${countryPack.currencyCode}",
                        style = MaterialTheme.typography.labelMedium,
                        color = DeepOceanNavy
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = DeepOceanNavy)
                        Text("Interface Language & RTL Layout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = lang == activeLanguage,
                                onClick = { onSelectLanguage(lang) },
                                label = { Text(lang.displayName) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Master FAO Species & Restricted Zones (${countryPack.code})", style = MaterialTheme.typography.titleMedium)
                    countryPack.speciesCatalog.forEach { sp ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${sp.faoCode} · ${sp.internationalName} (${sp.localName})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(sp.scientificName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (sp.protectedStatus) {
                                OceanStatusPill(label = "CITES PROTECTED", containerColor = CoralAlertContainer, contentColor = CoralAlert)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    countryPack.zones.forEach { z ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${z.code} · ${z.name}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            OceanStatusPill(
                                label = if (z.restricted) "RESTRICTED" else "OPEN",
                                containerColor = if (z.restricted) CoralAlertContainer else VerifiedEmeraldContainer,
                                contentColor = if (z.restricted) CoralAlert else VerifiedEmerald
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onSeedPilotData,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("seed_pilot_admin_button")
                    ) {
                        Text(labels.seedPilotData)
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = DeepOceanNavy)
                        Column {
                            Text(operatorName.ifBlank { "Signed-In Operator" }, style = MaterialTheme.typography.titleSmall)
                            Text(operatorEmail.ifBlank { "Authenticated Operator" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CoralAlert)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(labels.signOut)
                    }
                }
            }
        }

        item {
            Text("Immutable Platform Audit Trail (${logs.size})", style = MaterialTheme.typography.titleMedium)
        }

        items(logs, key = { it.id }) { log ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(log.action, style = MaterialTheme.typography.labelMedium, color = DeepOceanNavy, fontWeight = FontWeight.Bold)
                        Text(log.entityType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(log.details, style = MaterialTheme.typography.bodySmall)
                    Text("Actor: ${log.actorName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
