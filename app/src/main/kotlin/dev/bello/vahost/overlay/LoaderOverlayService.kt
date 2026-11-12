package dev.bello.vahost.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.bello.vahost.probe.GuestProbe
import dev.bello.vahost.probe.LoadedArtifact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Floating window showing what the guest has loaded, polled from
 * /proc/<pid>/maps. It is a window rather than a screen in the host UI
 * because the point is to watch while the guest is in the foreground.
 */
class LoaderOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var root: ComposeView
    private val owner = OverlayOwner()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var state by mutableStateOf(OverlayState())
    private var collapsed by mutableStateOf(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        owner.onCreate()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // Not focusable: the guest underneath must keep receiving input.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 160
        }

        root = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                LoaderOverlay(
                    state = state,
                    collapsed = collapsed,
                    onToggle = { collapsed = !collapsed },
                    onClose = { stopSelf() },
                    onDrag = { dx, dy ->
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        runCatching { windowManager.updateViewLayout(root, params) }
                    },
                )
            }
        }
        windowManager.addView(root, params)
        startPolling()
    }

    private fun startPolling() = scope.launch {
        val hostPackage = packageName
        var known = emptySet<String>()
        while (true) {
            val snapshot = withContext(Dispatchers.IO) {
                val guests = GuestProbe.guestPids(hostPackage)
                guests.map { (pid, name) -> Triple(pid, name, GuestProbe.artifacts(pid)) }
            }
            val all = snapshot.flatMap { it.third }
            // "new" means newly mapped since the last poll, which is what makes
            // the window worth watching rather than just a static list.
            val fresh = all.map { it.path }.toSet() - known
            known = all.map { it.path }.toSet()
            state = OverlayState(
                processes = snapshot.map { (pid, name, arts) -> GuestProcess(pid, name, arts) },
                newlyLoaded = fresh,
            )
            delay(1000)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { windowManager.removeView(root) }
        owner.onDestroy()
        super.onDestroy()
    }
}

data class GuestProcess(
    val pid: Int,
    val name: String,
    val artifacts: List<LoadedArtifact>,
)

data class OverlayState(
    val processes: List<GuestProcess> = emptyList(),
    val newlyLoaded: Set<String> = emptySet(),
)
