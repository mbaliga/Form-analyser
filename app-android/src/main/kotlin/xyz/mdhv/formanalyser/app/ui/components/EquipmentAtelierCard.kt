package xyz.mdhv.formanalyser.app.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.EquipmentAtelierActivity

/** Static, same-model artwork in the feed. Start the GPU viewer only on an explicit open. */
@Composable
fun EquipmentAtelierCard() {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val poster by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, dark) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val theme = if (dark) "dark" else "light"
            // Prefer the art-only GLB render, never a diagnostic screenshot containing UI.
            // Keep the earlier poster path as a fallback for source-only development builds.
            listOf(
                "atelier/cinematic/recurve-$theme.jpg",
                "atelier/posters/recurve-$theme.jpg",
            ).firstNotNullOfOrNull { path ->
                runCatching {
                    context.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                }.getOrNull()
            }
        }
    }
    val bg = if (dark) Color(0xff102119) else Color(0xffe0e5d9)
    val fg = if (dark) Color(0xfff3eddf) else Color(0xff253a30)
    Box(
        Modifier.fillMaxWidth().height(174.dp).clip(RoundedCornerShape(22.dp))
            .background(bg).clickable(role = Role.Button, onClickLabel = "Open 3D equipment studio") {
                context.startActivity(Intent(context, EquipmentAtelierActivity::class.java))
            }
    ) {
        poster?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(bg, bg.copy(alpha = 0.85f), Color.Transparent))))
        Column(Modifier.padding(20.dp).widthIn(max = 220.dp)) {
            Text("EQUIPMENT STUDIES", color = fg.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(11.dp))
            Text("Explore the details.", color = fg, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Text("Open the 3D studio", color = fg, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
