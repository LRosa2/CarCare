package io.github.lrosa2.carcare.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.lrosa2.carcare.domain.ReminderConfig
import io.github.lrosa2.carcare.domain.StatusSettings
import io.github.lrosa2.carcare.domain.Thresholds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

data class AppSettings(
    val reminderDay: Int = 1,
    val reminderTime: LocalTime = LocalTime.of(19, 0),
    val reminderIntervalMonths: Int = 1,
    /** First month of an every-N-months cycle; set whenever the interval is changed. */
    val reminderAnchor: YearMonth? = null,
    val paceWindowMonths: Int = 6,
    val soonKm: Int = 500,
    val soonMonths: Int = 1,
    val lastExport: LocalDate? = null,
    val lastPrompt: LocalDate? = null,
    val renagSent: Boolean = false,
    val onboarded: Boolean = false,
    /** The day km were last typed in (a reading or a visit), whatever date they were for. */
    val lastKmEntry: LocalDate? = null,
) {
    val reminderConfig get() = ReminderConfig(reminderDay, reminderIntervalMonths, reminderAnchor)
    val statusSettings get() = StatusSettings(paceWindowMonths, Thresholds(soonKm, soonMonths))
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private object K {
        val day = intPreferencesKey("reminderDay")
        val time = stringPreferencesKey("reminderTime")
        val interval = intPreferencesKey("reminderIntervalMonths")
        val anchor = stringPreferencesKey("reminderAnchor")
        val pace = intPreferencesKey("paceWindowMonths")
        val soonKm = intPreferencesKey("soonKm")
        val soonMonths = intPreferencesKey("soonMonths")
        val lastExport = stringPreferencesKey("lastExport")
        val lastPrompt = stringPreferencesKey("lastPrompt")
        val renagSent = booleanPreferencesKey("renagSent")
        val onboarded = booleanPreferencesKey("onboarded")
        val lastKmEntry = stringPreferencesKey("lastKmEntry")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = flow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[K.day] = s.reminderDay
            prefs[K.time] = s.reminderTime.toString()
            prefs[K.interval] = s.reminderIntervalMonths
            prefs.setOrRemove(K.anchor, s.reminderAnchor?.toString())
            prefs[K.pace] = s.paceWindowMonths
            prefs[K.soonKm] = s.soonKm
            prefs[K.soonMonths] = s.soonMonths
            prefs.setOrRemove(K.lastExport, s.lastExport?.toString())
            prefs.setOrRemove(K.lastPrompt, s.lastPrompt?.toString())
            prefs[K.renagSent] = s.renagSent
            prefs[K.onboarded] = s.onboarded
            prefs.setOrRemove(K.lastKmEntry, s.lastKmEntry?.toString())
        }
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.setOrRemove(key: Preferences.Key<String>, value: String?) {
        if (value == null) remove(key) else this[key] = value
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            reminderDay = this[K.day] ?: d.reminderDay,
            reminderTime = this[K.time]?.let(LocalTime::parse) ?: d.reminderTime,
            reminderIntervalMonths = this[K.interval] ?: d.reminderIntervalMonths,
            reminderAnchor = this[K.anchor]?.let(YearMonth::parse),
            paceWindowMonths = this[K.pace] ?: d.paceWindowMonths,
            soonKm = this[K.soonKm] ?: d.soonKm,
            soonMonths = this[K.soonMonths] ?: d.soonMonths,
            lastExport = this[K.lastExport]?.let(LocalDate::parse),
            lastPrompt = this[K.lastPrompt]?.let(LocalDate::parse),
            renagSent = this[K.renagSent] ?: false,
            onboarded = this[K.onboarded] ?: false,
            lastKmEntry = this[K.lastKmEntry]?.let(LocalDate::parse),
        )
    }
}
