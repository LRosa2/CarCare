package io.github.lrosa2.carcare.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lrosa2.carcare.data.CarRepository
import io.github.lrosa2.carcare.data.HomeState
import io.github.lrosa2.carcare.data.db.ItemEntity
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.MonthDay

/** Editable copy of an item; text fields keep raw text until Save. */
private data class ItemForm(
    val name: String,
    val seasonal: Boolean,
    val intervalKm: String,
    val intervalMonths: String,
    val seasonalDay: String,
    val seasonalMonth: String,
    val pinKm: String,
    val pinDate: LocalDate?,
    val toyotaRef: String,
    val myNote: String,
) {
    companion object {
        fun of(e: ItemEntity) = ItemForm(
            name = e.name,
            seasonal = e.seasonalMonth != null,
            intervalKm = e.intervalKm?.toString() ?: "",
            intervalMonths = e.intervalMonths?.toString() ?: "",
            seasonalDay = e.seasonalDay?.toString() ?: "",
            seasonalMonth = e.seasonalMonth?.toString() ?: "",
            pinKm = e.pinnedKm?.toString() ?: "",
            pinDate = e.pinnedDate,
            toyotaRef = e.toyotaRef,
            myNote = e.myNote,
        )
    }

    /** The entity to save, or an error message. */
    fun toEntity(base: ItemEntity): Pair<ItemEntity?, String?> {
        if (name.isBlank()) return null to "Name is empty."
        val km = intervalKm.takeIf { it.isNotBlank() }?.let { Fmt.parseKm(it) ?: return null to "Interval km is not a number." }
        val months = intervalMonths.takeIf { it.isNotBlank() }?.let { it.trim().toIntOrNull() ?: return null to "Interval months is not a number." }
        val pinK = pinKm.takeIf { it.isNotBlank() }?.let { Fmt.parseKm(it) ?: return null to "Pinned km is not a number." }
        var sMonth: Int? = null
        var sDay: Int? = null
        if (seasonal) {
            sMonth = seasonalMonth.trim().toIntOrNull()
            sDay = seasonalDay.trim().toIntOrNull()
            if (sMonth == null || sDay == null || runCatching { MonthDay.of(sMonth, sDay) }.isFailure) {
                return null to "Yearly date needs a valid day and month."
            }
        }
        if (listOfNotNull(km, months, pinK).any { it <= 0 }) return null to "Numbers must be above zero."
        return base.copy(
            name = name.trim(),
            intervalKm = if (seasonal) null else km,
            intervalMonths = if (seasonal) null else months,
            seasonalMonth = sMonth,
            seasonalDay = sDay,
            pinnedKm = pinK,
            pinnedDate = pinDate,
            toyotaRef = toyotaRef.trim(),
            myNote = myNote.trim(),
        ) to null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemScreen(repo: CarRepository, state: HomeState, id: String, onDone: () -> Unit, onVisit: (Long) -> Unit) {
    val entity = state.items[id]
    val status = state.overview.items.firstOrNull { it.spec.id == id }
    val scope = rememberCoroutineScope()
    val visits by repo.visitsWithItems.collectAsStateWithLifecycle(emptyList())
    var form by remember(entity != null) { mutableStateOf(entity?.let(ItemForm::of)) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entity?.name ?: "Item") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete item") }
                    IconButton(onClick = {
                        val f = form ?: return@IconButton
                        val (e, err) = f.toEntity(entity ?: return@IconButton)
                        if (e == null) error = err else scope.launch { repo.saveItem(e); onDone() }
                    }) { Icon(Icons.Default.Check, "Save") }
                },
            )
        },
    ) { padding ->
        val f = form ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            status?.let { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusChip(s.result.status)
                        Text(Fmt.dueLine(s.result), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            s.lastFull?.let { "Last full service: ${Fmt.km(it.km)} on ${Fmt.date(it.date)}" } ?: "Never logged",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (entity?.pinnedKm != null || entity?.pinnedDate != null) {
                            Text(
                                if (s.pinActive) "Pinned target is active." else "Pinned target was cleared by a later full service.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            OutlinedTextField(f.name, { form = f.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            SectionTitle("When is it due?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !f.seasonal, onClick = { form = f.copy(seasonal = false) }, label = { Text("Interval") })
                FilterChip(selected = f.seasonal, onClick = { form = f.copy(seasonal = true) }, label = { Text("Every year on a date") })
            }
            if (f.seasonal) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Day", f.seasonalDay, { form = f.copy(seasonalDay = it) }, Modifier.weight(1f))
                    NumberField("Month", f.seasonalMonth, { form = f.copy(seasonalMonth = it) }, Modifier.weight(1f))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Every", f.intervalKm, { form = f.copy(intervalKm = it) }, Modifier.weight(1f), suffix = "km")
                    NumberField("or every", f.intervalMonths, { form = f.copy(intervalMonths = it) }, Modifier.weight(1f), suffix = "months")
                }
                Text("Counted from the last full service. Leave one empty for km-only or time-only.", style = MaterialTheme.typography.bodySmall)
            }

            SectionTitle("Pinned next target (optional)")
            Text(
                "Replaces the interval of the same kind until the item is done again, e.g. to do two jobs together.",
                style = MaterialTheme.typography.bodySmall,
            )
            NumberField("At", f.pinKm, { form = f.copy(pinKm = it) }, suffix = "km")
            DateField("Or by", f.pinDate, { form = f.copy(pinDate = it) }, clearable = true)

            SectionTitle("Toyota reference")
            OutlinedTextField(f.toyotaRef, { form = f.copy(toyotaRef = it) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            SectionTitle("My note")
            OutlinedTextField(f.myNote, { form = f.copy(myNote = it) }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            SectionTitle("History")
            val mine = visits.filter { id in it.items }
            if (mine.isEmpty()) Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium)
            mine.forEach { v ->
                Row(
                    Modifier.fillMaxWidth().clickable { onVisit(v.visit.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Fmt.date(v.visit.date), Modifier.width(96.dp))
                    Text(Fmt.km(v.visit.km), Modifier.weight(1f))
                    if (v.items[id] == true) Text("partial", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${entity?.name}?") },
            text = { Text("The item disappears from the list and from every logged visit. This can't be undone (except by importing a backup).") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { repo.deleteItem(id); onDone() } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
