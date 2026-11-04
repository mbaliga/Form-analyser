package xyz.mdhv.formanalyser.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mdhv.formanalyser.model.ui.DockGeometry
import xyz.mdhv.formanalyser.model.ui.DockLayout
import xyz.mdhv.formanalyser.model.ui.DockMode
import kotlin.math.pow

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
    val previewDestination = remember(current) { mutableStateOf(current) }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .testTag("bocal-style-navigation-dock"),
        contentAlignment = Alignment.TopCenter,
    ) {
        // LocalConfiguration follows the app window (split screen, desktop windows), not the display.
        val layout = DockGeometry.resolve(
            availableWidthDp = maxWidth.value,
            windowHeightDp = LocalConfiguration.current.screenHeightDp.toFloat(),
            fontScale = LocalDensity.current.fontScale,
        )
        if (layout.mode == DockMode.RAIL) {
            CompactDockRail(items, current, badge, layout.compactLabels, onSelect, onQuickAdd, onSettings)
        } else {
            ArcStage(layout, items, current, badge, previewDestination, onSelect, onQuickAdd, onSettings)
        }
    }
}

/**
 * The curved dock on its 400×150 design stage, scaled to [layout]. The stage box, not the padded
 * outer box, receives the scrub gesture so `size.width` below stays in stage space when the stage is
 * narrower than the window.
 */
@Composable
private fun ArcStage(
    layout: DockLayout,
    items: List<Pair<String, Pair<ImageVector, String>>>,
    current: String?,
    badge: Boolean,
    previewDestination: MutableState<String?>,
    onSelect: (String) -> Unit,
    onQuickAdd: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(
        Modifier
            .width(layout.stageWidthDp.dp)
            .height(layout.stageHeightDp.dp)
            .pointerInput(items, current) {
                var scrubbing = false
                fun destinationAt(xPx: Float, yPx: Float): String? {
                    val factor = 400f / size.width
                    val x = xPx * factor
                    val y = yPx * factor
                    return BOCAL_DOCK_SEATS.minByOrNull { seat ->
                        (seat.x - x).pow(2) + (seat.y - y).pow(2)
                    }?.let { seat -> items.getOrNull(BOCAL_DOCK_SEATS.indexOf(seat))?.first }
                }
                detectDragGestures(
                    onDragStart = { offset ->
                        scrubbing = offset.y * 400f / size.width >= 70f
                        if (scrubbing) previewDestination.value = destinationAt(offset.x, offset.y) ?: current
                    },
                    onDragEnd = {
                        if (scrubbing) previewDestination.value?.let(onSelect)
                        scrubbing = false
                    },
                    onDragCancel = {
                        if (scrubbing) previewDestination.value = current
                        scrubbing = false
                    },
                ) { change, _ ->
                    if (scrubbing) destinationAt(change.position.x, change.position.y)?.let {
                        previewDestination.value = it
                    }
                }
            },
    ) {
        val scale = layout.scale
        val compactLabels = layout.compactLabels
        val shownContext = items.firstOrNull { it.first == previewDestination.value }?.second?.second ?: "Range"

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Current sport: Recurve" }
                .testTag("nav-context-recurve"),
        ) {
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

            val labelPath = upper.asAndroidPath()
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(198, 241, 150)
                textSize = 9f * unit
                letterSpacing = .11f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            val contextText = "RECURVE  ·  ${shownContext.uppercase()}"
            val pathLength = android.graphics.PathMeasure(labelPath, false).length
            val textOffset = ((pathLength - paint.measureText(contextText)) / 2f).coerceAtLeast(0f)
            drawContext.canvas.nativeCanvas.drawTextOnPath(contextText, labelPath, textOffset, 3f * unit, paint)
        }

        // The side controls sit on the upper-rail continuation; all positions and sizes
        // derive from the same 400×150 stage so the dock scales as one tactile component.
        // Every control's real box is at least 48 dp; the visible shapes stay smaller.
        DockSideAction(
            label = "Quick add",
            icon = Icons.Filled.Add,
            modifier = Modifier.dockSeat(18f, 50.79f, scale, 48.dp, 48.dp).graphicsLayer { rotationZ = -11.37f },
            onClick = onQuickAdd,
        )
        DockSideAction(
            label = "Settings",
            icon = Icons.Filled.Settings,
            modifier = Modifier.dockSeat(382f, 50.79f, scale, 48.dp, 48.dp).graphicsLayer { rotationZ = 11.37f },
            onClick = onSettings,
        )

        items.forEachIndexed { index, (destination, pair) ->
            val (icon, label) = pair
            val shownLabel = shortLabel(label, compactLabels)
            val seat = BOCAL_DOCK_SEATS.getOrNull(index) ?: return@forEachIndexed
            val selected = current == destination
            // 68×48 touch target around the unchanged 68×32 pill. dockSeat centres on the seat, so
            // the pill's visual centre does not move.
            Box(
                Modifier
                    .dockSeat(seat.x, seat.y, scale, width = 68.dp, height = 48.dp)
                    .graphicsLayer { rotationZ = seat.tilt }
                    .clip(RoundedCornerShape(50))
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(destination) })
                    .semantics(mergeDescendants = true) { contentDescription = label }
                    .testTag("nav-tab-$destination"),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .size(68.dp, 32.dp)
                        .clip(RoundedCornerShape(50))
                        .then(if (selected) Modifier.background(Color(0xFFE6E2D8)) else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    DockTabContent(icon, shownLabel, selected, showBadge = destination == "body" && badge)
                }
            }
        }
    }
}

