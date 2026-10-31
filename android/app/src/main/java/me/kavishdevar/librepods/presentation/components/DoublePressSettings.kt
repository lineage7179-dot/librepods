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

package me.kavishdevar.librepods.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.StemAction

/** True when double press is set to "next track, or open the chosen app when idle". */
@Composable
fun rememberDoublePressLaunchEnabled(): Boolean {
    val left = rememberSettingsString(StemAction.LEFT_DOUBLE_PRESS_ACTION_KEY)
    val right = rememberSettingsString(StemAction.RIGHT_DOUBLE_PRESS_ACTION_KEY)
    val launch = StemAction.NEXT_TRACK_OR_LAUNCH_APP.name
    return left == launch || right == launch
}

/** "Double-Press AirPods" row on the main settings screen. */
@Composable
fun DoublePressSettings(navigateToDoublePress: () -> Unit) {
    val context = LocalContext.current
    val launchEnabled = rememberDoublePressLaunchEnabled()
    val appPackage = rememberSettingsString(StemAction.DOUBLE_PRESS_APP_PACKAGE_KEY)
    val appLabel = remember(appPackage) { appPackage?.let { appLabelOrNull(context, it) } }

    val summary = when {
        !launchEnabled -> stringResource(R.string.double_press_next_track)
        appLabel != null -> stringResource(R.string.double_press_launch_summary, appLabel)
        else -> stringResource(R.string.double_press_launch_app_when_idle)
    }

    StyledList(
        title = stringResource(R.string.double_press_airpods)
    ) {
        StyledListItem(
            name = stringResource(R.string.double_press_both),
            description = summary,
            onClick = navigateToDoublePress
        )
    }
}
