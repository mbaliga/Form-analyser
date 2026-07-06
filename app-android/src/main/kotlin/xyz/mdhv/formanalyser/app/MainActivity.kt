package xyz.mdhv.formanalyser.app

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.aarso.crashrecovery.CrashRecovery
import xyz.mdhv.formanalyser.app.data.AppPrefs
import xyz.mdhv.formanalyser.app.domain.BodyViewModel
import xyz.mdhv.formanalyser.app.domain.CalendarViewModel
import xyz.mdhv.formanalyser.app.domain.HomeViewModel
import xyz.mdhv.formanalyser.app.domain.OnboardingViewModel
import xyz.mdhv.formanalyser.app.domain.RigsViewModel
import xyz.mdhv.formanalyser.app.domain.SessionViewModel
import xyz.mdhv.formanalyser.app.domain.SettingsViewModel
import xyz.mdhv.formanalyser.app.domain.WellnessViewModel
import xyz.mdhv.formanalyser.app.ui.BodyScreen
import xyz.mdhv.formanalyser.app.ui.CalendarScreen
import xyz.mdhv.formanalyser.app.ui.CaptureScreen
import xyz.mdhv.formanalyser.app.ui.ComingSoonScreen
import xyz.mdhv.formanalyser.app.ui.DocumentViewerScreen
import xyz.mdhv.formanalyser.app.ui.HomeScreen
import xyz.mdhv.formanalyser.app.ui.InjuryEditScreen
import xyz.mdhv.formanalyser.app.ui.LogScreen
import xyz.mdhv.formanalyser.app.ui.OnboardingScreen
import xyz.mdhv.formanalyser.app.ui.PhysioPlanEditScreen
import xyz.mdhv.formanalyser.app.ui.ReviewScreen
import xyz.mdhv.formanalyser.app.ui.SettingsAboutScreen
import xyz.mdhv.formanalyser.app.ui.SettingsAppearanceScreen
import xyz.mdhv.formanalyser.app.ui.SettingsCaptureScreen
import xyz.mdhv.formanalyser.app.ui.SettingsCycleScreen
import xyz.mdhv.formanalyser.app.ui.SettingsDataScreen
import xyz.mdhv.formanalyser.app.ui.SettingsMedicationScreen
import xyz.mdhv.formanalyser.app.ui.SettingsProfileScreen
import xyz.mdhv.formanalyser.app.ui.SettingsRigsScreen
import xyz.mdhv.formanalyser.app.ui.SettingsRootScreen
import xyz.mdhv.formanalyser.app.ui.SettingsStreakScreen
import xyz.mdhv.formanalyser.app.ui.SettingsWellnessScreen
import xyz.mdhv.formanalyser.app.ui.RigEditScreen
import xyz.mdhv.formanalyser.app.ui.TrainSetupScreen
import xyz.mdhv.formanalyser.app.ui.theme.FormAnalyserTheme
import xyz.mdhv.formanalyser.app.ui.theme.Hyle

