APP_ABI := armeabi-v7a arm64-v8a
APP_PLATFORM := android-21
APP_STL := c++_static
APP_OPTIM := release
# 2017 code vs clang 18: fbjni uses C++ VLAs, now an error by default.
APP_CPPFLAGS += -Wno-error -Wno-vla-cxx-extension
APP_CFLAGS   += -Wno-error
# Android 15 ships 16 KB-page devices. ndk-build does not pass this by default
# in r27 (it became the default in r28), so without it every .so here links
# 4 KB-aligned and will not load on a 16 KB-page device. Verify with
# `llvm-readelf -l` -- LOAD alignment must read 0x4000, not 0x1000.
APP_LDFLAGS  += -Wl,-z,max-page-size=16384
VA_ROOT          := $(call my-dir)
NDK_MODULE_PATH  := $(NDK_MODULE_PATH):$(VA_ROOT)
