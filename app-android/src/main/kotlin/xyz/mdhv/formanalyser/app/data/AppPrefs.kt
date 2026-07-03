package xyz.mdhv.formanalyser.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "crocodyl_prefs")

/** App preferences (Phase 1 Appendix A). Athlete profile fields live in Room; these are app prefs. */
class AppPrefs(private val context: Context) {
    private object Keys {
        val ROLE = stringPreferencesKey("role")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val HAPTIC_STRENGTH = stringPreferencesKey("haptic_strength")
        val GLOW_INTENSITY = intPreferencesKey("glow_intensity")
        val KEEP_RAW_VIDEO = booleanPreferencesKey("keep_raw_video")
    }

    val role: Flow<String> = context.dataStore.data.map { it[Keys.ROLE] ?: "ARCHER" }
    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDED] ?: false }
    val reduceMotion: Flow<Boolean> = context.dataStore.data.map { it[Keys.REDUCE_MOTION] ?: false }
    val hapticStrength: Flow<String> = context.dataStore.data.map { it[Keys.HAPTIC_STRENGTH] ?: "MED" }
    val glowIntensity: Flow<Int> = context.dataStore.data.map { it[Keys.GLOW_INTENSITY] ?: 100 }
    val keepRawVideo: Flow<Boolean> = context.dataStore.data.map { it[Keys.KEEP_RAW_VIDEO] ?: false }

    suspend fun setRole(v: String) = context.dataStore.edit { it[Keys.ROLE] = v }
    suspend fun setOnboarded(v: Boolean) = context.dataStore.edit { it[Keys.ONBOARDED] = v }
    suspend fun setReduceMotion(v: Boolean) = context.dataStore.edit { it[Keys.REDUCE_MOTION] = v }
    suspend fun setHapticStrength(v: String) = context.dataStore.edit { it[Keys.HAPTIC_STRENGTH] = v }
    suspend fun setGlowIntensity(v: Int) = context.dataStore.edit { it[Keys.GLOW_INTENSITY] = v }
    suspend fun setKeepRawVideo(v: Boolean) = context.dataStore.edit { it[Keys.KEEP_RAW_VIDEO] = v }
}
