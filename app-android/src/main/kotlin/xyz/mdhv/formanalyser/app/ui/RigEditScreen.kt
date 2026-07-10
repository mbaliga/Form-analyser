package xyz.mdhv.formanalyser.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.domain.ArrowDto
import xyz.mdhv.formanalyser.app.domain.RigTuning
import xyz.mdhv.formanalyser.app.domain.StabilizerDto
import xyz.mdhv.formanalyser.app.domain.Tuning
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleSectionHeader
import xyz.mdhv.formanalyser.equipment.TuningContext
import xyz.mdhv.formanalyser.equipment.TuningValidator
import xyz.mdhv.formanalyser.equipment.WarningSeverity

/**
 * Collapsible "Advanced tuning" section for the rig editor (Phase 4).
 *
 * Owns the advanced-field editing state (brace/tiller/nock, plunger/clicker, string build,
 * stabilizer, arrow), emitting an updated [RigTuning] on every edit via [onChange]. It intentionally
 * does NOT own the basic poundage fields (marked/riser/otf) — those stay in the existing rig editor
 * and are the single source of truth. The emitted [RigTuning] preserves whatever basics were in
 * [initial]; the caller re-stamps its live basics onto the value before persisting (see the
 * integration note).
 *
 * Computed FOC %, GPP vs effective poundage, and [TuningValidator] warnings are shown inline. The
 * two derived numbers are computed on-device, so they carry the radium-green provenance mark.
 *
 * @param initial parsed starting spec (see [Tuning.parseFull]); state re-seeds when its identity changes.
 * @param drawLengthMm athlete draw length, for arrow-length + KE context (may be null).
 * @param effectiveLbs live effective poundage resolved from the editor's basic fields (may be null).
 * @param bowLengthIn estimated assembled bow length for the brace-height band check (may be null);
 *   see [Tuning.estimatedBowLengthIn].
 */
@Composable
fun AdvancedTuningSection(
    initial: RigTuning,
    drawLengthMm: Int?,
    effectiveLbs: Double?,
    bowLengthIn: Double?,
    onChange: (RigTuning) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(initial) { mutableStateOf(!isAdvancedEmpty(initial)) }
    var adv by remember(initial) { mutableStateOf(initial) }

    fun update(next: RigTuning) {
        adv = next
        onChange(next)
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Collapsible header.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Advanced tuning",
                color = Hyle.OnBackground,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(if (expanded) "▾" else "▸", color = Hyle.OnSurfaceDim)
        }
        if (!expanded) {
            Text(
                "Brace, tiller, nocking point, plunger, clicker, string, stabiliser and arrow.",
                color = Hyle.OnSurfaceDim,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // --- Limb / string geometry ---
                HyleSectionHeader("Limb & string geometry")
                NumField("Brace height", initial.braceHeightMm, "mm") {
                    update(adv.copy(braceHeightMm = it))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        NumField("Tiller top", initial.tillerTopMm, "mm") {
                            update(adv.copy(tillerTopMm = it))
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        NumField("Tiller bottom", initial.tillerBottomMm, "mm") {
                            update(adv.copy(tillerBottomMm = it))
                        }
                    }
                }
                NumField("Nocking point height", initial.nockingPointHeightMm, "mm") {
                    update(adv.copy(nockingPointHeightMm = it))
                }

                // --- Plunger / clicker ---
                HyleSectionHeader("Plunger & clicker")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        IntField("Plunger tension", initial.plungerTensionSteps, "steps") {
                            update(adv.copy(plungerTensionSteps = it))
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        NumField("Clicker position", initial.clickerPositionMm, "mm") {
                            update(adv.copy(clickerPositionMm = it))
                        }
                    }
                }

                // --- String build ---
                HyleSectionHeader("String")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        IntField("Strands", initial.stringStrands, "") {
                            update(adv.copy(stringStrands = it))
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        TextField("Material", initial.stringMaterial) {
                            update(adv.copy(stringMaterial = it))
                        }
                    }
                }

                // --- Stabilizer ---
                HyleSectionHeader("Stabiliser (rods in ″, weights in oz)")
                val stab = adv.stabilizer ?: StabilizerDto()
                fun updateStab(next: StabilizerDto) =
                    update(adv.copy(stabilizer = if (next.isEmpty()) null else next))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        NumField("Long rod", initial.stabilizer?.longRodLengthIn, "″") {
                            updateStab(stab.copy(longRodLengthIn = it))
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        NumField("Side rod", initial.stabilizer?.sideRodLengthIn, "″") {
                            updateStab(stab.copy(sideRodLengthIn = it))
                        }
                    }
                }
                NumField("Extender", initial.stabilizer?.extenderLengthIn, "″") {
                    updateStab(stab.copy(extenderLengthIn = it))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        NumField("Front weight", initial.stabilizer?.frontWeightOz, "oz") {
                            updateStab(stab.copy(frontWeightOz = it))
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        NumField("Side weight", initial.stabilizer?.sideWeightOz, "oz") {
                            updateStab(stab.copy(sideWeightOz = it))
                        }
                    }
                }

                // --- Arrow ---
                HyleSectionHeader("Arrow")
                val arrow = adv.arrow ?: ArrowDto()
                fun updateArrow(next: ArrowDto) =
                    update(adv.copy(arrow = if (next.isEmpty()) null else next))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        IntField("Spine", initial.arrow?.spine, "") {
                            updateArrow(arrow.copy(spine = it))
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        NumField("Length", initial.arrow?.lengthMm, "mm") {
                            updateArrow(arrow.copy(lengthMm = it))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        NumField("Point", initial.arrow?.pointGrains, "gr") {
                            updateArrow(arrow.copy(pointGrains = it))
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        NumField("Total mass", initial.arrow?.totalMassGrains, "gr") {
                            updateArrow(arrow.copy(totalMassGrains = it))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        NumField("Balance point", initial.arrow?.balancePointMm, "mm") {
                            updateArrow(arrow.copy(balancePointMm = it))
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        IntField("Vanes", initial.arrow?.vaneCount, "") {
                            updateArrow(arrow.copy(vaneCount = it))
                        }
                    }
                }

                // --- Computed metrics (on-device → radium provenance) + warnings ---
                ComputedMetrics(adv = adv, effectiveLbs = effectiveLbs)
                TuningWarnings(
                    adv = adv,
                    drawLengthMm = drawLengthMm,
                    effectiveLbs = effectiveLbs,
                    bowLengthIn = bowLengthIn,
                )
            }
        }
    }
}

