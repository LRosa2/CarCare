package io.github.lrosa2.carcare.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.lrosa2.carcare.R
import io.github.lrosa2.carcare.domain.DueStatus
import io.github.lrosa2.carcare.domain.ItemStatus
import io.github.lrosa2.carcare.domain.Overview
import io.github.lrosa2.carcare.ui.Fmt
import io.github.lrosa2.carcare.ui.MainActivity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Two channels, explicit immutable PendingIntents (spec "Reminders"). */
object Notifications {
    const val CHANNEL_KM = "km_reminder"
    const val CHANNEL_DUE = "maintenance_due"
    private const val ID_KM = 1
    private const val ID_DUE = 2

    const val EXTRA_OPEN = "open"
    const val OPEN_ENTER_KM = "enter_km"
    const val OPEN_HOME = "home"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_KM, "Km reminder", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Monthly reminder to enter the odometer" },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DUE, "Maintenance due", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "An item became due soon or overdue" },
        )
    }

    /** False when the permission is missing, the app's notifications are off, or either channel is off. */
    fun canPost(context: Context): Boolean {
        val nmc = NotificationManagerCompat.from(context)
        if (!nmc.areNotificationsEnabled() || !hasPermission(context)) return false
        val nm = context.getSystemService(NotificationManager::class.java)
        return listOf(CHANNEL_KM, CHANNEL_DUE).all { id ->
            nm.getNotificationChannel(id)?.importance != NotificationManager.IMPORTANCE_NONE
        }
    }

    private fun hasPermission(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context, open: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, open)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun dueLines(items: List<ItemStatus>): List<String> = items
        .filter { it.result.status in setOf(DueStatus.OVERDUE, DueStatus.NEVER_DONE, DueStatus.DUE_SOON) }
        .map { "${it.spec.name}: ${Fmt.dueLine(it.result)}" }

    /** Monthly prompt: last km, items due (spec D25) and backup age (spec D22). */
    fun postKmPrompt(context: Context, overview: Overview, lastExport: LocalDate?, today: LocalDate, renag: Boolean) {
        val last = overview.latestReading?.let { "Last entered: ${Fmt.km(it.km)} on ${Fmt.date(it.date)}" } ?: "No km entered yet"
        val backup = lastExport?.let { "Last backup ${ChronoUnit.DAYS.between(it, today)} days ago" } ?: "No backup exported yet"
        val due = dueLines(overview.items)
        val text = buildString {
            appendLine(last)
            if (due.isNotEmpty()) {
                appendLine("Due:")
                due.forEach { appendLine("• $it") }
            }
            append(backup)
        }
        val title = if (renag) "Reminder: enter the km" else "Time to enter the km"
        post(
            context, ID_KM,
            NotificationCompat.Builder(context, CHANNEL_KM)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(if (due.isEmpty()) last else "$last · ${due.size} item(s) due")
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openApp(context, OPEN_ENTER_KM, ID_KM))
                .setAutoCancel(true),
        )
    }

    fun postDue(context: Context, changed: List<ItemStatus>) {
        if (changed.isEmpty()) return
        val lines = changed.map { "${it.spec.name}: ${Fmt.dueLine(it.result)}" }
        post(
            context, ID_DUE,
            NotificationCompat.Builder(context, CHANNEL_DUE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(if (changed.size == 1) "${changed[0].spec.name} is due" else "${changed.size} maintenance items due")
                .setContentText(lines.first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
                .setContentIntent(openApp(context, OPEN_HOME, ID_DUE))
                .setAutoCancel(true),
        )
    }

    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!hasPermission(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call: the in-app banner shows it.
        }
    }
}
