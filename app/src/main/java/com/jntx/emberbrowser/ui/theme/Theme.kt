package com.jntx.emberbrowser.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = EmberPrimary,
    onPrimary = EmberOnPrimary,
    primaryContainer = EmberPrimaryContainer,
    onPrimaryContainer = EmberOnPrimaryContainer,
    secondary = EmberSecondary,
    onSecondary = EmberOnSecondary,
    secondaryContainer = EmberSecondaryContainer,
    onSecondaryContainer = EmberOnSecondaryContainer,
    tertiary = EmberTertiary,
    onTertiary = EmberOnTertiary,
    tertiaryContainer = EmberTertiaryContainer,
    onTertiaryContainer = EmberOnTertiaryContainer,
    background = EmberBackground,
    onBackground = EmberOnBackground,
    surface = EmberSurface,
    onSurface = EmberOnSurface,
    surfaceVariant = EmberSurfaceVariant,
    onSurfaceVariant = EmberOnSurfaceVariant,
    outline = EmberOutline
)

private val DarkColorScheme = darkColorScheme(
    primary = EmberDarkPrimary,
    onPrimary = EmberDarkOnPrimary,
    background = EmberDarkBackground,
    onBackground = EmberDarkOnSurface,
    surface = EmberDarkSurface,
    onSurface = EmberDarkOnSurface,
    surfaceVariant = EmberSecondary,
    onSurfaceVariant = EmberDarkOnSurface
)

@Composable
fun EmberTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
