/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.utils

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import me.kavishdevar.librepods.BuildConfig
import me.kavishdevar.librepods.services.MediaSessionListenerService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What happens to the "AirPods connected" notification, to find out why One UI moves it into
 * its collapsed "more notifications" group. The app settings (About) copy [report] to the
 * clipboard.
 *
 * The report lists every active notification with its package, importance and flags, so that
 * the ones One UI filters can be compared with the ones it leaves alone. Titles and texts are
 * included for this app's notifications only.
 */
object NotificationDiagnostics {
    private const val MAX_EVENTS = 120
    private val events = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun record(event: String) {
        synchronized(events) {
            if (events.size >= MAX_EVENTS) events.removeFirst()
            events.addLast("${timeFormat.format(Date())} $event")
        }
    }

    fun report(context: Context): String = buildString {
        appendLine("LibrePods notification diagnostics")
        appendLine(
            "build=${BuildConfig.CUSTOM_BUILD} android=${Build.VERSION.RELEASE} " +
                "sdk=${Build.VERSION.SDK_INT} device=${Build.MODEL} display=${Build.DISPLAY}"
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        appendLine("notifications enabled=${manager.areNotificationsEnabled()}")
        manager.notificationChannels.forEach {
            appendLine("channel ${it.id} importance=${it.importance}")
        }
        val listener = MediaSessionListenerService.connected
        appendLine(
            if (listener != null) "-- active notifications of all apps, in the system's order --"
            else "-- active notifications of this app only (no notification access) --"
        )
        val lines = try {
            listener?.describeActiveNotifications()
                ?: manager.activeNotifications.map { describe(it, null, context.packageName) }
        } catch (e: Exception) {
            listOf("failed: $e")
        }
        lines.forEach { appendLine(it) }
        appendLine("-- events --")
        synchronized(events) { events.forEach { appendLine(it) } }
    }

    /** One line about a notification; [ranking] is what the system made of it, if known. */
    @Suppress("DEPRECATION")
    fun describe(
        sbn: StatusBarNotification,
        ranking: NotificationListenerService.Ranking?,
        ownPackage: String
    ): String {
        val notification = sbn.notification
        val own = sbn.packageName == ownPackage
        val flags = listOfNotNull(
            "ongoing".takeIf { (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 },
            "fgs".takeIf { (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0 },
            "noclear".takeIf { (notification.flags and Notification.FLAG_NO_CLEAR) != 0 },
            "summary".takeIf { (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0 },
            "alertonce".takeIf { (notification.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0 },
            "custom".takeIf { notification.contentView != null || notification.bigContentView != null }
        ).joinToString(",")
        val now = System.currentTimeMillis()
        return buildString {
            append(if (own) "* " else "  ")
            if (ranking != null) append("#${ranking.rank} ")
            append("${sbn.packageName} id=${sbn.id}")
            if (ranking != null) {
                append(" imp=${ranking.importance} ambient=${ranking.isAmbient}")
                append(" ch=${ranking.channel?.id}(${ranking.channel?.importance})")
            } else {
                append(" ch=${notification.channelId}")
            }
            append(" cat=${notification.category}")
            append(" flags=[$flags]/0x${Integer.toHexString(notification.flags)}")
            append(" posted=${(now - sbn.postTime) / 1000}s when=${(now - notification.`when`) / 1000}s")
            notification.group?.let { append(" group=$it") }
            sbn.overrideGroupKey?.let { append(" autogroup=$it") }
            if (own) {
                ranking?.importanceExplanation?.let { append(" why=\"$it\"") }
                append(" title=\"${notification.extras.getCharSequence(Notification.EXTRA_TITLE)}\"")
                append(" text=\"${notification.extras.getCharSequence(Notification.EXTRA_TEXT)}\"")
                append(" extras=${notification.extras.keySet().sorted()}")
            }
        }
    }
}
