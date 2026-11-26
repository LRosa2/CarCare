package io.github.lrosa2.carcare.domain

import java.time.LocalDate

/** A maintenance item as the engine sees it. Pins live in [rule]; [pinSetOn] decides if they still count. */
data class ItemSpec(
    val id: String,
    val name: String,
    val rule: Rule,
    val pinSetOn: LocalDate? = null,
)

/** One visit: date, km and the ticked items (value = partial). */
data class VisitRecord(val date: LocalDate, val km: Int, val items: Map<String, Boolean>)

data class StatusSettings(
    val paceWindowMonths: Int = 6,
    val thresholds: Thresholds = Thresholds(),
)

data class ItemStatus(
    val spec: ItemSpec,
    val lastFull: ServiceRecord?,
    val pinActive: Boolean,
    val result: DueResult,
    /** Visits that ticked this item, newest first. */
    val history: List<VisitRecord>,
)

data class Overview(
    val latestReading: OdometerReading?,
    val paceKmPerDay: Double?,
    val estimatedKm: Int?,
    val items: List<ItemStatus>,
)

/**
 * Turns items + history into what the home list shows (spec "Due logic"). Last full service and
 * pin state are always derived from history, never stored (spec D19).
 */
object StatusEngine {

    private val visitOrder = compareBy<VisitRecord>({ it.date }, { it.km })

    fun overview(
        items: List<ItemSpec>,
        visits: List<VisitRecord>,
        readings: List<OdometerReading>,
        today: LocalDate,
        settings: StatusSettings,
    ): Overview {
        // Visit km are odometer readings too.
        val allReadings = (readings + visits.map { OdometerReading(it.date, it.km) }).distinct()
        val pace = Odometer.paceKmPerDay(allReadings, settings.paceWindowMonths)
        val estimatedKm = Odometer.estimateKm(allReadings, pace, today)
        val latest = allReadings.maxWithOrNull(compareBy({ it.date }, { it.km }))

        val statuses = items.map { spec -> statusOf(spec, visits, estimatedKm, pace, today, settings) }
            .sortedWith(
                compareBy<ItemStatus>(
                    { it.result.status.ordinal },
                    { urgencyDate(it.result) ?: LocalDate.MAX },
                    { it.result.kmLeft ?: Int.MAX_VALUE },
                    { it.spec.name },
                ),
            )
        return Overview(latest, pace, estimatedKm, statuses)
    }

    private fun statusOf(
        spec: ItemSpec,
        visits: List<VisitRecord>,
        currentKm: Int?,
        pace: Double?,
        today: LocalDate,
        settings: StatusSettings,
    ): ItemStatus {
        val history = visits.filter { spec.id in it.items }.sortedWith(visitOrder.reversed())
        val fullServices = history.filter { it.items[spec.id] == false }
        val lastFull = fullServices.firstOrNull()?.let { ServiceRecord(it.date, it.km) }

        val hasPin = spec.rule.pinnedKm != null || spec.rule.pinnedDate != null
        val pinActive = hasPin && (spec.pinSetOn == null || fullServices.none { !it.date.isBefore(spec.pinSetOn) })
        val rule = if (pinActive) spec.rule else spec.rule.copy(pinnedKm = null, pinnedDate = null)

        val result = DueCalculator.evaluate(rule, lastFull, currentKm, pace, today, settings.thresholds)
        return ItemStatus(spec, lastFull, pinActive, result, history)
    }

    /** The earlier of the due date and the date the km target is expected. */
    fun urgencyDate(r: DueResult): LocalDate? =
        listOfNotNull(r.dueDate, r.projectedKmDate).minOrNull()
}
