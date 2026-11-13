#include "ArtHooker.h"

#include <jni.h>
#include <shadowhook.h>

#include <mutex>
#include <string>
#include <unordered_map>

#include <fb/include/fb/ALog.h>
#include <lsplant.hpp>

#include "ElfImage.h"

namespace va {

namespace {

/**
 * LSPlant hands inline_unhooker the FUNCTION it wants unhooked, while
 * ShadowHook unhooks by the STUB handle its hook call returned. Nothing
 * carries that handle between the two, so it is kept here.
 */
std::mutex gStubsLock;
std::unordered_map<void *, void *> gStubs;

ElfImage *gArt = nullptr;
bool gReady = false;

void *InlineHook(void *target, void *hooker) {
    void *original = nullptr;
    void *stub = shadowhook_hook_sym_addr(target, hooker, &original);
    if (stub == nullptr) {
        ALOGE("ArtHooker: inline hook failed at %p (%s)", target, shadowhook_to_errmsg(shadowhook_get_errno()));
        return nullptr;
    }
    {
        std::lock_guard<std::mutex> guard(gStubsLock);
        gStubs[target] = stub;
    }
    return original;
}

bool InlineUnhook(void *func) {
    void *stub = nullptr;
    {
        std::lock_guard<std::mutex> guard(gStubsLock);
        auto it = gStubs.find(func);
        if (it == gStubs.end()) {
            return false;
        }
        stub = it->second;
        gStubs.erase(it);
    }
    return shadowhook_unhook(stub) == 0;
}

}  // namespace

bool ArtHookerReady() { return gReady; }

bool InitArtHooker(JNIEnv *env) {
    if (gReady) {
        return true;
    }

    // SHARED mode: ART methods may be hooked by more than one thing, and
    // failing a second hook would be worse than the small overhead.
    if (shadowhook_init(SHADOWHOOK_MODE_SHARED, false) != 0) {
        ALOGE("ArtHooker: shadowhook_init failed: %s",
              shadowhook_to_errmsg(shadowhook_get_errno()));
        return false;
    }

    gArt = new ElfImage("libart.so");
    if (!gArt->valid()) {
        ALOGE("ArtHooker: libart.so not readable; ART hooking unavailable");
        return false;
    }
    if (gArt->symbolCount() == 0) {
        ALOGE("ArtHooker: libart.so exposes no symbols; ART hooking unavailable");
        return false;
    }

    lsplant::v2::InitInfo info{
            .inline_hooker = InlineHook,
            .inline_unhooker = InlineUnhook,
            .art_symbol_resolver = [](std::string_view name) -> void * {
                return gArt->symbol(name);
            },
            .art_symbol_prefix_resolver = [](std::string_view prefix) -> void * {
                return gArt->symbolByPrefix(prefix);
            },
    };

    gReady = lsplant::v2::Init(env, info);
    ALOGI("ArtHooker: lsplant init %s (%zu symbols from %s)",
          gReady ? "OK" : "FAILED", gArt->symbolCount(), gArt->path().c_str());
    return gReady;
}

bool HookArtMethod(JNIEnv *env, jobject target, jobject hooker, jobject callback) {
    if (!gReady) {
        return false;
    }
    return lsplant::v2::Hook(env, target, hooker, callback) != nullptr;
}

}  // namespace va
