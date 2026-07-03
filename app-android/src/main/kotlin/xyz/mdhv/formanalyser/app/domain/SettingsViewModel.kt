package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.data.AppDatabase
import xyz.mdhv.formanalyser.app.data.AppPrefs

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
}
