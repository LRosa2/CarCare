package io.github.lrosa2.carcare.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import io.github.lrosa2.carcare.CarCareApp
import io.github.lrosa2.carcare.reminders.Notifications
import io.github.lrosa2.carcare.reminders.ReminderScheduler
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** What a notification asked to open; consumed by the UI. */
    private val openRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openRequest.value = intent.getStringExtra(Notifications.EXTRA_OPEN)
        // Safe to repeat; keeps the daily check alive even if an alarm was lost (spec D26).
        lifecycleScope.launch { ReminderScheduler.reschedule(this@MainActivity) }
        val repo = (application as CarCareApp).repository
        setContent {
            CarCareTheme {
                CarCareRoot(repo, openRequest.value, onOpenHandled = { openRequest.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequest.value = intent.getStringExtra(Notifications.EXTRA_OPEN)
    }
}
