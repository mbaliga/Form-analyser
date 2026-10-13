package xyz.mdhv.formanalyser.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mdhv.formanalyser.app.ui.theme.Hyle

/** Bocal's floating, five-destination bottom dock, expressed with Crocodyl's Hyle colors. */
@Composable
internal fun ArcNavigationDock(
    items: List<Pair<String, Pair<ImageVector, String>>>,
    current: String?,
    badge: Boolean,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xF20D0E11))
            .border(1.dp, Color(0xFF292A30), RoundedCornerShape(24.dp)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (destination, pair) ->
            val (icon, label) = pair
            val selected = current == destination
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 3.dp, vertical = 3.dp)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .then(if (selected) Modifier.background(Color(0xFF182622)) else Modifier)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(destination) })
                    .semantics(mergeDescendants = true) { contentDescription = label }
                    .testTag("nav-tab-$destination"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (destination == "body" && badge) {
                    BadgedBox(badge = { Badge { Text("!") } }) {
                        Icon(icon, label, tint = if (selected) Hyle.AccentBright else Color(0xFF77777D), modifier = Modifier.size(20.dp))
                    }
                } else {
                    Icon(icon, label, tint = if (selected) Hyle.AccentBright else Color(0xFF77777D), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    label,
                    color = if (selected) Hyle.AccentBright else Color(0xFF85858B),
                    maxLines = 1,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
