package xyz.mdhv.formanalyser.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.*
import xyz.mdhv.formanalyser.app.data.AppPrefs
import xyz.mdhv.formanalyser.app.domain.*
import xyz.mdhv.formanalyser.app.ui.*
import xyz.mdhv.formanalyser.app.ui.theme.FormAnalyserTheme
import xyz.mdhv.formanalyser.app.ui.theme.Hyle

// NB: must NOT be named `R` — in package xyz.mdhv.formanalyser.app that collides with AGP's
// generated resources class xyz.mdhv.formanalyser.app.R at dex time (the resources class wins,
// so the object's INSTANCE field vanishes and any non-const access — e.g. TABS — throws
// NoSuchFieldError at runtime).
private object Routes {
    const val HOME = "home"
    const val TRAIN = "train"
    const val CAPTURE = "capture"
    const val REVIEW = "review"
    const val SCORE = "score"
    const val PROGRESS = "progress"
    const val BODY = "body"
    const val CALENDAR = "calendar"
    const val LOG = "log"
    const val COACH = "coach"
    const val SETTINGS = "settings"
    const val S_PROFILE = "s_profile"
    const val S_RIGS = "s_rigs"
    const val S_CAPTURE = "s_capture"
    const val S_APPEARANCE = "s_appearance"
    const val S_DATA = "s_data"
    const val S_ABOUT = "s_about"
    const val S_AI = "s_ai"
    const val S_EXPORT = "s_export"
    const val S_WELLNESS = "s_wellness"
    const val S_STREAK = "s_streak"
    const val S_CYCLE = "s_cycle"
    const val S_MEDICATION = "s_medication"
    const val RIG_EDIT = "rig_edit"
    const val INJURY_EDIT = "injury_edit"
    const val PLAN_EDIT = "plan_edit"
    const val DOC_VIEW = "doc_view"
    val TABS = setOf(HOME, TRAIN, PROGRESS, BODY, CALENDAR)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FormAnalyserTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val c = LocalContext.current
    val p = remember { AppPrefs(c) }
    val onboarded by produceState<Boolean?>(null, p) { p.onboarded.collect { value = it } }
    DbRecoveryNotice()
    when (onboarded) {
        null -> Box(Modifier.fillMaxSize())
        false -> {
            val vm: OnboardingViewModel = viewModel()
            OnboardingScreen(vm) {}
        }
        else -> MainShell()
    }
}

@Composable
/**
 * One-time, honest disclosure for [xyz.mdhv.formanalyser.app.data.DbRecovery]: if [AppDatabase.get]
 * had to back up and reset the database on this launch, say so — rather than the silent recovery
 * this replaced. Dismissing clears the flag so it doesn't reappear.
 */
private fun DbRecoveryNotice() {
    val c = LocalContext.current
    var e by remember { mutableStateOf(xyz.mdhv.formanalyser.app.data.DbRecovery.pendingNotice(c)) }
    val event = e ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Your saved data was reset") },
        text = {
            Text(
                event.backupPath?.let {
                    "Crocodyl couldn't open your saved data after an update. Your previous data was backed up at:\n\n$it"
                }
                    ?: "Crocodyl couldn't open your saved data after an update. A backup could not be made."
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    xyz.mdhv.formanalyser.app.data.DbRecovery.dismissNotice(c)
                    e = null
                }
            ) {
                Text("OK")
            }
        },
    )
}

