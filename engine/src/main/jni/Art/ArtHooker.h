#ifndef VA_ART_HOOKER_H
#define VA_ART_HOOKER_H

#include <jni.h>

namespace va {

/**
 * Brings up LSPlant over ShadowHook. Needed for framework methods that decide
 * in-process and never make a binder call, which the engine's dynamic-Proxy
 * hooks cannot reach -- Context.getObbDir() being the case that forced it.
 *
 * Returns false and leaves the engine running when ART hooking is not
 * available; every caller must cope with that rather than assume success.
 */
bool InitArtHooker(JNIEnv *env);

bool ArtHookerReady();

/** target/hooker/callback are java.lang.reflect.Method and the hooker class. */
bool HookArtMethod(JNIEnv *env, jobject target, jobject hooker, jobject callback);

}  // namespace va

#endif  // VA_ART_HOOKER_H
