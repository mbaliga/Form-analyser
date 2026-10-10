package xyz.mdhv.formanalyser.app.ui.components

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.EquipmentAtelierActivity
import xyz.mdhv.formanalyser.app.ui.theme.Hyle

/** Static, same-model artwork in the feed. Start the GPU viewer only on an explicit open. */
@Composable
fun EquipmentAtelierCard() {
    val context = LocalContext.current
    val bg = Color(0xff111018)
    val fg = Hyle.OnBackground
    Box(
        Modifier.fillMaxWidth().height(174.dp).clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xff1b1925), Color(0xff101513), Color(0xff111018)))
            )
            .border(1.dp, Hyle.HairlineStrong, RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClickLabel = "Open 3D equipment studio") {
                context.startActivity(Intent(context, EquipmentAtelierActivity::class.java))
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * .82f, size.height * .5f)
            listOf(.62f, .46f, .3f, .14f).forEachIndexed { index, radius ->
                drawCircle(
                    color = if (index == 3) Hyle.RadiumGreen.copy(alpha = .22f)
                    else Hyle.Accent.copy(alpha = .16f - index * .025f),
                    radius = size.minDimension * radius,
                    center = center,
                    style = Stroke(if (index == 3) 2.5f else 1.5f),
                )
            }
            drawLine(
                Hyle.OnBackground.copy(alpha = .34f),
                Offset(center.x - 90f, center.y + 72f),
                Offset(center.x + 45f, center.y - 52f),
                2f,
            )
        }
        Column(Modifier.padding(20.dp).widthIn(max = 220.dp)) {
            Text("EQUIPMENT STUDIES", color = Hyle.RadiumGreen, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(11.dp))
            Text("Explore the details.", color = fg, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Text("Open the 3D studio", color = fg, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
