package io.github.lrosa2.carcare.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.time.LocalDate

// Tables from spec "Data model". Every table carries a vehicleId (spec D2); v1 only uses vehicle 1.

const val VEHICLE_ID = 1L

@Entity(tableName = "vehicle")
data class VehicleEntity(
    @PrimaryKey val id: Long = VEHICLE_ID,
    val name: String,
    val notes: String = "",
)

@Entity(tableName = "item", indices = [Index("vehicleId")])
data class ItemEntity(
    /** Stable key, e.g. "engine_oil"; items added in the app get a UUID. */
    @PrimaryKey val id: String,
    val vehicleId: Long = VEHICLE_ID,
    val name: String,
    val sortOrder: Int,
    val intervalKm: Int? = null,
    val intervalMonths: Int? = null,
    val seasonalMonth: Int? = null,
    val seasonalDay: Int? = null,
    val pinnedKm: Int? = null,
    val pinnedDate: LocalDate? = null,
    /** The day the pin was set; a full service on or after it clears the pin (spec D19). */
    val pinSetOn: LocalDate? = null,
    val toyotaRef: String = "",
    val myNote: String = "",
    /** Last due state a notification was sent for (spec "Reminders" step 3). */
    val lastNotifiedStatus: String? = null,
)

@Entity(tableName = "reading", indices = [Index("vehicleId")])
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long = VEHICLE_ID,
    val date: LocalDate,
    val km: Int,
)

@Entity(tableName = "visit", indices = [Index("vehicleId")])
data class VisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long = VEHICLE_ID,
    val date: LocalDate,
    val km: Int,
    val note: String = "",
)

@Entity(
    tableName = "visit_item",
    primaryKeys = ["visitId", "itemId"],
    foreignKeys = [
        ForeignKey(entity = VisitEntity::class, parentColumns = ["id"], childColumns = ["visitId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("itemId")],
)
data class VisitItemEntity(
    val visitId: Long,
    val itemId: String,
    val partial: Boolean = false,
)

@Entity(tableName = "note_card", indices = [Index("vehicleId")])
data class NoteCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long = VEHICLE_ID,
    val title: String,
    val body: String,
    val sortOrder: Int,
)

class Converters {
    @TypeConverter fun fromDate(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun toDate(s: String?): LocalDate? = s?.let(LocalDate::parse)
}
