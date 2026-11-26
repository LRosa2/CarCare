package io.github.lrosa2.carcare.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lrosa2.carcare.data.CarRepository
import io.github.lrosa2.carcare.data.HomeState
import java.time.LocalDate

private sealed interface HistoryRow {
    val date: LocalDate
    val km: Int
    data class Visit(val id: Long, override val date: LocalDate, override val km: Int, val text: String) : HistoryRow
    data class Reading(val id: Long, override val date: LocalDate, override val km: Int) : HistoryRow
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(repo: CarRepository, state: HomeState, bottomBar: @Composable () -> Unit, onVisit: (Long) -> Unit, onReading: (Long) -> Unit) {
    val visits by repo.visitsWithItems.collectAsStateWithLifecycle(emptyList())
    val readings by repo.readings.collectAsStateWithLifecycle(emptyList())
    val names = state.items.mapValues { it.value.name }

    val rows: List<HistoryRow> = (
        visits.map { v ->
            val items = v.items.entries.joinToString(", ") { (id, partial) -> (names[id] ?: id) + if (partial) " (partial)" else "" }
            HistoryRow.Visit(v.visit.id, v.visit.date, v.visit.km, listOf(items, v.visit.note).filter { it.isNotBlank() }.joinToString("\n"))
        } + readings.map { HistoryRow.Reading(it.id, it.date, it.km) }
        ).sortedWith(compareByDescending<HistoryRow> { it.date }.thenByDescending { it.km })

    Scaffold(topBar = { TopAppBar(title = { Text("History") }) }, bottomBar = bottomBar) { padding ->
        if (rows.isEmpty()) {
            Text("Nothing logged yet.", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(rows, key = { r -> if (r is HistoryRow.Visit) "v${r.id}" else "r${(r as HistoryRow.Reading).id}" }) { r ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { if (r is HistoryRow.Visit) onVisit(r.id) else onReading((r as HistoryRow.Reading).id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(Modifier.width(104.dp)) {
                        Text(Fmt.date(r.date), style = MaterialTheme.typography.bodyMedium)
                        Text(Fmt.km(r.km), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when (r) {
                        is HistoryRow.Visit -> Text(r.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        is HistoryRow.Reading -> Text("Km reading", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
