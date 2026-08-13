package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleListRow

data class QuickAddAction(
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    onDismiss: () -> Unit,
    onPractice: () -> Unit,
    onScore: () -> Unit,
    onWellness: () -> Unit,
    onEquipment: () -> Unit,
    onGoal: () -> Unit,
    onPlan: () -> Unit,
    onEventOrNote: () -> Unit,
    onImport: () -> Unit,
) {
    val actions =
        listOf(
            QuickAddAction("Quick Practice", "Form capture with remembered setup", onPractice),
            QuickAddAction("Score a Round", "Numeric · plot · observer · End Scan review", onScore),
            QuickAddAction("Unscored Arrows", "Training session without a scorecard", onPractice),
            QuickAddAction("Plot an End", "Open scoring and switch to Plot", onScore),
            QuickAddAction(
                "Scan Target",
                "Open End Scan review; detector requires range validation",
                onScore,
            ),
            QuickAddAction("Wellness", "Optional check-in / body context", onWellness),
            QuickAddAction("Rest", "Log rest context", onWellness),
            QuickAddAction("Workout", "Log training context", onWellness),
            QuickAddAction("Equipment Change", "Rig and tuning", onEquipment),
            QuickAddAction("Plan Session", "Training plan", onPlan),
            QuickAddAction("Goal", "Create or review goals", onGoal),
            QuickAddAction("Event", "Competition / life / training event", onEventOrNote),
            QuickAddAction("Note", "Add context without inventing a metric", onEventOrNote),
            QuickAddAction("Import", "Portable import remains a validation gap", onImport),
        )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Quick Add", color = Hyle.OnBackground)
            Text("Local actions · no account required", color = Hyle.OnSurfaceDim)
            actions.forEach { a ->
                HyleListRow(
                    title = a.title,
                    subtitle = a.subtitle,
                    onClick =
                        if (a.enabled)
                            ({
                                a.onClick()
                                onDismiss()
                            })
                        else null,
                )
            }
        }
    }
}
