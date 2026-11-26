package io.github.lrosa2.carcare.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.lrosa2.carcare.data.CarRepository
import io.github.lrosa2.carcare.data.HomeState
import io.github.lrosa2.carcare.domain.ItemStatus
import io.github.lrosa2.carcare.reminders.Notifications
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    bottomBar: @Composable () -> Unit,
    onEnterKm: () -> Unit,
    onItem: (String) -> Unit,
    onLogVisit: () -> Unit,
    onAddItem: (String) -> Unit,
    repo: CarRepository,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var canNotify by remember { mutableStateOf(Notifications.canPost(context)) }
    LifecycleResumeEffect(Unit) {
        canNotify = Notifications.canPost(context)
        onPauseOrDispose { }
    }
    var addDialog by remember { mutableStateOf(false) }
    val today = repo.today()
    val backupDays = state.settings.lastExport?.let { ChronoUnit.DAYS.between(it, today) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.vehicleName) },
                actions = { IconButton(onClick = { addDialog = true }) { Icon(Icons.Default.Add, "Add item") } },
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onLogVisit, icon = { Icon(Icons.Default.Edit, null) }, text = { Text("Log visit") })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            item { KmCard(state, onEnterKm) }
            if (!canNotify) item {
                WarningBanner("Notifications are blocked, so the monthly km reminder can't reach you.", "Fix", onSettings)
            }
            if (backupDays == null || backupDays > 90) item {
                WarningBanner(
                    if (backupDays == null) "No backup exported yet." else "Last backup was $backupDays days ago.",
                    "Export", onSettings,
                )
            }
            items(state.overview.items, key = { it.spec.id }) { s ->
                ItemRow(s, onClick = { onItem(s.spec.id) })
                HorizontalDivider()
            }
        }
    }

    if (addDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("New item") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    addDialog = false
                    scope.launch { onAddItem(repo.newItem(name.trim())) }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun KmCard(state: HomeState, onEnterKm: () -> Unit) {
    val o = state.overview
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    o.estimatedKm?.let { "≈ ${Fmt.km(it)}" } ?: "No km yet",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                )
                o.latestReading?.let {
                    Text("Last entered ${Fmt.km(it.km)} on ${Fmt.date(it.date)}", style = MaterialTheme.typography.bodySmall)
                }
                o.paceKmPerDay?.let {
                    Text("Pace ≈ ${Fmt.km((it * 30.4).toInt())} / month", style = MaterialTheme.typography.bodySmall)
                }
                Text(mountedTyres(o.items), style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onEnterKm) { Text("Enter km") }
        }
    }
}

/**
 * Which tyre set is on (spec D7): the yearly-date item swapped most recently, e.g. "Winter tyres on".
 * The TPMS IDs of each set live in the "Tyres & TPMS" card.
 */
private fun mountedTyres(items: List<ItemStatus>): String {
    val latest = items.filter { it.spec.rule.seasonal != null && it.lastFull != null }.maxByOrNull { it.lastFull!!.date }
        ?: return "Tyres: no swap logged yet"
    return "Tyres: ${latest.spec.name} since ${Fmt.date(latest.lastFull!!.date)}"
}

@Composable
private fun ItemRow(s: ItemStatus, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(s.spec.name, style = MaterialTheme.typography.titleMedium)
            Text(Fmt.dueLine(s.result), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val last = s.lastFull?.let { "Last: ${Fmt.km(it.km)} · ${Fmt.date(it.date)}" } ?: "Last: never logged"
            Text(
                if (s.pinActive) "$last · pinned target" else last,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        StatusChip(s.result.status)
    }
}
