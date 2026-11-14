package com.lody.virtual.client;

import android.os.Binder;
import android.os.Build;
import android.os.Process;

import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.client.env.VirtualRuntime;
import com.lody.virtual.client.ipc.VActivityManager;
import com.lody.virtual.client.natives.NativeMethods;
import com.lody.virtual.helper.compat.BuildCompat;
import com.lody.virtual.helper.utils.PortLog;
import com.lody.virtual.helper.utils.VLog;
import com.lody.virtual.os.VUserHandle;
import com.lody.virtual.remote.InstalledAppInfo;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VirtualApp Native Project
 */
public class NativeEngine {

    private static final String TAG = NativeEngine.class.getSimpleName();

    private static Map<String, InstalledAppInfo> sDexOverrideMap;

    private static boolean sFlag = false;

    static {
        try {
            System.loadLibrary("va++");
        } catch (Throwable e) {
            VLog.e(TAG, VLog.getStackTraceString(e));
        }
    }

    static {
        NativeMethods.init();
    }


    public static void startDexOverride() {
        List<InstalledAppInfo> installedAppInfos = VirtualCore.get().getInstalledApps(0);
        sDexOverrideMap = new HashMap<>(installedAppInfos.size());
        for (InstalledAppInfo info : installedAppInfos) {
            try {
                sDexOverrideMap.put(new File(info.apkPath).getCanonicalPath(), info);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String getRedirectedPath(String origPath) {
        try {
            return nativeGetRedirectedPath(origPath);
        } catch (Throwable e) {
            VLog.e(TAG, VLog.getStackTraceString(e));
        }
        return origPath;
    }

    public static String resverseRedirectedPath(String origPath) {
        try {
            return nativeReverseRedirectedPath(origPath);
        } catch (Throwable e) {
            VLog.e(TAG, VLog.getStackTraceString(e));
        }
        return origPath;
    }

    public static void redirectDirectory(String origPath, String newPath) {
        if (!origPath.endsWith("/")) {
            origPath = origPath + "/";
        }
        if (!newPath.endsWith("/")) {
            newPath = newPath + "/";
        }
        try {
            nativeIORedirect(origPath, newPath);
        } catch (Throwable e) {
            VLog.e(TAG, VLog.getStackTraceString(e));
        }
    }

    public static void redirectFile(String origPath, String newPath) {
        if (origPath.endsWith("/")) {
            origPath = origPath.substring(0, origPath.length() - 1);
        }
        if (newPath.endsWith("/")) {
            newPath = newPath.substring(0, newPath.length() - 1);
        }

        try {
            nativeIORedirect(origPath, newPath);
        } catch (Throwable e) {
            VLog.e(TAG, VLog.getStackTraceString(e));
        }
    }

    public static void whitelist(String path) {
        try {
            nativeIOWhitelist(path);
        } catch (Throwable e) {
            VLog.e(TAG, VLog.getStackTraceString(e));
        }
    }

    public static void forbid(String path) {
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        try {
            nativeIOForbid(path);
        } catch (Throwable e) {
            VLog.e(TAG, VLog.getStackTraceString(e));
        }
    }

    public static void enableIORedirect() {
        try {
            String soPath = String.format("/data/data/%s/lib/libva++.so", VirtualCore.get().getHostPkg());
            if (!new File(soPath).exists()) {
                throw new RuntimeException("Unable to find the so.");
            }
            nativeEnableIORedirect(soPath, Build.VERSION.SDK_INT, BuildCompat.getPreviewSDKInt());
        } catch (Throwable e) {
            VLog.e(TAG, VLog.getStackTraceString(e));
        }
    }

    /**
     * ART hooking, for framework methods the Binder-proxy hooks cannot reach
     * because they decide in-process. Returns false when unavailable, and the
     * engine carries on without it.
     */
    private static native boolean nativeInitArtHooker();

    private static boolean sArtHookerReady;

    public static boolean initArtHooker() {
        try {
            sArtHookerReady = nativeInitArtHooker();
        } catch (Throwable e) {
            sArtHookerReady = false;
            VLog.w(TAG, "ART hooker unavailable: " + e.getMessage());
        }
        PortLog.w(TAG, "ART hooker ready=" + sArtHookerReady);
        return sArtHookerReady;
    }

    public static boolean isArtHookerReady() {
        return sArtHookerReady;
    }

    private static native boolean nativeDisableCheckJni();

    private static native boolean nativeStartPathTrace();

    private static native boolean nativeStartJniTrace();

    /**
     * Android turns CheckJNI on for a debuggable app, and a guest runs inside
     * this host's process, so it inherits it. A guest's native libraries are
     * release code that was never run under CheckJNI, whose extra signature
     * checks abort the process rather than return -- so an app that launches
     * normally dies here for a reason unrelated to being virtualized.
     */
    public static boolean disableCheckJni() {
        boolean off;
        try {
            off = nativeDisableCheckJni();
        } catch (Throwable e) {
            off = false;
            VLog.w(TAG, "CheckJNI unchanged: " + e.getMessage());
        }
        PortLog.w(TAG, "CheckJNI disabled=" + off);
        return off;
    }

    /**
     * Diagnostic: names the paths a guest's native code asks for and misses.
     * On arm64 this container does no syscall-level path redirection, so a
     * guest library that derives its own paths instead of reading the
     * ApplicationInfo it was given looks somewhere that does not exist.
     */
    public static boolean startPathTrace() {
        boolean on;
        try {
            on = nativeStartPathTrace();
        } catch (Throwable e) {
            on = false;
            VLog.w(TAG, "path trace unavailable: " + e.getMessage());
        }
        PortLog.w(TAG, "PathTrace started=" + on);
        return on;
    }

    /**
     * Diagnostic: names the JNI lookups a guest's native code makes and misses.
     * A packer that decides not to run registers nothing and reports nothing,
     * so the only trace of its decision is the lookup that came back empty.
     */
    public static boolean startJniTrace() {
        boolean on;
        try {
            on = nativeStartJniTrace();
        } catch (Throwable e) {
            on = false;
            VLog.w(TAG, "jni trace unavailable: " + e.getMessage());
        }
        PortLog.w(TAG, "JniTrace started=" + on);
        return on;
    }

    static void launchEngine() {
        if (sFlag) {
            return;
        }
        Method[] methods = {NativeMethods.gOpenDexFileNative, NativeMethods.gCameraNativeSetup, NativeMethods.gAudioRecordNativeCheckPermission};
        try {
            nativeLaunchEngine(methods, VirtualCore.get().getHostPkg(), VirtualRuntime.isArt(), Build.VERSION.SDK_INT, NativeMethods.gCameraMethodType);
        } catch (Throwable e) {
            VLog.e(TAG, VLog.getStackTraceString(e));
        }
        sFlag = true;
    }

    public static void onKillProcess(int pid, int signal) {
        VLog.e(TAG, "killProcess: pid = %d, signal = %d.", pid, signal);
        if (pid == android.os.Process.myPid()) {
            VLog.e(TAG, VLog.getStackTraceString(new Throwable()));
        }
    }

    public static int onGetCallingUid(int originUid) {
        int callingPid = Binder.getCallingPid();
        if (callingPid == Process.myPid()) {
            return VClientImpl.get().getBaseVUid();
        }
        if (callingPid == VirtualCore.get().getSystemPid()) {
            return Process.SYSTEM_UID;
        }
        int vuid = VActivityManager.get().getUidByPid(callingPid);
        if (vuid != -1) {
            return VUserHandle.getAppId(vuid);
        }
        VLog.d(TAG, "Unknown uid: " + callingPid);
        return VClientImpl.get().getBaseVUid();
    }

    public static void onOpenDexFileNative(String[] params) {
        String dexOrJarPath = params[0];
        String outputPath = params[1];
        VLog.d(TAG, "DexOrJarPath = %s, OutputPath = %s.", dexOrJarPath, outputPath);
        try {
            String canonical = new File(dexOrJarPath).getCanonicalPath();
            InstalledAppInfo info = sDexOverrideMap.get(canonical);
            if (info != null && !info.dependSystem) {
                outputPath = info.getOdexFile().getPath();
                params[1] = outputPath;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static native void nativeLaunchEngine(Object[] method, String hostPackageName, boolean isArt, int apiLevel, int cameraMethodType);

    private static native void nativeMark();

    private static native String nativeReverseRedirectedPath(String redirectedPath);

    private static native String nativeGetRedirectedPath(String orgPath);

    private static native void nativeIORedirect(String origPath, String newPath);

    private static native void nativeIOWhitelist(String path);

    private static native void nativeIOForbid(String path);

    private static native void nativeEnableIORedirect(String selfSoPath, int apiLevel, int previewApiLevel);

    public static int onGetUid(int uid) {
        return VClientImpl.get().getBaseVUid();
    }
}
