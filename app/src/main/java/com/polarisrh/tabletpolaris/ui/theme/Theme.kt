package com.polarisrh.tabletpolaris.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// The tablet is a fixed kiosk device: it always renders the Polaris RH brand
// (dark navy + blue accent), regardless of the device's system theme setting.
private val PolarisColors = darkColorScheme(
    primary = PolarisBlue,
    onPrimary = PolarisOnPrimary,
    secondary = PolarisTeal,
    onSecondary = PolarisOnPrimary,
    background = PolarisBackground,
    onBackground = PolarisOnPrimary,
    surface = PolarisSurfaceDark,
    onSurface = PolarisOnPrimary,
    surfaceVariant = PolarisSurfaceDark,
    onSurfaceVariant = PolarisMuted,
    error = PolarisError
)

@Composable
fun TabletPolarisTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PolarisColors,
        typography = PolarisTypography,
        content = content
    )
}
