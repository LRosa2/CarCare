package io.github.lrosa2.carcare.domain

import java.time.LocalDate
import java.time.MonthDay

/** One odometer value on one day (a monthly entry, or the km of a service visit). */
data class OdometerReading(val date: LocalDate, val km: Int)

/** The last FULL service of an item (partial jobs never count, spec D8). */
data class ServiceRecord(val date: LocalDate, val km: Int)

/**
 * When an item is due (spec D3). Intervals count from the last full service; a pin replaces the
 * interval of the same dimension (km or date) until the item is done again. Whichever comes first.
 * A [seasonal] item is due every year on that day and ignores the intervals (tyre swaps).
 */
data class Rule(
    val intervalKm: Int? = null,
    val intervalMonths: Int? = null,
    val seasonal: MonthDay? = null,
    val pinnedKm: Int? = null,
    val pinnedDate: LocalDate? = null,
)

/** Sorted from most to least urgent; the home list uses this order. */
enum class DueStatus { OVERDUE, NEVER_DONE, DUE_SOON, OK, NO_SCHEDULE }

data class DueResult(
    val status: DueStatus,
    val dueKm: Int? = null,
    val dueDate: LocalDate? = null,
    /** dueKm minus the current km; never negative. Null without a km target or a current km. */
    val kmLeft: Int? = null,
    /** Days until [dueDate]; negative when it has passed. */
    val daysLeft: Long? = null,
    /** The day the km target is expected at the current pace. */
    val projectedKmDate: LocalDate? = null,
)

/** "Due soon" lead (spec D5): the due date or projected date within [soonMonths], or [soonKm] left. */
data class Thresholds(val soonKm: Int = 500, val soonMonths: Int = 1)

sealed interface ReadingCheck {
    data object Ok : ReadingCheck
    /** Lower than a reading on an earlier (or the same) day. */
    data class TooLow(val previous: OdometerReading) : ReadingCheck
    /** Higher than a reading on a later day (a backdated entry). */
    data class TooHigh(val next: OdometerReading) : ReadingCheck
    /** Possible but implausible (e.g. an extra digit); the UI asks for confirmation. */
    data class Suspicious(val previous: OdometerReading, val kmPerDay: Double) : ReadingCheck
}
