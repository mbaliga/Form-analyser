package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.data.DocumentEntity
import xyz.mdhv.formanalyser.app.data.InjuryEntity
import xyz.mdhv.formanalyser.app.data.PainLogEntity
import xyz.mdhv.formanalyser.app.data.PhysioExerciseEntity
import xyz.mdhv.formanalyser.app.data.PhysioPlanEntity
import xyz.mdhv.formanalyser.app.data.PhysioSessionEntity
import xyz.mdhv.formanalyser.app.data.Repository
import xyz.mdhv.formanalyser.app.vault.Vault
import java.io.File
import java.time.LocalDate
import java.util.UUID

class BodyViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val vault = Vault(app)

    private val _painToday = MutableStateFlow<Map<String, Int>>(emptyMap())     // region -> max intensity today
    val painToday: StateFlow<Map<String, Int>> = _painToday

    private val _painWeeks = MutableStateFlow<List<Map<String, Int>>>(emptyList()) // last 8 weeks, oldest first
    val painWeeks: StateFlow<List<Map<String, Int>>> = _painWeeks

    private val _regionHistory = MutableStateFlow<List<PainLogEntity>>(emptyList())
    val regionHistory: StateFlow<List<PainLogEntity>> = _regionHistory

    private val _injuries = MutableStateFlow<List<InjuryEntity>>(emptyList())
    val injuries: StateFlow<List<InjuryEntity>> = _injuries

    private val _plans = MutableStateFlow<List<PhysioPlanEntity>>(emptyList())
    val plans: StateFlow<List<PhysioPlanEntity>> = _plans

    private val _planExercises = MutableStateFlow<Map<String, List<PhysioExerciseEntity>>>(emptyMap())
    val planExercises: StateFlow<Map<String, List<PhysioExerciseEntity>>> = _planExercises

    private val _documents = MutableStateFlow<List<DocumentEntity>>(emptyList())
    val documents: StateFlow<List<DocumentEntity>> = _documents

    private val _vaultError = MutableStateFlow<String?>(null)
    val vaultError: StateFlow<String?> = _vaultError

    fun load() {
        viewModelScope.launch {
            val athlete = withContext(Dispatchers.IO) { repo.currentAthlete() } ?: return@launch
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val startOfToday = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val eightWeeksAgo = now - 8L * 7 * 24 * 3600 * 1000
                val pain = repo.body.painSince(athlete.id, eightWeeksAgo)

                _painToday.value = pain.filter { it.ts >= startOfToday }
                    .groupBy { it.regionId }.mapValues { (_, logs) -> logs.maxOf { it.intensity } }

                _painWeeks.value = (7 downTo 0).map { w ->
                    val from = now - (w + 1L) * 7 * 24 * 3600 * 1000
                    val to = now - w * 7L * 24 * 3600 * 1000
                    pain.filter { it.ts in from until to }
                        .groupBy { it.regionId }.mapValues { (_, logs) -> logs.maxOf { it.intensity } }
                }

                _injuries.value = repo.body.injuries(athlete.id)
                val plans = repo.body.allPlans(athlete.id)
                _plans.value = plans
                _planExercises.value = plans.associate { it.id to repo.body.exercisesFor(it.id) }
                _documents.value = repo.body.documents(athlete.id)
            }
        }
    }

    fun loadRegionHistory(regionId: String) {
        viewModelScope.launch {
            val athlete = withContext(Dispatchers.IO) { repo.currentAthlete() } ?: return@launch
            _regionHistory.value = withContext(Dispatchers.IO) { repo.body.painForRegion(athlete.id, regionId, 50) }
        }
    }

    fun logPain(regionId: String, intensity: Int, tags: List<String>) {
        viewModelScope.launch {
            val athlete = withContext(Dispatchers.IO) { repo.currentAthlete() } ?: return@launch
            withContext(Dispatchers.IO) {
                // auto-link to an active injury covering this region (Phase 3 §E)
                val injury = repo.body.activeInjuries(athlete.id)
                    .firstOrNull { regionId in JsonLists.decode(it.regionsJson) }
                repo.body.insertPain(
                    PainLogEntity(
                        id = UUID.randomUUID().toString(), athleteId = athlete.id,
                        ts = System.currentTimeMillis(), regionId = regionId,
                        intensity = intensity, tagsJson = JsonLists.encode(tags), injuryId = injury?.id,
                    ),
                )
            }
            load()
        }
    }

    /** RESOLVED stamps a resolved date (today, if none) — the invariant from the brief. */
    fun saveInjury(existing: InjuryEntity?, regions: List<String>, severity: Int, mechanism: String, status: String, notes: String?) {
        viewModelScope.launch {
            val athlete = withContext(Dispatchers.IO) { repo.currentAthlete() } ?: return@launch
            withContext(Dispatchers.IO) {
                repo.body.upsertInjury(
                    InjuryEntity(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        athleteId = athlete.id,
                        onset = existing?.onset ?: LocalDate.now().toString(),
                        regionsJson = JsonLists.encode(regions),
                        severity = severity,
                        mechanism = mechanism,
                        status = status,
                        resolvedDate = if (status == "RESOLVED") (existing?.resolvedDate ?: LocalDate.now().toString()) else null,
                        notes = notes,
                    ),
                )
            }
            load()
        }
    }

    fun savePlan(existing: PhysioPlanEntity?, title: String, regions: List<String>, schedule: List<String>, exercises: List<Triple<String, Int, Pair<Int?, Int?>>>) {
        viewModelScope.launch {
            val athlete = withContext(Dispatchers.IO) { repo.currentAthlete() } ?: return@launch
            withContext(Dispatchers.IO) {
                val planId = existing?.id ?: UUID.randomUUID().toString()
                repo.body.upsertPlan(
                    PhysioPlanEntity(
                        id = planId, athleteId = athlete.id, title = title.ifBlank { "Physio plan" },
                        targetRegionsJson = JsonLists.encode(regions),
                        scheduleJson = JsonLists.encode(schedule),
                        startDate = existing?.startDate ?: LocalDate.now().toString(),
                    ),
                )
                repo.body.clearExercises(planId)
                exercises.filter { it.first.isNotBlank() }.forEach { (name, sets, repsHold) ->
                    repo.body.upsertExercise(
                        PhysioExerciseEntity(
                            id = UUID.randomUUID().toString(), planId = planId,
                            name = name, sets = sets, reps = repsHold.first, holdS = repsHold.second,
                        ),
                    )
                }
            }
            load()
        }
    }

    fun logPhysioSession(planId: String, completedExerciseIds: List<String>, note: String?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.body.insertPhysioSession(
                    PhysioSessionEntity(UUID.randomUUID().toString(), planId, System.currentTimeMillis(), JsonLists.encode(completedExerciseIds), note),
                )
            }
            load()
        }
    }

    fun importDocument(source: Uri, title: String, mime: String, injuryId: String?) {
        viewModelScope.launch {
            val athlete = withContext(Dispatchers.IO) { repo.currentAthlete() } ?: return@launch
            withContext(Dispatchers.IO) {
                val docId = UUID.randomUUID().toString()
                runCatching { vault.encryptFrom(source, docId) }
                    .onSuccess { enc ->
                        repo.body.insertDocument(
                            DocumentEntity(
                                id = docId, athleteId = athlete.id, ts = System.currentTimeMillis(),
                                title = title.ifBlank { "Document" }, mime = mime,
                                encPath = enc.encPath, sha256 = enc.sha256, sizeBytes = enc.sizeBytes,
                                injuryId = injuryId,
                            ),
                        )
                    }
                    .onFailure { _vaultError.value = it.message ?: "Import failed" }
            }
            load()
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                vault.delete(doc.encPath)
                repo.body.deleteDocument(doc.id)
            }
            load()
        }
    }

    /** Decrypt for viewing; returns the plaintext cache file. */
    suspend fun openDocument(doc: DocumentEntity): File = withContext(Dispatchers.IO) {
        val ext = when {
            doc.mime.contains("pdf") -> "pdf"
            doc.mime.contains("png") -> "png"
            else -> "jpg"
        }
        vault.decryptToView(doc.encPath, doc.id, ext)
    }

    fun clearVaultError() { _vaultError.value = null }

    override fun onCleared() {
        vault.wipeViewCache()
        super.onCleared()
    }
}
