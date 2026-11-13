package com.example.prismdiff.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.prismdiff.core.model.AppThemeMode

private val StudioOledColorScheme = darkColorScheme(
    primary = PrismDiffColors.PrismCyan,
    onPrimary = Color(0xFF002022),
    primaryContainer = PrismDiffColors.PrismCyanContainer,
    onPrimaryContainer = Color(0xFFA7F7FA),
    secondary = PrismDiffColors.PrismAmber,
    onSecondary = Color(0xFF281700),
    secondaryContainer = PrismDiffColors.PrismAmberContainer,
    onSecondaryContainer = Color(0xFFFFDDB3),
    tertiary = PrismDiffColors.PrismMagenta,
    onTertiary = Color(0xFF3E001B),
    background = PrismDiffColors.OledBlack,
    onBackground = Color(0xFFEDEFF4),
    surface = PrismDiffColors.StudioSurface,
    onSurface = Color(0xFFEDEFF4),
    surfaceVariant = PrismDiffColors.StudioSurfaceElevated,
    onSurfaceVariant = Color(0xFFB5BECB),
    surfaceContainerLowest = PrismDiffColors.OledBlack,
    surfaceContainerLow = PrismDiffColors.StudioSurface,
    surfaceContainer = PrismDiffColors.StudioSurfaceElevated,
    surfaceContainerHigh = PrismDiffColors.StudioSurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFF242C39),
    outline = Color(0xFF424C5D),
    outlineVariant = PrismDiffColors.StudioHairline,
    error = PrismDiffColors.PrismRed,
)

private val StudioLightColorScheme = lightColorScheme(
    primary = Color(0xFF00676C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB5F2F5),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF8C5000),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB7),
    onSecondaryContainer = Color(0xFF2D1600),
    tertiary = Color(0xFFB3265A),
    onTertiary = Color.White,
    background = PrismDiffColors.LightBackground,
    onBackground = PrismDiffColors.LightOnSurface,
    surface = PrismDiffColors.LightSurface,
    onSurface = PrismDiffColors.LightOnSurface,
    surfaceVariant = PrismDiffColors.LightSurfaceContainer,
    onSurfaceVariant = Color(0xFF4C5666),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9FC),
    surfaceContainer = Color(0xFFEDF1F7),
    surfaceContainerHigh = Color(0xFFE3E9F2),
    surfaceContainerHighest = Color(0xFFD8E0EC),
    outline = PrismDiffColors.LightOutline,
    outlineVariant = Color(0xFFD8E0EC),
)

@Composable
fun PrismDiffTheme(
    themeMode: AppThemeMode = AppThemeMode.OLED_DARK,
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        AppThemeMode.OLED_DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }

    val context = LocalContext.current
    val baseScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamic = if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (themeMode == AppThemeMode.OLED_DARK) {
                // Keep true OLED pitch-black canvas surfaces with Material You dynamic accents
                dynamic.copy(
                    background = PrismDiffColors.OledBlack,
                    surface = PrismDiffColors.StudioSurface,
                    surfaceContainerLowest = PrismDiffColors.OledBlack,
                    surfaceContainerLow = PrismDiffColors.StudioSurface,
                    surfaceContainer = PrismDiffColors.StudioSurfaceElevated,
                    surfaceContainerHigh = PrismDiffColors.StudioSurfaceContainerHigh,
                    outlineVariant = PrismDiffColors.StudioHairline,
                )
            } else {
                dynamic
            }
        }
        useDark -> StudioOledColorScheme
        else -> StudioLightColorScheme
    }

    MaterialTheme(
        colorScheme = baseScheme,
        typography = PrismDiffTypography,
        shapes = PrismDiffShapes,
        content = content,
    )
}
