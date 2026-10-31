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

package me.kavishdevar.librepods.presentation.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.StemAction
import me.kavishdevar.librepods.presentation.components.LaunchAppSettings
import me.kavishdevar.librepods.presentation.components.StyledButton
import me.kavishdevar.librepods.presentation.components.StyledList
import me.kavishdevar.librepods.presentation.components.StyledListItem
import me.kavishdevar.librepods.presentation.components.rememberDoublePressLaunchEnabled
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import me.kavishdevar.librepods.presentation.viewmodel.AirPodsViewModel

/**
 * Double press on either AirPod: next track (default), or next track while something is
 * playing and "open the chosen app and play" while idle.
 */
@Composable
fun DoublePressScreen(viewModel: AirPodsViewModel, navigateToPurchase: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val launchEnabled = rememberDoublePressLaunchEnabled()

    // Both buds always get the same double-press action; the service picks it up from prefs.
    val setDoublePressAction: (StemAction) -> Unit = { action ->
        prefs.edit {
            putString(StemAction.LEFT_DOUBLE_PRESS_ACTION_KEY, action.name)
            putString(StemAction.RIGHT_DOUBLE_PRESS_ACTION_KEY, action.name)
        }
    }

    val m3eEnabled = LocalDesignSystem.current == DesignSystem.Material
    val topPadding = if (m3eEnabled) 0.dp else WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPadding = if (m3eEnabled) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(scrollState)
            .padding(top = 8.dp)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(topPadding))

        StyledList(
            title = stringResource(R.string.double_press_both),
            description = stringResource(R.string.double_press_description)
        ) {
            StyledListItem(
                name = stringResource(R.string.double_press_next_track),
                selected = !launchEnabled,
                onClick = { setDoublePressAction(StemAction.NEXT_TRACK) }
            )

            StyledListItem(
                name = stringResource(R.string.double_press_launch_app_when_idle),
                selected = launchEnabled,
                onClick = { setDoublePressAction(StemAction.NEXT_TRACK_OR_LAUNCH_APP) },
                enabled = state.isPremium
            )
        }

        if (!state.isPremium) {
            Spacer(modifier = Modifier.height(24.dp))
            StyledButton(
                onClick = navigateToPurchase,
                backdrop = rememberLayerBackdrop(),
                modifier = Modifier.fillMaxWidth(),
                maxScale = 0.05f,
                surfaceColor = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    stringResource(R.string.unlock_advanced_features),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (launchEnabled) {
            Spacer(modifier = Modifier.height(32.dp))
            LaunchAppSettings(
                prefKey = StemAction.DOUBLE_PRESS_APP_PACKAGE_KEY,
                description = stringResource(R.string.double_press_app_description)
            )
        }

        Spacer(modifier = Modifier.height(bottomPadding))
    }
}
