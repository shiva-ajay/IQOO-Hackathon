package com.fixlens.alerts

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.fixlens.R
import com.fixlens.app.MainActivity
import com.fixlens.app.TAG
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Fixy's reminders on the phone itself: an inexact AlarmManager alarm per alert (no exact-alarm permission; a routine
 * check doesn't need the minute), a local notification when it fires, and re-arming after a reboot. No network: the
 * "push" is posted by the app, not sent from a server.
 */
object AlertScheduler {
    const val ACTION_FIRE = "com.fixlens.alerts.FIRE"
    /** A notification tap: open the app on its alert ([EXTRA_START]: start the check right away). */
    const val ACTION_OPEN = "com.fixlens.alerts.OPEN"
    const val EXTRA_ALERT = "alert"
    const val EXTRA_START = "start"
    private const val CHANNEL = "reminders"
    private const val SHORT_LEAD_MS = 10_000L
    /** How late a reminder may come: a routine check doesn't need the minute. */
    private const val WINDOW_MS = 60 * 60 * 1000L

    private val _changes = MutableStateFlow(0)
    /** Bumped whenever an alert is delivered in the background, so an open app re-reads the list. */
    val changes: StateFlow<Int> = _changes

    fun store(context: Context) = AlertStore(File(context.applicationContext.filesDir, "alerts.json"))

    /** Arms (or re-arms) [alert]'s alarm. A time already past fires right away. */
    fun arm(context: Context, alert: Alert) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = fireIntent(context, alert.id)
        // A plain inexact alarm may slip by up to 3/4 of its lead time (days, for a weekly check), so a set window
        // bounds it. Under 10 s ahead (a test, or overdue after a reboot) Android delivers it on time anyway.
        if (alert.dueAt - System.currentTimeMillis() < SHORT_LEAD_MS) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alert.dueAt, pending)
        } else {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, alert.dueAt, WINDOW_MS, pending)
        }
        Log.i(TAG, "Alert ${alert.id} (${alert.entryId}) armed for ${alert.dueAt} (in ${(alert.dueAt - System.currentTimeMillis()) / 1000} s)")
    }

    fun disarm(context: Context, id: String) {
        context.getSystemService(AlarmManager::class.java)?.cancel(fireIntent(context, id))
        context.getSystemService(NotificationManager::class.java)?.cancel(id.hashCode())
    }

    /** After a reboot or an app update the alarms are gone: arm every alert still waiting. */
    fun rearmAll(context: Context) {
        val waiting = store(context).all().filterNot { it.delivered }
        waiting.forEach { arm(context, it) }
        Log.i(TAG, "Alerts re-armed: ${waiting.size}")
    }

    /** The alarm went off: post the notification and mark the alert due. */
    fun deliver(context: Context, id: String) {
        var alert: Alert? = null
        store(context).edit { list ->
            list.map { if (it.id == id) it.copy(delivered = true).also { a -> alert = a } else it }
        }
        val due = alert ?: run {
            Log.w(TAG, "Alert $id fired but is gone (dismissed or replaced)")
            return
        }
        notify(context, due)
        _changes.value++
    }

    fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true
    }

    private fun notify(context: Context, alert: Alert) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Repair reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Routine checks Fixy scheduled after a repair"
            },
        )
        val start = Notification.Action.Builder(null, "Start the check", openIntent(context, alert.id, start = true)).build()
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fixy)
            .setColor(0xFFFF9F1C.toInt())
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(Notification.BigTextStyle().bigText(alert.body))
            .setSubText("Fixy")
            .setContentIntent(openIntent(context, alert.id, start = false))
            .setAutoCancel(true)
            .addAction(start)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        if (!notificationsAllowed(context)) Log.w(TAG, "Alert ${alert.id}: notifications are off, it shows in the app only")
        runCatching { manager.notify(alert.id.hashCode(), notification) }
            .onFailure { Log.e(TAG, "Alert ${alert.id}: notification failed", it) }
        Log.i(TAG, "Alert ${alert.id} delivered: \"${alert.title}\"")
    }

    private fun fireIntent(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, AlertReceiver::class.java).setAction(ACTION_FIRE).setData(Uri.parse("fixlens://alert/$id")).putExtra(EXTRA_ALERT, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openIntent(context: Context, id: String, start: Boolean): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN)
            // Distinct data per alert and action, so the two PendingIntents don't replace each other.
            .setData(Uri.parse("fixlens://alert/$id/${if (start) "start" else "open"}"))
            .putExtra(EXTRA_ALERT, id)
            .putExtra(EXTRA_START, start)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** An alert's alarm, or the phone finished booting / the app was updated (alarms are cleared then). */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlertScheduler.ACTION_FIRE -> intent.getStringExtra(AlertScheduler.EXTRA_ALERT)?.let { AlertScheduler.deliver(context, it) }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> AlertScheduler.rearmAll(context)
        }
    }
}
