package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.data.AppDatabase
import xyz.mdhv.formanalyser.app.data.AppPrefs
import xyz.mdhv.formanalyser.app.data.Repository
import xyz.mdhv.formanalyser.app.data.ScoringRepository

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = AppPrefs(app)

    val reduceMotion: Flow<Boolean> = prefs.reduceMotion
    val hapticStrength: Flow<String> = prefs.hapticStrength
    val glowIntensity: Flow<Int> = prefs.glowIntensity
    val keepRawVideo: Flow<Boolean> = prefs.keepRawVideo

    fun setReduceMotion(v: Boolean) = viewModelScope.launch { prefs.setReduceMotion(v) }

    fun setHapticStrength(v: String) = viewModelScope.launch { prefs.setHapticStrength(v) }

    fun setGlowIntensity(v: Int) = viewModelScope.launch { prefs.setGlowIntensity(v) }

    fun setKeepRawVideo(v: Boolean) = viewModelScope.launch { prefs.setKeepRawVideo(v) }

    /** Wipe everything on device and drop back to onboarding. */
    fun wipe(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { AppDatabase.get(getApplication()).clearAllTables() }
            prefs.setOnboarded(false)
            onDone()
        }
    }

    /** One retracted row as Settings → Data lists it. */
    data class Retracted(val id: String, val kind: Kind, val label: String, val detail: String) {
        enum class Kind {
            SESSION,
            SCORECARD,
            CHECKIN,
            PAIN,
            INJURY,
        }
    }

    private val repo = Repository(app)
    private val scoring = ScoringRepository(app)

    private val _retracted = MutableStateFlow<List<Retracted>>(emptyList())

    /**
     * What deleting actually did, and the way back.
     *
     * The delete confirmations promise the athlete their history is retracted rather than erased.
     * Without somewhere to see and undo it that promise is unfalsifiable — this is the surface that
     * makes it true.
     */
    val retracted: StateFlow<List<Retracted>> = _retracted

    fun loadRetracted() {
        viewModelScope.launch {
            _retracted.value =
                withContext(Dispatchers.IO) {
                    val a = repo.currentAthlete() ?: return@withContext emptyList()
                    repo.retractedSessions(a.id).map {
                        Retracted(
                            it.id,
                            Retracted.Kind.SESSION,
                            "Training session",
                            "${it.distanceMeters} m · ${it.arrowsActual ?: repo.shotCount(it.id)} arrow(s)",
                        )
                    } +
                        scoring.retractedScorecards().map {
                            Retracted(
                                it.id,
                                Retracted.Kind.SCORECARD,
                                it.roundName,
                                "${it.total} · ${it.distanceMeters} m",
                            )
                        } +
                        repo.wellness.retractedCheckins(a.id).map {
                            Retracted(
                                it.id,
                                Retracted.Kind.CHECKIN,
                                "Check-in",
                                it.kind.lowercase(),
                            )
                        } +
                        repo.body.retractedPainLogs(a.id).map {
                            Retracted(
                                it.id,
                                Retracted.Kind.PAIN,
                                "Pain entry",
                                "${it.regionId} · ${it.intensity}/10",
                            )
                        } +
                        repo.body.retractedInjuries(a.id).map {
                            Retracted(
                                it.id,
                                Retracted.Kind.INJURY,
                                "Injury",
                                "${it.mechanism.lowercase()} · severity ${it.severity}",
                            )
                        }
                }
        }
    }

    fun restore(item: Retracted) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (item.kind) {
                    Retracted.Kind.SESSION -> repo.restoreSession(item.id)
                    Retracted.Kind.SCORECARD -> scoring.restoreScorecard(item.id)
                    Retracted.Kind.CHECKIN -> repo.wellness.restoreCheckin(item.id)
                    Retracted.Kind.PAIN -> repo.body.restorePainLog(item.id)
                    Retracted.Kind.INJURY -> repo.body.restoreInjury(item.id)
                }
            }
            loadRetracted()
        }
    }
}
