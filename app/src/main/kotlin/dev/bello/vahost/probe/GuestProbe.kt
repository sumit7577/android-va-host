package dev.bello.vahost.probe

import java.io.File

/** One mapped artefact the guest has loaded. */
data class LoadedArtifact(
    val path: String,
    val kind: Kind,
) {
    enum class Kind { NATIVE_LIB, DEX, APK, OTHER }

    /** Last path segment, which is what is worth reading at a glance. */
    val name: String get() = path.substringAfterLast('/')

    /** True when it came out of the container rather than the system image. */
    val isGuest: Boolean get() = path.contains("/virtual/")

    /**
     * Where it came from, shortened for display. A guest path is long and
     * mostly the host's private directory, so it is cut back to the container
     * root -- otherwise "base.apk" reads as if it were the real install.
     */
    val displayDir: String
        get() {
            val dir = path.substringBeforeLast('/', "")
            val marker = "/virtual/"
            val at = dir.indexOf(marker)
            return if (at >= 0) "container:/" + dir.substring(at + marker.length) else dir
        }
}

/**
 * Reads what a guest process has actually mapped.
 *
 * Guests run under the host's uid, so /proc/<pid>/maps is readable directly.
 * Going through the kernel rather than hooking the loader means dlopen() from
 * native code shows up too, which a Java-level ClassLoader hook would miss.
 */
object GuestProbe {

    private val hostPid = android.os.Process.myPid()

    /** Guest processes: same uid as us, but not one of the engine's own. */
    fun guestPids(hostPackage: String): List<Pair<Int, String>> =
        File("/proc").listFiles().orEmpty()
            .mapNotNull { it.name.toIntOrNull() }
            .filter { it != hostPid }
            .mapNotNull { pid ->
                val cmdline = runCatching {
                    File("/proc/$pid/cmdline").readText().trim('\u0000', ' ', '\n')
                }.getOrNull().orEmpty()
                when {
                    cmdline.isEmpty() -> null
                    cmdline == hostPackage -> null
                    cmdline.startsWith("$hostPackage:") -> null
                    else -> pid to cmdline
                }
            }

    fun artifacts(pid: Int): List<LoadedArtifact> {
        val lines = runCatching { File("/proc/$pid/maps").readLines() }.getOrNull() ?: return emptyList()
        return lines
            .mapNotNull { line -> line.substringAfter(' ', "").substringAfterLast(' ', "").takeIf { it.startsWith("/") } }
            .distinct()
            .mapNotNull { path ->
                val kind = when {
                    path.endsWith(".so") -> LoadedArtifact.Kind.NATIVE_LIB
                    path.endsWith(".dex") || path.endsWith(".odex") ||
                        path.endsWith(".vdex") || path.endsWith(".art") ||
                        path.endsWith(".oat") -> LoadedArtifact.Kind.DEX
                    path.endsWith(".apk") || path.endsWith(".jar") -> LoadedArtifact.Kind.APK
                    else -> null
                } ?: return@mapNotNull null
                LoadedArtifact(path, kind)
            }
            .sortedWith(compareByDescending<LoadedArtifact> { it.isGuest }.thenBy { it.name })
    }
}

/** A guest process and everything it has mapped. */
data class GuestProcess(
    val pid: Int,
    val name: String,
    val artifacts: List<LoadedArtifact>,
) {
    val nativeLibs: Int get() = artifacts.count { it.kind == LoadedArtifact.Kind.NATIVE_LIB }
    val dexFiles: Int get() = artifacts.count { it.kind == LoadedArtifact.Kind.DEX }
}
