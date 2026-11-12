package dev.bello.vahost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.bello.vahost.ui.container.ContainerScreen
import dev.bello.vahost.ui.container.viewModel.ContainerEvent
import dev.bello.vahost.ui.container.viewModel.ContainerViewModel

class StatusActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        probeFramework()
        setContent {
            val vm: ContainerViewModel = viewModel(
                factory = viewModelFactory { initializer { ContainerViewModel(application) } }
            )
            // Scriptable from adb:
            //   am start -n …/.StatusActivity --es run <package>
            LaunchedEffect(Unit) {
                intent?.getStringExtra("run")?.let { vm.onTriggerEvent(ContainerEvent.RunApp(it)) }
            }
            ContainerScreen(viewModel = vm)
        }
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
            VaHostApp.note("$n -> $r")
        }
        // What do PackageParser's generate* methods actually take now?
        try {
            val pp = Class.forName("android.content.pm.PackageParser")
            pp.declaredMethods
                .filter { it.name.startsWith("generate") }
                .sortedBy { it.name }
                .forEach { m ->
                    VaHostApp.note("PackageParser.${m.name}(${m.parameterTypes.joinToString { it.simpleName }})")
                }
        } catch (t: Throwable) { VaHostApp.note("generate* probe failed: $t") }
        try {
            val pp = Class.forName("android.content.pm.PackageParser")
            val ust = pp.declaredMethods.first { it.name == "generateApplicationInfo" }.parameterTypes[2]
            VaHostApp.note("userState type = ${ust.name} (interface=${ust.isInterface})")
            ust.declaredFields.filter { java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .forEach { VaHostApp.note("  static field: ${it.name} : ${it.type.simpleName}") }
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
                VaHostApp.note("  $impl -> $r")
            }
        } catch (t: Throwable) { VaHostApp.note("userState probe failed: $t") }
        try {
            val pp = Class.forName("android.content.pm.PackageParser")
            val certs = pp.declaredMethods.filter { it.name.contains("ertificate") }
            VaHostApp.note("PackageParser cert methods: " +
                (certs.joinToString { "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})" }
                    .ifEmpty { "NONE" }))
        } catch (t: Throwable) { VaHostApp.note("cert probe failed: $t") }
        try {
            val lp = Class.forName("android.app.LoadedApk")
            lp.declaredMethods.filter { it.name.startsWith("make") }.forEach { m ->
                VaHostApp.note("LoadedApk.${m.name}(${m.parameterTypes.joinToString { it.simpleName }}) : ${m.returnType.simpleName}")
            }
        } catch (t: Throwable) { VaHostApp.note("LoadedApk probe failed: $t") }
    }
}
