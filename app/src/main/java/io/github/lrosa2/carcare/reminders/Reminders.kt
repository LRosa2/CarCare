package io.github.lrosa2.carcare.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.lrosa2.carcare.CarCareApp
import io.github.lrosa2.carcare.domain.PromptDecision
import io.github.lrosa2.carcare.domain.ReminderPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * One inexact daily alarm at the reminder time runs every check (spec D26). No exact-alarm
 * permission: setAndAllowWhileIdle may fire a few minutes late in Doze, which is fine here.
 */
object ReminderScheduler {
    private const val ACTION_DAILY = "io.github.lrosa2.carcare.DAILY_CHECK"

    fun schedule(context: Context, time: LocalTime) {
        val next = ReminderPolicy.nextAlarm(ZonedDateTime.now(), time)
        val am = context.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, DailyCheckReceiver::class.java).setAction(ACTION_DAILY),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Reads the time from settings; safe to call any number of times (replaces the pending alarm). */
    suspend fun reschedule(context: Context) {
        val app = context.applicationContext as CarCareApp
        schedule(context, app.settingsStore.current().reminderTime)
    }
}

/** The daily check (spec "Reminders"): monthly prompt, re-nag, status-change notifications. */
object DailyCheck {
    suspend fun run(context: Context) {
        val app = context.applicationContext as CarCareApp
        val repo = app.repository
        val s = repo.settings.current()
        if (!s.onboarded) return
        val today = repo.today()
        val (overview, items) = repo.overviewOnce()

        when (val d = ReminderPolicy.promptDecision(today, s.reminderConfig, s.lastPrompt, s.lastKmEntry)) {
            is PromptDecision.Prompt -> {
                Notifications.postKmPrompt(context, overview, s.lastExport, today, renag = false)
                repo.settings.update { it.copy(lastPrompt = d.promptDate, renagSent = false) }
            }
            is PromptDecision.SkipRecentEntry ->
                repo.settings.update { it.copy(lastPrompt = d.promptDate, renagSent = true) }
            PromptDecision.None ->
                if (ReminderPolicy.shouldRenag(today, s.lastPrompt, s.renagSent, s.lastKmEntry)) {
                    Notifications.postKmPrompt(context, overview, s.lastExport, today, renag = true)
                    repo.settings.update { it.copy(renagSent = true) }
                }
        }

        val now = overview.items.associate { it.spec.id to it.result.status }
        val changed = ReminderPolicy.newlyDue(now, items.associate { it.id to it.lastNotifiedStatus }).toSet()
        Notifications.postDue(context, overview.items.filter { it.spec.id in changed })
        items.forEach { item ->
            val status = now[item.id]?.name
            if (item.lastNotifiedStatus != status) repo.setNotified(item.id, status)
        }
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Fired by the daily alarm; not exported. */
class DailyCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        try {
            DailyCheck.run(context)
        } finally {
            ReminderScheduler.reschedule(context)
        }
    }
}

/** Alarms are lost on reboot and must follow clock changes: reschedule on these system events. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> ReminderScheduler.reschedule(context)
        }
    }
}
