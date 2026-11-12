package mirror.android.app;

import java.lang.ref.WeakReference;

import android.app.Application;
import android.app.IServiceConnection;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.IInterface;

import mirror.RefClass;
import mirror.RefObject;
import mirror.RefMethod;
import mirror.MethodParams;

public class LoadedApk {
    // NOTE: makeApplicationInnerDup maps to makeApplicationInner(boolean,Instrumentation,boolean)
    public static Class Class = RefClass.load(LoadedApk.class, "android.app.LoadedApk");
    public static RefObject<ApplicationInfo> mApplicationInfo;
    // The constructed Application is cached here even when
    // makeApplicationInner() returns null on this platform.
    public static RefObject<Application> mApplication;
    @MethodParams({boolean.class, Instrumentation.class})
    public static RefMethod<Application> makeApplication;
    // PORT FIX: Android 14 split the real work into makeApplicationInner and
    // left makeApplication as a deprecated wrapper, which returns null here.
    @MethodParams({boolean.class, Instrumentation.class})
    public static RefMethod<Application> makeApplicationInner;
    // Android 14+ private 3-arg form; the 2-arg wrapper passes
    // allowDuplicateInstances=false, which can short-circuit to a cached
    // (host) Application instead of building the guest's.
    @MethodParams({boolean.class, Instrumentation.class, boolean.class})
    public static RefMethod<Application> makeApplicationInnerDup;
    @MethodParams({ServiceConnection.class, Context.class, Handler.class, int.class})
    public static RefMethod<IServiceConnection> getServiceDispatcher;
    @MethodParams({Context.class, ServiceConnection.class})
    public static RefMethod<IServiceConnection> forgetServiceDispatcher;

    public static class ReceiverDispatcher {
        public static Class Class = RefClass.load(ReceiverDispatcher.class, "android.app.LoadedApk$ReceiverDispatcher");
        public static RefMethod<IInterface> getIIntentReceiver;
        public static RefObject<BroadcastReceiver> mReceiver;
        public static RefObject<IIntentReceiver> mIIntentReceiver;

        public static class InnerReceiver {
            public static Class Class = RefClass.load(InnerReceiver.class, "android.app.LoadedApk$ReceiverDispatcher$InnerReceiver");
            public static RefObject<WeakReference> mDispatcher;
        }
    }

    public static class ServiceDispatcher {
        public static Class Class = RefClass.load(ServiceDispatcher.class, "android.app.LoadedApk$ServiceDispatcher");
        public static RefObject<ServiceConnection> mConnection;
        public static RefObject<Context> mContext;

        public static class InnerConnection {
            public static Class Class = RefClass.load(InnerConnection.class, "android.app.LoadedApk$ServiceDispatcher$InnerConnection");
            public static RefObject<WeakReference> mDispatcher;
        }
    }
}