private object R {
    const val HOME = "home"; const val TRAIN = "train"; const val CAPTURE = "capture"; const val REVIEW = "review"
    const val PROGRESS = "progress"; const val BODY = "body"; const val CALENDAR = "calendar"
    const val LOG = "log"
    const val SETTINGS = "settings"; const val S_PROFILE = "s_profile"; const val S_RIGS = "s_rigs"
    const val S_CAPTURE = "s_capture"; const val S_APPEARANCE = "s_appearance"; const val S_DATA = "s_data"; const val S_ABOUT = "s_about"
    const val S_WELLNESS = "s_wellness"; const val S_STREAK = "s_streak"; const val S_CYCLE = "s_cycle"; const val S_MEDICATION = "s_medication"
    const val RIG_EDIT = "rig_edit"; const val INJURY_EDIT = "injury_edit"; const val PLAN_EDIT = "plan_edit"; const val DOC_VIEW = "doc_view"
    val TABS = setOf(HOME, TRAIN, PROGRESS, BODY, CALENDAR)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the previous run crashed (Crocodyl's camera / pose / session paths do fail in the
        // field), show the shared recovery screen instead of relaunching straight back into the
        // crash. Finishes this Activity, so a device-only crash can't wedge the app.
        if (CrashRecovery.maybeShowRecovery(this, appLabel = "Crocodyl")) return

        setContent {
            FormAnalyserTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val onboarded by produceState<Boolean?>(initialValue = null, prefs) { prefs.onboarded.collect { value = it } }

    when (onboarded) {
        null -> Box(Modifier.fillMaxSize()) // brief splash while the flag loads
        false -> {
            val ovm: OnboardingViewModel = viewModel()
            OnboardingScreen(ovm, onDone = { /* onboarded flag flips → AppRoot recomposes */ })
        }
        else -> MainShell()
    }
}

@Composable
private fun MainShell() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val sessionVm: SessionViewModel = viewModel()
    val homeVm: HomeViewModel = viewModel()
    val rigsVm: RigsViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()
    val wellnessVm: WellnessViewModel = viewModel()
    val calendarVm: CalendarViewModel = viewModel()
    val bodyVm: BodyViewModel = viewModel()
    val cycleEnabled by prefs.cycleEnabled.collectAsState(initial = false)
    val activeInjuries by homeVm.activeInjuryCount.collectAsState()

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val onTab = route in R.TABS

    Scaffold(
        topBar = { if (onTab) TopRow(title = tabTitle(route), onSettings = { nav.navigate(R.SETTINGS) }) },
        bottomBar = { if (onTab) BottomBar(currentRoute = route, injuryBadge = activeInjuries > 0, onSelect = { navigateTab(nav, it) }) },
    ) { padding ->
        NavHost(navController = nav, startDestination = R.HOME, modifier = Modifier.padding(padding)) {
            composable(R.HOME) {
                HomeScreen(
                    vm = homeVm,
                    onStartSession = { nav.navigate(R.TRAIN) },
                    onOpenReview = { id -> sessionVm.openSession(id); nav.navigate(R.REVIEW) },
                    onManageRigs = { nav.navigate(R.S_RIGS) },
                    onLog = { nav.navigate(R.LOG) },
                )
            }
            composable(R.TRAIN) {
                TrainSetupScreen(sessionVm, onStarted = { nav.navigate(R.CAPTURE) }, onManageRigs = { nav.navigate(R.S_RIGS) })
            }
            composable(R.CAPTURE) { CaptureScreen(sessionVm, onReview = { nav.navigate(R.REVIEW) }) }
            composable(R.REVIEW) { ReviewScreen(sessionVm) }
            composable(R.PROGRESS) { ComingSoonScreen("📈", listOf("Your baseline lives here soon —", "stability trends, projections, and what your form is telling you.")) }
            composable(R.BODY) {
                BodyScreen(
                    vm = bodyVm,
                    onEditInjury = { id -> nav.navigate("${R.INJURY_EDIT}/${id ?: "new"}") },
                    onEditPlan = { id -> nav.navigate("${R.PLAN_EDIT}/${id ?: "new"}") },
                )
            }
            composable(R.CALENDAR) { CalendarScreen(calendarVm, onLog = { nav.navigate(R.LOG) }) }
            composable(R.LOG) { LogScreen(wellnessVm, cycleEnabled = cycleEnabled, onDone = { nav.popBackStack() }) }

            composable("${R.INJURY_EDIT}/{injuryId}") { entry ->
                val iid = entry.arguments?.getString("injuryId")
                InjuryEditScreen(
                    bodyVm,
                    injuryId = if (iid == "new") null else iid,
                    onDone = { nav.popBackStack() },
                    onOpenDocument = { docId -> nav.navigate("${R.DOC_VIEW}/$docId") },
                )
            }
            composable("${R.PLAN_EDIT}/{planId}") { entry ->
                val pid = entry.arguments?.getString("planId")
                PhysioPlanEditScreen(bodyVm, planId = if (pid == "new") null else pid, onDone = { nav.popBackStack() })
            }
            composable("${R.DOC_VIEW}/{docId}") { entry ->
                val did = entry.arguments?.getString("docId") ?: return@composable
                DocumentViewerScreen(bodyVm, documentId = did, onClose = { nav.popBackStack() })
            }

            settingsGraph(nav, rigsVm, settingsVm, wellnessVm)
        }
    }
}

private fun NavGraphBuilder.settingsGraph(
    nav: androidx.navigation.NavHostController,
    rigsVm: RigsViewModel,
    settingsVm: SettingsViewModel,
    wellnessVm: WellnessViewModel,
) {
    composable(R.SETTINGS) {
        SettingsRootScreen(
            onProfile = { nav.navigate(R.S_PROFILE) },
            onRigs = { nav.navigate(R.S_RIGS) },
            onCapture = { nav.navigate(R.S_CAPTURE) },
            onWellness = { nav.navigate(R.S_WELLNESS) },
            onStreak = { nav.navigate(R.S_STREAK) },
            onCycle = { nav.navigate(R.S_CYCLE) },
            onMedication = { nav.navigate(R.S_MEDICATION) },
            onAppearance = { nav.navigate(R.S_APPEARANCE) },
            onData = { nav.navigate(R.S_DATA) },
            onAbout = { nav.navigate(R.S_ABOUT) },
        )
    }
    composable(R.S_PROFILE) { SettingsProfileScreen(rigsVm) }
    composable(R.S_RIGS) { SettingsRigsScreen(rigsVm, onEdit = { id -> nav.navigate("${R.RIG_EDIT}/${id ?: "new"}") }) }
    composable("${R.RIG_EDIT}/{rigId}") { entry ->
        val rid = entry.arguments?.getString("rigId")
        RigEditScreen(rigsVm, rigId = if (rid == "new") null else rid, onDone = { nav.popBackStack() })
    }
    composable(R.S_CAPTURE) { SettingsCaptureScreen(settingsVm) }
    composable(R.S_WELLNESS) { SettingsWellnessScreen(settingsVm) }
    composable(R.S_STREAK) { SettingsStreakScreen(wellnessVm) }
    composable(R.S_CYCLE) { SettingsCycleScreen(wellnessVm) }
    composable(R.S_MEDICATION) { SettingsMedicationScreen(wellnessVm) }
    composable(R.S_APPEARANCE) { SettingsAppearanceScreen(settingsVm) }
    composable(R.S_DATA) { SettingsDataScreen(settingsVm, onWiped = { /* onboarded flips → AppRoot recomposes */ }) }
    composable(R.S_ABOUT) { SettingsAboutScreen() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopRow(title: String, onSettings: () -> Unit) {
    val context = LocalContext.current
    // Debug-only affordance: long-press the "Crocodyl" title to preview the crash-recovery
    // screen without forcing a real crash. buildConfig isn't enabled here, so gate on the
    // debuggable flag (release builds get a plain, inert title). combinedClickable is a plain
    // Modifier extension, but Modifier.weight() is a RowScope extension — so only the clickable
    // part is precomputed here; .weight(1f) is applied inside the Row's RowScope below.
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val previewClick = if (debuggable) {
        Modifier.combinedClickable(
            onClick = {},
            onLongClick = { context.startActivity(CrashRecovery.previewIntent(context, appLabel = "Crocodyl")) },
        )
    } else {
        Modifier
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Hyle.OnBackground, modifier = Modifier.weight(1f).then(previewClick))
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Hyle.OnSurfaceDim) }
    }
}

