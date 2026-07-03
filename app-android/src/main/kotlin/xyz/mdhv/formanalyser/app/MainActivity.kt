package xyz.mdhv.formanalyser.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import xyz.mdhv.formanalyser.app.data.AppPrefs
import xyz.mdhv.formanalyser.app.domain.HomeViewModel
import xyz.mdhv.formanalyser.app.domain.OnboardingViewModel
import xyz.mdhv.formanalyser.app.domain.RigsViewModel
import xyz.mdhv.formanalyser.app.domain.SessionViewModel
import xyz.mdhv.formanalyser.app.domain.SettingsViewModel
import xyz.mdhv.formanalyser.app.ui.CaptureScreen
import xyz.mdhv.formanalyser.app.ui.ComingSoonScreen
import xyz.mdhv.formanalyser.app.ui.HomeScreen
import xyz.mdhv.formanalyser.app.ui.OnboardingScreen
import xyz.mdhv.formanalyser.app.ui.ReviewScreen
import xyz.mdhv.formanalyser.app.ui.SettingsAboutScreen
import xyz.mdhv.formanalyser.app.ui.SettingsAppearanceScreen
import xyz.mdhv.formanalyser.app.ui.SettingsCaptureScreen
import xyz.mdhv.formanalyser.app.ui.SettingsDataScreen
import xyz.mdhv.formanalyser.app.ui.SettingsProfileScreen
import xyz.mdhv.formanalyser.app.ui.SettingsRigsScreen
import xyz.mdhv.formanalyser.app.ui.SettingsRootScreen
import xyz.mdhv.formanalyser.app.ui.RigEditScreen
import xyz.mdhv.formanalyser.app.ui.TrainSetupScreen
import xyz.mdhv.formanalyser.app.ui.theme.FormAnalyserTheme
import xyz.mdhv.formanalyser.app.ui.theme.Hyle

private object R {
    const val HOME = "home"; const val TRAIN = "train"; const val CAPTURE = "capture"; const val REVIEW = "review"
    const val PROGRESS = "progress"; const val BODY = "body"; const val CALENDAR = "calendar"
    const val SETTINGS = "settings"; const val S_PROFILE = "s_profile"; const val S_RIGS = "s_rigs"
    const val S_CAPTURE = "s_capture"; const val S_APPEARANCE = "s_appearance"; const val S_DATA = "s_data"; const val S_ABOUT = "s_about"
    const val RIG_EDIT = "rig_edit"
    val TABS = setOf(HOME, TRAIN, PROGRESS, BODY, CALENDAR)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val sessionVm: SessionViewModel = viewModel()
    val homeVm: HomeViewModel = viewModel()
    val rigsVm: RigsViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val onTab = route in R.TABS

    Scaffold(
        topBar = { if (onTab) TopRow(title = tabTitle(route), onSettings = { nav.navigate(R.SETTINGS) }) },
        bottomBar = { if (onTab) BottomBar(currentRoute = route, onSelect = { navigateTab(nav, it) }) },
    ) { padding ->
        NavHost(navController = nav, startDestination = R.HOME, modifier = Modifier.padding(padding)) {
            composable(R.HOME) {
                HomeScreen(
                    vm = homeVm,
                    onStartSession = { nav.navigate(R.TRAIN) },
                    onOpenReview = { id -> sessionVm.openSession(id); nav.navigate(R.REVIEW) },
                    onManageRigs = { nav.navigate(R.S_RIGS) },
                )
            }
            composable(R.TRAIN) {
                TrainSetupScreen(sessionVm, onStarted = { nav.navigate(R.CAPTURE) }, onManageRigs = { nav.navigate(R.S_RIGS) })
            }
            composable(R.CAPTURE) { CaptureScreen(sessionVm, onReview = { nav.navigate(R.REVIEW) }) }
            composable(R.REVIEW) { ReviewScreen(sessionVm) }
            composable(R.PROGRESS) { ComingSoonScreen("📈", listOf("Your baseline lives here soon —", "stability trends, projections, and what your form is telling you.")) }
            composable(R.BODY) { ComingSoonScreen("🫀", listOf("A map of how your body's doing —", "pain, injuries, physio — arrives in an upcoming build.")) }
            composable(R.CALENDAR) { ComingSoonScreen("🗓️", listOf("Sessions, rest days, and your streak", "will live here.")) }

            settingsGraph(nav, rigsVm, settingsVm)
        }
    }
}

private fun NavGraphBuilder.settingsGraph(
    nav: androidx.navigation.NavHostController,
    rigsVm: RigsViewModel,
    settingsVm: SettingsViewModel,
) {
    composable(R.SETTINGS) {
        SettingsRootScreen(
            onProfile = { nav.navigate(R.S_PROFILE) },
            onRigs = { nav.navigate(R.S_RIGS) },
            onCapture = { nav.navigate(R.S_CAPTURE) },
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
    composable(R.S_APPEARANCE) { SettingsAppearanceScreen(settingsVm) }
    composable(R.S_DATA) { SettingsDataScreen(settingsVm, onWiped = { /* onboarded flips → AppRoot recomposes */ }) }
    composable(R.S_ABOUT) { SettingsAboutScreen() }
}

@Composable
private fun TopRow(title: String, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Hyle.OnBackground, modifier = Modifier.weight(1f))
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Hyle.OnSurfaceDim) }
    }
}

@Composable
private fun BottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
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
                icon = { Icon(icon, contentDescription = label) },
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
