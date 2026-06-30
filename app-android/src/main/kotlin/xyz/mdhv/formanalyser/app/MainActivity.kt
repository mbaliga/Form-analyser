package xyz.mdhv.formanalyser.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import xyz.mdhv.formanalyser.app.domain.SessionViewModel
import xyz.mdhv.formanalyser.app.ui.CaptureScreen
import xyz.mdhv.formanalyser.app.ui.HomeScreen
import xyz.mdhv.formanalyser.app.ui.ReviewScreen
import xyz.mdhv.formanalyser.app.ui.theme.FormAnalyserTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FormAnalyserTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Activity-scoped VM so all screens share one session.
                    val vm: SessionViewModel = viewModel()
                    AppNav(vm)
                }
            }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val CAPTURE = "capture"
    const val REVIEW = "review"
}

@Composable
private fun AppNav(vm: SessionViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                vm = vm,
                onSessionStarted = { nav.navigate(Routes.CAPTURE) },
            )
        }
        composable(Routes.CAPTURE) {
            CaptureScreen(
                vm = vm,
                onReview = { nav.navigate(Routes.REVIEW) },
            )
        }
        composable(Routes.REVIEW) {
            ReviewScreen(vm = vm)
        }
    }
}
