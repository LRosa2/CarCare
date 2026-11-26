package io.github.lrosa2.carcare.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StatusEngineTest {

    private val today = LocalDate.of(2026, 11, 23)
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private fun overview(items: List<ItemSpec>, visits: List<VisitRecord>, readings: List<OdometerReading> = emptyList()) =
        StatusEngine.overview(items, visits, readings, today, StatusSettings())

    @Test
    fun `last full service ignores partial jobs`() {
        val coolant = ItemSpec("engine_coolant", "Engine coolant", Rule(intervalKm = 60_000))
        val visits = listOf(
            VisitRecord(d(2023, 3, 20), 40_000, mapOf("engine_coolant" to false)),
            VisitRecord(d(2024, 6, 15), 60_000, mapOf("engine_coolant" to true)),
        )
        val s = overview(listOf(coolant), visits).items.single()
        assertEquals(ServiceRecord(d(2023, 3, 20), 40_000), s.lastFull)
        assertEquals(100_000, s.result.dueKm)
    }

    @Test
    fun `a pin stays active until a full service on or after the day it was set`() {
        val fluid = ItemSpec(
            "brake_fluid", "Brake fluid",
            Rule(intervalMonths = 24, pinnedKm = 95_000), pinSetOn = d(2026, 11, 23),
        )
        val before = listOf(VisitRecord(d(2026, 5, 10), 77_000, mapOf("brake_fluid" to false)))
        assertEquals(95_000, overview(listOf(fluid), before).items.single().result.dueKm)

        val after = before + VisitRecord(d(2026, 12, 1), 83_500, mapOf("brake_fluid" to false))
        val s = overview(listOf(fluid), after).items.single()
        assertNull(s.result.dueKm) // pin cleared, no km interval
        assertEquals(d(2028, 12, 1), s.result.dueDate)
        assertEquals(false, s.pinActive)
    }

    @Test
    fun `a partial job does not clear a pin`() {
        val coolant = ItemSpec("engine_coolant", "Engine coolant", Rule(intervalKm = 60_000, pinnedKm = 95_000), pinSetOn = d(2026, 1, 1))
        val visits = listOf(VisitRecord(d(2026, 6, 1), 78_000, mapOf("engine_coolant" to true)))
        assertEquals(95_000, overview(listOf(coolant), visits).items.single().result.dueKm)
    }

    @Test
    fun `visit km counts as an odometer reading`() {
        val oil = ItemSpec("engine_oil", "Engine oil", Rule(intervalKm = 5_000, intervalMonths = 12))
        val visits = listOf(
            VisitRecord(d(2026, 2, 1), 74_000, mapOf("engine_oil" to false)),
            VisitRecord(d(2026, 8, 1), 79_500, mapOf("engine_oil" to false)),
        )
        val o = overview(listOf(oil), visits)
        assertEquals(OdometerReading(d(2026, 8, 1), 79_500), o.latestReading)
        // 01.02 -> 01.08.2026 = 181 days, 5,500 km; 114 more days to 23.11.2026.
        assertEquals(79_500 + Math.round(5_500.0 / 181 * 114).toInt(), o.estimatedKm)
    }

    @Test
    fun `items are sorted by urgency then by nearest due date`() {
        val items = listOf(
            ItemSpec("ok", "OK item", Rule(intervalMonths = 24)),
            ItemSpec("never", "Never done", Rule(intervalMonths = 12)),
            ItemSpec("overdue", "Overdue", Rule(intervalMonths = 12)),
            ItemSpec("soon", "Soon", Rule(intervalMonths = 12)),
        )
        val visits = listOf(
            VisitRecord(d(2026, 1, 1), 74_000, mapOf("ok" to false)),
            VisitRecord(d(2025, 1, 1), 64_000, mapOf("overdue" to false)),
            VisitRecord(d(2025, 12, 10), 73_000, mapOf("soon" to false)),
        )
        assertEquals(listOf("overdue", "never", "soon", "ok"), overview(items, visits).items.map { it.spec.id })
    }

    @Test
    fun `history of an item lists its visits newest first`() {
        val oil = ItemSpec("engine_oil", "Engine oil", Rule(intervalKm = 5_000))
        val visits = listOf(
            VisitRecord(d(2025, 8, 10), 70_000, mapOf("engine_oil" to false)),
            VisitRecord(d(2026, 8, 1), 79_500, mapOf("engine_oil" to false)),
            VisitRecord(d(2026, 5, 10), 77_000, mapOf("brake_fluid" to false)),
        )
        val s = overview(listOf(oil), visits).items.single()
        assertEquals(listOf(79_500, 70_000), s.history.map { it.km })
    }
}
