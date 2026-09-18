package xyz.mdhv.formanalyser.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt's dependency-injection root (this pass — see CROCODYL_STATUS.md §4.4, "Hilt DI: kept manual
 * to de-risk blind compilation"). `@HiltAndroidApp` triggers Hilt's annotation processor to generate
 * the top-level [dagger.hilt.components.SingletonComponent] that every `@HiltViewModel` and
 * `@Inject`-constructed repository/service in the app hangs off, and wires `AndroidManifest.xml`'s
 * `android:name=".CrocodylApp"` so [MainActivity] (now `@AndroidEntryPoint`) can receive them.
 *
 * Deliberately empty otherwise: nothing in this codebase needs `Application.onCreate()` work today
 * (the old manual DI was per-ViewModel `Application`-constructor plumbing, not app-wide init), and
 * adding speculative startup logic here would be scope creep unrelated to the DI wiring itself.
 */
@HiltAndroidApp
class CrocodylApp : Application()
