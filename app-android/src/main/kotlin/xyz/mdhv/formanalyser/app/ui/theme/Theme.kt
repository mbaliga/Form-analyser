package xyz.mdhv.formanalyser.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Hyle design tokens (handoff §6): violet accent, dark/AMOLED surfaces, upper-left key light,
 * ~280ms cubic-bezier motion, and the provenance-glow convention
 * (radium-green = on-device inference, alien-cyan = cloud).
 */
object Hyle {
    val Accent = Color(0xFF8E7BFF)            // violet
    val AccentDim = Color(0xFF5B4FA8)
    val Background = Color(0xFF000000)        // AMOLED true black
    val Surface = Color(0xFF0E0E12)
    val SurfaceVariant = Color(0xFF17171F)
    val OnBackground = Color(0xFFEDEDF2)
    val OnSurfaceDim = Color(0xFF9A9AA8)
    val Danger = Color(0xFFFF6B6B)

    /** On-device inference glow. */
    val RadiumGreen = Color(0xFF66FF99)
    /** Cloud-sourced glow (reserved for when the web/coach layer lands). */
    val AlienCyan = Color(0xFF3DE0FF)

    /** ~280ms cubic-bezier — the standard Hyle transition. */
    val Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    const val MotionMillis = 280
}

private val HyleColors = darkColorScheme(
    primary = Hyle.Accent,
    onPrimary = Color(0xFF120F22),
    secondary = Hyle.RadiumGreen,
    background = Hyle.Background,
    onBackground = Hyle.OnBackground,
    surface = Hyle.Surface,
    onSurface = Hyle.OnBackground,
    surfaceVariant = Hyle.SurfaceVariant,
    onSurfaceVariant = Hyle.OnSurfaceDim,
    error = Hyle.Danger,
)

private val HyleType = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp),
)

@Composable
fun FormAnalyserTheme(content: @Composable () -> Unit) {
    // Dark-only by design (AMOLED). isSystemInDarkTheme kept for future light variant.
    @Suppress("UNUSED_VARIABLE") val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = HyleColors, typography = HyleType, content = content)
}
