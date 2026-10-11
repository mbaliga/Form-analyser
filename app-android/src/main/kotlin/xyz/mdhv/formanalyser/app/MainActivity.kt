package xyz.mdhv.formanalyser.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import xyz.mdhv.formanalyser.app.data.AppDatabase
import xyz.mdhv.formanalyser.app.data.AppPrefs
import xyz.mdhv.formanalyser.app.data.DatabaseRecoveryRequired
import xyz.mdhv.formanalyser.app.data.DbRecovery
import xyz.mdhv.formanalyser.app.domain.*
import xyz.mdhv.formanalyser.app.ui.*
import xyz.mdhv.formanalyser.app.ui.theme.FormAnalyserTheme
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.vault.Vault

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
    const val PAIRED_SESSION = "paired_session"
    val TABS = setOf(HOME, TRAIN, PROGRESS, BODY, CALENDAR)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // These directories contain only transient plaintext/staging bytes. Wipe them before any
        // screen is rendered, including after a process death during document viewing/capture.
        Vault.wipeViewCache(applicationContext)
        runCatching { File(applicationContext.cacheDir, "camera").deleteRecursively() }
        SafExportPublisher.clearOrphanedStaging(applicationContext.cacheDir)
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
    val c = LocalContext.current.applicationContext
    var attempt by remember { mutableIntStateOf(0) }
    var databaseState by remember(c) {
        mutableStateOf<DatabaseState>(DatabaseState.Loading)
    }
    LaunchedEffect(c, attempt) {
        databaseState = DatabaseState.Loading
        databaseState =
            withContext(Dispatchers.IO) {
                runCatching {
                    AppDatabase.get(c)
                    DatabaseState.Ready
                }.getOrElse { failure ->
                    DatabaseState.Blocked(
                        failure as? DatabaseRecoveryRequired
                            ?: DatabaseRecoveryRequired(
                                "form-analyser.db",
                                null,
                                failure,
                            )
                    )
                }
            }
    }
    when (val state = databaseState) {
        DatabaseState.Loading -> Box(Modifier.fillMaxSize())
        is DatabaseState.Blocked ->
            DatabaseRecoveryScreen(
                state.failure,
                onRetry = { attempt++ },
                onReset = {
                    withContext(Dispatchers.IO) {
                        AppDatabase.resetAfterConfirmation(c, state.failure.backupPath)
                    }
                    AppPrefs(c).setOnboarded(false)
                    databaseState = DatabaseState.Ready
                },
            )
        DatabaseState.Ready -> {
            val p = remember { AppPrefs(c) }
            val onboarded by p.onboarded.collectAsState(initial = null as Boolean?)
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
    }
}

private sealed interface DatabaseState {
    data object Loading : DatabaseState
    data object Ready : DatabaseState
    data class Blocked(val failure: DatabaseRecoveryRequired) : DatabaseState
}

/**
 * A migration failure is a data-preservation event, not a reason to silently start over. This
 * screen is deliberately outside the normal navigation graph: none of the normal ViewModels may
 * be constructed until the athlete has either retried the open or explicitly approved a reset.
 */
