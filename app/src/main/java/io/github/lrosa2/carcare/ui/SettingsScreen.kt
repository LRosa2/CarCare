package io.github.lrosa2.carcare.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.lrosa2.carcare.BuildConfig
import io.github.lrosa2.carcare.backup.BackupException
import io.github.lrosa2.carcare.backup.BackupFile
import io.github.lrosa2.carcare.backup.BackupIo
import io.github.lrosa2.carcare.data.AppSettings
import io.github.lrosa2.carcare.data.CarRepository
import io.github.lrosa2.carcare.data.HomeState
import io.github.lrosa2.carcare.domain.ReminderPolicy
import io.github.lrosa2.carcare.reminders.DailyCheck
import io.github.lrosa2.carcare.reminders.Notifications
import io.github.lrosa2.carcare.reminders.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repo: CarRepository, state: HomeState, bottomBar: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = state.settings
    var message by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<BackupFile?>(null) }

    // Reminder fields keep raw text until "Save".
    var day by remember(s.reminderDay) { mutableStateOf(s.reminderDay.toString()) }
    var interval by remember(s.reminderIntervalMonths) { mutableStateOf(s.reminderIntervalMonths.toString()) }
    var time by remember(s.reminderTime) { mutableStateOf(s.reminderTime) }
    var soonKm by remember(s.soonKm) { mutableStateOf(s.soonKm.toString()) }
    var soonMonths by remember(s.soonMonths) { mutableStateOf(s.soonMonths.toString()) }
    var pace by remember(s.paceWindowMonths) { mutableStateOf(s.paceWindowMonths.toString()) }
    var timeDialog by remember { mutableStateOf(false) }

    var health by remember { mutableStateOf(ReminderHealth.read(context)) }
    LifecycleResumeEffect(Unit) {
        health = ReminderHealth.read(context)
        onPauseOrDispose { }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        health = ReminderHealth.read(context)
        if (!it) ReminderHealth.openNotificationSettings(context)
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = try {
                BackupIo.write(context, uri, repo.export())
                repo.markExported()
                "Backup saved."
            } catch (e: BackupException) {
                e.message
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                pendingImport = BackupIo.read(context, uri)
            } catch (e: BackupException) {
                message = e.message
            }
        }
    }

    fun saveSettings() {
        val d = day.trim().toIntOrNull()
        val i = interval.trim().toIntOrNull()
        val sk = Fmt.parseKm(soonKm)
        val sm = soonMonths.trim().toIntOrNull()
        val p = pace.trim().toIntOrNull()
        if (d == null || d !in 1..28) { message = "Reminder day must be 1-28."; return }
        if (i == null || i !in 1..12) { message = "Reminder interval must be 1-12 months."; return }
        if (sk == null || sm == null || sm !in 0..12) { message = "Check the 'due soon' values."; return }
        if (p == null || p !in 1..24) { message = "Pace window must be 1-24 months."; return }
        scope.launch {
            repo.settings.update {
                it.copy(
                    reminderDay = d, reminderTime = time,
                    reminderIntervalMonths = i,
                    reminderAnchor = if (i != it.reminderIntervalMonths || it.reminderAnchor == null) YearMonth.from(repo.today()) else it.reminderAnchor,
                    soonKm = sk, soonMonths = sm, paceWindowMonths = p,
                )
            }
            ReminderScheduler.reschedule(context)
            message = "Saved."
        }
    }

    // Results show as a snackbar: the buttons sit far down a scrolled page.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); message = null }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            SectionTitle("Monthly km reminder")
            Text("Next: ${nextPromptText(s, repo.today())}", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField("Day of month", day, { day = it }, Modifier.weight(1f))
                NumberField("Every", interval, { interval = it }, Modifier.weight(1f), suffix = "months")
            }
            OutlinedButton(onClick = { timeDialog = true }) { Text("Time: $time") }

            SectionTitle("Due soon")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Within", soonMonths, { soonMonths = it }, Modifier.weight(1f), suffix = "months")
                NumberField("or", soonKm, { soonKm = it }, Modifier.weight(1f), suffix = "km")
            }
            NumberField("Pace from the last", pace, { pace = it }, suffix = "months")
            Button(onClick = ::saveSettings) { Text("Save settings") }

            SectionTitle("Backup")
            Text(
                s.lastExport?.let { "Last export: ${Fmt.date(it)}" } ?: "No backup exported yet.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Exports are JSON files with your full history and notes. Keep one somewhere off the phone (e.g. cloud drive).",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exporter.launch("carcare-backup-${repo.today()}.json") }) { Text("Export") }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Import") }
            }

            SectionTitle("Reminder health")
            HealthRow(
                ok = health.notifications,
                okText = "Notifications allowed",
                badText = "Notifications are blocked",
                action = "Allow",
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else ReminderHealth.openNotificationSettings(context)
            }
            HealthRow(
                ok = health.batteryUnrestricted,
                okText = "Battery: unrestricted",
                badText = "Battery optimised (the daily check may run late)",
                action = "Open",
            ) { ReminderHealth.openBatterySettings(context) }
            Text("Also turn off \"Pause app activity if unused\" so Android keeps the reminder.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { ReminderHealth.openUnusedAppSettings(context) }) { Text("Open app settings") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val (overview, _) = repo.overviewOnce()
                        Notifications.postKmPrompt(context, overview, s.lastExport, repo.today(), renag = false)
                    }
                }) { Text("Test reminder") }
                if (BuildConfig.DEBUG) OutlinedButton(onClick = { scope.launch { DailyCheck.run(context); message = "Daily check ran." } }) { Text("Run daily check") }
            }

            SectionTitle("About")
            Text("CarCare ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (timeDialog) {
        val ts = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timeDialog = false },
            title = { Text("Reminder time") },
            text = { TimePicker(ts) },
            confirmButton = { TextButton(onClick = { time = LocalTime.of(ts.hour, ts.minute); timeDialog = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { timeDialog = false }) { Text("Cancel") } },
        )
    }

    pendingImport?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace all data?") },
            text = {
                Text(
                    "The file has ${file.items.size} items, ${file.visits.size} visits, ${file.readings.size} readings and " +
                        "${file.cards.size} cards${file.exportedAt?.let { " (from $it)" } ?: ""}. " +
                        "Everything in the app is replaced. Export first if you want to keep the current data.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch { repo.import(file); ReminderScheduler.reschedule(context); message = "Imported." }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HealthRow(ok: Boolean, okText: String, badText: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            (if (ok) "✓ " else "⚠ ") + if (ok) okText else badText,
            Modifier.weight(1f),
            color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
        if (!ok) TextButton(onClick = onAction) { Text(action) }
    }
}

/** The next reminder day not yet handled, for display. */
private fun nextPromptText(s: AppSettings, today: LocalDate): String {
    val cfg = s.reminderConfig
    for (k in 0..24L) {
        val date = YearMonth.from(today).plusMonths(k).atDay(s.reminderDay.coerceIn(1, 28))
        if (date.isBefore(today)) continue
        if (ReminderPolicy.latestPromptDate(date, cfg) != date) continue
        if (s.lastPrompt != null && !date.isAfter(s.lastPrompt)) continue
        return "${Fmt.date(date)} at ${s.reminderTime}"
    }
    return "—"
}

/** What can silently break the reminder (spec D21). */
data class ReminderHealth(val notifications: Boolean, val batteryUnrestricted: Boolean) {
    companion object {
        fun read(context: Context) = ReminderHealth(
            notifications = Notifications.canPost(context),
            batteryUnrestricted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        )

        fun openNotificationSettings(context: Context) = start(
            context,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )

        fun openBatterySettings(context: Context) = start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

        fun openUnusedAppSettings(context: Context) {
            val intent = try {
                IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.packageName)
            } catch (_: UnsupportedOperationException) {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            }
            start(context, intent)
        }

        private fun start(context: Context, intent: Intent) {
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }
        }
    }
}
