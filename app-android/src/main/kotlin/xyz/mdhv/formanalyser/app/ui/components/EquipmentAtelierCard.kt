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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.EquipmentAtelierActivity
import xyz.mdhv.formanalyser.app.ui.theme.Hyle

/** Static, same-model artwork in the feed. Start the GPU viewer only on an explicit open. */
@Composable
fun EquipmentAtelierCard() {
    val context = LocalContext.current
    val poster = remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        poster.value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("atelier/cinematic/arrow-dark.jpg").use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    val bg = Color(0xff111018)
    Box(
        Modifier.fillMaxWidth().height(142.dp).clip(RoundedCornerShape(24.dp))
            .background(bg)
            .clickable(role = Role.Button, onClickLabel = "Open 3D equipment studio") {
                context.startActivity(Intent(context, EquipmentAtelierActivity::class.java))
            }
    ) {
        poster.value?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    0f to Color(0xff101016).copy(alpha = .56f),
                    .55f to Color(0xff101016).copy(alpha = .24f),
                    1f to Color.Transparent,
                )
            )
        )
        Column(
            Modifier.fillMaxHeight().padding(horizontal = 18.dp, vertical = 15.dp)
                .widthIn(max = 265.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("THE ATELIER  /  RECURVE", color = Hyle.RadiumGreen, style = MaterialTheme.typography.labelSmall)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Arrow, down to each part.", color = Hyle.OnBackground, style = MaterialTheme.typography.titleMedium)
                Text("Explore components and variants  →", color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
