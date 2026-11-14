#ifndef VA_JNI_TRACE_H
#define VA_JNI_TRACE_H

#include <jni.h>

namespace va {

/**
 * Reports the JNI lookups a guest's native code makes and does not get.
 *
 * A packer's JNI_OnLoad decides whether to register the code that decrypts the
 * app, and when it decides not to, it returns a good version and registers
 * nothing -- no error, no syscall, nothing to see from outside. This swaps the
 * calling thread's JNI function table for one that reports every FindClass,
 * GetMethodID and GetFieldID that comes back empty, plus every RegisterNatives,
 * so the refusal has a reason attached to it.
 *
 * Diagnostic only; every call is forwarded unchanged. Affects the calling
 * thread, which is where JNI_OnLoad runs.
 */
bool StartJniTrace(JNIEnv *env);

}  // namespace va

#endif  // VA_JNI_TRACE_H
