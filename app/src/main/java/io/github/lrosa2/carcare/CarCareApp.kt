package io.github.lrosa2.carcare

import android.app.Application
import io.github.lrosa2.carcare.data.CarRepository
import io.github.lrosa2.carcare.data.SettingsStore
import io.github.lrosa2.carcare.data.db.CarDatabase
import io.github.lrosa2.carcare.reminders.Notifications

/** Manual dependency wiring: one database, one settings store, one repository. */
class CarCareApp : Application() {
    val database by lazy { CarDatabase.build(this) }
    val settingsStore by lazy { SettingsStore(this) }
    val repository by lazy { CarRepository(database.dao(), settingsStore) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
