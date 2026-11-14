package com.lody.virtual.client.hook.proxies.mount;

import android.os.Build;

import com.lody.virtual.client.hook.base.MethodProxy;
import com.lody.virtual.client.hook.utils.MethodParameterUtils;

import java.io.File;
import java.lang.reflect.Method;

import com.lody.virtual.os.VEnvironment;

import mirror.android.os.storage.StorageVolume;

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

        /**
         * Environment builds its external-storage roots from these volumes, and
         * getObbDir()/getExternalFilesDir() are built from those roots. Pointing
         * them at container storage is the only way a guest reaches its own
         * expansion files: scoped storage hides another package's
         * Android/obb outright, so the real path reports exists()==false even
         * with all-files access, which Android 11+ explicitly excludes for
         * Android/data and Android/obb.
         *
         * Consequence: a guest sees an EMPTY external storage, not the device's
         * shared media. For a container that is the correct trade -- it is the
         * isolation the whole thing is for -- but it does mean a guest cannot
         * see real photos or downloads.
         */
        @Override
        public Object afterCall(Object who, Method method, Object[] args, Object result) throws Throwable {
            if (result == null || !isAppProcess()) {
                return result;
            }
            File root = VEnvironment.getExternalStorageDirectory();
            int count = java.lang.reflect.Array.getLength(result);
            for (int i = 0; i < count; i++) {
                Object volume = java.lang.reflect.Array.get(result, i);
                if (volume == null) {
                    continue;
                }
                // Both fields are final, hence the reflective rewrite; either
                // may be absent depending on API level.
                if (StorageVolume.mPath != null) {
                    StorageVolume.mPath.set(volume, root);
                }
                if (StorageVolume.mInternalPath != null) {
                    StorageVolume.mInternalPath.set(volume, root);
                }
            }
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
