package com.lody.virtual.client.hook.providers;

import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IInterface;
import android.os.ParcelFileDescriptor;

import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.client.hook.base.MethodBox;
import com.lody.virtual.helper.utils.VLog;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import mirror.android.content.IContentProvider;

/**
 * @author Lody
 */

public class ProviderHook implements InvocationHandler {

    public static final String QUERY_ARG_SQL_SELECTION = "android:query-arg-sql-selection";

    public static final String QUERY_ARG_SQL_SELECTION_ARGS =
            "android:query-arg-sql-selection-args";
    public static final String QUERY_ARG_SQL_SORT_ORDER = "android:query-arg-sql-sort-order";


    private static final Map<String, HookFetcher> PROVIDER_MAP = new HashMap<>();

    static {
        PROVIDER_MAP.put("settings", new HookFetcher() {
            @Override
            public ProviderHook fetch(boolean external, IInterface provider) {
                return new SettingsProviderHook(provider);
            }
        });
        PROVIDER_MAP.put("downloads", new HookFetcher() {
            @Override
            public ProviderHook fetch(boolean external, IInterface provider) {
                return new DownloadProviderHook(provider);
            }
        });
    }

    protected final Object mBase;

    public ProviderHook(Object base) {
        this.mBase = base;
    }

    private static HookFetcher fetchHook(String authority) {
        HookFetcher fetcher = PROVIDER_MAP.get(authority);
        if (fetcher == null) {
            fetcher = new HookFetcher() {
                @Override
                public ProviderHook fetch(boolean external, IInterface provider) {
                    if (external) {
                        return new ExternalProviderHook(provider);
                    }
                    return new InternalProviderHook(provider);
                }
            };
        }
        return fetcher;
    }

    private static IInterface createProxy(IInterface provider, ProviderHook hook) {
        if (provider == null || hook == null) {
            return null;
        }
        return (IInterface) Proxy.newProxyInstance(provider.getClass().getClassLoader(), new Class[]{
                IContentProvider.TYPE,
        }, hook);
    }

    public static IInterface createProxy(boolean external, String authority, IInterface provider) {
        if (provider instanceof Proxy && Proxy.getInvocationHandler(provider) instanceof ProviderHook) {
            return provider;
        }
        ProviderHook.HookFetcher fetcher = ProviderHook.fetchHook(authority);
        if (fetcher != null) {
            ProviderHook hook = fetcher.fetch(external, provider);
            IInterface proxyProvider = ProviderHook.createProxy(provider, hook);
            if (proxyProvider != null) {
                provider = proxyProvider;
            }
        }
        return provider;
    }

    public Bundle call(MethodBox methodBox, String method, String arg, Bundle extras) throws InvocationTargetException {
        return methodBox.call();
    }

    public Uri insert(MethodBox methodBox, Uri url, ContentValues initialValues) throws InvocationTargetException {

        return (Uri) methodBox.call();
    }

    public Cursor query(MethodBox methodBox, Uri url, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder, Bundle originQueryArgs) throws InvocationTargetException {
        return (Cursor) methodBox.call();
    }

    public String getType(MethodBox methodBox, Uri url) throws InvocationTargetException {
        return (String) methodBox.call();
    }

    public int bulkInsert(MethodBox methodBox, Uri url, ContentValues[] initialValues) throws InvocationTargetException {
        return (int) methodBox.call();
    }

    public int delete(MethodBox methodBox, Uri url, String selection, String[] selectionArgs) throws InvocationTargetException {
        return (int) methodBox.call();
    }

    public int update(MethodBox methodBox, Uri url, ContentValues values, String selection,
                      String[] selectionArgs) throws InvocationTargetException {
        return (int) methodBox.call();
    }

    public ParcelFileDescriptor openFile(MethodBox methodBox, Uri url, String mode) throws InvocationTargetException {
        return (ParcelFileDescriptor) methodBox.call();
    }

    public AssetFileDescriptor openAssetFile(MethodBox methodBox, Uri url, String mode) throws InvocationTargetException {
        return (AssetFileDescriptor) methodBox.call();
    }

    private static int indexOfType(Class<?>[] types, Class<?> want) {
        for (int i = 0; i < types.length; i++) if (types[i] == want) return i;
        return -1;
    }

