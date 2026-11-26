package io.github.lrosa2.carcare.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime

/** Monthly km prompt: day of month (1-28), every [intervalMonths] months counted from [anchor]. */
data class ReminderConfig(val day: Int, val intervalMonths: Int, val anchor: YearMonth?)

sealed interface PromptDecision {
    data object None : PromptDecision
    data class Prompt(val promptDate: LocalDate) : PromptDecision
    /** Km was entered in the week before the reminder day: mark it handled, no notification. */
    data class SkipRecentEntry(val promptDate: LocalDate) : PromptDecision
}

/**
 * When to notify (spec "Reminders", D6, D25, D26). Pure, so the daily alarm receiver stays thin.
 */
object ReminderPolicy {

    /** A km entry this many days before the reminder day makes the prompt unnecessary. */
    private const val RECENT_ENTRY_DAYS = 7L
    private const val RENAG_AFTER_DAYS = 3L
    private val dueStates = setOf(DueStatus.OVERDUE, DueStatus.NEVER_DONE, DueStatus.DUE_SOON)

    /** The most recent reminder day on or before [today] in an eligible month. */
    fun latestPromptDate(today: LocalDate, cfg: ReminderConfig): LocalDate? {
        val thisMonth = YearMonth.from(today)
        for (back in 0..cfg.intervalMonths.coerceAtLeast(1)) {
            val ym = thisMonth.minusMonths(back.toLong())
            val date = ym.atDay(cfg.day.coerceIn(1, 28))
            if (!date.isAfter(today) && eligible(ym, cfg)) return date
        }
        return null
    }

    private fun eligible(ym: YearMonth, cfg: ReminderConfig): Boolean {
        if (cfg.intervalMonths <= 1 || cfg.anchor == null) return true
        val months = (ym.year - cfg.anchor.year) * 12L + (ym.monthValue - cfg.anchor.monthValue)
        return Math.floorMod(months, cfg.intervalMonths.toLong()) == 0L
    }

    /** A reminder day that has not been handled yet; a missed one is caught up on the next check. */
    fun promptDecision(today: LocalDate, cfg: ReminderConfig, lastPrompt: LocalDate?, lastEntry: LocalDate?): PromptDecision {
        val date = latestPromptDate(today, cfg) ?: return PromptDecision.None
        if (lastPrompt != null && !lastPrompt.isBefore(date)) return PromptDecision.None
        if (lastEntry != null && !lastEntry.isBefore(date.minusDays(RECENT_ENTRY_DAYS))) {
            return PromptDecision.SkipRecentEntry(date)
        }
        return PromptDecision.Prompt(date)
    }

    fun shouldRenag(today: LocalDate, lastPrompt: LocalDate?, renagSent: Boolean, lastEntry: LocalDate?): Boolean {
        if (lastPrompt == null || renagSent) return false
        if (today.isBefore(lastPrompt.plusDays(RENAG_AFTER_DAYS))) return false
        return lastEntry == null || lastEntry.isBefore(lastPrompt)
    }

    /** Items now due (soon) whose state differs from the last one notified: each change notifies once. */
    fun newlyDue(now: Map<String, DueStatus>, lastNotified: Map<String, String?>): List<String> =
        now.filter { (id, status) -> status in dueStates && lastNotified[id] != status.name }.keys.toList()

    /** Today at [time] if still ahead, else tomorrow at [time], in the zone of [now] (keeps local time across DST). */
    fun nextAlarm(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
        val today = now.toLocalDate().atTime(time).atZone(now.zone)
        return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
    }
}
