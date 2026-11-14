#include "CheckJni.h"

#include <fb/include/fb/ALog.h>

#include "ElfImage.h"

namespace va {

bool DisableCheckJni(JNIEnv *env) {
    JavaVM *vm = nullptr;
    if (env == nullptr || env->GetJavaVM(&vm) != JNI_OK || vm == nullptr) {
        return false;
    }

    ElfImage art("libart.so");
    if (!art.valid()) {
        ALOGW("CheckJni: libart.so not readable");
        return false;
    }

    // JavaVMExt derives from JavaVM, so the JavaVM* is the `this` pointer.
    // Returns the previous setting, which is also how we report what changed.
    using SetCheckJniEnabled = bool (*)(void *, bool);
    auto fn = reinterpret_cast<SetCheckJniEnabled>(
            art.symbol("_ZN3art9JavaVMExt18SetCheckJniEnabledEb"));
    if (fn == nullptr) {
        fn = reinterpret_cast<SetCheckJniEnabled>(
                art.symbolByPrefix("_ZN3art9JavaVMExt18SetCheckJniEnabled"));
    }
    if (fn == nullptr) {
        ALOGW("CheckJni: SetCheckJniEnabled absent from %s (%zu symbols)",
              art.path().c_str(), art.symbolCount());
        return false;
    }

    bool was = fn(vm, false);
    ALOGI("CheckJni: disabled (was %s)", was ? "on" : "off");
    return true;
}

}  // namespace va
