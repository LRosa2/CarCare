package io.github.lrosa2.carcare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import io.github.lrosa2.carcare.domain.DueStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun StatusChip(status: DueStatus, modifier: Modifier = Modifier) {
    val c = statusColors(status)
    Box(
        modifier
            .background(c.container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(c.label, color = c.content, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun WarningBanner(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/** Date field with a calendar picker. The picker works in UTC millis; LocalDate has no zone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate?, onChange: (LocalDate?) -> Unit, modifier: Modifier = Modifier, clearable: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = date?.let(Fmt::date) ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { open = true }) { Icon(Icons.Default.DateRange, "Pick date") }
        },
        modifier = modifier.fillMaxWidth().clickable { open = true },
    )
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (clearable) TextButton(onClick = { onChange(null); open = false }) { Text("Clear") }
                    TextButton(onClick = { open = false }) { Text("Cancel") }
                }
            },
        ) { DatePicker(state) }
    }
}

/** Digits-only number field; [value] is the raw text so partial input survives recomposition. */
@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, suffix: String? = null, isError: Boolean = false, supporting: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onChange(new.filter { it.isDigit() || it == '\'' || it == ' ' }) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(top = 16.dp, bottom = 4.dp))
}

private val urlRegex = Regex("""(https?://[^\s]+|www\.[^\s]+)""")
// Swiss numbers as written in the notes (0xx xxx xx xx or 10 digits starting with 0) and +41 forms.
// Part numbers like 9098113044 do not start with 0, so they stay plain text.
private val phoneRegex = Regex("""(?<![\w.])(\+\d{2} ?\d{2} ?\d{3} ?\d{2} ?\d{2}|0\d{2} \d{3} \d{2} \d{2}|0\d{9})(?!\w)""")

/** Text with tappable web links and phone numbers (spec D9). */
@Composable
fun LinkifiedText(text: String, modifier: Modifier = Modifier) {
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    Text(linkify(text, linkStyle), modifier = modifier, style = MaterialTheme.typography.bodyMedium)
}

private fun linkify(text: String, style: TextLinkStyles): AnnotatedString {
    val matches = (urlRegex.findAll(text).map { it to "url" } + phoneRegex.findAll(text).map { it to "tel" })
        .sortedBy { it.first.range.first }
        .toList()
    return buildAnnotatedString {
        var pos = 0
        for ((m, kind) in matches) {
            if (m.range.first < pos) continue // phone number inside a URL
            append(text.substring(pos, m.range.first))
            val target = when (kind) {
                "tel" -> "tel:" + m.value.replace(" ", "")
                else -> if (m.value.startsWith("www.")) "https://${m.value}" else m.value
            }
            withLink(LinkAnnotation.Url(target, style)) { append(m.value) }
            pos = m.range.last + 1
        }
        append(text.substring(pos))
    }
}

@Composable
fun LabeledValue(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
