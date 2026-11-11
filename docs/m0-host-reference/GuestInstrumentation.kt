package dev.bello.container.host

import android.app.Activity
import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.util.Log

const val EXTRA_GUEST_CLASS = "dev.bello.container.GUEST_CLASS"
const val TAG = "M0Engine"

/**
 * The swap. ActivityThread asks Instrumentation to construct the activity named
 * by the (stub) ActivityInfo; we hand back the guest class loaded from the
 * guest apk instead, then point its Resources at the guest's asset table.
 *
 * Why this and not the ClientTransaction/mH hook the real engines use: the
 * LaunchActivityItem shape changed in Android 14 and again in 15, so hooking it
 * is version-specific. Instrumentation.newActivity has been stable for years.
 * The cost is that the ActivityInfo stays the stub's, so theme/label/launchMode
 * come from the stub — fixing that is M2 work.
 */
class GuestInstrumentation(private val base: Instrumentation) : Instrumentation() {

    override fun newActivity(cl: ClassLoader, className: String, intent: Intent): Activity {
        val guestClass = intent.getStringExtra(EXTRA_GUEST_CLASS)
        GuestEngine.note("newActivity<- className=$className extra=$guestClass ready=${GuestEngine.isReady}")
        if (guestClass != null && GuestEngine.isReady) {
            val gcl = GuestEngine.classLoader!!
            val loaded = gcl.loadClass(guestClass)
            GuestEngine.note("  loaded ${loaded.name} definedBy=${loaded.classLoader} engineCl=$gcl same=${loaded.classLoader === gcl}")
            return loaded.getDeclaredConstructor().newInstance() as Activity
        }
        return base.newActivity(cl, className, intent)
    }

    override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
        GuestEngine.note("callActivityOnCreate<- ${activity.javaClass.name}")
        if (activity.intent?.getStringExtra(EXTRA_GUEST_CLASS) != null) {
            injectGuestResources(activity)
        }
        base.callActivityOnCreate(activity, icicle)
    }

    /**
     * attach() has already run, onCreate has not. Swap the Resources on both the
     * ContextImpl and the ContextThemeWrapper, which is what Activity
     * .getResources() actually reads.
     */
    private fun injectGuestResources(activity: Activity) {
        val res = GuestEngine.resources ?: return
        val onWrapper = setField(activity, "mResources", res)
        val ctxImpl = (activity as ContextWrapper).baseContext
        val onContext = setField(ctxImpl, "mResources", res)
        Log.i(TAG, "injectGuestResources: wrapper=$onWrapper contextImpl=$onContext")
    }

    companion object {
        /**
         * Replace ActivityThread.mInstrumentation. The internal fields are copied
         * across, otherwise inherited methods such as execStartActivity run with
         * a null mThread and the host can no longer start anything.
         */
        fun install(): String {
            val atClass = Class.forName("android.app.ActivityThread")
            val current = atClass.getDeclaredMethod("currentActivityThread")
                .apply { isAccessible = true }
                .invoke(null) ?: error("no current ActivityThread")

            val field = atClass.getDeclaredField("mInstrumentation").apply { isAccessible = true }
            val base = field.get(current) as Instrumentation
            if (base is GuestInstrumentation) return "already installed"

            val mine = GuestInstrumentation(base)
            copyFields(Instrumentation::class.java, base, mine)
            field.set(current, mine)
            return "hooked ActivityThread.mInstrumentation (was ${base.javaClass.name})"
        }
    }
}
