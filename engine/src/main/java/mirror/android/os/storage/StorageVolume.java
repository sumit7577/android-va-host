package mirror.android.os.storage;

import java.io.File;

import mirror.RefClass;
import mirror.RefObject;

/**
 * StorageVolume carries the external-storage roots that Environment builds
 * getObbDir() and getExternalFilesDir() from. Both path fields are final, so
 * they are rewritten reflectively to point a guest at container-owned storage.
 */
public class StorageVolume {

    public static Class<?> TYPE = RefClass.load(StorageVolume.class, "android.os.storage.StorageVolume");

    public static RefObject<File> mPath;

    /** Present since API 24; the pre-FUSE path behind mPath. */
    public static RefObject<File> mInternalPath;
}
