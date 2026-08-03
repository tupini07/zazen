package com.zazen.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Every container role the app overrides also pins its matching "on" colour.
// Inheriting a baseline M3 "on" colour against a custom container is how the
// dark selected-segment/filter-chip text ended up at 3.8:1 (below WCAG AA).
private val DarkColorScheme = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo20,
    primaryContainer = Indigo40,
    onPrimaryContainer = Indigo90,
    secondary = Amber80,
    onSecondary = Amber10,
    secondaryContainer = Amber30,
    onSecondaryContainer = Amber80,
    tertiary = Amber80,
    onTertiary = Amber10,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    background = DarkSurface,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOnSurfaceVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo20,
    secondary = Amber40,
    onSecondary = Color.White,
    secondaryContainer = Amber80,
    onSecondaryContainer = Amber10,
    tertiary = Amber40,
    onTertiary = Color.White,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    background = LightSurface,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOnSurfaceVariant,
)

@Composable
fun ZazenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ZazenTypography,
        content = content,
    )
}
