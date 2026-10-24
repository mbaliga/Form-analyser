package xyz.mdhv.formanalyser.app.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Surface drawn behind a hero or card when no photograph is bundled. The hero text is always white,
 * so the fallback is a fixed dark gradient that does not follow the light/dark theme (a themed
 * surface would be near-white in the light theme and leave the white text unreadable).
 */
internal val HeroFallbackBrush: Brush =
    Brush.verticalGradient(listOf(Color(0xff1d1a2a), Color(0xff0e0d14)))