/**
 * Flat fallback for windows too short for the arc (a phone in landscape): one 56 dp rail along the
 * bottom edge with the same destinations, test tags and accessible names. No Canvas, no scrubbing.
 */
@Composable
private fun CompactDockRail(
    items: List<Pair<String, Pair<ImageVector, String>>>,
    current: String?,
    badge: Boolean,
    compactLabels: Boolean,
    onSelect: (String) -> Unit,
    onQuickAdd: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(DockGeometry.RAIL_HEIGHT.dp)
            .testTag("nav-rail")
            .background(Color(0xFF20232B), RoundedCornerShape(28.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockSideAction(label = "Quick add", icon = Icons.Filled.Add, modifier = Modifier.size(48.dp), onClick = onQuickAdd)
        items.forEach { (destination, pair) ->
            val (icon, label) = pair
            val selected = current == destination
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(destination) })
                    .semantics(mergeDescendants = true) { contentDescription = label }
                    .testTag("nav-tab-$destination"),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(50))
                        .then(if (selected) Modifier.background(Color(0xFFE6E2D8)) else Modifier)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    DockTabContent(icon, shortLabel(label, compactLabels), selected, showBadge = destination == "body" && badge)
                }
            }
        }
        DockSideAction(label = "Settings", icon = Icons.Filled.Settings, modifier = Modifier.size(48.dp), onClick = onSettings)
    }
}

private fun shortLabel(label: String, compact: Boolean): String =
    if (compact) when (label) {
        "Progress" -> "Prog"
        "Calendar" -> "Cal"
        else -> label
    } else label

/** Icon, with the label beside it only while the tab is selected. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun DockTabContent(icon: ImageVector, shownLabel: String, selected: Boolean, showBadge: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (showBadge) {
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

/** A round touch target of at least 48 dp (the caller sizes it) around a 44 dp visible button. */
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
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .testTag("dock-action-${label.lowercase().replace(' ', '-')}"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF353A45), Color(0xFF1B1D24)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color(0xFFC4C6CC), modifier = Modifier.size(20.dp))
        }
    }
}

private data class DockSeat(val x: Float, val y: Float, val tilt: Float)

private val BOCAL_DOCK_SEATS = listOf(
    DockSeat(46.8f, 107.4f, -9.6f),
    DockSeat(123.6f, 93.2f, -4.83f),
    DockSeat(200f, 87.5f, 0f),
    DockSeat(276.4f, 93.2f, 4.83f),
    DockSeat(353.2f, 107.4f, 9.6f),
)

private fun Modifier.dockSeat(
    x: Float,
    y: Float,
    scale: Float,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
): Modifier = this
    .offset(x = (x * scale).dp - width / 2, y = (y * scale).dp - height / 2)
    .size(width, height)
