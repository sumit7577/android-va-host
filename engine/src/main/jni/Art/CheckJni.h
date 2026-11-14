#ifndef VA_CHECK_JNI_H
#define VA_CHECK_JNI_H

#include <jni.h>

namespace va {

/**
 * Turns ART's CheckJNI off for this process.
 *
 * Android enables CheckJNI for a debuggable app, and a guest runs inside this
 * host's process, so it inherits that. A guest's own native libraries are
 * release code its vendor never ran under CheckJNI, and CheckJNI's extra
 * signature checks are fatal: an imprecise but working JNI call aborts the
 * process instead of returning. Apps that launch normally therefore die here
 * for a reason that has nothing to do with being virtualized.
 *
 * Returns false when the symbol is absent, which leaves CheckJNI on rather
 * than failing the launch.
 */
bool DisableCheckJni(JNIEnv *env);

}  // namespace va

#endif  // VA_CHECK_JNI_H
