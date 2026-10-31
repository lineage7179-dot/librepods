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

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import me.kavishdevar.librepods.services.MediaSessionListenerService
import android.media.session.MediaController as SessionMediaController

/**
 * Opens an app the user picked and starts playback in it (the "Open app & play"
 * press-and-hold action).
 *
 * Playback is aimed at the picked app's own media session when LibrePods has notification
 * access, which is the only way a regular app can reach another app's session. Without it,
 * a play key is sent straight to the app's media button receiver, and as a last resort to
 * whichever session currently owns the media keys.
 */
object AppLaunchPlayer {
    private const val TAG = "AppLaunchPlayer"

    /** Times (ms after the app was opened) at which we look for its media session. */
    private val ATTEMPT_TIMES_MS = longArrayOf(500, 1000, 1700, 2500, 3500, 5000, 7000, 9500)

    /** If no session has shown up by this time, also try the app's media button receiver. */
    private const val RECEIVER_FALLBACK_AFTER_MS = 3500L

    /** Without notification access we can only send a key once the app had time to start. */
    private const val NO_ACCESS_DELAY_MS = 1500L

    private const val MAX_PLAY_COMMANDS = 3

    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0

    fun launchAndPlay(context: Context, packageName: String) {
        // Stem presses arrive on the Bluetooth reader thread; keep all bookkeeping on the main thread.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { launchAndPlay(context, packageName) }
            return
        }
        val appContext = context.applicationContext
        // A new press cancels whatever the previous press was still waiting for.
        handler.removeCallbacksAndMessages(null)
        val run = ++generation

        if (!launchApp(context, packageName)) {
            Log.w(TAG, "Could not open $packageName, trying to start playback anyway")
        }

        if (hasNotificationAccess(appContext)) {
            scheduleAttempt(appContext, packageName, run, attempt = 0, playCommandsSent = 0, receiverTried = false)
        } else {
            Log.d(TAG, "No notification access, using media buttons for $packageName")
            handler.postDelayed({
                if (run == generation) sendPlayKey(appContext, packageName, allowGenericFallback = true)
            }, NO_ACCESS_DELAY_MS)
        }
    }

    fun hasNotificationAccess(context: Context): Boolean {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return false
        return notificationManager.isNotificationListenerAccessGranted(
            ComponentName(context, MediaSessionListenerService::class.java)
        )
    }

    private fun launchApp(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Log.w(TAG, "$packageName has no launcher activity or is not installed")
            return false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "\"Display over other apps\" is off, Android may block opening $packageName from the background")
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open $packageName", e)
            false
        }
    }

    private fun scheduleAttempt(
        context: Context,
        packageName: String,
        run: Int,
        attempt: Int,
        playCommandsSent: Int,
        receiverTried: Boolean
    ) {
        if (attempt >= ATTEMPT_TIMES_MS.size) {
            if (playCommandsSent == 0 && !receiverTried) {
                Log.d(TAG, "$packageName never published a media session, trying its media button receiver")
                sendPlayKey(context, packageName, allowGenericFallback = false)
            } else {
                Log.d(TAG, "Stopped waiting for $packageName to report playback")
            }
            return
        }

        val previous = if (attempt == 0) 0L else ATTEMPT_TIMES_MS[attempt - 1]
        val delay = ATTEMPT_TIMES_MS[attempt] - previous

        handler.postDelayed({
            if (run != generation) return@postDelayed

            var sent = playCommandsSent
            var triedReceiver = receiverTried
            val session = findSession(context, packageName)

            if (session != null) {
                val state = session.playbackState?.state
                if (state == PlaybackState.STATE_PLAYING) {
                    Log.d(TAG, "$packageName is playing")
                    return@postDelayed
                }
                val busy = state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_CONNECTING
                if (!busy && sent < MAX_PLAY_COMMANDS) {
                    Log.d(TAG, "Sending play to $packageName (state: $state)")
                    session.transportControls.play()
                    sent++
                }
            } else if (!triedReceiver && sent == 0 && ATTEMPT_TIMES_MS[attempt] >= RECEIVER_FALLBACK_AFTER_MS) {
                Log.d(TAG, "No media session from $packageName yet, trying its media button receiver")
                triedReceiver = sendPlayKey(context, packageName, allowGenericFallback = false)
            }

            scheduleAttempt(context, packageName, run, attempt + 1, sent, triedReceiver)
        }, delay)
    }

    private fun findSession(context: Context, packageName: String): SessionMediaController? {
        val sessionManager = context.getSystemService(MediaSessionManager::class.java) ?: return null
        return try {
            sessionManager
                .getActiveSessions(ComponentName(context, MediaSessionListenerService::class.java))
                .firstOrNull { it.packageName == packageName }
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification access is not available", e)
            null
        }
    }

    /**
     * Sends a play key to [packageName]'s media button receiver. When the app has none and
     * [allowGenericFallback] is set, the key goes to whichever session owns the media keys.
     * Returns true if a key was sent.
     */
    private fun sendPlayKey(context: Context, packageName: String, allowGenericFallback: Boolean): Boolean {
        val receiver = context.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(packageName),
            PackageManager.ResolveInfoFlags.of(0)
        ).firstOrNull()?.activityInfo

        if (receiver != null) {
            val target = ComponentName(receiver.packageName, receiver.name)
            Log.d(TAG, "Sending play key to $target")
            for (keyAction in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                val now = SystemClock.uptimeMillis()
                val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                intent.setComponent(target)
                intent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(now, now, keyAction, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
                context.sendBroadcast(intent)
            }
            return true
        }

        if (!allowGenericFallback) {
            Log.d(TAG, "$packageName has no media button receiver")
            return false
        }

        val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
        Log.d(TAG, "$packageName has no media button receiver, sending play to the current media session")
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
        return true
    }
}
