package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MasterKeyColorScheme = lightColorScheme(
    primary = SkyBluePrimary,
    onPrimary = Color.White,
    primaryContainer = SkyBlueContainer,
    onPrimaryContainer = OnSkyBlueContainer,
    secondary = SkyBluePrimaryDark,
    onSecondary = Color.White,
    secondaryContainer = SkyBlueContainer,
    onSecondaryContainer = OnSkyBlueContainer,
    tertiary = SkyBluePrimary,
    onTertiary = Color.White,
    background = BackgroundWhite,
    onBackground = TextDarkGray,
    surface = SurfaceWhite,
    onSurface = TextDarkGray,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondaryGray,
    outline = OutlineBorder,
    outlineVariant = LightGrayDivider,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = ErrorRed
)

@Composable
fun MasterKeyTheme(
    content: @Composable () -> Unit
) {
    // Strictly Light Theme per design requirement
    MaterialTheme(
        colorScheme = MasterKeyColorScheme,
        typography = Typography,
        content = content
    )
}
