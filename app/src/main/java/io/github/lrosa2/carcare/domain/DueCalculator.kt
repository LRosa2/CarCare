package io.github.lrosa2.carcare.domain

import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max

/** Pure due-date logic (spec D3, D5, D16). No Android types, so it runs in plain JVM tests. */
object DueCalculator {

    fun evaluate(
        rule: Rule,
        lastDone: ServiceRecord?,
        currentKm: Int?,
        paceKmPerDay: Double?,
        today: LocalDate,
        thresholds: Thresholds = Thresholds(),
    ): DueResult {
        val dueKm = rule.pinnedKm
            ?: if (rule.seasonal == null && rule.intervalKm != null && lastDone != null) lastDone.km + rule.intervalKm else null

        val dueDate = when {
            rule.seasonal != null -> nextSeasonal(rule.seasonal, lastDone?.date, today)
            rule.pinnedDate != null -> rule.pinnedDate
            rule.intervalMonths != null && lastDone != null -> lastDone.date.plusMonths(rule.intervalMonths.toLong())
            else -> null
        }

        if (dueKm == null && dueDate == null) {
            val hasInterval = rule.intervalKm != null || rule.intervalMonths != null
            return DueResult(if (hasInterval && lastDone == null) DueStatus.NEVER_DONE else DueStatus.NO_SCHEDULE)
        }

        val kmLeft = if (dueKm != null && currentKm != null) max(0, dueKm - currentKm) else null
        val daysLeft = dueDate?.let { ChronoUnit.DAYS.between(today, it) }
        val projectedKmDate = if (kmLeft != null && kmLeft > 0 && paceKmPerDay != null && paceKmPerDay > 0) {
            today.plusDays(ceil(kmLeft / paceKmPerDay).toLong())
        } else null

        val kmReached = dueKm != null && currentKm != null && currentKm >= dueKm
        val dateReached = dueDate != null && !today.isBefore(dueDate)
        val soonLimit = today.plusMonths(thresholds.soonMonths.toLong())
        val soon = (kmLeft != null && kmLeft <= thresholds.soonKm) ||
            (dueDate != null && !dueDate.isAfter(soonLimit)) ||
            (projectedKmDate != null && !projectedKmDate.isAfter(soonLimit))

        val status = when {
            kmReached || dateReached -> DueStatus.OVERDUE
            soon -> DueStatus.DUE_SOON
            else -> DueStatus.OK
        }
        return DueResult(status, dueKm, dueDate, kmLeft, daysLeft, projectedKmDate)
    }

    /**
     * The next yearly [day] at least half a year after the last swap, so a swap done a few days
     * early is not due again a few days later. Never logged: the next [day] from today.
     */
    internal fun nextSeasonal(day: MonthDay, lastDone: LocalDate?, today: LocalDate): LocalDate {
        val from = lastDone?.plusMonths(6) ?: today
        val sameYear = day.atYear(from.year)
        return if (sameYear.isBefore(from)) day.atYear(from.year + 1) else sameYear
    }
}
