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
import dev.aarso.hyle.RadiantHues
import dev.aarso.hyle.tokens.HyleTokens

/**
 * Hyle design tokens — sourced live from `dev.aarso:hyle` (the `hyle-design-system` git
 * submodule + Gradle `includeBuild`, gated behind `-PwithAndroid`; see settings.gradle.kts and
 * app-android/build.gradle.kts), no longer hand-copied. Governing law there: "state is SHOWN by
 * material behavior, never SAID by language"; UI surfaces sit at #121212-class, never pure black
 * (halation rule); ink is warm off-white at opacity tiers; light is scarce — spend it on the
 * single violet accent.
 *
 * `dev.aarso.hyle.tokens.HyleTokens.Color.*` values are `Argb` (a `Long` typealias, 0xAARRGGBB)
 * — the exact packing `androidx.compose.ui.graphics.Color(Long)` expects, so no conversion
 * helper is needed. `RadiantHues.RADIUM`/`COLD_CYAN` come from `dev.aarso.hyle` proper (not
 * `HyleTokens`): that's the hand-authored `Provenance` contract's "single canonical… import
 * this, do not re-express it" source for the on-device/cloud glow hue, of which
 * `HyleTokens.Color.colorPaletteProvenanceNative/Cloud` are just the generated JSON echo.
 *
 * Property names kept from the earlier ported approximation so every screen compiles
 * unchanged — only the value source moved from hand-copied hex literals to the real module.
 * Not migrated here: `Easing`'s cubic-bezier control points (`:hyle` exposes token
 * *durations*, ms only — no bezier curve is compiled to Kotlin yet, so this stays app-side) and
 * the body-map encoding hexes elsewhere in this app (BodyScreen etc. — those are the Crocodyl
 * briefs' body-map law, not general theme tokens, and were never part of this port).
 */
object Hyle {
    // Accent lane (color.palette.accent) — violet is the single north-star accent.
    val Accent = Color(HyleTokens.Color.colorPaletteAccentViolet)
    val AccentBright = Color(HyleTokens.Color.colorPaletteAccentVioletBright) // hover/bright
    val AccentDim = Color(HyleTokens.Color.colorPaletteAccentVioletDeep) // deep/active

    // Fields & surfaces (color.palette.field / control)
    val Background = Color(HyleTokens.Color.colorPaletteFieldNear) // field.near, the inky base
    val Surface = Color(HyleTokens.Color.colorBackgroundSurface) // background.surface, never black
    val SurfaceVariant = Color(HyleTokens.Color.controlSurfaceRaised) // control.surface-raised

    // Ink (warm off-white, opacity tiers)
    val OnBackground = Color(HyleTokens.Color.colorTextPrimary) // text.primary (ink.full, 92%)
    val OnSurfaceDim = Color(HyleTokens.Color.colorTextSecondary) // text.secondary (ink.dim, 42%)
    val InkFaint = Color(HyleTokens.Color.colorTextFaint) // text.faint (18%) — micro-labels

    // Hairlines (color.border)
    val Hairline = Color(HyleTokens.Color.colorBorderHairline)
    val HairlineStrong = Color(HyleTokens.Color.colorBorderStrong)

    // Signals (color.feedback) — red/green never semantic for archery data; these are for
    // destructive actions and system feedback only.
    val Danger = Color(HyleTokens.Color.colorFeedbackDanger)
    val Warning = Color(HyleTokens.Color.colorFeedbackWarning)
    val Success = Color(HyleTokens.Color.colorFeedbackSuccess)

    /** Provenance: radium yellow-green = on-device/native (RadiantHues.RADIUM). */
    val RadiumGreen = Color(RadiantHues.RADIUM)

    /** Provenance: cold clinical cyan = cloud/from-elsewhere (RadiantHues.COLD_CYAN). */
    val AlienCyan = Color(RadiantHues.COLD_CYAN)

    /**
     * motion: easing.standard — calm weighted ease, no spring. Not a `:hyle` token; see KDoc.
     */
    val Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** motion: duration.calm — "weight not bounce". */
    const val MotionMillis = HyleTokens.Duration.durationCalm
    const val MotionInstantMillis = HyleTokens.Duration.durationInstant
    const val MotionPaneMillis = HyleTokens.Duration.durationPane
}

private val HyleColors = darkColorScheme(
    primary = Hyle.Accent,
    onPrimary = Color(HyleTokens.Color.colorActionOnPrimary), // action.on-primary — ink-on-light
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
