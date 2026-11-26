package io.github.lrosa2.carcare.ui

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lrosa2.carcare.data.CarRepository
import io.github.lrosa2.carcare.data.HomeState
import io.github.lrosa2.carcare.domain.ReadingCheck
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterKmScreen(repo: CarRepository, state: HomeState, readingId: Long?, afterImport: Boolean, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var kmText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(repo.today()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(readingId) {
        if (readingId != null) {
            repo.readings.first().firstOrNull { it.id == readingId }?.let { kmText = it.km.toString(); date = it.date }
        }
    }

    fun save(km: Int, d: LocalDate) = scope.launch {
        repo.saveReading(readingId, d, km)
        onDone()
    }

    fun check() {
        val km = Fmt.parseKm(kmText) ?: run { error = "Enter the km as a whole number."; return }
        if (date.isAfter(repo.today())) { error = "The date can't be in the future."; return }
        scope.launch {
            when (val c = repo.checkReading(date, km, readingId = readingId)) {
                ReadingCheck.Ok -> save(km, date)
                is ReadingCheck.TooLow -> error = "Lower than ${Fmt.km(c.previous.km)} on ${Fmt.date(c.previous.date)}. " +
                    "If that older entry is wrong, fix it in History."
                is ReadingCheck.TooHigh -> error = "Higher than ${Fmt.km(c.next.km)} on the later date ${Fmt.date(c.next.date)}."
                is ReadingCheck.Suspicious -> confirm = "That is ${Fmt.km(c.kmPerDay.roundToInt())} per day since " +
                    "${Fmt.km(c.previous.km)} on ${Fmt.date(c.previous.date)}. Typo?"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (readingId == null) "Enter km" else "Edit reading") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (readingId != null) IconButton(onClick = { scope.launch { repo.deleteReading(readingId); onDone() } }) {
                        Icon(Icons.Default.Delete, "Delete reading")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (afterImport) {
                Text(
                    "Import done. Enter today's odometer so the estimates start from a real reading.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            state.overview.estimatedKm?.let {
                Text("Expected about ${Fmt.km(it)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            NumberField("Odometer", kmText, { kmText = it; error = null }, suffix = "km", isError = error != null, supporting = error)
            DateField("Date", date, { d -> if (d != null) { date = d; error = null } })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = ::check, enabled = kmText.isNotBlank()) { Text("Save") }
            }
        }
    }

    confirm?.let { msg ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Check the number") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { confirm = null; Fmt.parseKm(kmText)?.let { save(it, date) } }) { Text("Save anyway") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Fix it") } },
        )
    }
}
