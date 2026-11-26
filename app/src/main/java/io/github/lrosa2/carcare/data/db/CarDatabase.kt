package io.github.lrosa2.carcare.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [VehicleEntity::class, ItemEntity::class, ReadingEntity::class, VisitEntity::class, VisitItemEntity::class, NoteCardEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CarDatabase : RoomDatabase() {
    abstract fun dao(): CarDao

    companion object {
        /** No destructive migration, ever: schema changes ship with a migration (spec "Data model"). */
        fun build(context: Context): CarDatabase =
            Room.databaseBuilder(context, CarDatabase::class.java, "carcare.db").build()
    }
}
