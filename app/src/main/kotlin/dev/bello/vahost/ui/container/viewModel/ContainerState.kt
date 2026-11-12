package dev.bello.vahost.ui.container.viewModel

import dev.bello.vahost.core.ViewState

/** One launchable package, with the facts that decide whether it can run. */
data class GuestApp(
    val label: String,
    val packageName: String,
    val splitCount: Int,
    val isSystem: Boolean,
)

enum class ProgressBarState { Idle, Loading }

data class ContainerState(
    val apps: List<GuestApp> = emptyList(),
    /** Package currently being installed/launched, so its row can show a spinner. */
    val runningPackage: String? = null,
    val installedInEngine: List<String> = emptyList(),
    val isStartup: Boolean = false,
    val engineLaunched: Boolean = false,
    val log: List<String> = emptyList(),
    val loaderWindowShown: Boolean = false,
    /** Overlay permission is a per-app grant and MIUI hides it behind a prompt. */
    val canDrawOverlay: Boolean = false,
    val progressBarState: ProgressBarState = ProgressBarState.Idle,
) : ViewState
