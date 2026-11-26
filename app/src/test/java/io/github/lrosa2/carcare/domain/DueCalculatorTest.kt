package io.github.lrosa2.carcare.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.MonthDay

class DueCalculatorTest {

    private val today = LocalDate.of(2026, 11, 23)

    private fun eval(
        rule: Rule,
        lastDone: ServiceRecord?,
        currentKm: Int? = 83_000,
        pace: Double? = null,
    ) = DueCalculator.evaluate(rule, lastDone, currentKm, pace, today)

    @Test
    fun `km interval is counted from the last full service`() {
        val r = eval(Rule(intervalKm = 10_000), ServiceRecord(LocalDate.of(2026, 8, 1), 79_500))
        assertEquals(89_500, r.dueKm)
        assertEquals(6_500, r.kmLeft)
        assertEquals(DueStatus.OK, r.status)
    }

    @Test
    fun `km item is overdue once the odometer reaches the target`() {
        val r = eval(Rule(intervalKm = 5_000), ServiceRecord(LocalDate.of(2026, 1, 1), 78_000))
        assertEquals(DueStatus.OVERDUE, r.status)
        assertEquals(0, r.kmLeft)
    }

    @Test
    fun `km item is due soon within 500 km even without a pace`() {
        val r = eval(Rule(intervalKm = 5_000), ServiceRecord(LocalDate.of(2026, 8, 1), 78_400))
        assertEquals(400, r.kmLeft)
        assertEquals(DueStatus.DUE_SOON, r.status)
    }

    @Test
    fun `time item is overdue on and after its date`() {
        val r = eval(Rule(intervalMonths = 12), ServiceRecord(LocalDate.of(2024, 11, 5), 60_000))
        assertEquals(LocalDate.of(2025, 11, 5), r.dueDate)
        assertEquals(DueStatus.OVERDUE, r.status)
    }

    @Test
    fun `time item is due soon when its date is less than a month away`() {
        val r = eval(Rule(intervalMonths = 12), ServiceRecord(LocalDate.of(2025, 12, 10), 73_000))
        assertEquals(DueStatus.DUE_SOON, r.status)
        assertEquals(17L, r.daysLeft)
    }

    @Test
    fun `whichever comes first - the date wins when it is earlier`() {
        // Pollen filter: 15,000 km or 12 months from 20.09.2024. Km target not reached, date passed.
        val r = eval(
            Rule(intervalKm = 15_000, intervalMonths = 12),
            ServiceRecord(LocalDate.of(2024, 9, 20), 60_000),
            currentKm = 70_000,
        )
        assertEquals(75_000, r.dueKm)
        assertEquals(DueStatus.OVERDUE, r.status)
    }

    @Test
    fun `item with an interval that was never done is NEVER_DONE`() {
        val r = eval(Rule(intervalKm = 15_000, intervalMonths = 12), lastDone = null)
        assertEquals(DueStatus.NEVER_DONE, r.status)
        assertNull(r.dueKm)
        assertNull(r.dueDate)
    }

    @Test
    fun `pinned km replaces the km interval while the month interval still applies`() {
        // Brake fluid: every 24 months, pinned at 95,000 km.
        val r = eval(
            Rule(intervalMonths = 24, pinnedKm = 95_000),
            ServiceRecord(LocalDate.of(2026, 5, 10), 77_000),
        )
        assertEquals(95_000, r.dueKm)
        assertEquals(LocalDate.of(2028, 5, 10), r.dueDate)
        assertEquals(DueStatus.OK, r.status)
    }

    @Test
    fun `pinned km beats a km interval that would come later`() {
        val r = eval(Rule(intervalKm = 80_000, pinnedKm = 120_000), ServiceRecord(LocalDate.of(2024, 6, 15), 60_000))
        assertEquals(120_000, r.dueKm)
    }

    @Test
    fun `pinned target works for an item that was never done`() {
        val r = eval(Rule(intervalKm = 60_000, pinnedKm = 95_000), lastDone = null)
        assertEquals(95_000, r.dueKm)
        assertEquals(DueStatus.OK, r.status)
    }

    @Test
    fun `km target expected within a month at the current pace is due soon`() {
        // 770 km left at 35 km/day = 22 days.
        val r = eval(Rule(intervalKm = 5_000), ServiceRecord(LocalDate.of(2026, 8, 1), 78_770), pace = 35.0)
        assertEquals(770, r.kmLeft)
        assertEquals(LocalDate.of(2026, 12, 15), r.projectedKmDate)
        assertEquals(DueStatus.DUE_SOON, r.status)
    }

    @Test
    fun `km target far away at a slow pace stays OK`() {
        // 770 km left at 6 km/day = 129 days.
        val r = eval(Rule(intervalKm = 5_000), ServiceRecord(LocalDate.of(2026, 8, 1), 78_770), pace = 6.0)
        assertEquals(DueStatus.OK, r.status)
    }

    @Test
    fun `seasonal item is next due on its day at least half a year after the last swap`() {
        val rule = Rule(seasonal = MonthDay.of(10, 15))
        assertEquals(
            LocalDate.of(2026, 10, 15),
            eval(rule, ServiceRecord(LocalDate.of(2025, 10, 20), 71_000)).dueDate,
        )
        // Swapped a few days early: still next year, not five days later.
        assertEquals(
            LocalDate.of(2027, 10, 15),
            eval(rule, ServiceRecord(LocalDate.of(2026, 10, 10), 82_000)).dueDate,
        )
    }

    @Test
    fun `seasonal item never logged is due on its next day from today`() {
        val r = eval(Rule(seasonal = MonthDay.of(4, 15)), lastDone = null)
        assertEquals(LocalDate.of(2027, 4, 15), r.dueDate)
        assertEquals(DueStatus.OK, r.status)
    }

    @Test
    fun `item without interval or pin has no schedule`() {
        val r = eval(Rule(), ServiceRecord(LocalDate.of(2026, 1, 1), 74_000))
        assertEquals(DueStatus.NO_SCHEDULE, r.status)
    }

    @Test
    fun `unknown current km leaves km left empty and judges by date only`() {
        val r = eval(Rule(intervalKm = 5_000, intervalMonths = 12), ServiceRecord(LocalDate.of(2026, 8, 1), 79_500), currentKm = null)
        assertNull(r.kmLeft)
        assertEquals(DueStatus.OK, r.status)
    }
}
