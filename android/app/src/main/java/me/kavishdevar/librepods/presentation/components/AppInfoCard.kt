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

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.kavishdevar.librepods.BuildConfig
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.utils.NotificationDiagnostics

@Composable
fun AppInfoCard(
    navigateToReleaseNotesScreen: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    StyledList(title = stringResource(R.string.about)) {
        StyledListItem(
            name = stringResource(R.string.version),
            description = BuildConfig.VERSION_NAME,
            onClick = navigateToReleaseNotesScreen
        )

        if (BuildConfig.CUSTOM_BUILD.isNotEmpty()) {
            StyledListItem(
                name = stringResource(R.string.custom_build),
                description = BuildConfig.CUSTOM_BUILD,
            )
            // What happened to the "AirPods connected" notification, to paste into a report.
            // The system shows its own "copied" confirmation.
            StyledListItem(
                name = stringResource(R.string.notification_diagnostics),
                description = stringResource(R.string.notification_diagnostics_copy),
                onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                        ClipData.newPlainText(
                            "LibrePods notification diagnostics",
                            NotificationDiagnostics.report(context)
                        )
                    )
                }
            )
        }

        StyledListItem(
            name = stringResource(R.string.version_code),
            description = BuildConfig.VERSION_CODE.toString(),
        )

        StyledListItem(
            name = stringResource(R.string.flavor),
            description = BuildConfig.FLAVOR,
        )

        StyledListItem(
            name = stringResource(R.string.build_type),
            description = BuildConfig.BUILD_TYPE,
        )
    }
}
