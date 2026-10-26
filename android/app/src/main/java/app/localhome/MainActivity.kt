package app.localhome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.localhome.ui.add.AddDeviceScreen
import app.localhome.ui.home.HomeScreen
import app.localhome.ui.purifier.PurifierScreen
import app.localhome.ui.theme.LocalHomeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val home = (application as LocalHomeApp).home
        setContent {
            LocalHomeTheme {
                val nav = rememberNavController()
                NavHost(
                    nav,
                    startDestination = "home",
                    enterTransition = { slideInHorizontally { it / 5 } + fadeIn() },
                    exitTransition = { scaleOut(targetScale = 0.96f) + fadeOut() },
                    popEnterTransition = { scaleIn(initialScale = 0.96f) + fadeIn() },
                    popExitTransition = { slideOutHorizontally { it / 5 } + fadeOut() },
                ) {
                    composable("home") {
                        HomeScreen(
                            home,
                            onOpen = { nav.navigate("device/${it.id}") },
                            onAdd = { nav.navigate("add") },
                        )
                    }
                    composable("device/{id}") { entry ->
                        PurifierScreen(home, entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                    }
                    composable("add") {
                        AddDeviceScreen(home, onDone = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
