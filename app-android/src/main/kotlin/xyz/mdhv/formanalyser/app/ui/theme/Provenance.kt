package xyz.mdhv.formanalyser.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The provenance-glow convention: a soft colored halo that signals where a number came from.
 * Radium-green = computed by on-device inference; alien-cyan = came from the cloud.
 * Implemented with Material's spot/ambient shadow coloring so it reads as a light bloom from
 * the upper-left key light.
 */
fun Modifier.provenanceGlow(
    color: Color = Hyle.RadiumGreen,
    cornerRadiusDp: Int = 16,
    elevationDp: Int = 14,
): Modifier = this.shadow(
    elevation = elevationDp.dp,
    shape = RoundedCornerShape(cornerRadiusDp.dp),
    ambientColor = color,
    spotColor = color,
    clip = false,
)
