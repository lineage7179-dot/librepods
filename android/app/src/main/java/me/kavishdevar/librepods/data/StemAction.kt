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

package me.kavishdevar.librepods.data

import me.kavishdevar.librepods.bluetooth.AACPManager

enum class StemAction {
    PLAY_PAUSE,
    PREVIOUS_TRACK,
    NEXT_TRACK,
    DIGITAL_ASSISTANT,
    CYCLE_NOISE_CONTROL_MODES,
    LAUNCH_APP_AND_PLAY,

    /** Double press: next track while something is playing, otherwise open the chosen app and play. */
    NEXT_TRACK_OR_LAUNCH_APP;
    companion object {
        fun fromString(action: String): StemAction? {
            return entries.find { it.name == action }
        }

        /** SharedPreferences key holding the package opened by [LAUNCH_APP_AND_PLAY] for a bud ("left"/"right"). */
        fun launchAppPackageKey(side: String): String =
            if (side.lowercase() == "left") "left_long_press_app_package" else "right_long_press_app_package"

        /** SharedPreferences key holding the package opened by [NEXT_TRACK_OR_LAUNCH_APP] (both buds). */
        const val DOUBLE_PRESS_APP_PACKAGE_KEY = "double_press_app_package"

        const val LEFT_DOUBLE_PRESS_ACTION_KEY = "left_double_press_action"
        const val RIGHT_DOUBLE_PRESS_ACTION_KEY = "right_double_press_action"
        val defaultActions: Map<AACPManager.Companion.StemPressType, StemAction> = mapOf(
            AACPManager.Companion.StemPressType.SINGLE_PRESS to PLAY_PAUSE,
            AACPManager.Companion.StemPressType.DOUBLE_PRESS to NEXT_TRACK,
            AACPManager.Companion.StemPressType.TRIPLE_PRESS to PREVIOUS_TRACK,
            AACPManager.Companion.StemPressType.LONG_PRESS to CYCLE_NOISE_CONTROL_MODES,
        )
    }
}
