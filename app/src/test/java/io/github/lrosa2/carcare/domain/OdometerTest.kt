package io.github.lrosa2.carcare.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class OdometerTest {

    private val today = LocalDate.of(2026, 11, 23)
    private fun r(y: Int, m: Int, d: Int, km: Int) = OdometerReading(LocalDate.of(y, m, d), km)

    @Test
    fun `pace spans from the last reading before the window to the latest reading`() {
        val readings = listOf(
            r(2025, 8, 10, 70_000),
            r(2026, 2, 1, 74_000),
            r(2026, 5, 10, 77_000), // last reading on or before 23.05.2026, the window start
            r(2026, 8, 1, 79_500),
            r(2026, 11, 23, 83_000),
        )
        // 10.05.2026 -> 23.11.2026 = 197 days, 6,000 km.
        val pace = Odometer.paceKmPerDay(readings, windowMonths = 6)!!
        assertEquals(6_000.0 / 197, pace, 1e-9)
    }

    @Test
    fun `pace uses the earliest reading when history is shorter than the window`() {
        val readings = listOf(r(2026, 10, 1, 82_000), r(2026, 11, 1, 83_000))
        assertEquals(1_000.0 / 31, Odometer.paceKmPerDay(readings, windowMonths = 6)!!, 1e-9)
    }

    @Test
    fun `pace is unknown with a single reading or a span under two weeks`() {
        assertNull(Odometer.paceKmPerDay(listOf(r(2026, 11, 1, 83_000)), 6))
        assertNull(Odometer.paceKmPerDay(listOf(r(2026, 11, 1, 83_000), r(2026, 11, 10, 83_300)), 6))
    }

    @Test
    fun `pace ignores a decreasing odometer`() {
        assertNull(Odometer.paceKmPerDay(listOf(r(2026, 9, 1, 84_000), r(2026, 11, 1, 83_000)), 6))
    }

    @Test
    fun `estimated km adds pace times days since the latest reading`() {
        val readings = listOf(r(2026, 11, 13, 82_650))
        assertEquals(83_000, Odometer.estimateKm(readings, paceKmPerDay = 35.0, today = today))
        assertEquals(82_650, Odometer.estimateKm(readings, paceKmPerDay = null, today = today))
        assertNull(Odometer.estimateKm(emptyList(), 35.0, today))
    }

    @Test
    fun `a reading below an earlier one is rejected`() {
        val existing = listOf(r(2026, 8, 1, 79_500))
        val result = Odometer.validate(r(2026, 11, 23, 8_300), existing)
        assertTrue(result is ReadingCheck.TooLow)
        assertEquals(79_500, (result as ReadingCheck.TooLow).previous.km)
    }

    @Test
    fun `a backdated reading above a later one is rejected`() {
        val existing = listOf(r(2026, 5, 10, 77_000), r(2026, 8, 1, 79_500))
        val result = Odometer.validate(r(2026, 6, 1, 80_000), existing)
        assertTrue(result is ReadingCheck.TooHigh)
    }

    @Test
    fun `a consistent backdated reading is accepted`() {
        val existing = listOf(r(2026, 5, 10, 77_000), r(2026, 8, 1, 79_500))
        assertEquals(ReadingCheck.Ok, Odometer.validate(r(2026, 6, 1, 78_000), existing))
    }

    @Test
    fun `a jump far above the expected km is flagged as a likely typo`() {
        // Expected ~83,000 on 23.11.2026; 830,000 is an extra digit.
        val existing = listOf(r(2026, 8, 1, 79_500))
        val result = Odometer.validate(r(2026, 11, 23, 830_000), existing)
        assertTrue(result is ReadingCheck.Suspicious)
    }
}
