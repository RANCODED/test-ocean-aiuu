package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val OceanLightColorScheme = lightColorScheme(
    primary = DeepOceanNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1EAF8),
    onPrimaryContainer = DeepOceanNavy,
    secondary = CoastalTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF4ED),
    onSecondaryContainer = Color(0xFF063D34),
    tertiary = MaritimeAmber,
    onTertiary = Color.White,
    tertiaryContainer = MaritimeAmberContainer,
    onTertiaryContainer = Color(0xFF5A3300),
    error = CoralAlert,
    onError = Color.White,
    errorContainer = CoralAlertContainer,
    onErrorContainer = Color(0xFF6B150B),
    background = MistBackground,
    onBackground = InkPrimary,
    surface = CrispDeckWhite,
    onSurface = InkPrimary,
    surfaceVariant = SoftSurfSurface,
    onSurfaceVariant = InkSecondary,
    outline = Color(0xFFBFD0DD),
    outlineVariant = Color(0xFFDCE7EF)
)

private val OceanDarkColorScheme = darkColorScheme(
    primary = SeafoamMint,
    onPrimary = Color(0xFF042620),
    primaryContainer = AbyssalTeal,
    onPrimaryContainer = SeafoamLight,
    secondary = Color(0xFF5DD3C0),
    onSecondary = Color(0xFF042923),
    secondaryContainer = Color(0xFF134857),
    onSecondaryContainer = Color(0xFFBDF5EA),
    tertiary = MaritimeAmber,
    onTertiary = Color(0xFF2B1600),
    tertiaryContainer = Color(0xFF543308),
    onTertiaryContainer = MaritimeAmberContainer,
    error = Color(0xFFFF7A6B),
    onError = Color(0xFF4D0A03),
    errorContainer = Color(0xFF6E1A10),
    onErrorContainer = CoralAlertContainer,
    background = DeepSlateNight,
    onBackground = InkOnDark,
    surface = NightDeckSurface,
    onSurface = InkOnDark,
    surfaceVariant = NightCardSurface,
    onSurfaceVariant = InkOnDarkMuted,
    outline = Color(0xFF2B4A62),
    outlineVariant = Color(0xFF1D364A)
)

val OceanShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun OceanAiUUTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) OceanDarkColorScheme else OceanLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OceanTypography,
        shapes = OceanShapes,
        content = content
    )
}
