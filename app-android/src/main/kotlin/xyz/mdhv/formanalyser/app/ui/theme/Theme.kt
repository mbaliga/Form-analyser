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
 * Hyle design tokens — REAL system values, ported verbatim from the Hyle Design System repo
 * (`dev.aarso.hyle.tokens.HyleTokens`, generated from `tokens/*.json`, the cross-platform
 * source of truth). Governing law there: "state is SHOWN by material behavior, never SAID by
 * language"; UI surfaces sit at #121212-class, never pure black (halation rule); ink is warm
 * off-white at opacity tiers; light is scarce — spend it on the single violet accent.
 *
 * Property names kept from the earlier approximation so every screen compiles unchanged —
 * the values are now the token contract's.
 */
object Hyle {
    // Accent lane (color.palette.accent)
    val Accent = Color(0xFF8E7BFF)            // violet — the single north-star accent
    val AccentBright = Color(0xFFA593FF)      // hover/bright
    val AccentDim = Color(0xFF7867E6)         // deep/active

    // Fields & surfaces (color.palette.field / control)
    val Background = Color(0xFF0A0809)        // field.near — the inky base behind glass
    val Surface = Color(0xFF121212)           // background.surface — raised, never pure black
    val SurfaceVariant = Color(0xFF212128)    // control.surface-raised — lifted faces

    // Ink (warm off-white, opacity tiers)
    val OnBackground = Color(0xEBECE8E4)      // text.primary (ink.full, 92%)
    val OnSurfaceDim = Color(0x6BECE8E4)      // text.secondary (ink.dim, 42%)
    val InkFaint = Color(0x2EECE8E4)          // text.faint (18%) — micro-labels

    // Hairlines (color.border)
    val Hairline = Color(0x14FFFFFF)
    val HairlineStrong = Color(0x24FFFFFF)

    // Signals (color.feedback) — red/green never semantic for archery data; these are for
    // destructive actions and system feedback only.
    val Danger = Color(0xFFE5564B)
    val Warning = Color(0xFFE0941A)
    val Success = Color(0xFF5BBF7A)

    /** Provenance: radium yellow-green = on-device/native (RadiantHues.RADIUM). */
    val RadiumGreen = Color(0xFFC7EF9E)

    /** Provenance: cold clinical cyan = cloud/from-elsewhere (RadiantHues.COLD_CYAN). */
    val AlienCyan = Color(0xFF35E0FF)

    /** motion: easing.standard — calm weighted ease, no spring. */
    val Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** motion: duration.calm — "weight not bounce". */
    const val MotionMillis = 300
    const val MotionInstantMillis = 120
    const val MotionPaneMillis = 420
}

private val HyleColors = darkColorScheme(
    primary = Hyle.Accent,
    onPrimary = Color(0xFF0A0809),            // action.on-primary — ink-on-light
    secondary = Hyle.RadiumGreen,
    background = Hyle.Background,
    onBackground = Hyle.OnBackground,
    surface = Hyle.Surface,
    onSurface = Hyle.OnBackground,
    surfaceVariant = Hyle.SurfaceVariant,
    onSurfaceVariant = Hyle.OnSurfaceDim,
    outline = Hyle.Hairline,
    error = Hyle.Danger,
)

// Type scale from the token contract (font.size.*): 2xl 24 / xl 20 / md 16 / label 11.
private val HyleType = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

@Composable
fun FormAnalyserTheme(content: @Composable () -> Unit) {
    // Dark-only by design. isSystemInDarkTheme kept for a future light variant.
    @Suppress("UNUSED_VARIABLE") val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = HyleColors, typography = HyleType, content = content)
}
