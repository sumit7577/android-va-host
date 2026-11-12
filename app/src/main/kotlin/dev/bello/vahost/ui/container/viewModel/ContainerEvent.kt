package dev.bello.vahost.ui.container.viewModel

import dev.bello.vahost.core.ViewEvent

sealed class ContainerEvent : ViewEvent {
    /** Enumerate launchable packages on the device. */
    data object LoadApps : ContainerEvent()

    /**
     * Install the already-installed app's own APK into the engine, then launch
     * it. Takes the package only; the APK path is re-resolved at run time so a
     * stale sourceDir from an app update can never be used.
     */
    data class RunApp(val packageName: String) : ContainerEvent()

    /** Install + launch the embedded trivial guest -- the known-good baseline. */
    data object RunEmbeddedGuest : ContainerEvent()

    data object ClearLog : ContainerEvent()

    /** Show/hide the floating window listing what the guest has loaded. */
    data object ToggleLoaderWindow : ContainerEvent()
}
