package io.github.lrosa2.carcare.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPolicyTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private val monthly = ReminderConfig(day = 1, intervalMonths = 1, anchor = null)

    @Test
    fun `latest prompt date is this month's reminder day once it has passed`() {
        assertEquals(d(2026, 11, 1), ReminderPolicy.latestPromptDate(d(2026, 11, 23), monthly))
        assertEquals(d(2026, 12, 1), ReminderPolicy.latestPromptDate(d(2026, 12, 1), monthly))
        assertEquals(d(2026, 10, 15), ReminderPolicy.latestPromptDate(d(2026, 11, 10), monthly.copy(day = 15)))
    }

    @Test
    fun `every two months counts from the anchor month`() {
        val cfg = ReminderConfig(day = 1, intervalMonths = 2, anchor = YearMonth.of(2026, 11))
        assertEquals(d(2026, 11, 1), ReminderPolicy.latestPromptDate(d(2026, 12, 15), cfg))
        assertEquals(d(2027, 1, 1), ReminderPolicy.latestPromptDate(d(2027, 1, 1), cfg))
    }

    @Test
    fun `prompts when the reminder day came and no km was entered recently`() {
        val r = ReminderPolicy.promptDecision(d(2026, 12, 1), monthly, lastPrompt = d(2026, 11, 1), lastEntry = d(2026, 11, 2))
        assertEquals(PromptDecision.Prompt(d(2026, 12, 1)), r)
    }

    @Test
    fun `skips the prompt when km was entered in the week before the reminder day`() {
        val r = ReminderPolicy.promptDecision(d(2026, 12, 1), monthly, lastPrompt = d(2026, 11, 1), lastEntry = d(2026, 11, 28))
        assertEquals(PromptDecision.SkipRecentEntry(d(2026, 12, 1)), r)
    }

    @Test
    fun `does not prompt twice for the same reminder day`() {
        val r = ReminderPolicy.promptDecision(d(2026, 12, 5), monthly, lastPrompt = d(2026, 12, 1), lastEntry = null)
        assertEquals(PromptDecision.None, r)
    }

    @Test
    fun `a missed reminder day is caught up on the next check`() {
        val r = ReminderPolicy.promptDecision(d(2026, 12, 3), monthly, lastPrompt = d(2026, 11, 1), lastEntry = d(2026, 11, 2))
        assertEquals(PromptDecision.Prompt(d(2026, 12, 1)), r)
    }

    @Test
    fun `re-nags once three days after a prompt without a new entry`() {
        assertTrue(ReminderPolicy.shouldRenag(d(2026, 12, 4), lastPrompt = d(2026, 12, 1), renagSent = false, lastEntry = d(2026, 11, 2)))
        assertFalse(ReminderPolicy.shouldRenag(d(2026, 12, 3), d(2026, 12, 1), false, d(2026, 11, 2)))
        assertFalse(ReminderPolicy.shouldRenag(d(2026, 12, 4), d(2026, 12, 1), renagSent = true, lastEntry = d(2026, 11, 2)))
        assertFalse(ReminderPolicy.shouldRenag(d(2026, 12, 4), d(2026, 12, 1), false, lastEntry = d(2026, 12, 2)))
        assertFalse(ReminderPolicy.shouldRenag(d(2026, 12, 4), lastPrompt = null, renagSent = false, lastEntry = null))
    }

    @Test
    fun `only items whose due state changed are notified`() {
        val now = mapOf(
            "oil" to DueStatus.DUE_SOON, // was OK -> notify
            "filter" to DueStatus.OVERDUE, // was DUE_SOON -> notify
            "fan" to DueStatus.NEVER_DONE, // never notified -> notify
            "plugs" to DueStatus.OK, // fine
            "cabin" to DueStatus.OVERDUE, // already notified as OVERDUE
        )
        val last = mapOf("oil" to "OK", "filter" to "DUE_SOON", "cabin" to "OVERDUE")
        assertEquals(setOf("oil", "filter", "fan"), ReminderPolicy.newlyDue(now, last).toSet())
    }

    @Test
    fun `next alarm is today at the reminder time if still ahead, else tomorrow`() {
        val zone = ZoneId.of("Europe/Zurich")
        val seven = LocalTime.of(19, 0)
        val morning = ZonedDateTime.of(LocalDateTime.of(2026, 11, 23, 8, 0), zone)
        assertEquals(ZonedDateTime.of(LocalDateTime.of(2026, 11, 23, 19, 0), zone), ReminderPolicy.nextAlarm(morning, seven))
        val evening = ZonedDateTime.of(LocalDateTime.of(2026, 11, 23, 19, 0), zone)
        assertEquals(ZonedDateTime.of(LocalDateTime.of(2026, 11, 24, 19, 0), zone), ReminderPolicy.nextAlarm(evening, seven))
    }

    @Test
    fun `next alarm keeps the local time across the DST change`() {
        val zone = ZoneId.of("Europe/Zurich")
        val beforeSwitch = ZonedDateTime.of(LocalDateTime.of(2027, 3, 27, 20, 0), zone) // DST starts 28.03.2027
        val next = ReminderPolicy.nextAlarm(beforeSwitch, LocalTime.of(19, 0))
        assertEquals(LocalDateTime.of(2027, 3, 28, 19, 0), next.toLocalDateTime())
    }
}