/** True when no advanced field is recorded (section starts collapsed). */
private fun isAdvancedEmpty(t: RigTuning): Boolean =
    t.braceHeightMm == null && t.tillerTopMm == null && t.tillerBottomMm == null &&
        t.nockingPointHeightMm == null && t.plungerTensionSteps == null && t.clickerPositionMm == null &&
        t.stringStrands == null && t.stringMaterial == null &&
        (t.stabilizer == null || t.stabilizer.isEmpty()) &&
        (t.arrow == null || t.arrow.isEmpty())

@Composable
private fun ComputedMetrics(adv: RigTuning, effectiveLbs: Double?) {
    val foc = Tuning.focPercent(adv)
    val gpp = Tuning.gpp(adv, effectiveLbs)
    if (foc == null && gpp == null) return
    HyleSectionHeader("Computed")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Hyle.Surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (foc != null) {
            MetricRow("FOC", "${fmt1(foc)}%", "front-of-centre")
        }
        if (gpp != null) {
            MetricRow("GPP", "${fmt1(gpp)} gr/lb", "vs ≈${fmt1(effectiveLbs ?: 0.0)} lb effective")
        } else if (Tuning.gpp(adv, 1.0) != null) {
            // Mass known but no effective poundage resolved yet.
            Text(
                "Set marked or measured poundage to compute GPP.",
                color = Hyle.OnSurfaceDim,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** A computed-number row; the radium dot marks it as derived on-device. */
@Composable
private fun MetricRow(label: String, value: String, caption: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Hyle.RadiumGreen),
        )
        Text(label, color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
        Text(value, color = Hyle.OnBackground, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(caption, color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TuningWarnings(
    adv: RigTuning,
    drawLengthMm: Int?,
    effectiveLbs: Double?,
    bowLengthIn: Double?,
) {
    val warnings = remember(adv, drawLengthMm, effectiveLbs, bowLengthIn) {
        TuningValidator.validate(
            adv.toSpec(),
            TuningContext(
                bowLengthIn = bowLengthIn,
                drawLengthMm = drawLengthMm?.toDouble(),
                effectiveLbs = effectiveLbs,
            ),
        )
    }
    if (warnings.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        warnings.forEach { w ->
            val tint = if (w.severity == WarningSeverity.ERROR) Hyle.Danger else Hyle.Warning
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(if (w.severity == WarningSeverity.ERROR) "●" else "▲", color = tint)
                Text(w.message, color = Hyle.OnBackground, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// --- Field atoms -------------------------------------------------------------------------------

/** Optional decimal field. Seeds once from [initial]; emits the parsed value (null when blank). */
@Composable
private fun NumField(label: String, initial: Double?, suffix: String, onValue: (Double?) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial?.let(::fmtNum) ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = {
            val cleaned = it.filter { c -> c.isDigit() || c == '.' }.let(::singleDot)
            text = cleaned
            onValue(cleaned.toDoubleOrNull())
        },
        label = { Text(if (suffix.isBlank()) label else "$label ($suffix)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Optional integer field. */
@Composable
private fun IntField(label: String, initial: Int?, suffix: String, onValue: (Int?) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial?.toString() ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = {
            val cleaned = it.filter(Char::isDigit)
            text = cleaned
            onValue(cleaned.toIntOrNull())
        },
        label = { Text(if (suffix.isBlank()) label else "$label ($suffix)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Optional free-text field (blank → null). */
@Composable
private fun TextField(label: String, initial: String?, onValue: (String?) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = {
            val v = it.take(24)
            text = v
            onValue(v.ifBlank { null })
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun singleDot(s: String): String {
    val i = s.indexOf('.')
    if (i < 0) return s
    return s.substring(0, i + 1) + s.substring(i + 1).replace(".", "")
}

private fun fmtNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

private fun fmt1(v: Double): String {
    val r = Math.round(v * 10.0) / 10.0
    return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString()
}