@Composable
private fun MainShell() {
    val nav = rememberNavController()
    val c = LocalContext.current
    val prefs = remember { AppPrefs(c) }
    val sessionVm: SessionViewModel = viewModel()
    val homeVm: HomeViewModel = viewModel()
    val rigsVm: RigsViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()
    val wellnessVm: WellnessViewModel = viewModel()
    val calendarVm: CalendarViewModel = viewModel()
    val bodyVm: BodyViewModel = viewModel()
    val cycle by prefs.cycleEnabled.collectAsState(initial = false)
    val injuries by homeVm.activeInjuryCount.collectAsState()
    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route
    val onTab = route in Routes.TABS
    var quick by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { if (onTab) TopRow(tabTitle(route)) { nav.navigate(Routes.SETTINGS) } },
        bottomBar = { if (onTab) BottomBar(route, injuries > 0) { navigateTab(nav, it) } },
        floatingActionButton = {
            if (onTab) FloatingActionButton(onClick = { quick = true }) { Text("+") }
        },
    ) { pad ->
        NavHost(nav, Routes.HOME, Modifier.padding(pad)) {
            composable(Routes.HOME) {
                HomeScreen(
                    homeVm,
                    { nav.navigate(Routes.TRAIN) },
                    { nav.navigate(Routes.SCORE) },
                    { id ->
                        sessionVm.openSession(id)
                        nav.navigate(Routes.REVIEW)
                    },
                    { nav.navigate(Routes.S_RIGS) },
                    { nav.navigate(Routes.LOG) },
                    { nav.navigate(Routes.COACH) },
                )
            }
            composable(Routes.COACH) {
                val app = LocalContext.current.applicationContext as Application
                val vm: CoachViewModel = viewModel(factory = CoachViewModel.factory(app))
                CoachScreen(vm) { nav.navigate(Routes.S_AI) }
            }
            composable(Routes.TRAIN) {
                TrainSetupScreen(
                    sessionVm,
                    { nav.navigate(Routes.CAPTURE) },
                    { nav.navigate(Routes.S_RIGS) },
                )
            }
            composable(Routes.CAPTURE) { CaptureScreen(sessionVm) { nav.navigate(Routes.REVIEW) } }
            composable(Routes.REVIEW) { ReviewScreen(sessionVm) }
            composable(Routes.SCORE) {
                val vm: ScoringViewModel = viewModel()
                ScoringScreen(vm)
            }
            composable(Routes.PROGRESS) {
                val vm: ProgressViewModel = viewModel()
                ProgressScreen(vm)
            }
            composable(Routes.BODY) {
                BodyScreen(
                    bodyVm,
                    { id -> nav.navigate("${Routes.INJURY_EDIT}/${id?:"new"}") },
                    { id -> nav.navigate("${Routes.PLAN_EDIT}/${id?:"new"}") },
                )
            }
            composable(Routes.CALENDAR) { CalendarScreen(calendarVm) { nav.navigate(Routes.LOG) } }
            composable(Routes.LOG) { LogScreen(wellnessVm, cycle) { nav.popBackStack() } }
            composable("${Routes.INJURY_EDIT}/{injuryId}") { e ->
                val id = e.arguments?.getString("injuryId")
                InjuryEditScreen(bodyVm, if (id == "new") null else id, { nav.popBackStack() }) {
                    doc ->
                    nav.navigate("${Routes.DOC_VIEW}/$doc")
                }
            }
            composable("${Routes.PLAN_EDIT}/{planId}") { e ->
                val id = e.arguments?.getString("planId")
                PhysioPlanEditScreen(bodyVm, if (id == "new") null else id) { nav.popBackStack() }
            }
            composable("${Routes.DOC_VIEW}/{docId}") { e ->
                val id = e.arguments?.getString("docId") ?: return@composable
                DocumentViewerScreen(bodyVm, id) { nav.popBackStack() }
            }
            settingsGraph(nav, rigsVm, settingsVm, wellnessVm)
        }
    }
    if (quick)
        QuickAddSheet(
            { quick = false },
            { nav.navigate(Routes.TRAIN) },
            { nav.navigate(Routes.SCORE) },
            { nav.navigate(Routes.LOG) },
            { nav.navigate(Routes.S_RIGS) },
            { nav.navigate(Routes.PROGRESS) },
            { nav.navigate(Routes.PROGRESS) },
            { nav.navigate(Routes.LOG) },
            { nav.navigate(Routes.S_DATA) },
        )
}

