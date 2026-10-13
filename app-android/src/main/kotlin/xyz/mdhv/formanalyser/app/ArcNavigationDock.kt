package xyz.mdhv.formanalyser.app

import android.graphics.Paint as NativePaint
import android.graphics.Path as NativePath
import android.graphics.PathMeasure
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mdhv.formanalyser.app.ui.theme.Hyle

/** Bocal/Hyle primary navigation geometry, scaled as one 400 × 150 stage. */
@Composable
internal fun ArcNavigationDock(
    items: List<Pair<String, Pair<ImageVector, String>>>,
    current: String?,
    badge: Boolean,
    contextLabel: String,
    onQuickAdd: () -> Unit,
    onSettings: () -> Unit,
    onSelect: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val largeFont = LocalDensity.current.fontScale >= 1.2f
        val stageWidth = maxWidth
        val stageHeight = stageWidth * .375f
        val sx = stageWidth / 400f
        val sy = stageHeight / 150f
        Box(Modifier.fillMaxWidth().height(stageHeight)) {
            Canvas(Modifier.fillMaxSize()) {
                val scale = size.width / 400f
                val lower = Path().apply {
                    moveTo(22f * scale, 112f * scale)
                    quadraticTo(200f * scale, 77f * scale, 378f * scale, 112f * scale)
                }
                val upper = Path().apply {
                    moveTo(82.52f * scale, 40.12f * scale)
                    quadraticTo(200f * scale, 24.88f * scale, 317.48f * scale, 40.12f * scale)
                }
                val rail = Brush.verticalGradient(
                    colors = listOf(Color(0xFF30313A), Color(0xFF1D1E25), Color(0xFF111216)),
                    startY = 8f * scale,
                    endY = 145f * scale,
                )
                drawPath(lower, Color(0xFF09090B), style = Stroke(65f * scale, cap = StrokeCap.Round))
                drawPath(lower, rail, style = Stroke(61f * scale, cap = StrokeCap.Round))
                drawPath(upper, Color(0xFF09090B), style = Stroke(48f * scale, cap = StrokeCap.Round))
                drawPath(upper, rail, style = Stroke(44f * scale, cap = StrokeCap.Round))

                val textPath = NativePath().apply {
                    moveTo(82.52f * scale, 40.12f * scale)
                    quadTo(200f * scale, 24.88f * scale, 317.48f * scale, 40.12f * scale)
                }
                val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                    color = Hyle.OnSurfaceDim.toArgb()
                    textSize = 10f * scale
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                    textAlign = NativePaint.Align.LEFT
                    letterSpacing = .12f
                }
                val measure = PathMeasure(textPath, false)
                val title = contextLabel.uppercase()
                drawContext.canvas.nativeCanvas.drawTextOnPath(
                    title, textPath, (measure.length - paint.measureText(title)) / 2f,
                    3f * scale, paint,
                )
            }

            // Circular actions follow the upper rail's extrapolated guides, not the lower tabs.
            DockAction(
                description = "Quick add",
                icon = Icons.Filled.Add,
                rotation = -11.37f,
                modifier = Modifier.offset(
                    x = sx * 18f - 24.dp,
                    y = sy * 50.79f - 24.dp,
                ),
                onClick = onQuickAdd,
            )
            DockAction(
                description = "Settings",
                icon = Icons.Filled.Settings,
                rotation = 11.37f,
                modifier = Modifier.offset(
                    x = sx * 382f - 24.dp,
                    y = sy * 50.79f - 24.dp,
                ),
                onClick = onSettings,
            )

            val ts = listOf(.07f, .285f, .5f, .715f, .93f)
            items.forEachIndexed { index, (destination, pair) ->
                val (icon, label) = pair
                val selected = current == destination
                val visibleLabel = if (largeFont) when (label) {
                    "Progress" -> "Prog."
                    "Calendar" -> "Cal."
                    else -> label
                } else label
                val t = ts[index]
                val y = (112f * (1 - t) * (1 - t) + 154f * (1 - t) * t + 112f * t * t) / 150f
                val dx = 356f
                val dy = 2f * (1 - t) * (77f - 112f) + 2f * t * (112f - 77f)
                val rotation = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                val x = (22f + 356f * t) / 400f
                Column(
                    Modifier.offset(x = stageWidth * x - if (selected) 48.dp else 24.dp,
                        y = stageHeight * y - 22.dp)
                        .width(if (selected) 96.dp else 48.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .then(if (selected) Modifier.background(
                            Brush.verticalGradient(listOf(Color(0xFFF8F6EF), Color(0xFFD5D2C8))),
                        ) else Modifier)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(destination) })
                        .semantics(mergeDescendants = true) { contentDescription = label }
                        .testTag("nav-tab-$destination"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        if (destination == "body" && badge) {
                            BadgedBox(badge = { Badge { Text("!") } }) {
                                Icon(icon, label, tint = if (selected) Color(0xFF17181D) else Hyle.OnSurfaceDim,
                                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation })
                            }
                        } else {
                            Icon(icon, label, tint = if (selected) Color(0xFF17181D) else Hyle.OnSurfaceDim,
                                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation })
                        }
                        if (selected) {
                            Spacer(Modifier.width(6.dp))
                            Text(visibleLabel, color = Color(0xFF17181D), maxLines = 1,
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockAction(
    description: String,
    icon: ImageVector,
    rotation: Float,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.size(48.dp).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF30313A), Color(0xFF111216))))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Hyle.AccentBright,
            modifier = Modifier.size(19.dp).graphicsLayer { rotationZ = rotation })
    }
}
