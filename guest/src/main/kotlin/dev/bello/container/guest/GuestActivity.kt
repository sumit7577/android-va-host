package dev.bello.container.guest

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView

/**
 * M0 probe. Builds its UI in code (no layout/theme) so the only thing under
 * test is: does this class load from a NON-INSTALLED apk, and can it read its
 * own resources?
 *
 * It also prints the identity it actually sees. In M0 that identity is still
 * the HOST's — faking it is M2+ work, and this report is how we measure it.
 */
class GuestActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val banner = try {
            getString(R.string.guest_banner)
        } catch (t: Throwable) {
            "GUEST RESOURCES FAILED -> ${t.javaClass.simpleName}: ${t.message}"
        }

        val report = buildString {
            appendLine(banner)
            appendLine()
            appendLine("— what the guest sees —")
            // NOTE: inside buildString the receiver is the StringBuilder, so a
            // bare `javaClass` here reports StringBuilder, not the Activity.
            appendLine("class loader : ${this@GuestActivity.javaClass.classLoader}")
            appendLine("packageName  : $packageName")
            appendLine("process      : ${android.os.Process.myPid()}")
            appendLine("uid          : ${android.os.Process.myUid()}")
            appendLine("dataDir      : ${applicationInfo.dataDir}")
            appendLine("filesDir     : ${filesDir.absolutePath}")
            appendLine("sourceDir    : ${applicationInfo.sourceDir}")
            appendLine()
            appendLine("Expected in M0: packageName/dataDir are still the HOST's.")
            appendLine("Resources + ClassLoader are what M0 proves.")
        }

        android.util.Log.i("M0Guest", report)
        try {
            java.io.File(filesDir, "m0-report.txt")
                .appendText("\n--- GUEST ACTIVITY RAN ---\n" + report + "\n")
        } catch (t: Throwable) { /* ignore */ }

        val tv = TextView(this).apply {
            text = report
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.BLACK)
            setPadding(40, 60, 40, 60)
            setBackgroundColor(Color.WHITE)
        }
        setContentView(ScrollView(this).apply { addView(tv) })
    }
}
