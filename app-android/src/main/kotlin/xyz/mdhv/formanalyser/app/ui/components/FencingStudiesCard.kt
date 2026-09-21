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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.FencingActivity

/** Entry card for the Fencing equipment and manual referee surface. */
@Composable
fun FencingStudiesCard() {
    val context = LocalContext.current
    val poster = remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        poster.value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("atelier/art/range-dawn.webp").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    val bg = Color(0xff17212b)
    Box(
        Modifier.fillMaxWidth().height(174.dp).clip(RoundedCornerShape(22.dp))
            .background(bg).clickable(role = Role.Button, onClickLabel = "Open Fencing studies") {
                context.startActivity(Intent(context, FencingActivity::class.java))
            },
    ) {
        poster.value?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(bg, bg.copy(alpha = .86f), Color.Transparent))))
        Column(Modifier.padding(20.dp).widthIn(max = 250.dp)) {
            Text("FENCING STUDIES", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(10.dp))
            Text("Sabre · Foil · Epee", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Weapon information and a manual referee console", color = Color.White.copy(alpha = .86f))
        }
    }
}
