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

package me.kavishdevar.librepods.services

import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Log
import me.kavishdevar.librepods.utils.NotificationDiagnostics

/**
 * Android only lets an app look up other apps' media sessions
 * (MediaSessionManager.getActiveSessions) while it is an enabled notification listener,
 * which the "Open app & play" press-and-hold action needs to start playback in the exact
 * app the user picked.
 *
 * It also feeds [NotificationDiagnostics]: what the system does with this app's own
 * notifications (posted, removed and why, importance changes), and, only when the user copies
 * the diagnostics report, the package, importance and flags of the other active
 * notifications. The content of other apps' notifications is never read.
 */
class MediaSessionListenerService : NotificationListenerService() {
    /** Last recorded ranking of each of this app's notifications, to record only changes. */
    private val lastRanking = HashMap<String, String>()

    override fun onListenerConnected() {
        connected = this
    }

    override fun onListenerDisconnected() {
        if (connected === this) connected = null
    }

    override fun onDestroy() {
        if (connected === this) connected = null
        super.onDestroy()
    }

    // The parameters are nullable on purpose: the platform does not promise otherwise, and a
    // null check failing here would take the whole app, AirPods service included, down.
    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        if (sbn == null || sbn.packageName != packageName) return
        try {
            NotificationDiagnostics.record(
                "system: posted " +
                    NotificationDiagnostics.describe(sbn, rankingOf(sbn.key, rankingMap), packageName)
            )
        } catch (e: Exception) {
            Log.w(TAG, "onNotificationPosted: $e")
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int
    ) {
        if (sbn == null || sbn.packageName != packageName) return
        lastRanking.remove(sbn.key)
        NotificationDiagnostics.record("system: removed id=${sbn.id} reason=$reason")
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        if (rankingMap == null) return
        try {
            val own = "|$packageName|"
            val keys = rankingMap.orderedKeys
            for (key in keys) {
                if (!key.contains(own)) continue
                val ranking = rankingOf(key, rankingMap) ?: continue
                val state = "imp=${ranking.importance} ambient=${ranking.isAmbient} " +
                    "ch=${ranking.channel?.id}(${ranking.channel?.importance}) " +
                    "autogroup=${ranking.overrideGroupKey}"
                if (lastRanking.put(key, state) != state) {
                    NotificationDiagnostics.record(
                        "system: ranking $key $state rank=${ranking.rank}/${keys.size}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "onNotificationRankingUpdate: $e")
        }
    }

    /** Every active notification as one line, in the order the system ranks them. */
    fun describeActiveNotifications(): List<String> {
        val rankingMap = currentRanking
        return activeNotifications.orEmpty()
            .map { it to rankingOf(it.key, rankingMap) }
            .sortedBy { it.second?.rank ?: Int.MAX_VALUE }
            .map { (sbn, ranking) -> NotificationDiagnostics.describe(sbn, ranking, packageName) }
    }

    private fun rankingOf(key: String, rankingMap: RankingMap?): Ranking? {
        val ranking = Ranking()
        return if (rankingMap != null && rankingMap.getRanking(key, ranking)) ranking else null
    }

    companion object {
        private const val TAG = "MediaSessionListener"

        /** The listener while the system has it bound (notification access granted). */
        @Volatile
        var connected: MediaSessionListenerService? = null
            private set
    }
}
