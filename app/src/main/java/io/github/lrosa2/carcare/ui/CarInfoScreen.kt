package io.github.lrosa2.carcare.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lrosa2.carcare.data.CarRepository
import io.github.lrosa2.carcare.data.db.NoteCardEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Editable reference cards from the Keep note (spec D9). Links and phone numbers are tappable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarInfoScreen(repo: CarRepository, bottomBar: @Composable () -> Unit, onCard: (Long?) -> Unit) {
    val cards by repo.cards.collectAsStateWithLifecycle(emptyList())
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Car info") },
                actions = { IconButton(onClick = { onCard(null) }) { Icon(Icons.Default.Add, "Add card") } },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cards, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onCard(c.id) }) { Icon(Icons.Default.Edit, "Edit ${c.title}") }
                        }
                        if (c.body.isBlank()) {
                            Text("Empty — tap the pencil to add notes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LinkifiedText(c.body)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(repo: CarRepository, id: Long?, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var base by remember { mutableStateOf<NoteCardEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != null) repo.cards.first().firstOrNull { it.id == id }?.let { base = it; title = it.title; body = it.body }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == null) "New card" else "Edit card") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (id != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete card") }
                    IconButton(enabled = title.isNotBlank(), onClick = {
                        scope.launch {
                            val card = (base ?: NoteCardEntity(title = "", body = "", sortOrder = 0)).copy(title = title.trim(), body = body.trimEnd())
                            repo.saveCard(card)
                            onDone()
                        }
                    }) { Icon(Icons.Default.Check, "Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(body, { body = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 10)
        }
    }

    if (confirmDelete && id != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"$title\"?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { repo.deleteCard(id); onDone() } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
