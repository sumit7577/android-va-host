package com.lody.virtual.client.hook.proxies.connectivity;

import android.content.Context;

import com.lody.virtual.client.hook.base.BinderInvocationProxy;
import com.lody.virtual.client.hook.base.MethodProxy;
import com.lody.virtual.client.hook.base.ReplaceGuestPkgMethodProxy;
import com.lody.virtual.client.hook.base.StaticMethodProxy;
import com.lody.virtual.client.ipc.ServiceManagerNative;

import java.lang.reflect.Method;

import mirror.android.net.IConnectivityManager;

/**
 * @author legency
 */
public class ConnectivityStub extends BinderInvocationProxy {

    public ConnectivityStub() {
        super(IConnectivityManager.Stub.asInterface, Context.CONNECTIVITY_SERVICE);
    }

    @Override
    protected void onBindMethods() {
        super.onBindMethods();
        // PORT FIX: this stub registered nothing, so every ConnectivityManager
        // call carrying a callingPackageName went out with the GUEST package
        // under the host uid and came back as
        //   SecurityException: Package <guest> does not belong to <host uid>
        // Any guest that registers a network callback in Application.onCreate
        // -- which is most of them, and every UE4 title -- died there.
        // On these methods the package is the first String argument
        // (callingAttributionTag follows it), which is what
        // ReplaceCallingPkgMethodProxy rewrites.
        for (String m : new String[]{
                "listenForNetwork",
                "requestNetwork",
                "pendingRequestForNetwork",
                "pendingListenForNetwork",
                "requestRouteToHostAddress",
                "getNetworkCapabilities",
                "getLinkProperties",
                "getActiveNetworkInfo",
                "getActiveNetwork",
                "getAllNetworkInfo",
                "getNetworkInfo",
                "reportNetworkConnectivity",
                "startCaptivePortalApp",
                "getCaptivePortalServerUrl",
        }) {
            addMethodProxy(new ReplaceGuestPkgMethodProxy(m));
        }
    }
}
