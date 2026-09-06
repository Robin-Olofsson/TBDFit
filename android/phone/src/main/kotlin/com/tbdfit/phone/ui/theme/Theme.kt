package com.tbdfit.phone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Only three seams for a future Dark/Light/System picker — no settings screen or picker UI exists
// yet, and none is built by this change. DARK is the product default for now (not System), per
// explicit product direction; this is the one place that decision lives.
enum class AppTheme { DARK, LIGHT, SYSTEM }

private val TbdfitDarkColorScheme = darkColorScheme(
    primary = Color(0xFF6C5CE7),
    onPrimary = Color.White,
    secondary = Color(0xFF00B894),
    onSecondary = Color.White,
    background = Color(0xFF121212),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFC7C7C7),
    // Explicit neutral override — Material3 otherwise auto-derives this tonal-elevation surface by
    // blending `primary`'s hue in, which is exactly why a "neutral" surface could still read as
    // purple-tinted. Used by the email-auth TopAppBar (see EmailAuthScreen.kt). Confirmed unused
    // by any other screen at the time of this change (MainActivity/ProfileScreens use plain
    // `surface`/`background`), so overriding it here cannot bleed into unrelated UI. `primary`
    // itself is deliberately left unchanged — see the UI-polish report for why.
    surfaceContainerHigh = Color(0xFF242424),
    error = Color(0xFFFF6B6B),
    onError = Color.Black,
)

private val TbdfitLightColorScheme = lightColorScheme(
    primary = Color(0xFF6C5CE7),
    secondary = Color(0xFF00B894),
)

// The one place app-wide colors are defined — screens reference MaterialTheme.colorScheme.*
// rather than hardcoding colors, so this is also the one place to change them. The Google
// sign-in button is a deliberate, documented exception: its white/neutral styling is part of
// Google's own branding requirement, not a scattered app color, and stays fixed regardless of
// theme (see LandingScreen.kt).
@Composable
fun TbdfitTheme(theme: AppTheme = AppTheme.DARK, content: @Composable () -> Unit) {
    val colorScheme = when (theme) {
        AppTheme.DARK -> TbdfitDarkColorScheme
        AppTheme.LIGHT -> TbdfitLightColorScheme
        AppTheme.SYSTEM -> if (isSystemInDarkTheme()) TbdfitDarkColorScheme else TbdfitLightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
