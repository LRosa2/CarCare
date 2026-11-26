package io.github.lrosa2.carcare.data

import io.github.lrosa2.carcare.backup.BackupCodec
import io.github.lrosa2.carcare.backup.BackupFile
import io.github.lrosa2.carcare.backup.CardJson
import io.github.lrosa2.carcare.backup.ItemJson
import io.github.lrosa2.carcare.backup.ReadingJson
import io.github.lrosa2.carcare.backup.SettingsJson
import io.github.lrosa2.carcare.backup.VehicleJson
import io.github.lrosa2.carcare.backup.VisitItemJson
import io.github.lrosa2.carcare.backup.VisitJson
import io.github.lrosa2.carcare.data.db.CarDao
import io.github.lrosa2.carcare.data.db.ItemEntity
import io.github.lrosa2.carcare.data.db.NoteCardEntity
import io.github.lrosa2.carcare.data.db.ReadingEntity
import io.github.lrosa2.carcare.data.db.VehicleEntity
import io.github.lrosa2.carcare.data.db.VisitEntity
import io.github.lrosa2.carcare.data.db.VisitItemEntity
import io.github.lrosa2.carcare.domain.ItemSpec
import io.github.lrosa2.carcare.domain.OdometerReading
import io.github.lrosa2.carcare.domain.Odometer
import io.github.lrosa2.carcare.domain.Overview
import io.github.lrosa2.carcare.domain.ReadingCheck
import io.github.lrosa2.carcare.domain.ReminderPolicy
import io.github.lrosa2.carcare.domain.Rule
import io.github.lrosa2.carcare.domain.StatusEngine
import io.github.lrosa2.carcare.domain.VisitRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.YearMonth
import java.util.UUID

/** Everything the home list needs, recomputed whenever data or settings change. */
data class HomeState(
    val vehicleName: String,
    val overview: Overview,
    val items: Map<String, ItemEntity>,
    val settings: AppSettings,
    val hasItems: Boolean,
)

/** A visit with its ticks, for History and Log visit. */
data class VisitWithItems(val visit: VisitEntity, val items: Map<String, Boolean>)

