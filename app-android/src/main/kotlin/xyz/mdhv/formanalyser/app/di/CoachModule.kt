package xyz.mdhv.formanalyser.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import xyz.mdhv.formanalyser.app.ai.AiSettings
import xyz.mdhv.formanalyser.app.ai.OnDeviceEngineRegistry
import xyz.mdhv.formanalyser.app.ai.OnDeviceLlmClient

/**
 * Coach on-device-runtime wiring for Hilt.
 *
 * [OnDeviceLlmClient] cannot be a plain `@Inject constructor` class like
 * [xyz.mdhv.formanalyser.app.ai.CoachLlmRouter] because building it needs a `modelPath` lambda
 * (mirroring the BYOK clients' `apiKey()` pattern — see that class's KDoc) and a registration side
 * effect, not just its declared constructor parameters. `@Singleton` here matches the pre-Hilt
 * factory: this app has exactly one on-device engine at a time by design, registered with
 * [OnDeviceEngineRegistry] so Settings' remove/replace flow can close its native resources before
 * touching the model file on disk.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoachModule {

    @Provides
    @Singleton
    fun provideOnDeviceLlmClient(
        @ApplicationContext context: Context,
        aiSettings: AiSettings,
    ): OnDeviceLlmClient {
        val client =
            OnDeviceLlmClient(context, modelPath = { runBlocking { aiSettings.onDeviceModelPath.first() } })
        // So Settings' remove/replace flow can close this engine's native resources before touching
        // the file on disk — unchanged from the pre-Hilt factory; only WHO calls it moved.
        OnDeviceEngineRegistry.register(client)
        return client
    }
}
