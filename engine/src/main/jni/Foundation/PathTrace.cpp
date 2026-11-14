#include "PathTrace.h"

#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include <fb/include/fb/ALog.h>
#include <shadowhook.h>

namespace va {
namespace {

bool sStarted = false;

// Only the roots where redirection decides the answer. /proc, /sys and /dev
// misses are constant background noise and would bury the signal.
bool endsWith(const char *path, const char *suffix) {
    size_t lp = strlen(path), ls = strlen(suffix);
    return lp >= ls && strcmp(path + (lp - ls), suffix) == 0;
}

bool worthReporting(const char *path) {
    if (path == nullptr) {
        return false;
    }
    if (strncmp(path, "/data/", 6) != 0
        && strncmp(path, "/storage/", 9) != 0
        && strncmp(path, "/sdcard", 7) != 0
        && strncmp(path, "/mnt/", 5) != 0) {
        return false;
    }
    // ART probes for compiled artefacts that a container never produces, and
    // misses them every time for every guest. That is expected, it is dozens of
    // lines per launch, and it hides the one path a guest actually wanted.
    if (strncmp(path, "/data/dalvik-cache/", 19) == 0
        || strncmp(path, "/data/misc/apexdata/", 20) == 0
        || strncmp(path, "/data/system/theme/", 19) == 0) {
        return false;
    }
    return !(endsWith(path, ".vdex") || endsWith(path, ".odex") || endsWith(path, ".dm")
             || endsWith(path, ".art") || endsWith(path, ".prof"));
}

// The surfaces a protector reads to decide it is being virtualized. These
// calls SUCCEED, so they have to be reported on success, unlike a missing file.
bool isDetectionSurface(const char *path) {
    if (path == nullptr || strncmp(path, "/proc/", 6) != 0) {
        return false;
    }
    return strstr(path, "maps") != nullptr
           || strstr(path, "status") != nullptr
           || strstr(path, "cmdline") != nullptr
           || strstr(path, "mountinfo") != nullptr
           || strstr(path, "mounts") != nullptr
           || strstr(path, "task") != nullptr;
}

// Logging clobbers errno, and the caller is about to read it.
void report(const char *fn, const char *path, int ret, int err) {
    if (isDetectionSurface(path)) {
        ALOGW("PathTrace: READS %s(%s) -> %d", fn, path, ret);
        errno = err;
        return;
    }
    if (ret >= 0 || !worthReporting(path)) {
        return;
    }
    ALOGW("PathTrace: %s(%s) -> errno %d", fn, path, err);
    errno = err;
}

// A protector asks for ro.debuggable, ro.secure, ro.build.tags and friends
// before it will run. Naming what it asks for says what it objects to.
int pt_sysprop(const char *name, char *value) {
    SHADOWHOOK_STACK_SCOPE();
    int ret = SHADOWHOOK_CALL_PREV(pt_sysprop, name, value);
    int err = errno;
    if (name != nullptr) {
        ALOGW("PathTrace: getprop(%s) = %s", name, (ret > 0 && value) ? value : "");
    }
    errno = err;
    return ret;
}

int pt___openat(int fd, const char *path, int flags, int mode) {
    SHADOWHOOK_STACK_SCOPE();
    int ret = SHADOWHOOK_CALL_PREV(pt___openat, fd, path, flags, mode);
    int err = errno;
    report("openat", path, ret, err);
    return ret;
}

int pt_faccessat(int dirfd, const char *path, int mode, int flags) {
    SHADOWHOOK_STACK_SCOPE();
    int ret = SHADOWHOOK_CALL_PREV(pt_faccessat, dirfd, path, mode, flags);
    int err = errno;
    report("faccessat", path, ret, err);
    return ret;
}

int pt_fstatat64(int dirfd, const char *path, struct stat *buf, int flags) {
    SHADOWHOOK_STACK_SCOPE();
    int ret = SHADOWHOOK_CALL_PREV(pt_fstatat64, dirfd, path, buf, flags);
    int err = errno;
    report("fstatat", path, ret, err);
    return ret;
}

int pt_stat(const char *path, struct stat *buf) {
    SHADOWHOOK_STACK_SCOPE();
    int ret = SHADOWHOOK_CALL_PREV(pt_stat, path, buf);
    int err = errno;
    report("stat", path, ret, err);
    return ret;
}

int pt_lstat(const char *path, struct stat *buf) {
    SHADOWHOOK_STACK_SCOPE();
    int ret = SHADOWHOOK_CALL_PREV(pt_lstat, path, buf);
    int err = errno;
    report("lstat", path, ret, err);
    return ret;
}

int pt_access(const char *path, int mode) {
    SHADOWHOOK_STACK_SCOPE();
    int ret = SHADOWHOOK_CALL_PREV(pt_access, path, mode);
    int err = errno;
    report("access", path, ret, err);
    return ret;
}

// Referenced only when the property trace above is turned back on.
__attribute__((unused)) void *kSyspropProbe = (void *) pt_sysprop;

bool hook(const char *name, void *proxy) {
    void *stub = shadowhook_hook_sym_name("libc.so", name, proxy, nullptr);
    if (stub == nullptr) {
        ALOGW("PathTrace: cannot hook %s: %s", name,
              shadowhook_to_errmsg(shadowhook_get_errno()));
        return false;
    }
    return true;
}

}  // namespace

bool StartPathTrace() {
    if (sStarted) {
        return true;
    }
    // SHARED, to agree with the mode ArtHooker asks for -- shadowhook keeps the
    // mode of whichever call arrives first, and a mismatch would break the
    // other caller's hooks. Shared mode also refuses re-entry into a proxy by
    // default, which is what keeps logging from recursing through open().
    if (shadowhook_init(SHADOWHOOK_MODE_SHARED, false) != 0) {
        ALOGW("PathTrace: shadowhook_init failed: %s",
              shadowhook_to_errmsg(shadowhook_get_errno()));
        return false;
    }
    int ok = 0;
    ok += hook("__openat", (void *) pt___openat) ? 1 : 0;
    ok += hook("faccessat", (void *) pt_faccessat) ? 1 : 0;
    ok += hook("fstatat64", (void *) pt_fstatat64) ? 1 : 0;
    ok += hook("stat", (void *) pt_stat) ? 1 : 0;
    ok += hook("lstat", (void *) pt_lstat) ? 1 : 0;
    ok += hook("access", (void *) pt_access) ? 1 : 0;
    // __system_property_get is deliberately not hooked: MIUI reads properties
    // constantly and the trace drowns everything else. Hook it when a guest's
    // own decision looks like it turns on a property.
    sStarted = ok > 0;
    ALOGI("PathTrace: %d/6 libc hooks installed", ok);
    return sStarted;
}

}  // namespace va