private fun NavGraphBuilder.settingsGraph(
    nav: androidx.navigation.NavHostController,
    rigs: RigsViewModel,
    settings: SettingsViewModel,
    wellness: WellnessViewModel,
) {
    composable(Routes.SETTINGS) {
        SettingsRootScreen(
            { nav.navigate(Routes.S_PROFILE) },
            { nav.navigate(Routes.S_RIGS) },
            { nav.navigate(Routes.S_CAPTURE) },
            { nav.navigate(Routes.S_WELLNESS) },
            { nav.navigate(Routes.S_STREAK) },
            { nav.navigate(Routes.S_CYCLE) },
            { nav.navigate(Routes.S_MEDICATION) },
            { nav.navigate(Routes.S_APPEARANCE) },
            { nav.navigate(Routes.S_AI) },
            { nav.navigate(Routes.S_DATA) },
            { nav.navigate(Routes.S_ABOUT) },
        )
    }
    composable(Routes.S_AI) { AiSettingsScreen() }
    composable(Routes.S_EXPORT) {
        val vm: ExportViewModel = viewModel()
        ExportScreen(vm)
    }
    composable(Routes.S_PROFILE) { SettingsProfileScreen(rigs) }
    composable(Routes.S_RIGS) {
        SettingsRigsScreen(rigs) { id -> nav.navigate("${Routes.RIG_EDIT}/${id?:"new"}") }
    }
    composable("${Routes.RIG_EDIT}/{rigId}") { e ->
        val id = e.arguments?.getString("rigId")
        RigEditScreen(rigs, if (id == "new") null else id) { nav.popBackStack() }
    }
    composable(Routes.S_CAPTURE) { SettingsCaptureScreen(settings) }
    composable(Routes.S_WELLNESS) { SettingsWellnessScreen(settings) }
    composable(Routes.S_STREAK) { SettingsStreakScreen(wellness) }
    composable(Routes.S_CYCLE) { SettingsCycleScreen(wellness) }
    composable(Routes.S_MEDICATION) { SettingsMedicationScreen(wellness) }
    composable(Routes.S_APPEARANCE) { SettingsAppearanceScreen(settings) }
    composable(Routes.S_DATA) {
        SettingsDataScreen(settings, {}, { nav.navigate(Routes.S_EXPORT) })
    }
    composable(Routes.S_ABOUT) { SettingsAboutScreen() }
}

@Composable
private fun TopRow(title: String, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Hyle.OnBackground,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, "Settings", tint = Hyle.OnSurfaceDim)
        }
    }
}

@Composable
private fun BottomBar(current: String?, badge: Boolean, onSelect: (String) -> Unit) {
    val items =
        listOf(
            Routes.HOME to (Icons.Filled.Home to "Home"),
            Routes.TRAIN to (Icons.Filled.CameraAlt to "Train"),
            Routes.PROGRESS to (Icons.Filled.ShowChart to "Progress"),
            Routes.BODY to (Icons.Filled.Accessibility to "Body"),
            Routes.CALENDAR to (Icons.Filled.CalendarMonth to "Calendar"),
        )
    NavigationBar(containerColor = Hyle.Surface) {
        items.forEach { (dest, p) ->
            val (icon: ImageVector, label) = p
            NavigationBarItem(
                current == dest,
                { onSelect(dest) },
                icon = {
                    if (dest == Routes.BODY && badge)
                        BadgedBox({ Badge { Text("!") } }) { Icon(icon, label) }
                    else Icon(icon, label)
                },
                label = { Text(label) },
            )
        }
    }
}

private fun tabTitle(r: String?) =
    when (r) {
        Routes.HOME -> "Crocodyl"
        Routes.TRAIN -> "Train"
        Routes.PROGRESS -> "Progress"
        Routes.BODY -> "Body"
        Routes.CALENDAR -> "Calendar"
        else -> "Crocodyl"
    }

private fun navigateTab(nav: androidx.navigation.NavHostController, dest: String) {
    nav.navigate(dest) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
