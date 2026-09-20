package xyz.mdhv.formanalyser.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.ui.theme.FormAnalyserTheme

/** Community directory shell; records will be sourced with provenance and user consent. */
class CommunityDirectoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FormAnalyserTheme {
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TextButton(onClick = ::finish) { Text("Back") }
                    Text("Community directory", style = MaterialTheme.typography.headlineMedium)
                    Text("Coaches, training centres and stores — community-maintained with source and update date.")
                    DirectoryCategory("Coaches", "Technique, form and competition support")
                    DirectoryCategory("Training centres", "Clubs, pistes, ranges and facilities")
                    DirectoryCategory("Stores", "Equipment and service providers")
                    Text(
                        "No live records are bundled yet. See docs/crocodyl/community-directory for the prescribed format and provenance rules.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectoryCategory(title: String, subtitle: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