class CarRepository(
    private val dao: CarDao,
    val settings: SettingsStore,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun today(): LocalDate = LocalDate.now(clock)

    val visitsWithItems: Flow<List<VisitWithItems>> = combine(dao.visits(), dao.visitItems()) { visits, ticks ->
        val byVisit = ticks.groupBy { it.visitId }
        visits.map { v -> VisitWithItems(v, byVisit[v.id].orEmpty().associate { it.itemId to it.partial }) }
    }

    val readings: Flow<List<ReadingEntity>> = dao.readings()
    val cards: Flow<List<NoteCardEntity>> = dao.cards()

    val home: Flow<HomeState> = combine(
        dao.vehicle(), dao.items(), visitsWithItems, dao.readings(), settings.flow,
    ) { vehicle, items, visits, readings, s ->
        HomeState(
            vehicleName = vehicle?.name ?: "My car",
            overview = computeOverview(items, visits, readings, s),
            items = items.associateBy { it.id },
            settings = s,
            hasItems = items.isNotEmpty(),
        )
    }

    private fun computeOverview(items: List<ItemEntity>, visits: List<VisitWithItems>, readings: List<ReadingEntity>, s: AppSettings) =
        StatusEngine.overview(
            items.map { it.toSpec() },
            visits.map { VisitRecord(it.visit.date, it.visit.km, it.items) },
            readings.map { OdometerReading(it.date, it.km) },
            today(),
            s.statusSettings,
        )

    /** Fresh overview for the alarm receiver (no UI collecting the flows). */
    suspend fun overviewOnce(): Pair<Overview, List<ItemEntity>> {
        val items = dao.itemsOnce()
        val ticks = dao.visitItemsOnce().groupBy { it.visitId }
        val visits = dao.visitsOnce().map { v -> VisitWithItems(v, ticks[v.id].orEmpty().associate { it.itemId to it.partial }) }
        return computeOverview(items, visits, dao.readingsOnce(), settings.current()) to items
    }

    // ---- Readings -----------------------------------------------------------------------------

    private suspend fun allReadings(excludeReadingId: Long? = null, excludeVisitId: Long? = null): List<OdometerReading> =
        dao.readingsOnce().filter { it.id != excludeReadingId }.map { OdometerReading(it.date, it.km) } +
            dao.visitsOnce().filter { it.id != excludeVisitId }.map { OdometerReading(it.date, it.km) }

    suspend fun checkReading(date: LocalDate, km: Int, readingId: Long? = null, visitId: Long? = null): ReadingCheck =
        Odometer.validate(OdometerReading(date, km), allReadings(readingId, visitId))

    suspend fun saveReading(id: Long?, date: LocalDate, km: Int) {
        if (id == null) dao.insertReading(ReadingEntity(date = date, km = km)) else dao.updateReading(ReadingEntity(id = id, date = date, km = km))
        markKmEntered()
    }

    suspend fun deleteReading(id: Long) = dao.deleteReading(id)

    private suspend fun markKmEntered() = settings.update { it.copy(lastKmEntry = today()) }

    // ---- Visits -------------------------------------------------------------------------------

    suspend fun visit(id: Long): VisitWithItems? =
        dao.visit(id)?.let { v -> VisitWithItems(v, dao.visitItemsOf(id).associate { it.itemId to it.partial }) }

    suspend fun saveVisit(id: Long?, date: LocalDate, km: Int, note: String, ticks: Map<String, Boolean>) {
        dao.saveVisit(VisitEntity(id = id ?: 0, date = date, km = km, note = note.trim()), ticks)
        markKmEntered()
    }

    suspend fun deleteVisit(id: Long) = dao.deleteVisit(id)

    // ---- Items --------------------------------------------------------------------------------

    suspend fun item(id: String) = dao.item(id)

    /** Saves an edited item; a changed pin gets today's date as pinSetOn (spec D19). */
    suspend fun saveItem(edited: ItemEntity) {
        val old = dao.item(edited.id)
        val pinChanged = old == null || old.pinnedKm != edited.pinnedKm || old.pinnedDate != edited.pinnedDate
        val hasPin = edited.pinnedKm != null || edited.pinnedDate != null
        val pinSetOn = when {
            !hasPin -> null
            pinChanged -> today()
            else -> old.pinSetOn
        }
        dao.upsertItem(edited.copy(pinSetOn = pinSetOn))
    }

    suspend fun newItem(name: String): String {
        val id = UUID.randomUUID().toString()
        dao.upsertItem(ItemEntity(id = id, name = name, sortOrder = dao.maxSortOrder() + 1))
        return id
    }

    suspend fun deleteItem(id: String) = dao.deleteItem(id)

    suspend fun setNotified(id: String, status: String?) = dao.setNotified(id, status)

    // ---- Cards --------------------------------------------------------------------------------

    suspend fun saveCard(card: NoteCardEntity) {
        val sort = if (card.id == 0L) dao.cardsOnce().maxOfOrNull { it.sortOrder }?.plus(1) ?: 0 else card.sortOrder
        dao.upsertCard(card.copy(sortOrder = sort))
    }

    suspend fun deleteCard(id: Long) = dao.deleteCard(id)

    // ---- Import / export (spec D10, D20) --------------------------------------------------------

    suspend fun export(): BackupFile {
        val s = settings.current()
        val ticks = dao.visitItemsOnce().groupBy { it.visitId }
        return BackupFile(
            formatVersion = BackupCodec.CURRENT_VERSION,
            exportedAt = today().toString(),
            vehicle = dao.vehicleOnce()?.let { VehicleJson(it.name, it.notes) } ?: VehicleJson("My car"),
            items = dao.itemsOnce().map { it.toJson() },
            readings = dao.readingsOnce().sortedBy { it.date }.map { ReadingJson(it.date.toString(), it.km) },
            visits = dao.visitsOnce().sortedWith(compareBy({ it.date }, { it.km })).map { v ->
                VisitJson(v.date.toString(), v.km, v.note, ticks[v.id].orEmpty().map { VisitItemJson(it.itemId, it.partial) })
            },
            cards = dao.cardsOnce().map { CardJson(it.title, it.body) },
            settings = SettingsJson(
                reminderDay = s.reminderDay,
                reminderTime = s.reminderTime.toString(),
                reminderIntervalMonths = s.reminderIntervalMonths,
                paceWindowMonths = s.paceWindowMonths,
                soonKm = s.soonKm,
                soonMonths = s.soonMonths,
            ),
        )
    }

    suspend fun markExported() = settings.update { it.copy(lastExport = today()) }

    /** Replaces all data with [file] (already validated by BackupCodec.decode). */
    suspend fun import(file: BackupFile) {
        dao.replaceAll(
            vehicle = VehicleEntity(name = file.vehicle.name, notes = file.vehicle.notes),
            items = file.items.mapIndexed { i, it -> it.toEntity(i) },
            readings = file.readings.map { ReadingEntity(date = LocalDate.parse(it.date), km = it.km) },
            visits = file.visits.map { v ->
                VisitEntity(date = LocalDate.parse(v.date), km = v.km, note = v.note) to v.items.associate { it.id to it.partial }
            },
            cards = file.cards.mapIndexed { i, c -> NoteCardEntity(title = c.title, body = c.body, sortOrder = i) },
        )
        val latestEntry = (file.readings.map { it.date } + file.visits.map { it.date }).maxOrNull()?.let(LocalDate::parse)
        settings.update { s ->
            val js = file.settings
            val updated = s.copy(
                reminderDay = js?.reminderDay ?: s.reminderDay,
                reminderTime = js?.reminderTime?.let(LocalTime::parse) ?: s.reminderTime,
                reminderIntervalMonths = js?.reminderIntervalMonths ?: s.reminderIntervalMonths,
                reminderAnchor = YearMonth.from(today()),
                paceWindowMonths = js?.paceWindowMonths ?: s.paceWindowMonths,
                soonKm = js?.soonKm ?: s.soonKm,
                soonMonths = js?.soonMonths ?: s.soonMonths,
                lastKmEntry = latestEntry,
                onboarded = true,
            )
            // This month's reminder day counts as handled: the app asks for the km right after
            // an import anyway, so the first monthly prompt comes next time.
            updated.copy(
                lastPrompt = ReminderPolicy.latestPromptDate(today(), updated.reminderConfig),
                renagSent = true,
            )
        }
    }

    suspend fun hasItems(): Boolean = dao.itemCount() > 0
}

