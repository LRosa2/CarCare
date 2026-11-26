package io.github.lrosa2.carcare.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.lrosa2.carcare.backup.BackupException
import io.github.lrosa2.carcare.backup.BackupIo
import io.github.lrosa2.carcare.data.CarRepository
import io.github.lrosa2.carcare.data.DefaultCatalogue
import kotlinx.coroutines.launch

/** First launch: import the personal file, or start from the bundled item list (spec D20). */
@Composable
fun OnboardingScreen(repo: CarRepository, onDone: (askKm: Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone(true) }
    fun finish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else onDone(true)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                repo.import(BackupIo.read(context, uri))
                finish()
            } catch (e: BackupException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.systemBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Welcome to CarCare", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Import your data file (carcare-import.json or a CarCare backup) to bring in your service history, " +
                    "intervals and car notes. Or start with the default Yaris Hybrid items and log your history yourself.",
                style = MaterialTheme.typography.bodyLarge,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !busy,
                onClick = { error = null; picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import data file") }
            OutlinedButton(
                enabled = !busy,
                onClick = { busy = true; scope.launch { repo.import(DefaultCatalogue.file()); busy = false; finish() } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start with default items") }
        }
    }
}
