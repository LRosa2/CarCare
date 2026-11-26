package io.github.lrosa2.carcare.ui

import io.github.lrosa2.carcare.domain.DueResult
import io.github.lrosa2.carcare.domain.DueStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FmtTest {

    @Test
    fun `km uses the Swiss apostrophe grouping`() {
        assertEquals("123'456 km", Fmt.km(123_456))
        assertEquals("500 km", Fmt.km(500))
    }

    @Test
    fun `dates are dd_MM_yyyy and months are short English`() {
        assertEquals("10.05.2028", Fmt.date(LocalDate.of(2028, 5, 10)))
        assertEquals("Dec 2026", Fmt.month(LocalDate.of(2026, 12, 15)))
    }

    @Test
    fun `due line combines km left, due date and the expected month`() {
        val r = DueResult(
            DueStatus.DUE_SOON, dueKm = 83_770, dueDate = LocalDate.of(2027, 8, 1),
            kmLeft = 770, daysLeft = 251, projectedKmDate = LocalDate.of(2026, 12, 15),
        )
        assertEquals("≈ 770 km left (≈ Dec 2026) · or by 01.08.2027", Fmt.dueLine(r))
    }

    @Test
    fun `due line for overdue and never-done items`() {
        assertEquals("Never logged — due now", Fmt.dueLine(DueResult(DueStatus.NEVER_DONE)))
        val overdue = DueResult(DueStatus.OVERDUE, dueKm = 75_000, dueDate = LocalDate.of(2025, 9, 20), kmLeft = 0, daysLeft = -429)
        assertEquals("Overdue: was due at 75'000 km or 20.09.2025", Fmt.dueLine(overdue))
    }

    @Test
    fun `parses km typed with or without separators`() {
        assertEquals(123_456, Fmt.parseKm("123'456"))
        assertEquals(123_456, Fmt.parseKm(" 123 456 "))
        assertEquals(null, Fmt.parseKm("abc"))
        assertEquals(null, Fmt.parseKm(""))
    }
}
