package com.lody.virtual.client.hook.proxies.mount;

import android.os.Build;

import com.lody.virtual.client.hook.base.MethodProxy;
import com.lody.virtual.client.hook.utils.MethodParameterUtils;

import java.io.File;
import java.lang.reflect.Method;

/**
 * @author Lody
 */

class MethodProxies {

    /** UserHandle.PER_USER_RANGE; uid / this == the user id that owns the uid. */
    private static final int PER_USER_RANGE = 100000;

    static class GetVolumeList extends MethodProxy {

        @Override
        public String getMethodName() {
            return "getVolumeList";
        }

        @Override
        public boolean beforeCall(Object who, Method method, Object... args) {
            if (args == null || args.length == 0) {
                return super.beforeCall(who, method, args);
            }
            if (args[0] instanceof Integer) {
                // PORT FIX: this slot is `int uid` only up to API 25. From API 26
                // the interface is getVolumeList(int userId, String callingPackage,
                // int flags), so writing a uid into it makes the framework read
                // e.g. 10458 as a *user id*, conclude we are asking about another
                // user, and throw:
                //   SecurityException: Need INTERACT_ACROSS_USERS to get volumes
                //   for another user
                // which kills any guest that touches getExternalStorageDirectory().
                args[0] = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? getRealUid() / PER_USER_RANGE
                        : getRealUid();
            }
            MethodParameterUtils.replaceFirstAppPkg(args);
            return super.beforeCall(who, method, args);
        }

        @Override
        public Object afterCall(Object who, Method method, Object[] args, Object result) throws Throwable {
            return result;
        }
    }

    static class Mkdirs extends MethodProxy {

        @Override
        public String getMethodName() {
            return "mkdirs";
        }

        @Override
        public boolean beforeCall(Object who, Method method, Object... args) {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return super.beforeCall(who, method, args);
        }

        @Override
        public Object call(Object who, Method method, Object... args) throws Throwable {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                return super.call(who, method, args);
            }
            String path;
            if (args.length == 1) {
                path = (String) args[0];
            } else {
                path = (String) args[1];
            }
            File file = new File(path);
            if (!file.exists() && !file.mkdirs()) {
                return -1;
            }
            return 0;
        }
    }
}
