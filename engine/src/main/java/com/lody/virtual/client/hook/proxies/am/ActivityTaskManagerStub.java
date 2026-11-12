package com.lody.virtual.client.hook.proxies.am;

import android.os.Build;
import android.os.IInterface;

import com.lody.virtual.client.hook.base.Inject;
import com.lody.virtual.client.hook.base.MethodInvocationProxy;
import com.lody.virtual.client.hook.base.MethodInvocationStub;

import mirror.android.app.ActivityTaskManager;
import mirror.android.util.Singleton;

/**
 * Hooks IActivityTaskManager, which owns startActivity and the rest of the
 * activity lifecycle from Android 10 on. The method proxies are the same ones
 * ActivityManagerStub uses -- the interface moved, the handling did not --
 * so this reuses that class rather than duplicating it.
 *
 * @see ActivityManagerStub
 */
@Inject(MethodProxies.class)
public class ActivityTaskManagerStub extends MethodInvocationProxy<MethodInvocationStub<IInterface>> {

    public ActivityTaskManagerStub() {
        super(new MethodInvocationStub<IInterface>(getTaskManager()));
    }

    private static IInterface getTaskManager() {
        Object singleton = ActivityTaskManager.IActivityTaskManagerSingleton.get();
        return singleton == null ? null : (IInterface) Singleton.get.call(singleton);
    }

    @Override
    public void inject() throws Throwable {
        Object singleton = ActivityTaskManager.IActivityTaskManagerSingleton.get();
        if (singleton != null) {
            Singleton.mInstance.set(singleton, getInvocationStub().getProxyInterface());
        }
    }

    @Override
    public boolean isEnvBad() {
        Object singleton = ActivityTaskManager.IActivityTaskManagerSingleton.get();
        return singleton == null
                || !(Singleton.mInstance.get(singleton) instanceof IInterface)
                || Singleton.mInstance.get(singleton) != getInvocationStub().getProxyInterface();
    }

    /** Only exists on Android 10+; before that the calls live on IActivityManager. */
    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && ActivityTaskManager.TYPE != null
                && ActivityTaskManager.IActivityTaskManagerSingleton != null
                && ActivityTaskManager.IActivityTaskManagerSingleton.get() != null;
    }
}
