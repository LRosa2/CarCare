package io.github.lrosa2.carcare.ui

import io.github.lrosa2.carcare.domain.DueResult
import io.github.lrosa2.carcare.domain.DueStatus
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Swiss number and date format with an English UI (spec D17). */
object Fmt {
    private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val monthFmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

    private fun number(n: Int): String {
        val symbols = DecimalFormatSymbols(Locale.ROOT).apply { groupingSeparator = '\'' }
        return DecimalFormat("#,##0", symbols).format(n)
    }

    fun km(km: Int): String = "${number(km)} km"
    fun date(d: LocalDate): String = d.format(dateFmt)
    fun month(d: LocalDate): String = d.format(monthFmt)

    /** One line under an item name on the home list. */
    fun dueLine(r: DueResult): String = when (r.status) {
        DueStatus.NEVER_DONE -> "Never logged — due now"
        DueStatus.NO_SCHEDULE -> "No schedule"
        DueStatus.OVERDUE -> "Overdue: was due " + listOfNotNull(
            r.dueKm?.let { "at ${km(it)}" },
            r.dueDate?.let { date(it) },
        ).joinToString(" or ")
        DueStatus.DUE_SOON, DueStatus.OK -> {
            val kmPart = when {
                r.kmLeft != null -> "≈ ${km(r.kmLeft)} left" + (r.projectedKmDate?.let { " (≈ ${month(it)})" } ?: "")
                r.dueKm != null -> "at ${km(r.dueKm)}"
                else -> null
            }
            val datePart = r.dueDate?.let { date(it) }
            when {
                kmPart != null && datePart != null -> "$kmPart · or by $datePart"
                kmPart != null -> kmPart
                datePart != null -> "Due $datePart"
                else -> ""
            }
        }
    }

    /** Accepts "123456", "123'456", "123 456", "123.456"; null when not a whole number. */
    fun parseKm(text: String): Int? {
        val digits = text.trim().filterNot { it == '\'' || it == '’' || it == ' ' || it == '.' || it == ',' || it == ' ' }
        if (digits.isEmpty() || !digits.all(Char::isDigit) || digits.length > 7) return null
        return digits.toIntOrNull()
    }
}