@Composable
private fun DatabaseRecoveryScreen(
    failure: DatabaseRecoveryRequired,
    onRetry: () -> Unit,
    onReset: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var resetting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val canReset = DbRecovery.isUsableBackup(context, failure.backupPath, failure.dbName)
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Saved data needs recovery", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (canReset) {
                "Crocodyl could not open the saved database after an update. It has not been " +
                    "deleted. A verified copy is preserved on this device."
            } else {
                "Crocodyl could not open the saved database after an update, and it could not " +
                    "verify a recovery copy. Nothing has been deleted."
            }
        )
        failure.backupPath?.let { path ->
            Text("Recovery copy:\n$path", color = Hyle.OnSurfaceDim)
        }
        Text(
            "Resetting creates a fresh database and returns you to onboarding. The preserved copy " +
                "is not removed by that action.",
            color = Hyle.OnSurfaceDim,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetry, enabled = !resetting) { Text("Try again") }
            Button(
                onClick = {
                    resetting = true
                    error = null
                    scope.launch {
                        try {
                            onReset()
                        } catch (t: Throwable) {
                            if (t is kotlinx.coroutines.CancellationException) throw t
                            error = t.message ?: "The database could not be reset."
                            resetting = false
                        }
                    }
                },
                enabled = canReset && !resetting,
            ) {
                Text(if (resetting) "Resetting…" else "Reset after backup")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
/**
 * One-time, honest disclosure for [DbRecovery]: if the athlete explicitly approved a reset after a
 * failed migration, say so and retain the recovery path rather than hiding the data-loss boundary.
 * Dismissing clears the flag so it doesn't reappear.
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
                    "Crocodyl couldn't open your saved data after an update. After your confirmation, " +
                        "a fresh database was created. Your previous data was preserved on this device at:\n\n$it"
                }
                    ?: "Crocodyl couldn't open your saved data after an update. After your confirmation, " +
                        "a fresh database was created, but a recovery copy could not be located."
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
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    Scaffold(
        topBar = {
            if (onTab) TopRow(tabTitle(route)) { nav.navigate(Routes.SETTINGS) }
            // Every other route used to render with no bar, no back and no title. 22 of 27
            // destinations were reachable and then inescapable except by system back — and on a
            // desktop/ChromeOS window there is no system back gesture at all.
            // Dispatches back rather than popping the stack directly, so a screen guarding an
            // in-flight capture or an unsaved form with a BackHandler is honoured by the bar's own
            // arrow too. With no handler enabled the NavController's own callback pops, which is
            // exactly what popBackStack() did.
            else DetailBar(detailTitle(route)) { backDispatcher?.onBackPressed() }
        },
        bottomBar = {
            if (onTab) BottomBar(route, injuries > 0, { quick = true }) { navigateTab(nav, it) }
        },
    ) { pad ->
        // imePadding() once here rather than on eleven screens: every text field in the app lives
        // inside this NavHost, and on targetSdk 35 the window no longer resizes for the keyboard,
        // so without it the field being typed into can sit underneath the IME with no way to
        // scroll it into view. Applied after the Scaffold's own padding so the two stack.
        NavHost(nav, Routes.HOME, Modifier.padding(pad).imePadding()) {
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
                    { nav.navigate(Routes.PAIRED_SESSION) },
                )
            }
            composable(Routes.PAIRED_SESSION) { PairedSessionScreen(prefs) { nav.popBackStack() } }
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
            composable(Routes.CAPTURE) {
                CaptureScreen(
                    sessionVm,
                    onReview = { nav.navigate(Routes.REVIEW) },
                    onBackToSetup = { nav.popBackStack() },
                )
            }
            // A deleted session has nothing left to review, so the screen cannot stay open on it.
            composable(Routes.REVIEW) { ReviewScreen(sessionVm) { nav.popBackStack() } }
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
        // A data wipe used to "work" only as a side effect: the empty lambda meant the screen
        // stayed put and the tree happened to recompose when the onboarding pref flipped. Return
        // the athlete to Home explicitly and clear the stack behind them — after a wipe, every
        // entry above Home refers to records that no longer exist.
        SettingsDataScreen(
            settings,
            {
                nav.navigate(Routes.HOME) {
                    popUpTo(Routes.HOME) { inclusive = true }
                    launchSingleTop = true
                }
            },
            { nav.navigate(Routes.S_EXPORT) },
        )
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
            style = if (title == "Crocodyl") MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            color = Hyle.OnBackground,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, "Settings", tint = Hyle.OnSurfaceDim)
        }
    }
}

@Composable
private fun BottomBar(
    current: String?,
    badge: Boolean,
    onAdd: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val items =
        listOf(
            Routes.HOME to (Icons.Filled.Home to "Home"),
            Routes.TRAIN to (Icons.Filled.CameraAlt to "Train"),
            Routes.PROGRESS to (Icons.Filled.Timeline to "Progress"),
            Routes.BODY to (Icons.Filled.Accessibility to "Body"),
            Routes.CALENDAR to (Icons.Filled.CalendarMonth to "Calendar"),
        )
    Row(
        Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(Hyle.Surface.copy(alpha = .97f))
            .border(1.dp, Hyle.HairlineStrong, RoundedCornerShape(25.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (dest, pair) ->
            val (icon: ImageVector, label) = pair
            val selected = current == dest
            Column(
                Modifier.weight(1f).heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(if (selected) Hyle.Accent.copy(alpha = .18f) else Hyle.Surface.copy(alpha = 0f))
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(dest) })
                    .padding(horizontal = 2.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (dest == Routes.BODY && badge) {
                    BadgedBox({ Badge { Text("!") } }) {
                        Icon(icon, label, tint = if (selected) Hyle.AccentBright else Hyle.OnSurfaceDim)
                    }
                } else {
                    Icon(icon, label, tint = if (selected) Hyle.AccentBright else Hyle.OnSurfaceDim)
                }
                Text(
                    label,
                    color = if (selected) Hyle.OnBackground else Hyle.OnSurfaceDim,
                    fontSize = 9.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
        Column(
            Modifier.size(48.dp).clip(CircleShape)
                .background(Hyle.Accent.copy(alpha = .17f))
                .clickable(onClickLabel = "Open quick actions", role = Role.Button, onClick = onAdd),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Add, "Quick actions", tint = Hyle.AccentBright)
            Text("Add", color = Hyle.OnBackground, fontSize = 9.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * Top bar for every non-tab destination: a title and the way out.
 *
 * Deliberately mirrors [TopRow]'s metrics so a push does not feel like a different app — same
 * titleLarge on [Hyle.OnBackground], same dim icon tint, same 12dp top inset. The back arrow is
 * auto-mirrored because the app already takes handedness and RTL seriously elsewhere.
 */
@Composable
private fun DetailBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Hyle.OnBackground)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Hyle.OnBackground,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Title for a pushed destination. Matches on the route prefix so parameterised routes
 * (`injury_edit/{injuryId}`) resolve without repeating the argument pattern here.
 */
private fun detailTitle(r: String?): String =
    when (r?.substringBefore("/")) {
        Routes.CAPTURE -> "Capture"
        Routes.REVIEW -> "Review"
        Routes.SCORE -> "Score"
        Routes.LOG -> "Log"
        Routes.COACH -> "Coach"
        Routes.SETTINGS -> "Settings"
        Routes.S_PROFILE -> "Profile"
        Routes.S_RIGS -> "Rigs"
        Routes.S_CAPTURE -> "Capture"
        Routes.S_APPEARANCE -> "Appearance"
        Routes.S_DATA -> "Data"
        Routes.S_ABOUT -> "About"
        Routes.S_AI -> "AI coach"
        Routes.S_EXPORT -> "Export"
        Routes.S_WELLNESS -> "Wellness"
        Routes.S_STREAK -> "Streak"
        Routes.S_CYCLE -> "Cycle"
        Routes.S_MEDICATION -> "Medication"
        Routes.RIG_EDIT -> "Rig"
        Routes.INJURY_EDIT -> "Injury"
        Routes.PLAN_EDIT -> "Physio plan"
        Routes.DOC_VIEW -> "Document"
        Routes.PAIRED_SESSION -> "Local Multiplayer"
        else -> "Crocodyl"
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
