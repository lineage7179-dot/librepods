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

@file:OptIn(ExperimentalMaterial3Api::class)

package me.kavishdevar.librepods.presentation.components

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.StemAction
import me.kavishdevar.librepods.services.MediaSessionListenerService
import me.kavishdevar.librepods.utils.AppLaunchPlayer
import java.text.Collator

private const val TAG = "LaunchAppSettings"
private const val PREFS_NAME = "settings"

/** An app that can be picked for the "Open app & play" press-and-hold action. */
data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isMusicApp: Boolean = false
)

/**
 * Settings shown when an "open app & play" action is selected: which app to open (stored
 * under [prefKey]), and the two permissions that make it work reliably.
 */
@Composable
fun LaunchAppSettings(prefKey: String, description: String) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val selectedPackage = rememberSettingsString(prefKey)
    val selectedApp = remember(selectedPackage) { selectedPackage?.let { loadApp(context, it) } }
    var showPicker by remember { mutableStateOf(false) }

    // Choosing this action without an app yet: ask for the app right away.
    LaunchedEffect(Unit) {
        if (selectedPackage == null) showPicker = true
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasNotificationAccess by remember { mutableStateOf(AppLaunchPlayer.hasNotificationAccess(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawOverlays = Settings.canDrawOverlays(context)
                hasNotificationAccess = AppLaunchPlayer.hasNotificationAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selectedIcon = selectedApp?.icon
    val selectedName = selectedApp?.label ?: selectedPackage ?: stringResource(R.string.choose_app)

    StyledList(
        title = stringResource(R.string.app_to_launch),
        description = description
    ) {
        StyledListItem(
            name = selectedName,
            description = selectedPackage,
            orientation = ListItemOrientation.Vertical,
            onClick = { showPicker = true },
            leadingContent = if (selectedIcon != null) {
                @Composable { AppIcon(selectedIcon) }
            } else null
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    StyledList(
        title = stringResource(R.string.required_permissions),
        description = stringResource(R.string.launch_app_permissions_description)
    ) {
        StyledListItem(
            name = stringResource(R.string.permission_overlay),
            description = stringResource(R.string.permission_overlay_description),
            orientation = ListItemOrientation.Vertical,
            onClick = { openOverlaySettings(context) },
            trailingContent = { PermissionStatus(canDrawOverlays) }
        )
        StyledListItem(
            name = stringResource(R.string.permission_notification_access),
            description = stringResource(R.string.permission_notification_access_description),
            orientation = ListItemOrientation.Vertical,
            onClick = { openNotificationAccessSettings(context) },
            trailingContent = { PermissionStatus(hasNotificationAccess) }
        )
    }

    if (showPicker) {
        AppPickerSheet(
            selectedPackage = selectedPackage,
            onDismiss = { showPicker = false },
            onAppSelected = { packageName ->
                prefs.edit { putString(prefKey, packageName) }
            }
        )
    }
}

/** The package picked for the long press of [side] ("left"/"right"). */
@Composable
fun rememberLongPressAppPackage(side: String): String? =
    rememberSettingsString(StemAction.launchAppPackageKey(side))

/** A string from the app's "settings" SharedPreferences, kept in sync while it changes. */
@Composable
fun rememberSettingsString(key: String): String? {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var value by remember(key) { mutableStateOf(prefs.getString(key, null)) }

    DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, changedKey ->
            if (changedKey == key) value = changedPrefs.getString(key, null)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}

/** Display name of an installed app, or null if it is not installed anymore. */
fun appLabelOrNull(context: Context, packageName: String): String? {
    return try {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            .loadLabel(pm)
            .toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap) {
    Image(
        bitmap = icon,
        contentDescription = null,
        modifier = Modifier.size(36.dp)
    )
}

@Composable
private fun PermissionStatus(granted: Boolean) {
    Text(
        text = stringResource(if (granted) R.string.permission_allowed else R.string.permission_tap_to_allow),
        style = MaterialTheme.typography.bodySmall,
        color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(start = 8.dp)
    )
}

@Composable
private fun AppPickerSheet(
    selectedPackage: String?,
    onDismiss: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.choose_app),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_apps)) },
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            val allApps = apps
            if (allApps == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.loading_apps),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                val filtered = remember(allApps, query) { allApps.filter { it.matches(query) } }
                val musicApps = filtered.filter { it.isMusicApp }
                val otherApps = filtered.filterNot { it.isMusicApp }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 640.dp)
                ) {
                    if (filtered.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = stringResource(R.string.no_apps_found),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 24.dp, horizontal = 4.dp)
                            )
                        }
                    }
                    if (musicApps.isNotEmpty()) {
                        item(key = "header_music") { PickerSectionHeader(stringResource(R.string.music_apps)) }
                        items(musicApps, key = { "music_" + it.packageName }) { app ->
                            AppRow(app = app, selected = app.packageName == selectedPackage) {
                                onAppSelected(app.packageName)
                                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                            }
                        }
                    }
                    if (otherApps.isNotEmpty()) {
                        item(key = "header_other") { PickerSectionHeader(stringResource(R.string.other_apps)) }
                        items(otherApps, key = { "other_" + it.packageName }) { app ->
                            AppRow(app = app, selected = app.packageName == selectedPackage) {
                                onAppSelected(app.packageName)
                                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                            }
                        }
                    }
                    item(key = "bottom_space") { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PickerSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun AppRow(app: LaunchableApp, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = app.icon
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
        } else {
            Spacer(modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun LaunchableApp.matches(query: String): Boolean {
    val q = query.trim()
    return q.isEmpty() || label.contains(q, ignoreCase = true) || packageName.contains(q, ignoreCase = true)
}

private fun iconSizePx(context: Context): Int =
    (40 * context.resources.displayMetrics.density).toInt().coerceIn(48, 128)

private fun loadApp(context: Context, packageName: String): LaunchableApp? {
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        val size = iconSizePx(context)
        LaunchableApp(
            packageName = packageName,
            label = info.loadLabel(pm).toString(),
            icon = runCatching { info.loadIcon(pm).toBitmap(size, size).asImageBitmap() }.getOrNull()
        )
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}

/** Every app with a launcher icon; apps that expose a media player are flagged as music apps. */
private fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val pm = context.packageManager
    val flags = PackageManager.ResolveInfoFlags.of(0)
    val size = iconSizePx(context)

    val musicPackages = HashSet<String>()
    runCatching {
        pm.queryIntentServices(Intent("android.media.browse.MediaBrowserService"), flags)
            .forEach { musicPackages.add(it.serviceInfo.packageName) }
        pm.queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON), flags)
            .forEach { musicPackages.add(it.activityInfo.packageName) }
    }.onFailure { Log.w(TAG, "Could not look up music apps", it) }

    val collator = Collator.getInstance()
    return pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        flags
    )
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != context.packageName }
        .map { info ->
            val packageName = info.activityInfo.packageName
            LaunchableApp(
                packageName = packageName,
                label = info.loadLabel(pm).toString(),
                icon = runCatching { info.loadIcon(pm).toBitmap(size, size).asImageBitmap() }.getOrNull(),
                isMusicApp = packageName in musicPackages
            )
        }
        .sortedWith(Comparator { a, b -> collator.compare(a.label, b.label) })
}

private fun openOverlaySettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:${context.packageName}".toUri()
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { Log.w(TAG, "Could not open overlay settings", it) }
}

private fun openNotificationAccessSettings(context: Context) {
    val component = ComponentName(context, MediaSessionListenerService::class.java)
    val detailIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val listIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(detailIntent)
    } catch (e: Exception) {
        runCatching { context.startActivity(listIntent) }
            .onFailure { Log.w(TAG, "Could not open notification access settings", it) }
    }
}
