APP_ABI := armeabi-v7a
APP_PLATFORM := android-21
APP_STL := c++_static
APP_OPTIM := release
# 2017 code vs clang 18: fbjni uses C++ VLAs, now an error by default.
APP_CPPFLAGS += -Wno-error -Wno-vla-cxx-extension
APP_CFLAGS   += -Wno-error
VA_ROOT          := $(call my-dir)
NDK_MODULE_PATH  := $(NDK_MODULE_PATH):$(VA_ROOT)
