package dev.bello.vahost

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.lody.virtual.client.core.InstallStrategy
import com.lody.virtual.client.core.VirtualCore
import com.lody.virtual.client.ipc.VActivityManager
import java.io.File

private const val GUEST_PKG = "dev.bello.container.guest"
private const val USER_ID = 0

class StatusActivity : Activity() {

    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.DKGRAY); setPadding(24, 12, 24, 24)
        }
        setContentView(ScrollView(this).apply {
            addView(LinearLayout(this@StatusActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 48, 32, 32)
                addView(TextView(this@StatusActivity).apply {
                    text = "Container host"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setTextColor(Color.BLACK)
                })
                addView(Button(this@StatusActivity).apply {
                    text = "Install guest"; setOnClickListener { install() }
                })
                addView(Button(this@StatusActivity).apply {
                    text = "Launch guest"; setOnClickListener { launch() }
                })
                addView(log)
            })
        })
        probeFramework()
        status()
        if (savedInstanceState == null) {
            // Scriptable from adb: install then launch, no tapping required.
            install()
            launch()
        }
    }

    private fun say(s: String) {
        VaHostApp.note(s)
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) log.append(s + "\n\n")
        else runOnUiThread { log.append(s + "\n\n") }
    }

    /** Which framework internals the 2017 parser path depends on still exist. */
    private fun probeFramework() {
        for (n in listOf(
            "android.content.pm.PackageParser",
            "android.content.pm.PackageUserState",
            "android.content.pm.pkg.PackageUserState",
            "android.content.pm.parsing.ApkLiteParseUtils"
        )) {
            val r = try {
                val c = Class.forName(n)
                val ctor = try { c.getDeclaredConstructor(); "has no-arg ctor" } catch (e: Throwable) { "NO no-arg ctor" }
                "FOUND via ${c.classLoader ?: "boot"} ($ctor)"
            } catch (t: Throwable) { "ABSENT (${t.javaClass.simpleName})" }
            say("$n -> $r")
        }
        // What do PackageParser's generate* methods actually take now?
        try {
            val pp = Class.forName("android.content.pm.PackageParser")
            pp.declaredMethods
                .filter { it.name.startsWith("generate") }
                .sortedBy { it.name }
                .forEach { m ->
                    say("PackageParser.${m.name}(${m.parameterTypes.joinToString { it.simpleName }})")
                }
        } catch (t: Throwable) { say("generate* probe failed: $t") }
        try {
            val pp = Class.forName("android.content.pm.PackageParser")
            val ust = pp.declaredMethods.first { it.name == "generateApplicationInfo" }.parameterTypes[2]
            say("userState type = ${ust.name} (interface=${ust.isInterface})")
            ust.declaredFields.filter { java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .forEach { say("  static field: ${it.name} : ${it.type.simpleName}") }
            for (impl in listOf(
                "android.content.pm.pkg.PackageUserStateImpl",
                "android.content.pm.pkg.PackageUserStateInternal",
                "android.content.pm.pkg.PackageUserStateDefault"
            )) {
                val r = try {
                    val c = Class.forName(impl)
                    val ok = try { c.getDeclaredConstructor().newInstance(); "no-arg ctor OK" }
                             catch (e: Throwable) { "ctor: ${e.javaClass.simpleName}" }
                    "FOUND ($ok)"
                } catch (t: Throwable) { "ABSENT" }
                say("  $impl -> $r")
            }
        } catch (t: Throwable) { say("userState probe failed: $t") }
        try {
            val pp = Class.forName("android.content.pm.PackageParser")
            val certs = pp.declaredMethods.filter { it.name.contains("ertificate") }
            say("PackageParser cert methods: " +
                (certs.joinToString { "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})" }
                    .ifEmpty { "NONE" }))
        } catch (t: Throwable) { say("cert probe failed: $t") }
    }

    private fun status() {
        val core = VirtualCore.get()
        say(
            "isStartup=${core.isStartup} " +
            "engineLaunched=${runCatching { core.isEngineLaunched }.getOrElse { "ERR" }} " +
            "installed=${runCatching { core.getInstalledApps(0).map { it.packageName } }.getOrElse { "ERR:${it.message}" }}"
        )
    }

    /** Write the embedded guest apk out, then hand it to the engine. */
    private fun install() {
        try {
            val apk = File(filesDir, "payload/guest.apk").apply { parentFile?.mkdirs() }
            if (apk.exists()) { apk.setWritable(true); apk.delete() }
            assets.open("guest.apk").use { i -> apk.outputStream().use { i.copyTo(it) } }
            say("payload written ${apk.length()} bytes")

            val r = VirtualCore.get().installPackage(apk.absolutePath, InstallStrategy.UPDATE_IF_EXIST)
            say("installPackage -> success=${r.isSuccess} pkg=${r.packageName} update=${r.isUpdate} error=${r.error}")
            status()
        } catch (t: Throwable) {
            say("install FAILED ${t.javaClass.name}: ${t.message}")
            say(android.util.Log.getStackTraceString(t).lines().take(10).joinToString("\n"))
        }
    }

    /** Resolve the guest's launch intent through the engine and start it. */
    private fun launch() {
        try {
            val intent = VirtualCore.get().getLaunchIntent(GUEST_PKG, USER_ID)
            say("getLaunchIntent -> $intent")
            if (intent == null) { say("no launch intent; is it installed?"); return }
            // startActivity is a synchronous binder call into the engine server.
            // Run it off the main thread so a hang can be diagnosed instead of
            // silently freezing the app.
            val worker = Thread({
                try {
                    val code = VActivityManager.get().startActivity(intent, USER_ID)
                    say("startActivity -> $code  (0 == ok)")
                } catch (t: Throwable) {
                    say("startActivity THREW ${t.javaClass.name}: ${t.message}")
                }
            }, "va-launch")
            worker.start()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (worker.isAlive) {
                    say("startActivity STILL BLOCKED after 5s — stack:")
                    worker.stackTrace.take(16).forEach { say("    at $it") }
                }
            }, 5000)
        } catch (t: Throwable) {
            say("launch FAILED ${t.javaClass.name}: ${t.message}")
            say(android.util.Log.getStackTraceString(t).lines().take(14).joinToString("\n"))
        }
    }
}
