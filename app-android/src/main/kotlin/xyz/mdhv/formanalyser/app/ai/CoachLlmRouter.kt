package xyz.mdhv.formanalyser.app.ai

import javax.inject.Inject
import javax.inject.Singleton
import xyz.mdhv.formanalyser.app.ai.providers.CloudLlmClients
import xyz.mdhv.formanalyser.coach.CoachModel
import xyz.mdhv.formanalyser.coach.LlmClient
import xyz.mdhv.formanalyser.coach.ModelKind

/**
 * Routes a [CoachModel] to the [LlmClient] that serves it, and reports whether a required BYOK key
 * is present — the object graph [xyz.mdhv.formanalyser.app.domain.CoachViewModel]'s old hand-rolled
 * `ViewModelProvider.Factory` used to build inline (pre-Hilt), now a single `@Inject`-constructed
 * class Hilt assembles once from [KeyVault] and the [OnDeviceLlmClient] singleton (see
 * [xyz.mdhv.formanalyser.app.di.CoachModule] for that binding).
 *
 * A plain class rather than a pair of bound function types on purpose: nothing here can be compiled
 * to confirm it (no Android SDK in this environment — CI is the judge, same caveat as the rest of
 * the Android layer), so this took the least exotic Dagger shape available rather than betting that
 * two structurally-similar function bindings (`(CoachModel) -> LlmClient?` vs `(CoachModel) ->
 * Boolean`) resolve unambiguously. It also reads the same as every other `@Inject constructor` in
 * this module-by-module conversion.
 */
@Singleton
class CoachLlmRouter @Inject constructor(
    private val keyVault: KeyVault,
    private val onDevice: OnDeviceLlmClient,
) {
    /** The client for [model], or null if none is available (never thrown — see [LlmClient]'s seam). */
    fun resolve(model: CoachModel): LlmClient? =
        when (model.kind) {
            ModelKind.CLOUD -> CloudLlmClients.forModel(model) { p -> keyVault.getKey(p) }
            ModelKind.ON_DEVICE -> onDevice
        }

    /** True if [model] can be called right now — a BYOK key for cloud, always true on-device. */
    fun keyPresent(model: CoachModel): Boolean =
        when (model.kind) {
            ModelKind.CLOUD -> keyVault.hasKey(model.provider)
            ModelKind.ON_DEVICE -> true
        }
}