fun ItemEntity.toSpec() = ItemSpec(
    id = id,
    name = name,
    rule = Rule(
        intervalKm = intervalKm,
        intervalMonths = intervalMonths,
        seasonal = if (seasonalMonth != null && seasonalDay != null) MonthDay.of(seasonalMonth, seasonalDay) else null,
        pinnedKm = pinnedKm,
        pinnedDate = pinnedDate,
    ),
    pinSetOn = pinSetOn,
)

private fun ItemEntity.toJson() = ItemJson(
    id = id, name = name, intervalKm = intervalKm, intervalMonths = intervalMonths,
    seasonal = if (seasonalMonth != null && seasonalDay != null) MonthDay.of(seasonalMonth, seasonalDay).toString() else null,
    pinnedKm = pinnedKm, pinnedDate = pinnedDate?.toString(), pinSetOn = pinSetOn?.toString(),
    toyotaRef = toyotaRef, myNote = myNote,
)

private fun ItemJson.toEntity(sortOrder: Int): ItemEntity {
    val md = seasonal?.let(MonthDay::parse)
    return ItemEntity(
        id = id, name = name, sortOrder = sortOrder,
        intervalKm = intervalKm, intervalMonths = intervalMonths,
        seasonalMonth = md?.monthValue, seasonalDay = md?.dayOfMonth,
        pinnedKm = pinnedKm, pinnedDate = pinnedDate?.let(LocalDate::parse), pinSetOn = pinSetOn?.let(LocalDate::parse),
        toyotaRef = toyotaRef, myNote = myNote,
    )
}
