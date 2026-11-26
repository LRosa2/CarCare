package io.github.lrosa2.carcare.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Pace, estimated current km and plausibility checks for odometer readings (spec D5). */
object Odometer {

    /** Spans shorter than this give a pace too noisy to project with. */
    private const val MIN_SPAN_DAYS = 14

    /** Above this average since the previous reading, an entry is probably a typo. */
    const val SUSPICIOUS_KM_PER_DAY = 1_500.0

    /**
     * Average km/day from the last reading on or before (latest − [windowMonths]) to the latest
     * reading, or from the earliest reading when history is shorter. Null when there is no usable span.
     */
    fun paceKmPerDay(readings: List<OdometerReading>, windowMonths: Int): Double? {
        if (readings.size < 2) return null
        val sorted = readings.sortedWith(compareBy({ it.date }, { it.km }))
        val latest = sorted.last()
        val windowStart = latest.date.minusMonths(windowMonths.toLong())
        val start = sorted.lastOrNull { !it.date.isAfter(windowStart) } ?: sorted.first()
        val days = ChronoUnit.DAYS.between(start.date, latest.date)
        val km = latest.km - start.km
        if (days < MIN_SPAN_DAYS || km < 0) return null
        return km.toDouble() / days
    }

    /** Latest reading plus pace × days since; the latest reading itself without a pace. */
    fun estimateKm(readings: List<OdometerReading>, paceKmPerDay: Double?, today: LocalDate): Int? {
        val latest = readings.maxWithOrNull(compareBy({ it.date }, { it.km })) ?: return null
        val days = ChronoUnit.DAYS.between(latest.date, today)
        if (paceKmPerDay == null || days <= 0) return latest.km
        return latest.km + (paceKmPerDay * days).roundToInt()
    }

    /** A new reading must fit between its neighbours in time; a huge jump needs confirmation. */
    fun validate(candidate: OdometerReading, existing: List<OdometerReading>): ReadingCheck {
        val previous = existing
            .filter { !it.date.isAfter(candidate.date) }
            .maxWithOrNull(compareBy({ it.km }, { it.date }))
        if (previous != null && candidate.km < previous.km) return ReadingCheck.TooLow(previous)

        val next = existing
            .filter { it.date.isAfter(candidate.date) }
            .minWithOrNull(compareBy({ it.km }, { it.date }))
        if (next != null && candidate.km > next.km) return ReadingCheck.TooHigh(next)

        if (previous != null) {
            val days = ChronoUnit.DAYS.between(previous.date, candidate.date).coerceAtLeast(1)
            val kmPerDay = (candidate.km - previous.km).toDouble() / days
            if (kmPerDay > SUSPICIOUS_KM_PER_DAY) return ReadingCheck.Suspicious(previous, kmPerDay)
        }
        return ReadingCheck.Ok
    }
}
