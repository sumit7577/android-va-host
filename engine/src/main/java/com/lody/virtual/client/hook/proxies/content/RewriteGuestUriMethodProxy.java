package com.lody.virtual.client.hook.proxies.content;

import android.content.pm.ProviderInfo;
import android.net.Uri;

import com.lody.virtual.client.hook.base.ReplaceGuestPkgMethodProxy;
import com.lody.virtual.client.ipc.ServiceManagerNative;
import com.lody.virtual.client.ipc.VPackageManager;
import com.lody.virtual.os.VUserHandle;

import java.lang.reflect.Method;

/**
 * Sends a guest's own content URI to the real ContentService under a host
 * authority instead of the guest's.
 *
 * Android 14 made ContentService ask the real ActivityManager whether the
 * caller may reach a URI's authority before it will register an observer or
 * deliver a change. The authority belongs to the guest and the process belongs
 * to the host, so the real ActivityManager resolves that authority to the real
 * installation of the guest app and refuses:
 *
 *   SecurityException: Permission Denial: opening provider <provider>
 *   from ProcessRecord{... <host>:p0 ...}
 *
 * which arrives in the middle of an ordinary ContentProvider.query, because
 * Cursor.setNotificationUri registers an observer. Observing a URI is routine,
 * so this stopped any guest that has a provider of its own.
 *
 * The rewrite is a pure function of the URI and is applied to registration and
 * to notification alike, so change delivery inside the container still works.
 * The original authority is kept as the first path segment, so two guest
 * authorities sharing a path do not notify each other. An observer is handed
 * the rewritten URI; only the authority moves, the rest of the URI is intact.
 */
public class RewriteGuestUriMethodProxy extends ReplaceGuestPkgMethodProxy {

    public RewriteGuestUriMethodProxy(String name) {
        super(name);
    }

    @Override
    public Object call(Object who, Method method, Object... args) throws Throwable {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Uri) {
                    args[i] = rewrite((Uri) args[i]);
                } else if (args[i] instanceof Uri[]) {
                    Uri[] uris = (Uri[]) args[i];
                    for (int j = 0; j < uris.length; j++) {
                        uris[j] = rewrite(uris[j]);
                    }
                }
            }
        }
        return super.call(who, method, args);
    }

    private Uri rewrite(Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme())) {
            return uri;
        }
        String authority = uri.getAuthority();
        if (authority == null || !isGuestAuthority(authority)) {
            return uri;
        }
        Uri.Builder b = new Uri.Builder()
                .scheme("content")
                // Already fully qualified with the host package at startup.
                .authority(ServiceManagerNative.SERVICE_CP_AUTH)
                .appendPath(authority);
        for (String segment : uri.getPathSegments()) {
            b.appendPath(segment);
        }
        String query = uri.getEncodedQuery();
        if (query != null) {
            b.encodedQuery(query);
        }
        return b.build();
    }

    private boolean isGuestAuthority(String authority) {
        try {
            ProviderInfo info = VPackageManager.get()
                    .resolveContentProvider(authority, 0, VUserHandle.myUserId());
            return info != null && isAppPkg(info.packageName);
        } catch (Throwable e) {
            return false;
        }
    }
}
