package com.lody.virtual.client.hook.proxies.content;

import com.lody.virtual.client.hook.base.BinderInvocationProxy;
import com.lody.virtual.client.hook.base.ReplaceGuestPkgMethodProxy;

import mirror.android.content.IContentService;

/**
 * @author Lody
 *
 * @see IContentService
 */

public class ContentServiceStub extends BinderInvocationProxy {

    public ContentServiceStub() {
        super(IContentService.Stub.asInterface, "content");
    }

    @Override
    protected void onBindMethods() {
        super.onBindMethods();
        // PORT FIX: this stub bound nothing, so every ContentResolver call
        // carrying a callingPackage went out with the GUEST package under the
        // host uid and came back as a SecurityException. Observing a content
        // URI is routine -- settings, contacts, media -- so this took out a
        // large share of real apps.
        //
        // Matching by VALUE rather than argument position: these signatures
        // have gained parameters repeatedly (attributionTag, userId), and the
        // positional helpers are exactly what keeps breaking.
        // A guest's own URI has to move to a host authority as well, or the
        // real ContentService refuses it outright; see RewriteGuestUriMethodProxy.
        addMethodProxy(new RewriteGuestUriMethodProxy("registerContentObserver"));
        addMethodProxy(new RewriteGuestUriMethodProxy("notifyChange"));
        for (String m : new String[]{
                "unregisterContentObserver",
                "requestSync",
                "sync",
                "cancelSync",
                "getSyncAdapterTypes",
                "getIsSyncable",
                "setIsSyncable",
                "getMasterSyncAutomatically",
                "setMasterSyncAutomatically",
                "getSyncAutomatically",
                "setSyncAutomatically",
                "addPeriodicSync",
                "removePeriodicSync",
                "getPeriodicSyncs",
                "isSyncActive",
                "getCurrentSyncs",
                "getSyncStatus",
        }) {
            addMethodProxy(new ReplaceGuestPkgMethodProxy(m));
        }
    }
}
