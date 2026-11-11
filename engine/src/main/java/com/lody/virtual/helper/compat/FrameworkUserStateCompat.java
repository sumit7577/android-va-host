package com.lody.virtual.helper.compat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PORT FIX (Android 13+).
 *
 * PackageParser.generate*Info() used to take `android.content.pm.PackageUserState`,
 * a concrete class with a public no-arg constructor. It was replaced by the
 * interface `android.content.pm.pkg.FrameworkPackageUserState`, and no
 * instantiable implementation is reachable from an app.
 *
 * So we supply our own via a dynamic Proxy that answers "a normally installed,
 * unhidden, enabled app" for every accessor.
 */
public class FrameworkUserStateCompat {

    private static volatile Object sInstance;
    private static volatile boolean sResolved;

    public static Object get() {
        if (sResolved) return sInstance;
        synchronized (FrameworkUserStateCompat.class) {
            if (sResolved) return sInstance;
            sInstance = resolve();
            sResolved = true;
            return sInstance;
        }
    }

    /** The interface type, so callers can check availability. */
    public static Class<?> type() {
        try {
            return Class.forName("android.content.pm.pkg.FrameworkPackageUserState");
        } catch (Throwable t) {
            try {
                return Class.forName("android.content.pm.PackageUserState");
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    private static Object resolve() {
        // Pre-13: the real class, constructed normally.
        try {
            Class<?> legacy = Class.forName("android.content.pm.PackageUserState");
            return legacy.getDeclaredConstructor().newInstance();
        } catch (Throwable ignored) {
        }
        // 13+: proxy the interface.
        try {
            Class<?> itf = Class.forName("android.content.pm.pkg.FrameworkPackageUserState");
            return Proxy.newProxyInstance(itf.getClassLoader(), new Class[]{itf}, new DefaultState());
        } catch (Throwable t) {
            return null;
        }
    }

    private static final class DefaultState implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            Class<?> ret = method.getReturnType();

            // Object methods must behave, or the proxy breaks in collections.
            if ("toString".equals(name)) return "FrameworkPackageUserState(default)";
            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
            if ("equals".equals(name)) return proxy == (args == null ? null : args[0]);

            if (ret == boolean.class) {
                // Installed and visible; everything else off.
                return "isInstalled".equals(name);
            }
            if (ret == int.class) return 0;      // includes COMPONENT_ENABLED_STATE_DEFAULT
            if (ret == long.class) return 0L;
            if (ret == float.class) return 0f;
            if (ret == double.class) return 0d;
            if (ret == short.class) return (short) 0;
            if (ret == byte.class) return (byte) 0;
            if (ret == char.class) return (char) 0;
            if (ret == void.class) return null;
            if (Set.class.isAssignableFrom(ret)) return Collections.emptySet();
            if (List.class.isAssignableFrom(ret)) return Collections.emptyList();
            if (Map.class.isAssignableFrom(ret)) return Collections.emptyMap();
            if (ret.isArray()) return java.lang.reflect.Array.newInstance(ret.getComponentType(), 0);
            return null;
        }
    }
}
