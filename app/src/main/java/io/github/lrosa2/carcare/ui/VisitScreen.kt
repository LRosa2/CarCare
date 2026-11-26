package io.github.lrosa2.carcare.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lrosa2.carcare.data.CarRepository
import io.github.lrosa2.carcare.data.HomeState
import io.github.lrosa2.carcare.domain.DueStatus
import io.github.lrosa2.carcare.domain.ItemStatus
import io.github.lrosa2.carcare.domain.ReadingCheck
import io.github.lrosa2.carcare.domain.StatusEngine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** "Coming up" margin for bundling jobs into one visit (spec D25). */
private const val COMING_UP_KM = 2_000
private const val COMING_UP_MONTHS = 2L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitScreen(repo: CarRepository, state: HomeState, visitId: Long?, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(repo.today()) }
    // Empty on purpose: an accepted estimate would be stored as a real reading and skew the pace (D18).
    var kmText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val ticks = remember { mutableStateMapOf<String, Boolean>() } // itemId -> partial
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(visitId == null) }

    LaunchedEffect(visitId) {
        if (visitId != null) {
            repo.visit(visitId)?.let { v ->
                date = v.visit.date; kmText = v.visit.km.toString(); note = v.visit.note
                ticks.clear(); ticks.putAll(v.items)
            }
            loaded = true
        }
    }

    fun save(km: Int) = scope.launch {
        repo.saveVisit(visitId, date, km, note, ticks.toMap())
        onDone()
    }

    fun check() {
        val km = Fmt.parseKm(kmText) ?: run { error = "Enter the km as a whole number."; return }
        if (ticks.isEmpty()) { error = "Tick at least one item."; return }
        if (date.isAfter(repo.today())) { error = "The date can't be in the future."; return }
        scope.launch {
            when (val c = repo.checkReading(date, km, visitId = visitId)) {
                ReadingCheck.Ok -> save(km)
                is ReadingCheck.TooLow -> error = "Km lower than ${Fmt.km(c.previous.km)} on ${Fmt.date(c.previous.date)}."
                is ReadingCheck.TooHigh -> error = "Km higher than ${Fmt.km(c.next.km)} on the later date ${Fmt.date(c.next.date)}."
                is ReadingCheck.Suspicious -> confirm = "That is ${Fmt.km(c.kmPerDay.roundToInt())} per day since " +
                    "${Fmt.km(c.previous.km)} on ${Fmt.date(c.previous.date)}. Typo?"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (visitId == null) "Log visit" else "Edit visit") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (visitId != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete visit") }
                    IconButton(onClick = ::check) { Icon(Icons.Default.Check, "Save") }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            DateField("Date", date, { d -> if (d != null) date = d })
            NumberField(
                "Odometer", kmText, { kmText = it; error = null }, suffix = "km",
                supporting = state.overview.estimatedKm?.takeIf { visitId == null }?.let { "Expected about ${Fmt.km(it)}" },
            )
            OutlinedTextField(note, { note = it }, label = { Text("Note (garage, oil used, …)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            SectionTitle("Done in this visit")
            Text("Mark 'partial' for a top-up or partial change: it stays in history but does not reset the due date.", style = MaterialTheme.typography.bodySmall)
            state.overview.items.forEach { s ->
                VisitItemRow(
                    s = s,
                    comingUp = comingUp(s, repo),
                    ticked = s.spec.id in ticks,
                    partial = ticks[s.spec.id] == true,
                    onTick = { on -> if (on) ticks[s.spec.id] = false else ticks.remove(s.spec.id); error = null },
                    onPartial = { p -> ticks[s.spec.id] = p },
                )
                HorizontalDivider()
            }
        }
    }

    confirm?.let { msg ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Check the number") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { confirm = null; Fmt.parseKm(kmText)?.let { save(it) } }) { Text("Save anyway") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Fix it") } },
        )
    }
    if (confirmDelete && visitId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this visit?") },
            text = { Text("Due dates are recalculated from the remaining history.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { repo.deleteVisit(visitId); onDone() } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Due soon/overdue, or within 2,000 km / 2 months: worth doing in the same visit. */
private fun comingUp(s: ItemStatus, repo: CarRepository): Boolean {
    if (s.result.status in setOf(DueStatus.OVERDUE, DueStatus.NEVER_DONE, DueStatus.DUE_SOON)) return true
    if (s.result.status != DueStatus.OK) return false
    val kmClose = s.result.kmLeft?.let { it <= COMING_UP_KM } ?: false
    val dateClose = StatusEngine.urgencyDate(s.result)?.let { !it.isAfter(repo.today().plusMonths(COMING_UP_MONTHS)) } ?: false
    return kmClose || dateClose
}

@Composable
private fun VisitItemRow(
    s: ItemStatus,
    comingUp: Boolean,
    ticked: Boolean,
    partial: Boolean,
    onTick: (Boolean) -> Unit,
    onPartial: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable { onTick(!ticked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = ticked, onCheckedChange = onTick)
        Column(Modifier.weight(1f)) {
            Text(s.spec.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                // Due items already say so; "Coming up" flags OK items close enough to bundle.
                (if (comingUp && s.result.status == DueStatus.OK) "Coming up · " else "") + Fmt.dueLine(s.result),
                style = MaterialTheme.typography.bodySmall,
                color = if (comingUp) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ticked) FilterChip(selected = partial, onClick = { onPartial(!partial) }, label = { Text("partial") })
    }
}