    private static int lastIndexOfType(Class<?>[] types, Class<?> want) {
        for (int i = types.length - 1; i >= 0; i--) if (types[i] == want) return i;
        return -1;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object... args) throws Throwable {
        try {
            processArgs(method, args);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        final Class<?>[] ptypes0 = method.getParameterTypes();
        // PORT FIX (Android 12+): IContentProvider calls carry an
        // AttributionSource, and the framework rejects the call when its uid
        // does not match the real caller:
        //   SecurityException: Calling uid: 10450 doesn't match source uid: 10001
        // The engine's spoofed identity leaks into that object, so put the
        // host's genuine AttributionSource back before passing the call on.
        // TRADE-OFF: provider calls therefore attribute to the host, not the
        // guest. Per-guest attribution needs a real solution later.
        try {
            if (Build.VERSION.SDK_INT >= 31 && ptypes0.length > 0
                    && "android.content.AttributionSource".equals(ptypes0[0].getName())) {
                Object real = VirtualCore.get().getContext().getAttributionSource();
                if (real != null) args[0] = real;
            }
        } catch (Throwable ignored) {
        }

        MethodBox methodBox = new MethodBox(method, mBase, args);
        // PORT FIX: `start` used to be hard-coded to 1 (the old leading
        // callingPackage String). IContentProvider's signatures have gained and
        // lost leading parameters repeatedly -- Android 11 added featureId, and
        // Android 12+ replaced the leading strings with an AttributionSource, so
        // on 15 `call` is (AttributionSource, String authority, String method,
        // String arg, Bundle extras). A fixed offset silently reads the wrong
        // argument and blew up as "String cannot be cast to Bundle".
        // Anchor on parameter TYPES instead, which is stable across versions.
        final Class<?>[] ptypes = ptypes0;
        final int uriAt = indexOfType(ptypes, Uri.class);
        int start = uriAt >= 0 ? uriAt
                : (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 ? 1 : 0);
        try {
            String name = method.getName();
            if ("call".equals(name)) {
                // extras is the trailing Bundle; method and arg precede it.
                int extrasAt = lastIndexOfType(ptypes, Bundle.class);
                if (extrasAt < 2) {
                    return methodBox.call();   // shape we do not recognise: pass through
                }
                String methodName = (String) args[extrasAt - 2];
                String arg = (String) args[extrasAt - 1];
                Bundle extras = (Bundle) args[extrasAt];
                return call(methodBox, methodName, arg, extras);
            } else if ("insert".equals(name)) {
                Uri url = (Uri) args[start];
                ContentValues initialValues = (ContentValues) args[start + 1];
                return insert(methodBox, url, initialValues);
            } else if ("getType".equals(name)) {
                return getType(methodBox, (Uri) args[uriAt >= 0 ? uriAt : 0]);
            } else if ("delete".equals(name)) {
                Uri url = (Uri) args[start];
                String selection = (String) args[start + 1];
                String[] selectionArgs = (String[]) args[start + 2];
                return delete(methodBox, url, selection, selectionArgs);
            } else if ("bulkInsert".equals(name)) {
                Uri url = (Uri) args[start];
                ContentValues[] initialValues = (ContentValues[]) args[start + 1];
                return bulkInsert(methodBox, url, initialValues);
            } else if ("update".equals(name)) {
                Uri url = (Uri) args[start];
                ContentValues values = (ContentValues) args[start + 1];
                String selection = (String) args[start + 2];
                String[] selectionArgs = (String[]) args[start + 3];
                return update(methodBox, url, values, selection, selectionArgs);
            } else if ("openFile".equals(name)) {
                Uri url = (Uri) args[start];
                String mode = (String) args[start + 1];
                return openFile(methodBox, url, mode);
            } else if ("openAssetFile".equals(name)) {
                Uri url = (Uri) args[start];
                String mode = (String) args[start + 1];
                return openAssetFile(methodBox, url, mode);
            } else if ("query".equals(name)) {
                Uri url = (Uri) args[start];
                String[] projection = (String[]) args[start + 1];
                String selection = null;
                String[] selectionArgs = null;
                String sortOrder = null;
                Bundle queryArgs = null;
                if (Build.VERSION.SDK_INT >= 26) {
                    queryArgs = (Bundle) args[start + 2];
                    if (queryArgs != null) {
                        selection = queryArgs.getString(QUERY_ARG_SQL_SELECTION);
                        selectionArgs = queryArgs.getStringArray(QUERY_ARG_SQL_SELECTION_ARGS);
                        sortOrder = queryArgs.getString(QUERY_ARG_SQL_SORT_ORDER);
                    }
                } else {
                    selection = (String) args[start + 2];
                    selectionArgs = (String[]) args[start + 3];
                    sortOrder = (String) args[start + 4];
                }
                return query(methodBox, url, projection, selection, selectionArgs, sortOrder, queryArgs);
            }
            return methodBox.call();
        } catch (Throwable e) {
            VLog.d("ProviderHook", "call: %s (%s) with error", method.getName(), Arrays.toString(args));
            if (e instanceof InvocationTargetException) {
                throw e.getCause();
            }
            throw e;
        }
    }

    protected void processArgs(Method method, Object... args) {

    }

    public interface HookFetcher {
        ProviderHook fetch(boolean external, IInterface provider);
    }
}
