package io.github.lrosa2.carcare.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CarDao {

    @Query("SELECT * FROM vehicle LIMIT 1") abstract fun vehicle(): Flow<VehicleEntity?>
    @Query("SELECT * FROM vehicle LIMIT 1") abstract suspend fun vehicleOnce(): VehicleEntity?

    @Query("SELECT * FROM item ORDER BY sortOrder, name") abstract fun items(): Flow<List<ItemEntity>>
    @Query("SELECT * FROM item ORDER BY sortOrder, name") abstract suspend fun itemsOnce(): List<ItemEntity>
    @Query("SELECT * FROM item WHERE id = :id") abstract suspend fun item(id: String): ItemEntity?
    @Query("SELECT COUNT(*) FROM item") abstract suspend fun itemCount(): Int
    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM item") abstract suspend fun maxSortOrder(): Int
    @Upsert abstract suspend fun upsertItem(item: ItemEntity)
    @Query("DELETE FROM item WHERE id = :id") abstract suspend fun deleteItem(id: String)
    @Query("UPDATE item SET lastNotifiedStatus = :status WHERE id = :id")
    abstract suspend fun setNotified(id: String, status: String?)

    @Query("SELECT * FROM reading ORDER BY date DESC, km DESC") abstract fun readings(): Flow<List<ReadingEntity>>
    @Query("SELECT * FROM reading ORDER BY date DESC, km DESC") abstract suspend fun readingsOnce(): List<ReadingEntity>
    @Insert abstract suspend fun insertReading(r: ReadingEntity): Long
    @Update abstract suspend fun updateReading(r: ReadingEntity)
    @Query("DELETE FROM reading WHERE id = :id") abstract suspend fun deleteReading(id: Long)

    @Query("SELECT * FROM visit ORDER BY date DESC, km DESC") abstract fun visits(): Flow<List<VisitEntity>>
    @Query("SELECT * FROM visit ORDER BY date DESC, km DESC") abstract suspend fun visitsOnce(): List<VisitEntity>
    @Query("SELECT * FROM visit WHERE id = :id") abstract suspend fun visit(id: Long): VisitEntity?
    @Insert abstract suspend fun insertVisit(v: VisitEntity): Long
    @Update abstract suspend fun updateVisit(v: VisitEntity)
    @Query("DELETE FROM visit WHERE id = :id") abstract suspend fun deleteVisit(id: Long)

    @Query("SELECT * FROM visit_item") abstract fun visitItems(): Flow<List<VisitItemEntity>>
    @Query("SELECT * FROM visit_item") abstract suspend fun visitItemsOnce(): List<VisitItemEntity>
    @Query("SELECT * FROM visit_item WHERE visitId = :visitId") abstract suspend fun visitItemsOf(visitId: Long): List<VisitItemEntity>
    @Insert abstract suspend fun insertVisitItems(items: List<VisitItemEntity>)
    @Query("DELETE FROM visit_item WHERE visitId = :visitId") abstract suspend fun clearVisitItems(visitId: Long)

    @Query("SELECT * FROM note_card ORDER BY sortOrder, id") abstract fun cards(): Flow<List<NoteCardEntity>>
    @Query("SELECT * FROM note_card ORDER BY sortOrder, id") abstract suspend fun cardsOnce(): List<NoteCardEntity>
    @Upsert abstract suspend fun upsertCard(c: NoteCardEntity)
    @Query("DELETE FROM note_card WHERE id = :id") abstract suspend fun deleteCard(id: Long)

    @Upsert abstract suspend fun upsertVehicle(v: VehicleEntity)
    @Insert abstract suspend fun insertItems(items: List<ItemEntity>)
    @Insert abstract suspend fun insertReadings(r: List<ReadingEntity>)
    @Insert abstract suspend fun insertCards(c: List<NoteCardEntity>)

    @Query("DELETE FROM visit_item") protected abstract suspend fun wipeVisitItems()
    @Query("DELETE FROM visit") protected abstract suspend fun wipeVisits()
    @Query("DELETE FROM reading") protected abstract suspend fun wipeReadings()
    @Query("DELETE FROM item") protected abstract suspend fun wipeItems()
    @Query("DELETE FROM note_card") protected abstract suspend fun wipeCards()
    @Query("DELETE FROM vehicle") protected abstract suspend fun wipeVehicles()

    /** Save a visit and its ticked items in one go; [visit] id 0 inserts, else updates. */
    @Transaction
    open suspend fun saveVisit(visit: VisitEntity, items: Map<String, Boolean>): Long {
        val id = if (visit.id == 0L) insertVisit(visit) else visit.id.also { updateVisit(visit) }
        clearVisitItems(id)
        insertVisitItems(items.map { (itemId, partial) -> VisitItemEntity(id, itemId, partial) })
        return id
    }

    /** Import (spec D20): everything is replaced, nothing merged. */
    @Transaction
    open suspend fun replaceAll(
        vehicle: VehicleEntity,
        items: List<ItemEntity>,
        readings: List<ReadingEntity>,
        visits: List<Pair<VisitEntity, Map<String, Boolean>>>,
        cards: List<NoteCardEntity>,
    ) {
        wipeVisitItems(); wipeVisits(); wipeReadings(); wipeItems(); wipeCards(); wipeVehicles()
        upsertVehicle(vehicle)
        insertItems(items)
        insertReadings(readings)
        visits.forEach { (v, ticks) -> saveVisit(v, ticks) }
        insertCards(cards)
    }
}
