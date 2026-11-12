package com.lody.virtual.client.hook.proxies.am;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import com.lody.virtual.client.VClientImpl;
import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.client.interfaces.IInjector;
import com.lody.virtual.client.ipc.VActivityManager;
import com.lody.virtual.helper.utils.ComponentUtils;
import com.lody.virtual.helper.utils.Reflect;
import com.lody.virtual.helper.utils.PortLog;
import com.lody.virtual.helper.utils.VLog;
import com.lody.virtual.remote.InstalledAppInfo;
import com.lody.virtual.remote.StubActivityRecord;

import mirror.android.app.ActivityManagerNative;
import mirror.android.app.ActivityThread;
import mirror.android.app.IActivityManager;

/**
     * @author Lody
     * @see Handler.Callback
     */
    public class HCallbackStub implements Handler.Callback, IInjector {


        // PORT FIX: ActivityThread$H.LAUNCH_ACTIVITY was removed in Android 9 when the


        // activity lifecycle moved to EXECUTE_TRANSACTION / ClientTransaction.


        // Unguarded this NPEd in <clinit>, aborting VirtualCore.startup() in every


        // virtual process, so the client never attached and the server's


        // startActivity() binder call hung forever. -1 never matches a real msg id.


        private static final int LAUNCH_ACTIVITY =


                ActivityThread.H.LAUNCH_ACTIVITY != null ? ActivityThread.H.LAUNCH_ACTIVITY.get() : -1;
        private static final int CREATE_SERVICE =
                ActivityThread.H.CREATE_SERVICE != null ? ActivityThread.H.CREATE_SERVICE.get() : -1;
        private static final int SCHEDULE_CRASH =
                ActivityThread.H.SCHEDULE_CRASH != null ? ActivityThread.H.SCHEDULE_CRASH.get() : -1;

        private static final int EXECUTE_TRANSACTION =
            ActivityThread.H.EXECUTE_TRANSACTION != null ? ActivityThread.H.EXECUTE_TRANSACTION.get() : -1;

    private static final String TAG = HCallbackStub.class.getSimpleName();
        private static final HCallbackStub sCallback = new HCallbackStub();

        private boolean mCalling = false;


        private Handler.Callback otherCallback;

        private HCallbackStub() {
        }

        public static HCallbackStub getDefault() {
            return sCallback;
        }

        private static Handler getH() {
            return ActivityThread.mH.get(VirtualCore.mainThread());
        }

        private static Handler.Callback getHCallback() {
            try {
                Handler handler = getH();
                return mirror.android.os.Handler.mCallback.get(handler);
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public boolean handleMessage(Message msg) {
            if (!mCalling) {
                mCalling = true;
                try {
                    if (LAUNCH_ACTIVITY == msg.what) {
                        if (!handleLaunchActivity(msg)) {
                            return true;
                        }
                    } else if (EXECUTE_TRANSACTION == msg.what) {
                        if (!handleExecuteTransaction(msg)) {
                            return true;
                        }
                    } else if (CREATE_SERVICE == msg.what) {
                        if (!VClientImpl.get().isBound()) {
                            ServiceInfo info = Reflect.on(msg.obj).get("info");
                            VClientImpl.get().bindApplication(info.packageName, info.processName);
                        }
                    } else if (SCHEDULE_CRASH == msg.what) {
                        // to avoid the exception send from System.
                        return true;
                    }
                    if (otherCallback != null) {
                        boolean desired = otherCallback.handleMessage(msg);
                        mCalling = false;
                        return desired;
                    } else {
                        mCalling = false;
                    }
                } finally {
                    mCalling = false;
                }
            }
            return false;
        }

        private boolean handleLaunchActivity(Message msg) {
            Object r = msg.obj;
            Intent stubIntent = ActivityThread.ActivityClientRecord.intent.get(r);
            StubActivityRecord saveInstance = new StubActivityRecord(stubIntent);
            if (saveInstance.intent == null) {
                return true;
            }
            Intent intent = saveInstance.intent;
            ComponentName caller = saveInstance.caller;
            IBinder token = ActivityThread.ActivityClientRecord.token.get(r);
            ActivityInfo info = saveInstance.info;
            if (VClientImpl.get().getToken() == null) {
                InstalledAppInfo installedAppInfo = VirtualCore.get().getInstalledAppInfo(info.packageName, 0);
                if(installedAppInfo == null){
                    return true;
                }
                VActivityManager.get().processRestarted(info.packageName, info.processName, saveInstance.userId);
                getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
                return false;
            }
            if (!VClientImpl.get().isBound()) {
                VClientImpl.get().bindApplication(info.packageName, info.processName);
                getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
                return false;
            }
            int taskId = IActivityManager.getTaskForActivity.call(
                    ActivityManagerNative.getDefault.call(),
                    token,
                    false
            );
            VActivityManager.get().onActivityCreate(ComponentUtils.toComponentName(info), caller, token, info, intent, ComponentUtils.getTaskAffinity(info), taskId, info.launchMode, info.flags);
            ClassLoader appClassLoader = VClientImpl.get().getClassLoader(info.applicationInfo);
            intent.setExtrasClassLoader(appClassLoader);
            ActivityThread.ActivityClientRecord.intent.set(r, intent);
            ActivityThread.ActivityClientRecord.activityInfo.set(r, info);
            return true;
        }

            /**
         * PORT FIX (Android 9+). The lifecycle no longer arrives as LAUNCH_ACTIVITY
         * with an ActivityClientRecord; it arrives as EXECUTE_TRANSACTION carrying a
         * ClientTransaction whose item list holds a LaunchActivityItem. The swap is
         * the same — replace the stub's Intent and ActivityInfo with the guest's —
         * only the container has changed.
         *
         * Field names are probed rather than assumed: the list is mTransactionItems
         * on 15, mActivityCallbacks on 14, mCallbacks earlier.
         *
         * @return false if the message was re-posted and must not be delivered now.
         */
        private boolean handleExecuteTransaction(Message msg) {
            Object transaction = msg.obj;
            PortLog.w(TAG, "EXECUTE_TRANSACTION id=" + EXECUTE_TRANSACTION + " msg.what=" + msg.what
                    + " obj=" + (transaction == null ? "null" : transaction.getClass().getName()));
            if (transaction == null) return true;

            java.util.List<?> items = null;
            for (String field : new String[]{"mTransactionItems", "mActivityCallbacks", "mCallbacks"}) {
                try {
                    Object v = Reflect.on(transaction).get(field);
                    if (v instanceof java.util.List && !((java.util.List<?>) v).isEmpty()) {
                        items = (java.util.List<?>) v;
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
            if (items == null) {
                StringBuilder fields = new StringBuilder();
                for (java.lang.reflect.Field f : transaction.getClass().getDeclaredFields()) {
                    fields.append(f.getName()).append(':').append(f.getType().getSimpleName()).append(' ');
                }
                PortLog.w(TAG, "no item list found; ClientTransaction fields = " + fields);
                return true;
            }
            StringBuilder names = new StringBuilder();
            for (Object it : items) names.append(it == null ? "null" : it.getClass().getName()).append(' ');
            PortLog.w(TAG, "transaction items = " + names);

            for (Object item : items) {
                if (item == null || !item.getClass().getName().endsWith("LaunchActivityItem")) {
                    continue;
                }
                Intent stubIntent = null;
                try {
                    stubIntent = Reflect.on(item).get("mIntent");
                } catch (Throwable e) {
                    StringBuilder fs = new StringBuilder();
                    for (java.lang.reflect.Field f : item.getClass().getDeclaredFields()) {
                        fs.append(f.getName()).append(':').append(f.getType().getSimpleName()).append(' ');
                    }
                    PortLog.w(TAG, "mIntent unreadable (" + e + "); LaunchActivityItem fields = " + fs);
                    continue;
                }
                if (stubIntent == null) { PortLog.w(TAG, "mIntent is null"); continue; }
                PortLog.w(TAG, "stubIntent = " + stubIntent);

                StubActivityRecord saveInstance = new StubActivityRecord(stubIntent);
                if (saveInstance.intent == null) {
                    PortLog.w(TAG, "StubActivityRecord decoded no target intent");
                    continue;
                }

                Intent intent = saveInstance.intent;
                ComponentName caller = saveInstance.caller;
                ActivityInfo info = saveInstance.info;

                PortLog.w(TAG, "target=" + saveInstance.info.packageName + "/" + saveInstance.info.name
                        + " token=" + (VClientImpl.get().getToken() != null)
                        + " bound=" + VClientImpl.get().isBound());
                if (VClientImpl.get().getToken() == null) {
                    InstalledAppInfo installedAppInfo =
                            VirtualCore.get().getInstalledAppInfo(info.packageName, 0);
                    if (installedAppInfo == null) return true;
                    VActivityManager.get().processRestarted(
                            info.packageName, info.processName, saveInstance.userId);
                    getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
                    return false;
                }
                if (!VClientImpl.get().isBound()) {
                    final Thread bindThread = Thread.currentThread();
                    Thread watchdog = new Thread(new Runnable() {
                        @Override public void run() {
                            try { Thread.sleep(5000); } catch (InterruptedException ignored) { return; }
                            StringBuilder sb = new StringBuilder("bindApplication STILL BLOCKED after 5s:\n");
                            for (StackTraceElement e : bindThread.getStackTrace()) {
                                sb.append("    at ").append(e).append('\n');
                            }
                            PortLog.w(TAG, sb.toString());
                        }
                    }, "bind-watchdog");
                    watchdog.setDaemon(true);
                    watchdog.start();
                    try {
                        VClientImpl.get().bindApplication(info.packageName, info.processName);
                        watchdog.interrupt();
                        PortLog.w(TAG, "bindApplication OK, bound=" + VClientImpl.get().isBound()
                                + " -> reposting transaction");
                    } catch (Throwable e) {
                        Throwable root = e;
                        while (root.getCause() != null) root = root.getCause();
                        PortLog.w(TAG, "bindApplication FAILED " + root
                                + " @" + (root.getStackTrace().length > 0 ? root.getStackTrace()[0] : "?"));
                        return true;
                    }
                    getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
                    return false;
                }

                IBinder token = null;
                for (String f : new String[]{"mActivityToken", "mToken"}) {
                    try {
                        Object t = Reflect.on(item).get(f);
                        if (t instanceof IBinder) { token = (IBinder) t; break; }
                    } catch (Throwable ignored) {
                    }
                }
                if (token == null) {
                    try {
                        Object t = Reflect.on(transaction).get("mActivityToken");
                        if (t instanceof IBinder) token = (IBinder) t;
                    } catch (Throwable ignored) {
                    }
                }

                int taskId = -1;
                try {
                    taskId = IActivityManager.getTaskForActivity.call(
                            ActivityManagerNative.getDefault.call(), token, false);
                } catch (Throwable ignored) {
                }

                VActivityManager.get().onActivityCreate(
                        ComponentUtils.toComponentName(info), caller, token, info, intent,
                        ComponentUtils.getTaskAffinity(info), taskId, info.launchMode, info.flags);

                ClassLoader appClassLoader = VClientImpl.get().getClassLoader(info.applicationInfo);
                intent.setExtrasClassLoader(appClassLoader);
                try {
                    Reflect.on(item).set("mIntent", intent);
                    Reflect.on(item).set("mInfo", info);
                    PortLog.w(TAG, "SWAPPED stub -> " + info.packageName + "/" + info.name);
                } catch (Throwable e) {
                    VLog.e(TAG, "could not rewrite LaunchActivityItem: " + e);
                }
            }
            return true;
        }

        @Override
        public void inject() throws Throwable {
            otherCallback = getHCallback();
            mirror.android.os.Handler.mCallback.set(getH(), this);
        }

        @Override
        public boolean isEnvBad() {
            Handler.Callback callback = getHCallback();
            boolean envBad = callback != this;
            if (callback != null && envBad) {
                VLog.d(TAG, "HCallback has bad, other callback = " + callback);
            }
            return envBad;
        }

    }
