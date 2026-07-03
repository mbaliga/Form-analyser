package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.ui.theme.HyleEmptyState

/** Shared stub for the not-yet-built tabs (Phase 1 §5). No dead buttons. */
@Composable
fun ComingSoonScreen(icon: String, lines: List<String>) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        HyleEmptyState(icon = icon, lines = lines)
    }
}
