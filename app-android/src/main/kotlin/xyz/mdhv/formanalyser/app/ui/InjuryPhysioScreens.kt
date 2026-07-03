package xyz.mdhv.formanalyser.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.mdhv.formanalyser.app.domain.BodyViewModel
import xyz.mdhv.formanalyser.app.domain.JsonLists
import xyz.mdhv.formanalyser.app.ui.components.BodyAtlasCanvas
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleListRow
import xyz.mdhv.formanalyser.app.ui.theme.HyleSectionHeader
import xyz.mdhv.formanalyser.app.ui.theme.HyleSegmented
import xyz.mdhv.formanalyser.app.ui.theme.HyleStepper
import xyz.mdhv.formanalyser.body.BodyFace
import java.io.File

/** Two-face multi-select region picker used by injury + physio editors. */
@Composable
fun RegionPicker(selected: Set<String>, onToggle: (String) -> Unit) {
    var face by remember { mutableStateOf(BodyFace.BACK) }
    HyleSegmented(listOf(BodyFace.FRONT, BodyFace.BACK), face, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { face = it }
    BodyAtlasCanvas(face = face, selected = selected, onTap = onToggle)
}

@Composable
fun InjuryEditScreen(vm: BodyViewModel, injuryId: String?, onDone: () -> Unit, onOpenDocument: (String) -> Unit) {
    LaunchedEffect(Unit) { vm.load() }
    val injuries by vm.injuries.collectAsState()
    val documents by vm.documents.collectAsState()
    val vaultError by vm.vaultError.collectAsState()
    val existing = injuries.firstOrNull { it.id == injuryId }

    var regions by remember(existing?.id) { mutableStateOf(JsonLists.decode(existing?.regionsJson).toSet()) }
    var severity by remember(existing?.id) { mutableStateOf(existing?.severity ?: 1) }
    var mechanism by remember(existing?.id) { mutableStateOf(existing?.mechanism ?: "OVERUSE") }
    var status by remember(existing?.id) { mutableStateOf(existing?.status ?: "ACTIVE") }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes ?: "") }

    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && existing != null) {
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            vm.importDocument(uri, title = "Report ${documents.size + 1}", mime = mime, injuryId = existing.id)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (existing == null) "Log injury" else "Injury", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        HyleSectionHeader("Regions")
        RegionPicker(selected = regions) { id -> regions = if (id in regions) regions - id else regions + id }
        HyleSectionHeader("Severity")
        HyleSegmented(listOf(1, 2, 3), severity, { "$it" }) { severity = it }
        HyleSectionHeader("Mechanism")
        HyleSegmented(listOf("ACUTE", "OVERUSE", "UNKNOWN"), mechanism, { it.lowercase() }) { mechanism = it }
        HyleSectionHeader("Status")
        HyleSegmented(listOf("ACTIVE", "RECOVERING", "RESOLVED"), status, { it.lowercase() }) { status = it }
        if (status == "RESOLVED") Text("Resolving stamps today as the resolved date.", color = Hyle.OnSurfaceDim)
        OutlinedTextField(notes, { notes = it.take(500) }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())

        if (existing != null) {
            HyleSectionHeader("Documents (encrypted vault)")
            vaultError?.let { Text(it, color = Hyle.Danger) }
            documents.filter { it.injuryId == existing.id }.forEach { doc ->
                HyleListRow(
                    title = doc.title,
                    subtitle = "${doc.mime} · ${doc.sizeBytes / 1024} KB",
                    onClick = { onOpenDocument(doc.id) },
                    trailing = { OutlinedButton(onClick = { vm.deleteDocument(doc) }) { Text("Delete") } },
                )
            }
            OutlinedButton(
                onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Attach document (PDF / image)") }
        }

        Button(
            onClick = {
                vm.saveInjury(existing, regions.toList(), severity, mechanism, status, notes.ifBlank { null })
                onDone()
            },
            enabled = regions.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
    }
}

@Composable
fun PhysioPlanEditScreen(vm: BodyViewModel, planId: String?, onDone: () -> Unit) {
    LaunchedEffect(Unit) { vm.load() }
    val plans by vm.plans.collectAsState()
    val planExercises by vm.planExercises.collectAsState()
    val existing = plans.firstOrNull { it.id == planId }

    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "") }
    var regions by remember(existing?.id) { mutableStateOf(JsonLists.decode(existing?.targetRegionsJson).toSet()) }
    var schedule by remember(existing?.id) { mutableStateOf(JsonLists.decode(existing?.scheduleJson).toSet()) }
    var exercises by remember(existing?.id) {
        mutableStateOf(
            planExercises[existing?.id]?.map { Triple(it.name, it.sets, it.reps to it.holdS) }
                ?: listOf(Triple<String, Int, Pair<Int?, Int?>>("", 3, Pair(10, null))),
        )
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (existing == null) "New physio plan" else "Physio plan", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        OutlinedTextField(title, { title = it.take(60) }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        HyleSectionHeader("Target regions")
        RegionPicker(selected = regions) { id -> regions = if (id in regions) regions - id else regions + id }
        HyleSectionHeader("Days")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU").forEach { d ->
                FilterChip(selected = d in schedule, onClick = { schedule = if (d in schedule) schedule - d else schedule + d }, label = { Text(d) })
            }
        }
        HyleSectionHeader("Exercises")
        exercises.forEachIndexed { i, (name, sets, repsHold) ->
            OutlinedTextField(
                value = name,
                onValueChange = { v -> exercises = exercises.toMutableList().also { it[i] = Triple(v.take(60), sets, repsHold) } },
                label = { Text("Exercise ${i + 1}") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text("Sets", color = Hyle.OnSurfaceDim)
                    HyleStepper(sets, { v -> exercises = exercises.toMutableList().also { it[i] = Triple(name, v, repsHold) } }, 1..10)
                }
                Column {
                    Text("Reps", color = Hyle.OnSurfaceDim)
                    HyleStepper(repsHold.first ?: 0, { v -> exercises = exercises.toMutableList().also { it[i] = Triple(name, sets, (if (v == 0) null else v) to repsHold.second) } }, 0..50)
                }
            }
        }
        OutlinedButton(
            onClick = { exercises = exercises + Triple<String, Int, Pair<Int?, Int?>>("", 3, Pair(10, null)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add exercise") }

        Button(
            onClick = {
                vm.savePlan(existing, title, regions.toList(), schedule.toList(), exercises)
                onDone()
            },
            enabled = regions.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save plan") }
    }
}

/** Decrypts to the view cache and renders (image, or PDF page count note). Cache wiped on close. */
@Composable
fun DocumentViewerScreen(vm: BodyViewModel, documentId: String, onClose: () -> Unit) {
    val documents by vm.documents.collectAsState()
    val doc = documents.firstOrNull { it.id == documentId }
    var file by remember { mutableStateOf<File?>(null) }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(doc?.id) {
        val d = doc ?: return@LaunchedEffect
        scope.launch {
            val f = vm.openDocument(d)
            file = f
            bitmap = when {
                d.mime.contains("pdf") -> renderPdfFirstPage(f)
                else -> android.graphics.BitmapFactory.decodeFile(f.absolutePath)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(doc?.title ?: "Document", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        val bmp = bitmap
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = doc?.title,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text("Decrypting…", color = Hyle.OnSurfaceDim)
        }
        Button(onClick = { vm.vault.wipeViewCache(); onClose() }, modifier = Modifier.fillMaxWidth()) { Text("Close") }
    }
}

private fun renderPdfFirstPage(file: File): android.graphics.Bitmap? = runCatching {
    val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = android.graphics.pdf.PdfRenderer(pfd)
    try {
        val page = renderer.openPage(0)
        try {
            val scale = 2
            val bmp = android.graphics.Bitmap.createBitmap(page.width * scale, page.height * scale, android.graphics.Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        } finally {
            page.close()
        }
    } finally {
        renderer.close()
    }
}.getOrNull()
