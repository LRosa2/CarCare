package io.github.lrosa2.carcare.backup

import io.github.lrosa2.carcare.domain.ItemSpec
import io.github.lrosa2.carcare.domain.OdometerReading
import io.github.lrosa2.carcare.domain.Rule
import io.github.lrosa2.carcare.domain.VisitRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.format.DateTimeParseException

/**
 * The one JSON format for the owner's import file, exports and backups (spec D10, D20).
 * Dates are ISO yyyy-MM-dd; a seasonal day is a MonthDay string like "--10-15".
 */
@Serializable
data class BackupFile(
    val formatVersion: Int,
    val exportedAt: String? = null,
    val vehicle: VehicleJson,
    val items: List<ItemJson>,
    val readings: List<ReadingJson> = emptyList(),
    val visits: List<VisitJson> = emptyList(),
    val cards: List<CardJson> = emptyList(),
    val settings: SettingsJson? = null,
)

@Serializable
data class VehicleJson(val name: String, val notes: String = "")

@Serializable
data class ItemJson(
    val id: String,
    val name: String,
    val intervalKm: Int? = null,
    val intervalMonths: Int? = null,
    val seasonal: String? = null,
    val pinnedKm: Int? = null,
    val pinnedDate: String? = null,
    val pinSetOn: String? = null,
    val toyotaRef: String = "",
    val myNote: String = "",
)

@Serializable
data class ReadingJson(val date: String, val km: Int)

@Serializable
data class VisitJson(val date: String, val km: Int, val note: String = "", val items: List<VisitItemJson>)

@Serializable
data class VisitItemJson(val id: String, val partial: Boolean = false)

@Serializable
data class CardJson(val title: String, val body: String)

@Serializable
data class SettingsJson(
    val reminderDay: Int = 1,
    val reminderTime: String = "19:00",
    val reminderIntervalMonths: Int = 1,
    val paceWindowMonths: Int = 6,
    val soonKm: Int = 500,
    val soonMonths: Int = 1,
)

fun ItemJson.toItemSpec(): ItemSpec = ItemSpec(
    id = id,
    name = name,
    rule = Rule(
        intervalKm = intervalKm,
        intervalMonths = intervalMonths,
        seasonal = seasonal?.let(MonthDay::parse),
        pinnedKm = pinnedKm,
        pinnedDate = pinnedDate?.let(LocalDate::parse),
    ),
    pinSetOn = pinSetOn?.let(LocalDate::parse),
)

fun BackupFile.toItemSpecs(): List<ItemSpec> = items.map { it.toItemSpec() }

fun BackupFile.toVisitRecords(): List<VisitRecord> = visits.map { v ->
    VisitRecord(LocalDate.parse(v.date), v.km, v.items.associate { it.id to it.partial })
}

fun BackupFile.toReadings(): List<OdometerReading> = readings.map { OdometerReading(LocalDate.parse(it.date), it.km) }

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupCodec {
    const val CURRENT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** Parses and validates; every problem becomes a [BackupException] with a readable message. */
    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupException("Not a CarCare backup file: ${e.message?.lineSequence()?.firstOrNull()}", e)
        } catch (e: IllegalArgumentException) {
            throw BackupException("Not a CarCare backup file: ${e.message?.lineSequence()?.firstOrNull()}", e)
        }
        validate(file)
        return file
    }

    private fun validate(f: BackupFile) {
        if (f.formatVersion > CURRENT_VERSION) {
            throw BackupException("This file was made by a newer CarCare (format ${f.formatVersion}). Update the app first.")
        }
        if (f.formatVersion < 1) throw BackupException("Unknown file format ${f.formatVersion}.")

        val ids = f.items.map { it.id }
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.firstOrNull()?.let {
            throw BackupException("Item id \"$it\" appears more than once.")
        }
        f.items.forEach { item ->
            if (item.name.isBlank()) throw BackupException("Item \"${item.id}\" has no name.")
            item.seasonal?.let { parse("item ${item.id} seasonal", it) { s -> MonthDay.parse(s) } }
            item.pinnedDate?.let { date("item ${item.id} pinnedDate", it) }
            item.pinSetOn?.let { date("item ${item.id} pinSetOn", it) }
            listOfNotNull(item.intervalKm, item.intervalMonths, item.pinnedKm).forEach {
                if (it <= 0) throw BackupException("Item \"${item.id}\" has a non-positive interval or pin ($it).")
            }
        }
        f.readings.forEach {
            date("reading", it.date)
            if (it.km < 0) throw BackupException("Reading on ${it.date} has negative km.")
        }
        val known = ids.toSet()
        f.visits.forEach { v ->
            date("visit", v.date)
            if (v.km < 0) throw BackupException("Visit on ${v.date} has negative km.")
            v.items.forEach {
                if (it.id !in known) throw BackupException("Visit on ${v.date} ticks unknown item \"${it.id}\".")
            }
        }
        f.settings?.let { s ->
            if (s.reminderDay !in 1..28) throw BackupException("Reminder day must be 1-28, was ${s.reminderDay}.")
            parse("reminderTime", s.reminderTime) { LocalTime.parse(it) }
        }
    }

    private fun date(where: String, value: String) = parse("$where date", value) { LocalDate.parse(it) }

    private fun <T> parse(where: String, value: String, parser: (String) -> T): T = try {
        parser(value)
    } catch (e: DateTimeParseException) {
        throw BackupException("Bad $where \"$value\".", e)
    }
}
