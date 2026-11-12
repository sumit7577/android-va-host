package mirror.android.app;

import android.content.Intent;
import android.os.IBinder;

import mirror.MethodParams;
import mirror.RefClass;
import mirror.RefMethod;

/**
 * Android 14 (UpsideDownCake) added a trailing `long bindSeq` to
 * IApplicationThread.scheduleBindService. The 4-argument mirror therefore
 * resolves to null on 14+, and the engine's bindService died with
 *   NPE: 'RefMethod.call(...)' on a null object reference
 *     at IApplicationThreadCompat.scheduleBindService
 * which surfaced in the guest as "Expected to unbox a 'int'", because the
 * server's int return never arrived.
 */
public class IApplicationThreadUDC {
    public static Class<?> TYPE = RefClass.load(IApplicationThreadUDC.class, "android.app.IApplicationThread");

    @MethodParams({IBinder.class, Intent.class, boolean.class, int.class, long.class})
    public static RefMethod<Void> scheduleBindService;
}
