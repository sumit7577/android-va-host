package com.lody.virtual.helper.compat;

import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;

import mirror.android.os.BaseBundle;
import mirror.android.os.BundleICS;

/**
 * @author Lody
 */
public class BundleCompat {
    public static IBinder getBinder(Bundle bundle, String key) {
        if (Build.VERSION.SDK_INT >= 18) {
            return bundle.getBinder(key);
        } else {
            return mirror.android.os.Bundle.getIBinder.call(bundle, key);
        }
    }

    public static void putBinder(Bundle bundle, String key, IBinder value) {
        if (Build.VERSION.SDK_INT >= 18) {
            bundle.putBinder(key, value);
        } else {
            mirror.android.os.Bundle.putIBinder.call(bundle, key, value);
        }
    }

    public static void clearParcelledData(Bundle bundle) {
        Parcel obtain = Parcel.obtain();
        obtain.writeInt(0);
        obtain.setDataPosition(0);
        Parcel parcel;
        // PORT FIX: from Android 13 BaseBundle also tracks whether the data was
        // parcelled by native code, and refuses to unparcel it in Java:
        //   IllegalStateException at Preconditions.checkState
        //     at BaseBundle.unparcel -> Activity.restoreHasCurrentPermissionRequest
        // which killed any activity restored with saved state. The Parcel being
        // installed below is written here in Java, so the flag is stale by
        // definition -- clear it with the data it describes.
        if (BaseBundle.mParcelledByNative != null) {
            try {
                BaseBundle.mParcelledByNative.set(bundle, false);
            } catch (Throwable ignored) {
                // Absent before API 33; nothing to clear.
            }
        }
        if (BaseBundle.TYPE != null) {
            parcel = BaseBundle.mParcelledData.get(bundle);
            if (parcel != null) {
                parcel.recycle();
            }
            BaseBundle.mParcelledData.set(bundle, obtain);
        } else if (BundleICS.TYPE != null) {
            parcel = BundleICS.mParcelledData.get(bundle);
            if (parcel != null) {
                parcel.recycle();
            }
            BundleICS.mParcelledData.set(bundle, obtain);
        }
    }
}
