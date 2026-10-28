package com.michelle.raouf.fetch_jet.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

val FetchJetDarkColorScheme: ColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = CyanOnPrimary,
    primaryContainer = CyanPrimaryContainer,
    onPrimaryContainer = CyanOnPrimaryContainer,
    secondary = VioletSecondary,
    onSecondary = VioletOnSecondary,
    secondaryContainer = VioletContainer,
    onSecondaryContainer = VioletOnContainer,
    tertiary = RoseTertiary,
    background = ObsidianBackground,
    surface = ObsidianSurface,
    surfaceVariant = ObsidianSurfaceVariant,
    onBackground = ObsidianOnBackground,
    onSurface = ObsidianOnSurface,
    onSurfaceVariant = ObsidianOnSurfaceVariant,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant,
    error = RedErrorDark,
    onError = RedOnErrorDark,
    errorContainer = RedErrorContainerDark,
    onErrorContainer = RedOnErrorContainerDark
)

val FetchJetLightColorScheme: ColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = CobaltOnPrimary,
    primaryContainer = CobaltLightContainer,
    onPrimaryContainer = CobaltOnLightContainer,
    secondary = IndigoLight,
    onSecondary = IndigoOnLight,
    secondaryContainer = IndigoLightContainer,
    onSecondaryContainer = IndigoOnLightContainer,
    tertiary = RoseRedLight,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = RedErrorLight,
    onError = RedOnErrorLight,
    errorContainer = RedErrorContainerLight,
    onErrorContainer = RedOnErrorContainerLight
)

@Composable
fun FetchJetTheme(
    isDarkMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDarkMode) FetchJetDarkColorScheme else FetchJetLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}