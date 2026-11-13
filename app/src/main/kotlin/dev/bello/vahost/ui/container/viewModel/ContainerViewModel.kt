package dev.bello.vahost.ui.container.viewModel

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.viewModelScope
import com.lody.virtual.client.core.InstallStrategy
import com.lody.virtual.client.core.VirtualCore
import com.lody.virtual.client.ipc.VActivityManager
import dev.bello.vahost.VaHostApp
import dev.bello.vahost.core.AppDataStore
import dev.bello.vahost.core.AppDataStoreManager
import dev.bello.vahost.core.BaseViewModel
import dev.bello.vahost.core.DataStoreKeys
import dev.bello.vahost.overlay.LoaderOverlayService
import dev.bello.vahost.probe.GuestProbe
import dev.bello.vahost.probe.GuestProcess
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val EMBEDDED_GUEST_PKG = "dev.bello.container.guest"
private const val USER_ID = 0

class ContainerViewModel(
    private val app: Application,
    private val dataStore: AppDataStore = AppDataStoreManager(app),
) : BaseViewModel<ContainerEvent, ContainerState>() {

    override fun setInitialState() = ContainerState()

    init {
        restoreSettings()
        viewModelScope.launch {
            awaitEngine()
            refreshEngineStatus()
        }
        onTriggerEvent(ContainerEvent.LoadApps)
    }

    /**
     * System libraries outnumber the guest's own files by a few hundred to one,
     * so the filter stays off unless the user turned it on before.
     */
    private fun restoreSettings() = viewModelScope.launch {
        val raw = runCatching { dataStore.readValue(DataStoreKeys.SHOW_SYSTEM_LIBS) }
            .onFailure { VaHostApp.note("restoreSettings FAILED ${it.javaClass.name}: ${it.message}") }
            .getOrNull()
        VaHostApp.note("restoreSettings read ${DataStoreKeys.SHOW_SYSTEM_LIBS}=$raw")
        val stored = raw?.toBooleanStrictOrNull() ?: false
        setState { copy(showSystemLibs = stored) }
    }

    override fun onTriggerEvent(event: ContainerEvent) {
        when (event) {
            ContainerEvent.LoadApps -> loadApps()
            is ContainerEvent.RunApp -> runApp(event.packageName)
            ContainerEvent.RunEmbeddedGuest -> runEmbeddedGuest()
            ContainerEvent.ClearLog -> setState { copy(log = emptyList()) }
            ContainerEvent.ToggleLoaderWindow -> toggleLoaderWindow()
            is ContainerEvent.SelectTab -> selectTab(event.tab)
            ContainerEvent.RefreshProcesses -> refreshProcesses()
            ContainerEvent.ToggleSystemLibs -> toggleSystemLibs()
        }
    }

    /**
     * targetSdk 24 keeps the host exempt from API 30 package-visibility
     * filtering, so querying LAUNCHER activities really does return everything.
     */
    private fun loadApps() = viewModelScope.launch {
        setState { copy(progressBarState = ProgressBarState.Loading) }
        val apps = withContext(Dispatchers.IO) {
            val pm = app.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != app.packageName }
                .mapNotNull { pkg ->
                    runCatching {
                        val ai = pm.getApplicationInfo(pkg, 0)
                        GuestApp(
                            label = pm.getApplicationLabel(ai).toString(),
                            packageName = pkg,
                            splitCount = ai.splitSourceDirs?.size ?: 0,
                            isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        )
                    }.getOrNull()
                }
                .sortedBy { it.label.lowercase() }
        }
        setState { copy(apps = apps, progressBarState = ProgressBarState.Idle) }
        say("found ${apps.size} launchable packages")
    }

    private fun runApp(pkg: String) = viewModelScope.launch {
        setState { copy(runningPackage = pkg) }
        showLoaderWindowIfPossible()
        if (!awaitEngine()) { setState { copy(runningPackage = null) }; return@launch }
        withContext(Dispatchers.IO) {
            try {
                val ai = app.packageManager.getApplicationInfo(pkg, 0)
                val splits = ai.splitSourceDirs?.size ?: 0
                say("--- $pkg ---")
                say("sourceDir = ${ai.sourceDir}")
                if (splits > 0) {
                    say(
                        "WARNING: $splits split APKs. installPackage() takes the base " +
                        "only, so split resources and per-ABI native libs are missing."
                    )
                }
                installThenLaunch(ai.sourceDir, pkg)
            } catch (t: Throwable) {
                say("runApp FAILED ${t.javaClass.name}: ${t.message}")
            }
        }
        setState { copy(runningPackage = null) }
        refreshEngineStatus()
    }

    /** Writes the embedded apk out of assets, then takes the same path as a real app. */
    private fun runEmbeddedGuest() = viewModelScope.launch {
        setState { copy(runningPackage = EMBEDDED_GUEST_PKG) }
        if (!awaitEngine()) { setState { copy(runningPackage = null) }; return@launch }
        withContext(Dispatchers.IO) {
            try {
                val apk = File(app.filesDir, "payload/guest.apk").apply { parentFile?.mkdirs() }
                if (apk.exists()) { apk.setWritable(true); apk.delete() }
                app.assets.open("guest.apk").use { i -> apk.outputStream().use { i.copyTo(it) } }
                say("payload written ${apk.length()} bytes")
                installThenLaunch(apk.absolutePath, EMBEDDED_GUEST_PKG)
            } catch (t: Throwable) {
                say("embedded guest FAILED ${t.javaClass.name}: ${t.message}")
            }
        }
        setState { copy(runningPackage = null) }
        refreshEngineStatus()
    }

    /**
     * The server lives in its own process, started on demand when its
     * ContentProvider is first acquired. Acquiring it retries only 5x100ms
     * inside the engine, and a cold start has to fork the process and run the
     * full stub injection before the provider publishes -- so a single call
     * loses the race and surfaces as an NPE from the engine's unguarded
     * client.call(). Keep asking until it answers.
     */
    private suspend fun awaitEngine(timeoutMs: Long = 15_000): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var attempt = 0
        while (SystemClock.uptimeMillis() < deadline) {
            attempt++
            val up = runCatching { VirtualCore.get().waitForEngine(); true }.getOrDefault(false)
            if (up) {
                if (attempt > 1) say("engine ready after $attempt attempts")
                return true
            }
            delay(250)
        }
        say("engine NOT ready after ${timeoutMs}ms")
        return false
    }

    private fun installThenLaunch(apkPath: String, pkg: String) {
        val r = VirtualCore.get().installPackage(apkPath, InstallStrategy.UPDATE_IF_EXIST)
        say("installPackage -> success=${r.isSuccess} pkg=${r.packageName} error=${r.error}")
        if (!r.isSuccess) return

        val launch = VirtualCore.get().getLaunchIntent(pkg, USER_ID)
        say("getLaunchIntent -> $launch")
        if (launch == null) { say("no launch intent for $pkg"); return }

        // The engine returns the result over binder, so a server-side throw
        // arrives here as null rather than an int. Unboxing it blind turns a
        // diagnosable server fault into "Expected to unbox a 'int'".
        val code: Int? = runCatching { VActivityManager.get().startActivity(launch, USER_ID) }
            .getOrElse { say("startActivity THREW ${it.javaClass.name}: ${it.message}"); return }
        if (code == null) {
            say("startActivity returned null — the engine server threw; check va-report-x2.txt")
            return
        }
        say("startActivity -> $code  (0 == ok)")
    }

    /** Polling only runs while the Process tab is on screen. */
    private var processPoll: Job? = null

    private fun selectTab(tab: ContainerTab) {
        setState { copy(selectedTab = tab) }
        processPoll?.cancel()
        if (tab == ContainerTab.Process) {
            processPoll = viewModelScope.launch {
                while (isActive) {
                    refreshProcesses()
                    delay(1500)
                }
            }
        }
    }

    private fun refreshProcesses() = viewModelScope.launch {
        val found = withContext(Dispatchers.IO) {
            GuestProbe.guestPids(app.packageName).map { (pid, name) ->
                GuestProcess(pid, name, GuestProbe.artifacts(pid))
            }
        }
        setState { copy(processes = found) }
    }

    private fun toggleSystemLibs() = viewModelScope.launch {
        val next = !state.value.showSystemLibs
        setState { copy(showSystemLibs = next) }
        dataStore.setValue(DataStoreKeys.SHOW_SYSTEM_LIBS, next.toString())
    }

    private fun toggleLoaderWindow() {
        if (!Settings.canDrawOverlays(app)) {
            say("overlay permission not granted — enable 'Display over other apps' for this app")
            app.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + app.packageName),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val intent = Intent(app, LoaderOverlayService::class.java)
        if (state.value.loaderWindowShown) {
            app.stopService(intent)
        } else {
            app.startService(intent)
        }
        setState { copy(loaderWindowShown = !loaderWindowShown) }
    }

    /** Opened alongside a guest launch so the loads are visible as they happen. */
    private fun showLoaderWindowIfPossible() {
        if (state.value.loaderWindowShown) {
            return
        }
        if (!Settings.canDrawOverlays(app)) {
            // Silently skipping here just looked like the window was broken.
            say("loader window skipped: no overlay permission (MIUI reverts `appops set`, " +
                "grant it in Settings > Apps > VA Host > Display over other apps)")
            return
        }
        app.startService(Intent(app, LoaderOverlayService::class.java))
        setState { copy(loaderWindowShown = true) }
    }

    private fun refreshEngineStatus() {
        val core = VirtualCore.get()
        setState {
            copy(
                isStartup = core.isStartup,
                engineLaunched = runCatching { core.isEngineLaunched }.getOrDefault(false),
                installedInEngine = runCatching {
                    core.getInstalledApps(0).map { it.packageName }
                }.getOrDefault(emptyList()),
                canDrawOverlay = Settings.canDrawOverlays(app),
            )
        }
    }

    /** Mirrors to PortLog so a run is still readable when the UI is gone. */
    private fun say(line: String) {
        VaHostApp.note(line)
        setState { copy(log = log + line) }
    }
}
