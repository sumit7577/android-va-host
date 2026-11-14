#include "JniTrace.h"

#include <string.h>
#include <sys/mman.h>

#include <fb/include/fb/ALog.h>

namespace va {
namespace {

const JNINativeInterface *gOrig = nullptr;
JNINativeInterface gTable;

jclass tr_FindClass(JNIEnv *env, const char *name) {
    jclass c = gOrig->FindClass(env, name);
    if (c == nullptr) {
        ALOGW("JniTrace: FindClass(%s) -> NULL", name ? name : "?");
    }
    return c;
}

// Failures only. Logging every lookup buries a working guest's log in
// hundreds of lines; a lookup that came back empty is the one that matters.
jmethodID tr_GetMethodID(JNIEnv *env, jclass c, const char *name, const char *sig) {
    jmethodID m = gOrig->GetMethodID(env, c, name, sig);
    if (m == nullptr) {
        ALOGW("JniTrace: GetMethodID(%s%s) -> NULL", name ? name : "?", sig ? sig : "");
    }
    return m;
}

jmethodID tr_GetStaticMethodID(JNIEnv *env, jclass c, const char *name, const char *sig) {
    jmethodID m = gOrig->GetStaticMethodID(env, c, name, sig);
    if (m == nullptr) {
        ALOGW("JniTrace: GetStaticMethodID(%s%s) -> NULL", name ? name : "?", sig ? sig : "");
    }
    return m;
}

jfieldID tr_GetFieldID(JNIEnv *env, jclass c, const char *name, const char *sig) {
    jfieldID f = gOrig->GetFieldID(env, c, name, sig);
    if (f == nullptr) {
        ALOGW("JniTrace: GetFieldID(%s%s) -> NULL", name ? name : "?", sig ? sig : "");
    }
    return f;
}

jfieldID tr_GetStaticFieldID(JNIEnv *env, jclass c, const char *name, const char *sig) {
    jfieldID f = gOrig->GetStaticFieldID(env, c, name, sig);
    if (f == nullptr) {
        ALOGW("JniTrace: GetStaticFieldID(%s%s) -> NULL", name ? name : "?", sig ? sig : "");
    }
    return f;
}

jint tr_RegisterNatives(JNIEnv *env, jclass c, const JNINativeMethod *methods, jint count) {
    jint ret = gOrig->RegisterNatives(env, c, methods, count);
    // methods[0] is read straight from the caller's own array: no JNI call, so
    // nothing here can disturb the state JNI_OnLoad is in.
    const char *first = (count > 0 && methods != nullptr && methods[0].name != nullptr)
                        ? methods[0].name : "?";
    ALOGW("JniTrace: RegisterNatives(count=%d, first=%s) -> %d", count, first, ret);
    return ret;
}

}  // namespace

bool StartJniTrace(JNIEnv *env) {
    if (env == nullptr || gOrig != nullptr) {
        return gOrig != nullptr;
    }
    const JNINativeInterface *orig = env->functions;
    if (orig == nullptr) {
        return false;
    }
    gOrig = orig;
    memcpy(&gTable, orig, sizeof(JNINativeInterface));
    gTable.FindClass = tr_FindClass;
    gTable.GetMethodID = tr_GetMethodID;
    gTable.GetStaticMethodID = tr_GetStaticMethodID;
    gTable.GetFieldID = tr_GetFieldID;
    gTable.GetStaticFieldID = tr_GetStaticFieldID;
    gTable.RegisterNatives = tr_RegisterNatives;
    // JNIEnv::functions is a plain writable field of the thread's JNIEnvExt;
    // the table it points at is the read-only shared one, which is why this
    // installs a copy rather than editing it in place.
    env->functions = &gTable;
    ALOGI("JniTrace: installed on this thread");
    return true;
}

}  // namespace va
