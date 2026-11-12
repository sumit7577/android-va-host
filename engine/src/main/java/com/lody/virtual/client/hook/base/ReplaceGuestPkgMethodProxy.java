package com.lody.virtual.client.hook.base;

import java.lang.reflect.Method;

/**
 * Replaces the guest package name with the host's wherever it appears in the
 * arguments, matching by VALUE rather than by position.
 *
 * The positional helpers (ReplaceCallingPkgMethodProxy, ReplaceLastPkgMethodProxy)
 * assume a fixed argument layout, and that assumption is exactly what keeps
 * breaking as the framework inserts parameters -- attributionTag after a
 * package, featureId after a package, and so on. Matching on the value cannot
 * rewrite the wrong argument: if the string is not the guest package it is
 * left alone.
 *
 * Use this for calls that reach the real framework, which validates the
 * package against the real calling uid and otherwise throws
 * "Package <guest> does not belong to <host uid>".
 */
public class ReplaceGuestPkgMethodProxy extends MethodProxy {

    private final String mName;

    public ReplaceGuestPkgMethodProxy(String name) {
        mName = name;
    }

    @Override
    public String getMethodName() {
        return mName;
    }

    @Override
    public boolean beforeCall(Object who, Method method, Object... args) {
        if (args != null) {
            String guest = getAppPkg();
            String host = getHostPkg();
            if (guest != null && !guest.equals(host)) {
                for (int i = 0; i < args.length; i++) {
                    if (guest.equals(args[i])) {
                        args[i] = host;
                    }
                }
            }
        }
        return super.beforeCall(who, method, args);
    }
}
