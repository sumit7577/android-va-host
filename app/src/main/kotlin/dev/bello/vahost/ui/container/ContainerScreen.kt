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
import dev.bello.vahost.ui.container.viewModel.ContainerState
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
        Surface(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { EngineStatus(state) }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onEvent(ContainerEvent.RunEmbeddedGuest) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Run test guest") }
                        OutlinedButton(
                            onClick = { onEvent(ContainerEvent.ToggleLoaderWindow) },
                        ) {
                            Text(
                                if (state.loaderWindowShown) "Hide loader"
                                else if (state.canDrawOverlay) "Loader window"
                                else "Allow overlay"
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Installed apps — tap to run inside the container",
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
            }
        }
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
