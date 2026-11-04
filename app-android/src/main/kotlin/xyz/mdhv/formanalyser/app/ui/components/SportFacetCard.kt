package xyz.mdhv.formanalyser.app.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.BoulderingActivity
import xyz.mdhv.formanalyser.app.MuayThaiActivity

@Composable
fun MuayThaiFacetCard() = SportFacetCard(
    imagePath = "atelier/cinematic/muay-thai-training.jpg",
    eyebrow = "MUAY THAI",
    title = "Learn the rhythm.",
    subtitle = "Log rounds, focus and effort",
    accent = Color(0xffffb26b),
    onClick = { context -> context.startActivity(Intent(context, MuayThaiActivity::class.java)) },
)

@Composable
fun BoulderingFacetCard() = SportFacetCard(
    imagePath = "atelier/cinematic/bouldering-session.jpg",
    eyebrow = "BOULDERING",
    title = "Trace your progress.",
    subtitle = "Track grades, attempts and sends",
    accent = Color(0xffb6a5ff),
    onClick = { context -> context.startActivity(Intent(context, BoulderingActivity::class.java)) },
)

@Composable
private fun SportFacetCard(
    imagePath: String,
    eyebrow: String,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: (android.content.Context) -> Unit,
) {
    val context = LocalContext.current
    val poster = remember(imagePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(imagePath) {
        poster.value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(imagePath).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    val base = Color(0xff111016)
    Box(
        Modifier.fillMaxWidth().height(154.dp).clip(RoundedCornerShape(24.dp))
            .background(base).clickable(role = Role.Button, onClickLabel = "Open $eyebrow training journal") {
                onClick(context)
            },
    ) {
        poster.value?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(base.copy(alpha = .94f), base.copy(alpha = .65f), Color.Transparent))
            ),
        )
        Column(Modifier.padding(20.dp).widthIn(max = 280.dp)) {
            Text(eyebrow, color = accent, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(10.dp))
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(7.dp))
            Text(subtitle, color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