@Composable
private fun BottomBar(currentRoute: String?, injuryBadge: Boolean, onSelect: (String) -> Unit) {
    val items = listOf(
        R.HOME to (Icons.Filled.Home to "Home"),
        R.TRAIN to (Icons.Filled.CameraAlt to "Train"),
        R.PROGRESS to (Icons.Filled.ShowChart to "Progress"),
        R.BODY to (Icons.Filled.Accessibility to "Body"),
        R.CALENDAR to (Icons.Filled.CalendarMonth to "Calendar"),
    )
    NavigationBar(containerColor = Hyle.Surface) {
        items.forEach { (dest, iconLabel) ->
            val (icon: ImageVector, label) = iconLabel
            NavigationBarItem(
                selected = currentRoute == dest,
                onClick = { onSelect(dest) },
                icon = {
                    if (dest == R.BODY && injuryBadge) {
                        BadgedBox(badge = { Badge { Text("!") } }) { Icon(icon, contentDescription = label) }
                    } else {
                        Icon(icon, contentDescription = label)
                    }
                },
                label = { Text(label) },
            )
        }
    }
}

private fun tabTitle(route: String?): String = when (route) {
    R.HOME -> "Crocodyl"; R.TRAIN -> "Train"; R.PROGRESS -> "Progress"; R.BODY -> "Body"; R.CALENDAR -> "Calendar"; else -> "Crocodyl"
}

private fun navigateTab(nav: androidx.navigation.NavHostController, dest: String) {
    nav.navigate(dest) {
        popUpTo(R.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
