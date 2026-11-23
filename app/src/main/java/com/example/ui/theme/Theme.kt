package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.preferences.AccentTheme
import com.example.data.preferences.ThemeMode

val VaultShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun ObsidianVaultTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentTheme: AccentTheme = AccentTheme.CHAMPAGNE_GOLD,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = remember(isDark, accentTheme) {
        val rawPrimary = Color(accentTheme.primaryHex)
        val rawSecondary = Color(accentTheme.secondaryHex)
        val targetPrimary = if (isDark) rawPrimary else darkenForLightMode(rawPrimary)
        val targetOnPrimary = if (isDark) Color(0xFF0A0C12) else Color.White

        if (isDark) {
            darkColorScheme(
                primary = targetPrimary,
                onPrimary = targetOnPrimary,
                primaryContainer = targetPrimary.copy(alpha = 0.16f),
                onPrimaryContainer = rawSecondary,
                secondary = rawSecondary,
                onSecondary = Color(0xFF0B0E14),
                secondaryContainer = ObsidianSurfaceElevated,
                onSecondaryContainer = ObsidianTextPrimary,
                tertiary = VaultInfo,
                background = ObsidianBlack,
                onBackground = ObsidianTextPrimary,
                surface = ObsidianSurface,
                onSurface = ObsidianTextPrimary,
                surfaceVariant = ObsidianSurfaceVariant,
                onSurfaceVariant = ObsidianTextSecondary,
                surfaceContainer = ObsidianSurface,
                surfaceContainerHigh = ObsidianSurfaceVariant,
                surfaceContainerHighest = ObsidianSurfaceElevated,
                outline = ObsidianOutline,
                outlineVariant = ObsidianOutlineSubtle,
                error = VaultDanger,
                onError = Color.White
            )
        } else {
            lightColorScheme(
                primary = targetPrimary,
                onPrimary = targetOnPrimary,
                primaryContainer = targetPrimary.copy(alpha = 0.14f),
                onPrimaryContainer = targetPrimary,
                secondary = darkenForLightMode(rawSecondary),
                onSecondary = Color.White,
                secondaryContainer = AlabasterSurfaceVariant,
                onSecondaryContainer = AlabasterTextPrimary,
                tertiary = VaultInfo,
                background = AlabasterBg,
                onBackground = AlabasterTextPrimary,
                surface = AlabasterSurface,
                onSurface = AlabasterTextPrimary,
                surfaceVariant = AlabasterSurfaceVariant,
                onSurfaceVariant = AlabasterTextSecondary,
                surfaceContainer = AlabasterSurface,
                surfaceContainerHigh = AlabasterSurfaceVariant,
                outline = AlabasterOutline,
                outlineVariant = AlabasterOutline.copy(alpha = 0.6f),
                error = VaultDanger,
                onError = Color.White
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VaultTypography,
        shapes = VaultShapes,
        content = content
    )
}

private fun darkenForLightMode(color: Color): Color {
    return Color(
        red = (color.red * 0.72f).coerceIn(0f, 1f),
        green = (color.green * 0.72f).coerceIn(0f, 1f),
        blue = (color.blue * 0.72f).coerceIn(0f, 1f),
        alpha = 1f
    )
}
