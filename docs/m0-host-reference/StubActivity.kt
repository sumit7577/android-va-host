package dev.bello.container.host

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView

/**
 * A stub slot. If the hook works this class is NEVER instantiated — the system
 * is told to launch it, and we hand back the guest activity instead.
 *
 * Seeing this screen means the Instrumentation hook did not fire.
 */
class StubActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GuestEngine.note("!! StubActivity.onCreate ran — swap did not happen")
        setContentView(TextView(this).apply {
            text = "STUB REACHED — the Instrumentation hook did not fire.\n" +
                   "The guest was never swapped in."
            setTextColor(Color.RED)
            setPadding(40, 80, 40, 40)
        })
    }
}
