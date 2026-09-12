package xyz.mdhv.formanalyser.app.ui.theme

import android.app.Activity
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

private data class HylePalette(
    val accent: Color,
    val accentBright: Color,
    val accentDim: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceRich: Color,
    val surfaceDeep: Color,
    val onBackground: Color,
    val onSurfaceDim: Color,
    val inkFaint: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val danger: Color,
    val warning: Color,
    val success: Color,
    val radiumGreen: Color,
    val alienCyan: Color,
    val bodyMuscleTop: Color,
    val bodyMuscleBottom: Color,
    val bodySilhouetteTop: Color,
    val bodySilhouetteBottom: Color,
)

private val DarkPalette = HylePalette(
    accent = Color(0xFF8E7BFF),
    accentBright = Color(0xFFA593FF),
    accentDim = Color(0xFF7867E6),
    background = Color(0xFF0A0809),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF212128),
    surfaceRich = Color(0xFF171421),
    surfaceDeep = Color(0xFF15131D),
    onBackground = Color(0xEBECE8E4),
    onSurfaceDim = Color(0x99ECE8E4),
    inkFaint = Color(0x52ECE8E4),
    hairline = Color(0x14FFFFFF),
    hairlineStrong = Color(0x24FFFFFF),
    danger = Color(0xFFE5564B),
    warning = Color(0xFFE0941A),
    success = Color(0xFF5BBF7A),
    radiumGreen = Color(0xFFC7EF9E),
    alienCyan = Color(0xFF35E0FF),
    bodyMuscleTop = Color(0xFF282631),
    bodyMuscleBottom = Color(0xFF17161D),
    bodySilhouetteTop = Color(0xFF1C1B22),
    bodySilhouetteBottom = Color(0xFF0E0D12),
)

private val LightPalette = HylePalette(
    accent = Color(0xFF6652D9),
    accentBright = Color(0xFF765FE8),
    accentDim = Color(0xFF5140B7),
    background = Color(0xFFF5F2ED),
    surface = Color(0xFFFFFCF7),
    surfaceVariant = Color(0xFFEAE5DE),
    surfaceRich = Color(0xFFF0EAFB),
    surfaceDeep = Color(0xFFEDE8F5),
    onBackground = Color(0xFF201E22),
    onSurfaceDim = Color(0xFF625E66),
    inkFaint = Color(0xFF918B94),
    hairline = Color(0x1F201E22),
    hairlineStrong = Color(0x33201E22),
    danger = Color(0xFFB93636),
    warning = Color(0xFF9B5A00),
    success = Color(0xFF267044),
    radiumGreen = Color(0xFF397B20),
    alienCyan = Color(0xFF007A91),
    bodyMuscleTop = Color(0xFFE1DCE5),
    bodyMuscleBottom = Color(0xFFCFC8D3),
    bodySilhouetteTop = Color(0xFFE8E3EA),
    bodySilhouetteBottom = Color(0xFFD7D0DA),
)

/**
 * Hyle semantic tokens. The accessors intentionally remain plain [Color] values because canvas
 * draw lambdas are not composable; [FormAnalyserTheme] swaps the palette before composing the UI.
 */
object Hyle {
    private var palette: HylePalette = DarkPalette

    internal fun applyPalette(dark: Boolean) {
        palette = if (dark) DarkPalette else LightPalette
    }

    val Accent get() = palette.accent
    val AccentBright get() = palette.accentBright
    val AccentDim get() = palette.accentDim
    val Background get() = palette.background
    val Surface get() = palette.surface
    val SurfaceVariant get() = palette.surfaceVariant
    val SurfaceRich get() = palette.surfaceRich
    val SurfaceDeep get() = palette.surfaceDeep
    val OnBackground get() = palette.onBackground
    val OnSurfaceDim get() = palette.onSurfaceDim
    val InkFaint get() = palette.inkFaint
    val Hairline get() = palette.hairline
    val HairlineStrong get() = palette.hairlineStrong
    val Danger get() = palette.danger
    val Warning get() = palette.warning
    val Success get() = palette.success
    val RadiumGreen get() = palette.radiumGreen
    val AlienCyan get() = palette.alienCyan
    val BodyMuscleTop get() = palette.bodyMuscleTop
    val BodyMuscleBottom get() = palette.bodyMuscleBottom
    val BodySilhouetteTop get() = palette.bodySilhouetteTop
    val BodySilhouetteBottom get() = palette.bodySilhouetteBottom

    val Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    const val MotionMillis = 300
    const val MotionInstantMillis = 120
    const val MotionPaneMillis = 420
}

private val DarkColors = darkColorScheme(
    primary = DarkPalette.accent,
    onPrimary = DarkPalette.background,
    secondary = DarkPalette.radiumGreen,
    background = DarkPalette.background,
    onBackground = DarkPalette.onBackground,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.onBackground,
    surfaceVariant = DarkPalette.surfaceVariant,
    onSurfaceVariant = DarkPalette.onSurfaceDim,
    outline = DarkPalette.hairlineStrong,
    error = DarkPalette.danger,
)

private val LightColors = lightColorScheme(
    primary = LightPalette.accent,
    onPrimary = Color.White,
    secondary = LightPalette.radiumGreen,
    background = LightPalette.background,
    onBackground = LightPalette.onBackground,
    surface = LightPalette.surface,
    onSurface = LightPalette.onBackground,
    surfaceVariant = LightPalette.surfaceVariant,
    onSurfaceVariant = LightPalette.onSurfaceDim,
    outline = LightPalette.hairlineStrong,
    error = LightPalette.danger,
)

private val HyleType = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

@Composable
fun FormAnalyserTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    Hyle.applyPalette(dark)
    val colors = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            activity.window.statusBarColor = colors.background.toArgb()
            activity.window.navigationBarColor = colors.surface.toArgb()
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = HyleType, content = content)
}
