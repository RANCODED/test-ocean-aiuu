package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.config.LocalizedLabels
import com.example.data.model.IuuRiskLevel
import com.example.data.model.LicenseStatus
import com.example.data.model.TripStatus
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CoralAlertContainer
import com.example.ui.theme.DeepOceanNavy
import com.example.ui.theme.MaritimeAmber
import com.example.ui.theme.MaritimeAmberContainer
import com.example.ui.theme.SeafoamMint
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldContainer

@Composable
fun OceanStatusPill(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun tripStatusPillColors(status: TripStatus): Pair<Color, Color> = when (status) {
    TripStatus.AT_SEA -> Color(0xFFD6EEF8) to DeepOceanNavy
    TripStatus.LANDED -> MaritimeAmberContainer to Color(0xFF6B3E00)
    TripStatus.VERIFIED -> VerifiedEmeraldContainer to VerifiedEmerald
    TripStatus.FLAGGED -> CoralAlertContainer to CoralAlert
}

@Composable
fun riskPillColors(risk: IuuRiskLevel): Pair<Color, Color> = when (risk) {
    IuuRiskLevel.NONE, IuuRiskLevel.LOW -> VerifiedEmeraldContainer to VerifiedEmerald
    IuuRiskLevel.MEDIUM -> MaritimeAmberContainer to Color(0xFF6B3E00)
    IuuRiskLevel.HIGH -> CoralAlertContainer to CoralAlert
}

@Composable
fun licensePillColors(status: LicenseStatus): Pair<Color, Color> = when (status) {
    LicenseStatus.ACTIVE -> VerifiedEmeraldContainer to VerifiedEmerald
    LicenseStatus.EXPIRING -> MaritimeAmberContainer to Color(0xFF6B3E00)
    LicenseStatus.EXPIRED -> CoralAlertContainer to CoralAlert
}

@Composable
fun OfflineSyncStrip(
    labels: LocalizedLabels,
    isNetworkOnline: Boolean,
    isSimulatedOffline: Boolean,
    pendingSyncCount: Int,
    onToggleSimulatedOffline: () -> Unit,
    onSyncPending: () -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveOffline = !isNetworkOnline || isSimulatedOffline
    val bgColor = if (effectiveOffline) MaritimeAmberContainer else VerifiedEmeraldContainer
    val fgColor = if (effectiveOffline) Color(0xFF5E3400) else VerifiedEmerald

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, fgColor.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (effectiveOffline) Icons.Default.CloudOff else Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = fgColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (effectiveOffline) labels.offlineModeBanner else labels.onlineSyncedBanner,
                        style = MaterialTheme.typography.labelMedium,
                        color = fgColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Sea Mode",
                        style = MaterialTheme.typography.labelSmall,
                        color = fgColor
                    )
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = isSimulatedOffline,
                        onCheckedChange = { onToggleSimulatedOffline() },
                        modifier = Modifier.testTag("sea_offline_toggle"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaritimeAmber,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = SeafoamMint
                        )
                    )
                }
            }
            if (pendingSyncCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "$pendingSyncCount queued trip(s) waiting for port reconciliation",
                        style = MaterialTheme.typography.bodySmall,
                        color = fgColor
                    )
                    if (!effectiveOffline) {
                        OutlinedButton(
                            onClick = onSyncPending,
                            border = BorderStroke(1.dp, fgColor),
                            modifier = Modifier.testTag("sync_port_button")
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = fgColor, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sync Now", color = fgColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = accent, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyWorkspaceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    primaryButtonLabel: String? = null,
    onPrimaryClick: (() -> Unit)? = null,
    secondaryButtonLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            if (primaryButtonLabel != null && onPrimaryClick != null) {
                Button(
                    onClick = onPrimaryClick,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepOceanNavy)
                ) {
                    Text(primaryButtonLabel)
                }
            }
            if (secondaryButtonLabel != null && onSecondaryClick != null) {
                OutlinedButton(
                    onClick = onSecondaryClick,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(secondaryButtonLabel)
                }
            }
        }
    }
}

@Composable
fun ErrorRetryBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
