package xyz.mdhv.formanalyser.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.atan2

/** Crocodyl's Recurve adaptation of Bocal's two-level floating arc dock. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun ArcNavigationDock(
    items: List<Pair<String, Pair<ImageVector, String>>>,
    current: String?,
    badge: Boolean,
    onSelect: (String) -> Unit,
    onQuickAdd: () -> Unit,
    onSettings: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .testTag("bocal-style-navigation-dock"),
        contentAlignment = Alignment.TopStart,
    ) {
        val scale = maxWidth.value / 400f
        val stageHeight = (150f * scale).dp
        val compactLabels = maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.2f

        Canvas(Modifier.fillMaxWidth().height(stageHeight)) {
            val unit = size.width / 400f
            val lower = Path().apply {
                moveTo(22f * unit, 112f * unit)
                quadraticTo(200f * unit, 77f * unit, 378f * unit, 112f * unit)
            }
            val upper = Path().apply {
                moveTo(82.52f * unit, 40.12f * unit)
                quadraticTo(200f * unit, 24.88f * unit, 317.48f * unit, 40.12f * unit)
            }
            drawPath(lower, Color(0xFF090A0D), style = Stroke(64f * unit, cap = StrokeCap.Round))
            drawPath(
                lower,
                Brush.verticalGradient(
                    0f to Color(0xFF303640),
                    .52f to Color(0xFF20232B),
                    1f to Color(0xFF171920),
                    startY = 30f * unit,
                    endY = 150f * unit,
                ),
                style = Stroke(59f * unit, cap = StrokeCap.Round),
            )
            drawPath(
                lower,
                Color.White.copy(alpha = .055f),
                style = Stroke(1f * unit, cap = StrokeCap.Round),
            )
            drawPath(upper, Color(0xFF0A0B0F), style = Stroke(47f * unit, cap = StrokeCap.Round))
            drawPath(
                upper,
                Brush.verticalGradient(
                    0f to Color(0xFF282C35),
                    1f to Color(0xFF1A1C23),
                    startY = 16f * unit,
                    endY = 64f * unit,
                ),
                style = Stroke(42f * unit, cap = StrokeCap.Round),
            )
            drawPath(upper, Color.White.copy(alpha = .05f), style = Stroke(1f * unit, cap = StrokeCap.Round))
        }

        // The side controls sit on the upper-rail continuation; all positions and sizes
        // derive from the same 400×150 stage so the dock scales as one tactile component.
        DockSideAction(
            label = "Quick add",
            icon = Icons.Filled.Add,
            modifier = Modifier.dockSeat(18f, 50.79f, scale, 48.dp, 48.dp),
            onClick = onQuickAdd,
        )
        DockSideAction(
            label = "Settings",
            icon = Icons.Filled.Settings,
            modifier = Modifier.dockSeat(382f, 50.79f, scale, 48.dp, 48.dp),
            onClick = onSettings,
        )

        Surface(
            modifier = Modifier
                .dockSeat(200f, 38f, scale, width = 156.dp, height = 36.dp)
                .semantics { contentDescription = "Current sport: Recurve" }
                .testTag("nav-context-recurve"),
            shape = RoundedCornerShape(50),
            color = Color(0xFF1C2027),
            shadowElevation = 3.dp,
            tonalElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "RECURVE  ·  RANGE",
                    color = Hyle.RadiumGreen,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    letterSpacing = 1.05.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }

        val seats = listOf(
            DockSeat(46.8f, 107.4f, -9.6f),
            DockSeat(123.6f, 93.2f, -4.83f),
            DockSeat(200f, 87.5f, 0f),
            DockSeat(276.4f, 93.2f, 4.83f),
            DockSeat(353.2f, 107.4f, 9.6f),
        )
        items.forEachIndexed { index, (destination, pair) ->
            val (icon, label) = pair
            val shownLabel = if (compactLabels) when (label) {
                "Progress" -> "Prog"
                "Calendar" -> "Cal"
                else -> label
            } else label
            val seat = seats.getOrNull(index) ?: return@forEachIndexed
            val selected = current == destination
            Column(
                Modifier
                    .dockSeat(seat.x, seat.y, scale, width = 68.dp, height = 32.dp)
                    .graphicsLayer { rotationZ = seat.tilt }
                    .clip(RoundedCornerShape(50))
                    .then(if (selected) Modifier.background(Color(0xFFE6E2D8)) else Modifier)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(destination) })
                    .semantics(mergeDescendants = true) { contentDescription = label }
                    .testTag("nav-tab-$destination"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (destination == "body" && badge) {
                        BadgedBox(badge = { Badge { Text("!") } }) {
                            Icon(icon, null, tint = if (selected) Color(0xFF14161C) else Color(0xFF9297A2), modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Icon(icon, null, tint = if (selected) Color(0xFF14161C) else Color(0xFF9297A2), modifier = Modifier.size(18.dp))
                    }
                    AnimatedContent(
                        targetState = selected,
                        label = "navigation-label",
                    ) { active ->
                        if (active) {
                            Spacer(Modifier.width(5.dp))
                            Text(
                                shownLabel,
                                color = Color(0xFF14161C),
                                maxLines = 1,
                                fontSize = 8.5.sp,
                                lineHeight = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockSideAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF353A45), Color(0xFF1B1D24))))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .testTag("dock-action-${label.lowercase().replace(' ', '-')}") ,
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color(0xFFC4C6CC), modifier = Modifier.size(20.dp))
    }
}

private data class DockSeat(val x: Float, val y: Float, val tilt: Float)

private fun Modifier.dockSeat(
    x: Float,
    y: Float,
    scale: Float,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
): Modifier = this
    .offset(x = (x * scale).dp - width / 2, y = (y * scale).dp - height / 2)
    .size(width, height)
