package dev.bello.container.host

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import dalvik.system.DexClassLoader
import java.io.File

/**
 * M0 engine: install a guest apk, parse it, and build the two things that
 * make a guest's code runnable — its ClassLoader and its Resources.
 *
 * Deliberately NOT here yet (M1+): identity faking, Binder proxies, path
 * redirection, process pooling.
 */
object GuestEngine {

    private const val GUEST_ASSET = "guest.apk"

    lateinit var apkPath: String; private set
    var appInfo: ApplicationInfo? = null; private set
    var guestPackage: String? = null; private set
    var launchClass: String? = null; private set
    var classLoader: ClassLoader? = null; private set
    var resources: Resources? = null; private set

    val isReady: Boolean get() = classLoader != null && resources != null

    /** Report sink shared with the hook, which has no Context of its own. */
    @Volatile var reportFile: File? = null
    fun note(s: String) {
        android.util.Log.i("M0Engine", s)
        try { reportFile?.appendText(s + "\n") } catch (_: Throwable) {}
    }

    /** Copy the bundled apk into our own storage, as a real install would. */
    fun install(ctx: Context): String {
        reportFile = File(ctx.filesDir, "m0-report.txt")
        val dir = File(ctx.filesDir, "virtual/0/dev.bello.container.guest").apply { mkdirs() }
        val out = File(dir, "base.apk")
        // Always refresh: setReadOnly() below makes the file unwritable, so a
        // stale copy would silently survive every later build of the guest.
        if (out.exists()) { out.setWritable(true); out.delete() }
        ctx.assets.open(GUEST_ASSET).use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        // Android 14+: dynamically loaded code MUST be read-only or loading throws.
        // This is the W^X rule from the plan, hit on the very first milestone.
        out.setReadOnly()
        apkPath = out.absolutePath
        return "installed ${out.length()} bytes -> $apkPath (readOnly=${!out.canWrite()})"
    }

    /** Parse the apk and build the guest ClassLoader + Resources. */
    fun prepare(ctx: Context): String {
        val pi = ctx.packageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES)
            ?: error("packageManager could not parse $apkPath")
        val ai = pi.applicationInfo ?: error("archive has no applicationInfo")

        // getPackageArchiveInfo leaves these null — nothing resolves without them.
        ai.sourceDir = apkPath
        ai.publicSourceDir = apkPath
        appInfo = ai
        guestPackage = pi.packageName
        launchClass = pi.activities?.firstOrNull()?.name
            ?: error("guest declares no activity")

        val optimized = File(ctx.codeCacheDir, "guest-dex").apply { mkdirs() }
        classLoader = DexClassLoader(apkPath, optimized.absolutePath, null, ctx.classLoader)

        // Guest resources = a fresh AssetManager containing ONLY the guest apk,
        // so the guest's 0x7f ids resolve against its own table, not the host's.
        val am = AssetManager::class.java.getDeclaredConstructor().newInstance()
        AssetManager::class.java
            .getMethod("addAssetPath", String::class.java)
            .invoke(am, apkPath)
        resources = Resources(am, ctx.resources.displayMetrics, ctx.resources.configuration)

        return "parsed pkg=$guestPackage launch=$launchClass"
    }
}
