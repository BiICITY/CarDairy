package app.woorichadiary.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import app.woorichadiary.DiaryApp
import app.woorichadiary.service.DiaryService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, DiaryService::class.java))
        val controller = (application as DiaryApp).controller

        setContent {
            MaterialTheme {
                val state by controller.state.collectAsStateWithLifecycle()
                when {
                    state.driving -> DrivingScreen()
                    state.family == null -> SetupScreen(controller::completeSetup)
                    else -> HomeScreen(state, controller)
                }
            }
        }
    }
}
