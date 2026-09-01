package xyz.mdhv.formanalyser.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A labelled segmented single-select control (identity = position + label, not hue). */
@Composable
fun <T> HyleSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Hyle.SurfaceVariant),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { opt ->
            val isSel = opt == selected
            Text(
                text = label(opt),
                color = if (isSel) Hyle.OnBackground else Hyle.OnSurfaceDim,
                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSel) Hyle.Accent.copy(alpha = 0.25f) else Hyle.SurfaceVariant)
                    .clickable { onSelect(opt) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** A stepper with 48dp targets. Value shown between −/+. */
@Composable
fun HyleStepper(
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    step: Int = 1,
    suffix: String = "",
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = { if (value - step >= range.first) onChange(value - step) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = Hyle.Accent)
        }
        Text("$value$suffix", color = Hyle.OnBackground, style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = { if (value + step <= range.last) onChange(value + step) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Increase", tint = Hyle.Accent)
        }
    }
}

@Composable
fun HyleSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = Hyle.OnSurfaceDim,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

@Composable
fun HyleListRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Hyle.Surface)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.padding(end = 12.dp)) {
            Text(title, color = Hyle.OnBackground, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun HyleEmptyState(icon: String, lines: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Hyle.SurfaceVariant, RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(icon, style = MaterialTheme.typography.headlineMedium)
        lines.forEach { Text(it, color = Hyle.OnSurfaceDim) }
    }
}
