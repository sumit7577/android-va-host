package dev.bello.vahost

import android.app.Application
import android.content.Context
import android.os.Build
import com.lody.virtual.client.core.VirtualCore
import com.lody.virtual.client.stub.VASettings
import java.io.File

/**
 * Boots VirtualApp's engine inside a host we control.
 *
 * Init order is VirtualApp's own: startup() in attachBaseContext (it must run
 * before any component exists), then initialize() in onCreate, which dispatches
 * per-process — the engine runs a main, a server and N virtual processes.
 */
class VaHostApp : Application() {

    companion object {
        @Volatile var report: File? = null
        fun note(s: String) {
            android.util.Log.i("VAHost", s)
            try { report?.appendText(s + "\n") } catch (_: Throwable) {}
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // One file per process: main and :x both boot the engine, and a shared
        // file meant whichever started second truncated the other's output.
        val proc = (if (Build.VERSION.SDK_INT >= 28) getProcessName() else null)
            ?: "pid${android.os.Process.myPid()}"
        report = File(base.filesDir, "va-report-${proc.substringAfterLast(':', "main")}.txt")
        try { report!!.writeText("== VA boot ${Build.VERSION.SDK_INT} / ${Build.SUPPORTED_ABIS.toList()} ==\n") } catch (_: Throwable) {}
        note("process=${android.os.Process.myPid()} 64bit=${android.os.Process.is64Bit()}")
        try {
            VASettings.ENABLE_IO_REDIRECT = true
            VASettings.ENABLE_INNER_SHORTCUT = false
            VirtualCore.get().startup(base)
            note("startup() OK")
            val fails = com.lody.virtual.client.core.InvocationStubManager.getInjectFailures()
            note("stub injection failures: ${fails.size}")
            fails.forEach { note("   X $it") }
        } catch (t: Throwable) {
            note("startup() FAILED ${t.javaClass.name}: ${t.message}")
            // Walk the cause chain: ExceptionInInitializerError hides the real
            // failure, and the top-level trace alone says nothing useful.
            var c: Throwable? = t
            var depth = 0
            while (c != null && depth < 5) {
                note("  [$depth] ${c.javaClass.name}: ${c.message}")
                c.stackTrace.take(6).forEach { note("        at $it") }
                c = c.cause
                depth++
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            VirtualCore.get().initialize(object : VirtualCore.VirtualInitializer() {
                override fun onMainProcess() = note("initialize -> onMainProcess")
                override fun onVirtualProcess() = note("initialize -> onVirtualProcess")
                override fun onServerProcess() = note("initialize -> onServerProcess")
            })
            note("initialize() OK")
        } catch (t: Throwable) {
            note("initialize() FAILED ${t.javaClass.name}: ${t.message}")
            note(android.util.Log.getStackTraceString(t).lines().take(12).joinToString("\n"))
        }
    }
}
