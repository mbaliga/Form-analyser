package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.data.AppPrefs
import xyz.mdhv.formanalyser.app.data.CheckinEntity
import xyz.mdhv.formanalyser.app.data.CycleEntity
import xyz.mdhv.formanalyser.app.data.EventEntity
import xyz.mdhv.formanalyser.app.data.HiatusEntity
import xyz.mdhv.formanalyser.app.data.LifeEventEntity
import xyz.mdhv.formanalyser.app.data.MedicationEntity
import xyz.mdhv.formanalyser.app.data.MoodEntity
import xyz.mdhv.formanalyser.app.data.Repository
import xyz.mdhv.formanalyser.app.data.RestDayEntity
import xyz.mdhv.formanalyser.app.data.SorenessEntity
import xyz.mdhv.formanalyser.wellness.CycleEstimate
import xyz.mdhv.formanalyser.wellness.CycleEstimator

/** Standalone "+ Log" entries + hiatus + cycle + medication (Phase 2 §D/F). */
class WellnessViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val prefs = AppPrefs(app)

    private val _openHiatus = MutableStateFlow<HiatusEntity?>(null)
    val openHiatus: StateFlow<HiatusEntity?> = _openHiatus

    private val _medications = MutableStateFlow<List<MedicationEntity>>(emptyList())
    val medications: StateFlow<List<MedicationEntity>> = _medications

    private val _cycles = MutableStateFlow<List<CycleEntity>>(emptyList())
    val cycles: StateFlow<List<CycleEntity>> = _cycles

    private val _cycleEstimate = MutableStateFlow<CycleEstimate?>(null)
    val cycleEstimate: StateFlow<CycleEstimate?> = _cycleEstimate

    /** Set after saving an impact-3 life event → UI shows the hiatus offer dialog. */
    private val _hiatusOfferFor = MutableStateFlow<String?>(null)
    val hiatusOfferFor: StateFlow<String?> = _hiatusOfferFor

    /** The last few check-ins, so Log can offer to take one back. */
    private val _recentCheckins = MutableStateFlow<List<CheckinEntity>>(emptyList())
    val recentCheckins: StateFlow<List<CheckinEntity>> = _recentCheckins

    /**
     * Retract a check-in.
     *
     * Not a delete: it feeds readiness, the streak and the calendar dots, and a post-session one is
     * referenced by `sessions.postCheckinId` and carries the RPE the load model reads. Restorable
     * from Settings → Data.
     */
    fun deleteCheckin(id: String) = io {
        repo.wellness.retractCheckin(id, System.currentTimeMillis())
    }

    fun load() {
        viewModelScope.launch {
            _openHiatus.value = withContext(Dispatchers.IO) { repo.wellness.openHiatus() }
            _medications.value = withContext(Dispatchers.IO) { repo.wellness.allMedications() }
            val cycles = withContext(Dispatchers.IO) { repo.wellness.allCycles() }
            _cycles.value = cycles
            withContext(Dispatchers.IO) { repo.currentAthlete() }
                ?.let { a ->
                    _recentCheckins.value =
                        withContext(Dispatchers.IO) {
                                repo.wellness.checkinsSince(
                                    a.id,
                                    System.currentTimeMillis() - 14L * 86_400_000L,
                                )
                            }
                            .sortedByDescending { it.ts }
                }
            _cycleEstimate.value =
                CycleEstimator.estimate(
                    cycleStarts = cycles.map { LocalDate.parse(it.startDate) },
                    today = LocalDate.now(),
                )
        }
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block() }
            load()
        }
    }

    fun logStandaloneCheckin(
        energy: Int?,
        sleep: Int?,
        motivation: Int?,
        soreness: List<String>,
        note: String?,
    ) = io {
        val athlete = repo.currentAthlete() ?: return@io
        val cid = UUID.randomUUID().toString()
        repo.wellness.insertCheckin(
            CheckinEntity(
                cid,
                athlete.id,
                System.currentTimeMillis(),
                "STANDALONE",
                false,
                energy,
                sleep,
                motivation,
                null,
                null,
                note,
            )
        )
        if (soreness.isNotEmpty())
            repo.wellness.insertSoreness(soreness.distinct().map { SorenessEntity(cid, it) })
    }

    fun logRestDay(date: LocalDate, planned: Boolean, note: String?) = io {
        repo.wellness.insertRestDay(RestDayEntity(date.toString(), planned, note))
    }

    fun logMood(mood: Int, tags: List<String>, note: String?) = io {
        repo.wellness.insertMood(
            MoodEntity(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                mood,
                JsonLists.encode(tags),
                note,
            )
        )
    }

    fun logLifeEvent(category: String, impact: Int, title: String, ongoing: Boolean) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            withContext(Dispatchers.IO) {
                repo.wellness.insertLifeEvent(
                    LifeEventEntity(
                        id = id,
                        startDate = LocalDate.now().toString(),
                        endDate = if (ongoing) null else LocalDate.now().toString(),
                        category = category,
                        impact = impact,
                        title = title,
                    )
                )
            }
            if (impact >= 3) _hiatusOfferFor.value = id
            load()
        }
    }

    fun acceptHiatusOffer() {
        val eventId = _hiatusOfferFor.value ?: return
        _hiatusOfferFor.value = null
        startHiatus(eventId)
    }

    fun declineHiatusOffer() {
        _hiatusOfferFor.value = null
    }

    fun startHiatus(lifeEventId: String? = null) = io {
        if (repo.wellness.openHiatus() == null) {
            repo.wellness.insertHiatus(
                HiatusEntity(
                    UUID.randomUUID().toString(),
                    LocalDate.now().toString(),
                    null,
                    lifeEventId,
                )
            )
        }
    }

    fun endHiatus() = io {
        repo.wellness.openHiatus()?.let {
            repo.wellness.endHiatus(it.id, LocalDate.now().toString())
        }
    }

    fun logCycleStart(date: LocalDate, flow: Int?) = io {
        repo.wellness.insertCycle(
            CycleEntity(UUID.randomUUID().toString(), date.toString(), null, flow)
        )
    }

    fun logMedication(name: String, dose: String?, taken: Boolean) = io {
        repo.wellness.insertMedication(
            MedicationEntity(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                name.trim(),
                dose?.trim()?.ifBlank { null },
                null,
                taken,
            )
        )
    }

    fun logEvent(title: String, tags: List<String>) = io {
        repo.wellness.insertEvent(
            EventEntity(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                title.trim(),
                null,
                JsonLists.encode(tags),
            )
        )
    }
}
