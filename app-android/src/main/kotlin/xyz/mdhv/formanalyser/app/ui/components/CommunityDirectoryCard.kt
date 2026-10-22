package xyz.mdhv.formanalyser.app.ui.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.CommunityDirectoryActivity

@Composable
fun CommunityDirectoryCard() {
    val context = LocalContext.current
    OutlinedCard(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open community directory") {
            context.startActivity(Intent(context, CommunityDirectoryActivity::class.java))
        },
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("COMMUNITY DIRECTORY", style = MaterialTheme.typography.labelSmall)
            Text("Coaches, centres and stores", style = MaterialTheme.typography.titleMedium)
            Text("Open format, source-aware and user-contributed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
