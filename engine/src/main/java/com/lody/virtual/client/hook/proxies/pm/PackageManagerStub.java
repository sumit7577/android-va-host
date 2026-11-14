package com.lody.virtual.client.hook.proxies.pm;

import android.os.Build;
import android.os.IInterface;

import com.lody.virtual.client.hook.base.BinderInvocationStub;
import com.lody.virtual.client.hook.base.Inject;
import com.lody.virtual.client.hook.base.LogInvocation;
import com.lody.virtual.client.hook.base.MethodInvocationProxy;
import com.lody.virtual.client.hook.base.MethodInvocationStub;
import com.lody.virtual.client.hook.base.ResultStaticMethodProxy;
import com.lody.virtual.helper.compat.BuildCompat;

import mirror.android.app.ActivityThread;

/**
 * @author Lody
 */
// PORT DIAGNOSTIC: packers interrogate IPackageManager from their own native
// code before they will decrypt anything, so what this answers decides whether
// a protected guest starts at all. Logs every call and its result.
@LogInvocation(LogInvocation.Condition.ALWAYS)
@Inject(MethodProxies.class)
public final class PackageManagerStub extends MethodInvocationProxy<MethodInvocationStub<IInterface>> {

    public PackageManagerStub() {
        super(new MethodInvocationStub<>(ActivityThread.sPackageManager.get()));
    }

    @Override
    protected void onBindMethods() {
        super.onBindMethods();
        addMethodProxy(new ResultStaticMethodProxy("addPermissionAsync", true));
        addMethodProxy(new ResultStaticMethodProxy("addPermission", true));
        addMethodProxy(new ResultStaticMethodProxy("performDexOpt", true));
        addMethodProxy(new ResultStaticMethodProxy("performDexOptIfNeeded", false));
        addMethodProxy(new ResultStaticMethodProxy("performDexOptSecondary", true));
        addMethodProxy(new ResultStaticMethodProxy("addOnPermissionsChangeListener", 0));
        addMethodProxy(new ResultStaticMethodProxy("removeOnPermissionsChangeListener", 0));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            addMethodProxy(new ResultStaticMethodProxy("checkPackageStartable", 0));
        }
        if (BuildCompat.isOreo()) {
            addMethodProxy(new ResultStaticMethodProxy("notifyDexLoad", 0));
            addMethodProxy(new ResultStaticMethodProxy("notifyPackageUse", 0));
            addMethodProxy(new ResultStaticMethodProxy("setInstantAppCookie", false));
            addMethodProxy(new ResultStaticMethodProxy("isInstantApp", false));
        }

    }

    @Override
    public void inject() throws Throwable {
        final IInterface hookedPM = getInvocationStub().getProxyInterface();
        ActivityThread.sPackageManager.set(hookedPM);
        BinderInvocationStub pmHookBinder = new BinderInvocationStub(getInvocationStub().getBaseInterface());
        pmHookBinder.copyMethodProxies(getInvocationStub());
        // Native code that resolves the service itself -- getService("package")
        // then IPackageManager$Stub.asInterface -- arrives through this stub and
        // not the one above, so it has to be logged to the same condition or
        // those calls are invisible.
        pmHookBinder.setInvocationLoggingCondition(
                getInvocationStub().getInvocationLoggingCondition());
        pmHookBinder.replaceService("package");
    }

    @Override
    public boolean isEnvBad() {
        return getInvocationStub().getProxyInterface() != ActivityThread.sPackageManager.get();
    }
}
