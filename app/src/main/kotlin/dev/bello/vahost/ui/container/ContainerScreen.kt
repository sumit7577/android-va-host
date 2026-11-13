package dev.bello.vahost.ui.container

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bello.vahost.ui.container.viewModel.ContainerEvent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import dev.bello.vahost.probe.GuestProcess
import dev.bello.vahost.probe.LoadedArtifact
import dev.bello.vahost.ui.container.viewModel.ContainerState
import dev.bello.vahost.ui.container.viewModel.ContainerTab
import dev.bello.vahost.ui.container.viewModel.ContainerViewModel
import dev.bello.vahost.ui.container.viewModel.GuestApp
import dev.bello.vahost.ui.container.viewModel.ProgressBarState

@Composable
fun ContainerScreen(viewModel: ContainerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ContainerScreen(state = state, onEvent = viewModel::onTriggerEvent)
}

@Composable
private fun ContainerScreen(
    state: ContainerState,
    onEvent: (ContainerEvent) -> Unit,
) {
    MaterialTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    ContainerTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = state.selectedTab == tab,
                            onClick = { onEvent(ContainerEvent.SelectTab(tab)) },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        ContainerTab.Home -> Icons.Filled.Home
                                        ContainerTab.Process -> Icons.Filled.List
                                        ContainerTab.Settings -> Icons.Filled.Settings
                                    },
                                    contentDescription = tab.name,
                                )
                            },
                            label = { Text(tab.name) },
                        )
                    }
                }
            },
        ) { padding ->
            Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (state.selectedTab) {
                    ContainerTab.Home -> HomeTab(state, onEvent)
                    ContainerTab.Process -> ProcessTab(state, onEvent)
                    ContainerTab.Settings -> SettingsTab(state, onEvent)
                }
            }
        }
    }
}

@Composable
private fun HomeTab(state: ContainerState, onEvent: (ContainerEvent) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { EngineStatus(state) }
        item {
            OutlinedButton(
                onClick = { onEvent(ContainerEvent.RunEmbeddedGuest) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Run embedded test guest") }
        }
        item {
            Text(
                text = "Installed apps \u2014 tap to run inside the container",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (state.progressBarState == ProgressBarState.Loading) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }
        items(state.apps, key = { it.packageName }) { app ->
            AppRow(
                app = app,
                isRunning = state.runningPackage == app.packageName,
                isInstalled = app.packageName in state.installedInEngine,
                onClick = { onEvent(ContainerEvent.RunApp(app.packageName)) },
            )
        }
        if (state.log.isNotEmpty()) {
            item { LogHeader(onClear = { onEvent(ContainerEvent.ClearLog) }) }
            items(state.log) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun EngineStatus(state: ContainerState) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text("Container host", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "startup=${state.isStartup}  engine=${state.engineLaunched}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "installed in engine: " +
                state.installedInEngine.joinToString().ifEmpty { "none" },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Icons are loaded per row rather than with the app list: there are a couple of
 * hundred packages and LazyColumn only composes the visible ones, so this keeps
 * all but a screenful of drawables out of memory.
 */
@Composable
private fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(ICON_PX, ICON_PX)
                    .asImageBitmap()
            }.getOrNull()
        }
    }.value
}

private const val ICON_PX = 128

@Composable
private fun AppRow(
    app: GuestApp,
    isRunning: Boolean,
    isInstalled: Boolean,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, enabled = !isRunning, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val icon = rememberAppIcon(app.packageName)
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                val tags = buildList {
                    // The engine installs a single base APK, so splits are the
                    // most likely reason a real app misbehaves once it starts.
                    if (app.splitCount > 0) add("${app.splitCount} splits")
                    if (app.isSystem) add("system")
                    if (isInstalled) add("in engine")
                }
                if (tags.isNotEmpty()) {
                    Text(
                        text = tags.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (isRunning) CircularProgressIndicator(modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LogHeader(onClear: () -> Unit) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Log", style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = onClear) { Text("Clear") }
    }
}

@Composable
private fun ProcessTab(state: ContainerState, onEvent: (ContainerEvent) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text("Guest processes", style = MaterialTheme.typography.headlineSmall)
                Text(
                    // Read from /proc/<pid>/maps rather than hooking the loader,
                    // so dlopen() from native code shows up too.
                    text = "what each guest process has mapped, live from /proc",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (state.processes.isEmpty()) {
            item {
                Text(
                    text = "No guest process running. Launch an app from Home.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.showSystemLibs) "all mapped files" else "guest files only",
                    style = MaterialTheme.typography.labelMedium,
                )
                OutlinedButton(onClick = { onEvent(ContainerEvent.ToggleSystemLibs) }) {
                    Text(if (state.showSystemLibs) "Hide system" else "Show system")
                }
            }
        }

        state.processes.forEach { process ->
            // The guest's own dex and .so are the point; system libs are noise
            // unless asked for.
            val shown = if (state.showSystemLibs) process.artifacts else process.artifacts.filter { it.isGuest }
            item(key = "head${process.pid}") { ProcessHeader(process, shown.size) }
            if (shown.isEmpty()) {
                item(key = "empty${process.pid}") {
                    Text(
                        text = "  no guest-owned files mapped yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(shown, key = { "${process.pid}${it.path}" }) { artifact ->
                ArtifactRow(artifact)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ProcessHeader(process: GuestProcess, shownCount: Int) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = process.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "pid ${process.pid}  ·  ${process.dexFiles} dex  ·  ${process.nativeLibs} native libs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ArtifactRow(artifact: LoadedArtifact) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when (artifact.kind) {
                LoadedArtifact.Kind.NATIVE_LIB -> "so"
                LoadedArtifact.Kind.DEX -> "dex"
                LoadedArtifact.Kind.APK -> "apk"
                LoadedArtifact.Kind.OTHER -> "?"
            },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artifact.name,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                // Guest artefacts are the interesting ones; system libs are context.
                color = if (artifact.isGuest) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = artifact.displayDir,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsTab(state: ContainerState, onEvent: (ContainerEvent) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        item {
            SettingRow(
                title = "Loader window",
                subtitle = if (state.canDrawOverlay) {
                    "Floats the mapped-file list over the guest"
                } else {
                    "Needs \"Display over other apps\" — MIUI reverts adb grants"
                },
                action = if (state.loaderWindowShown) "Hide" else if (state.canDrawOverlay) "Show" else "Grant",
                onClick = { onEvent(ContainerEvent.ToggleLoaderWindow) },
            )
        }
        item {
            SettingRow(
                title = "Show system libraries",
                subtitle = if (state.showSystemLibs) {
                    "Process tab lists every mapped file"
                } else {
                    "Process tab shows only the guest's own files"
                },
                action = if (state.showSystemLibs) "On" else "Off",
                onClick = { onEvent(ContainerEvent.ToggleSystemLibs) },
            )
        }
        item {
            SettingRow(
                title = "Engine",
                subtitle = "startup=${state.isStartup}  launched=${state.engineLaunched}",
                action = "Refresh",
                onClick = { onEvent(ContainerEvent.RefreshProcesses) },
            )
        }
        item {
            SettingRow(
                title = "Installed in engine",
                subtitle = state.installedInEngine.joinToString().ifEmpty { "none" },
                action = null,
                onClick = {},
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, action: String?, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (action != null) {
                OutlinedButton(onClick = onClick) { Text(action) }
            }
        }
    }
}
