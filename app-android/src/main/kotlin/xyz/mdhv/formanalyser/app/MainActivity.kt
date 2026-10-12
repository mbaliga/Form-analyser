package xyz.mdhv.formanalyser.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
            if (onTab) {
                if (route != Routes.HOME) TopRow(tabTitle(route)) { nav.navigate(Routes.SETTINGS) }
            }
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
                    { nav.navigate(Routes.SETTINGS) },
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
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 12.dp, top = 7.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                if (title == "Crocodyl") "CROCODYL  /  RANGE" else "CROCODYL  /  TRAINING",
                color = Hyle.RadiumGreen,
                fontSize = 9.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(title, style = MaterialTheme.typography.titleMedium, color = Hyle.OnBackground)
        }
        IconButton(
            onClick = onSettings,
            modifier = Modifier.size(42.dp).clip(CircleShape).background(Hyle.SurfaceVariant.copy(alpha = .72f)),
        ) {
            Icon(Icons.Filled.Settings, "Settings", tint = Hyle.OnBackground, modifier = Modifier.size(19.dp))
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
    var scrubbedDestination by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    val selectDestination: (String) -> Unit = { destination ->
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onSelect(destination)
    }
    val selectedLabel = items.firstOrNull { it.first == (scrubbedDestination ?: current) }?.second?.second
        ?: "Home"
    BoxWithConstraints(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        // Hyle's navigation is a single 400 × 150 stage. Both arcs and their controls scale
        // together, so the contextual controls stay on the upper curve instead of drifting to
        // screen-edge FAB positions.
        val scale = maxWidth / 400.dp
        val stageWidth = maxWidth
        val seatsX = listOf(.117f, .309f, .5f, .691f, .883f)
        // Positions sampled from the lower Bézier so each control sits on the curve.
        val seatsY = listOf(.716f, .652f, .630f, .652f, .716f)
        val tilts = listOf(-9.6f, -4.83f, 0f, 4.83f, 9.6f)
        Box(Modifier.fillMaxWidth().height(150.dp * scale)) {
            Canvas(Modifier.fillMaxSize()) {
                val s = size.width / 400f
                val lower = Path().apply {
                    moveTo(22f * s, 112f * s)
                    quadraticTo(200f * s, 77f * s, 378f * s, 112f * s)
                }
                val upper = Path().apply {
                    moveTo(82.52f * s, 40.12f * s)
                    quadraticTo(200f * s, 24.88f * s, 317.48f * s, 40.12f * s)
                }
                val strokeCap = StrokeCap.Round
                listOf(lower to 61f, upper to 44f).forEach { (path, width) ->
                    drawPath(path, color = Hyle.Background.copy(alpha = .96f), style = Stroke(width * s, cap = strokeCap))
                    drawPath(
                        path,
                        brush = Brush.verticalGradient(0f to Hyle.SurfaceVariant, .55f to Hyle.Surface, 1f to Hyle.Background),
                        style = Stroke((width - 3f) * s, cap = strokeCap),
                    )
                    drawPath(path, color = Hyle.OnBackground.copy(alpha = .045f), style = Stroke(1f * s, cap = strokeCap))
                }
                // The active destination follows the upper Bézier rather than sitting on a
                // detached, flat label. It also becomes the live scrub preview while dragging.
                val textPath = android.graphics.Path().apply {
                    moveTo(82.52f * s, 40.12f * s)
                    quadTo(200f * s, 24.88f * s, 317.48f * s, 40.12f * s)
                }
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = Hyle.OnBackground.toArgb()
                    textSize = 14f * s
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawTextOnPath(selectedLabel, textPath, 0f, 5f * s, paint)
            }

            // Drag across the contextual rail to preview destinations; release commits the
            // highlighted route. The ordinary lower seats remain independently tappable.
            Box(
                Modifier.offset(x = 82.52.dp * scale, y = 15.dp * scale)
                    .width(234.96.dp * scale).height(50.dp * scale)
                    .pointerInput(items, current) {
                        var preview: String? = null
                        detectDragGestures(
                            onDragStart = { point ->
                                val index = ((point.x / size.width) * items.size).toInt().coerceIn(items.indices)
                                preview = items[index].first
                                scrubbedDestination = preview
                            },
                            onDrag = { change, _ ->
                                val index = ((change.position.x / size.width) * items.size).toInt().coerceIn(items.indices)
                                preview = items[index].first
                                scrubbedDestination = preview
                                change.consume()
                            },
                            onDragEnd = { preview?.let(selectDestination); scrubbedDestination = null },
                            onDragCancel = { scrubbedDestination = null },
                        )
                    }
                    .semantics { contentDescription = "Swipe across the upper arc to preview and select $selectedLabel" }
                    .testTag("nav-context-arc"),
            )

            items.forEachIndexed { index, (dest, pair) ->
                val (icon: ImageVector, label) = pair
                val selected = current == dest
                Box(
                    Modifier.align(Alignment.TopStart)
                        .offset(x = stageWidth * seatsX[index] - 48.dp * scale, y = 150.dp * seatsY[index] - 21.dp * scale)
                        .width(96.dp * scale).height(44.dp * scale)
                        .graphicsLayer { rotationZ = tilts[index] }
                        .testTag("nav-tab-$dest"),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier.widthIn(min = 44.dp * scale).height(42.dp * scale)
                            .clip(CircleShape)
                            .then(if (selected) Modifier
                                .background(Brush.radialGradient(listOf(Hyle.AccentBright, Hyle.Accent, Hyle.AccentDim)))
                                .border(1.dp * scale, Hyle.OnBackground.copy(alpha = .18f), CircleShape) else Modifier)
                            .selectable(selected = selected, role = Role.Tab, onClick = { selectDestination(dest) })
                            .padding(horizontal = if (selected) 12.dp * scale else 10.dp * scale),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (dest == Routes.BODY && badge) {
                            BadgedBox({ Badge { Text("!") } }) {
                                Icon(icon, label, tint = if (selected) Hyle.Background else Hyle.OnSurfaceDim, modifier = Modifier.size(19.dp * scale))
                            }
                        } else {
                            Icon(icon, label, tint = if (selected) Hyle.Background else Hyle.OnSurfaceDim, modifier = Modifier.size(19.dp * scale))
                        }
                        if (selected) {
                            Spacer(Modifier.width(7.dp * scale))
                            Text(
                                label,
                                color = Hyle.Background,
                                fontSize = 11.sp * scale,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // Left context control starts a range session; the right retains the familiar Add
            // sheet. Their centers match the continuation of the upper-rail geometry.
            ContextDockButton(
                modifier = Modifier.offset(x = (18.dp - 22.dp) * scale, y = (50.79.dp - 22.dp) * scale),
                icon = Icons.Filled.CameraAlt,
                label = "Train",
                stageScale = scale,
                rotation = -11.37f,
                onClick = { selectDestination(Routes.TRAIN) },
            )
            ContextDockButton(
                modifier = Modifier.offset(x = (382.dp - 22.dp) * scale, y = (50.79.dp - 22.dp) * scale),
                icon = Icons.Filled.Add,
                label = "Add",
                stageScale = scale,
                rotation = 11.37f,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAdd()
                },
            )
        }
    }
}

@Composable
private fun ContextDockButton(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    stageScale: Float,
    rotation: Float,
    onClick: () -> Unit,
) {
    Box(
        modifier.size(44.dp * stageScale).clip(CircleShape).background(Hyle.SurfaceVariant)
            .border(1.dp * stageScale, Hyle.HairlineStrong, CircleShape)
            .graphicsLayer { rotationZ = rotation }
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Hyle.AccentBright, modifier = Modifier.size(20.dp * stageScale))
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
