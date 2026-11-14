package mirror.android.os;

import android.os.Parcel;

import mirror.RefClass;
import mirror.RefObject;


public class BaseBundle {
    public static Class<?> TYPE = RefClass.load(BaseBundle.class, "android.os.BaseBundle");
    public static RefObject<Parcel> mParcelledData;

    /**
     * Android 13+ records whether the parcelled data came from native code and
     * refuses to unparcel it in Java if so. Replacing mParcelledData without
     * clearing this leaves the flag stale and trips that assertion.
     */
    public static RefObject<Boolean> mParcelledByNative;
}