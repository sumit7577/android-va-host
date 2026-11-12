package mirror.android.content;

import java.util.List;

import mirror.RefClass;
import mirror.RefObject;

/**
 * @author Lody
 */

public class IntentFilter {
    public static Class Class = RefClass.load(IntentFilter.class, android.content.IntentFilter.class);
    // PORT FIX: this was ArrayList<String> until the framework switched it to
    // android.util.ArraySet. Typing it as Object keeps the mirror honest and
    // moves the decision to the one place that actually reads it.
    public static RefObject<Object> mActions;
}
