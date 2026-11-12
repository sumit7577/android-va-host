package mirror.android.app;

import mirror.RefClass;
import mirror.RefStaticObject;

/**
 * Android 10 split IActivityManager, moving the activity lifecycle calls --
 * startActivity among them -- to IActivityTaskManager, reached through this
 * singleton. The 2017 tree this port is based on predates the split and hooks
 * only IActivityManager, so on 10+ every activity call a GUEST makes went
 * straight to the real framework. The first launch still worked because the
 * host drives that one through the engine; the guest's own startActivity did
 * not, and came back as
 *   SecurityException: Permission Denial: package=<guest> does not belong to
 *   uid=<host uid>
 */
public class ActivityTaskManager {

    public static Class<?> TYPE = RefClass.load(ActivityTaskManager.class, "android.app.ActivityTaskManager");

    public static RefStaticObject<Object> IActivityTaskManagerSingleton;
}
