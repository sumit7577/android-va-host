package dev.bello.container.host

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.lsposed.hiddenapibypass.HiddenApiBypass

class HostActivity : Activity() {

    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        log = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.DKGRAY)
            setPadding(32, 16, 32, 32)
        }

        val run = Button(this).apply {
            text = "Install + launch guest"
            setOnClickListener { runSpike() }
        }

        setContentView(ScrollView(this).apply {
            addView(LinearLayout(this@HostActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 48, 32, 32)
                addView(TextView(this@HostActivity).apply {
                    text = "M0 spike — load a non-installed apk's Activity"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    setTextColor(Color.BLACK)
                })
                addView(run)
                addView(log)
            })
        })

        say("device API ${Build.VERSION.SDK_INT}, host pid ${android.os.Process.myPid()}")

        // Auto-run so the spike is scriptable from adb with no screen reading.
        if (savedInstanceState == null) runSpike()
    }

    private fun runSpike() {
        log.text = ""
        try { java.io.File(filesDir, "m0-report.txt").writeText("== M0 run ==\n") } catch (_: Throwable) {}
        try {
            step("hidden-api") {
                if (Build.VERSION.SDK_INT >= 28) {
                    HiddenApiBypass.addHiddenApiExemptions("")
                    "exemptions added"
                } else "not needed below API 28"
            }
            step("install") { GuestEngine.install(this) }
            step("prepare") { GuestEngine.prepare(this) }
            step("hook") { GuestInstrumentation.install() }

            val guest = GuestEngine.launchClass!!
            say("launching stub carrying $guest")
            startActivity(
                Intent(this, StubActivity::class.java)
                    .putExtra(EXTRA_GUEST_CLASS, guest)
            )
        } catch (t: Throwable) {
            say("FAILED: ${t.javaClass.name}: ${t.message}")
            android.util.Log.e(TAG, "spike failed", t)
        }
    }

    private inline fun step(name: String, body: () -> String) {
        val r = body()
        say("[$name] $r")
        android.util.Log.i(TAG, "[$name] $r")
    }

    private fun say(s: String) {
        log.append(s + "\n\n")
        // MIUI filters app logcat, so the result also goes to a file that
        // `adb run-as` can read. This is the spike's real output channel.
        try { java.io.File(filesDir, "m0-report.txt").appendText(s + "\n") } catch (_: Throwable) {}
    }
}
