LOCAL_PATH := $(call my-dir)
MAIN_LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := va++

LOCAL_CFLAGS := -Wno-error=format-security -fpermissive -DLOG_TAG=\"VA++\"
# lsplant.hpp uses std::function and string_view; keep exceptions/RTTI off for
# the rest of the tree but allow them for the ART hooking layer.
LOCAL_CFLAGS += -fno-rtti -fno-exceptions
LOCAL_CPPFLAGS += -std=c++20

LOCAL_C_INCLUDES += $(MAIN_LOCAL_PATH)
LOCAL_C_INCLUDES += $(MAIN_LOCAL_PATH)/Foundation
LOCAL_C_INCLUDES += $(MAIN_LOCAL_PATH)/Jni
LOCAL_C_INCLUDES += $(MAIN_LOCAL_PATH)/Art
LOCAL_C_INCLUDES += $(MAIN_LOCAL_PATH)/xz

LOCAL_SRC_FILES := Jni/VAJni.cpp \
				   Foundation/IOUniformer.cpp \
				   Foundation/VMPatch.cpp \
				   Foundation/SymbolFinder.cpp \
				   Foundation/Path.cpp \
				   Foundation/SandboxFs.cpp \
				   Art/ElfImage.cpp \
				   xz/xz_crc32.c \
				   xz/xz_dec_lzma2.c \
				   xz/xz_dec_stream.c \
				   Art/ArtHooker.cpp \
				   Substrate/hde64.c \
                   Substrate/SubstrateDebug.cpp \
                   Substrate/SubstrateHook.cpp \
                   Substrate/SubstratePosixMemory.cpp \

LOCAL_LDLIBS := -llog -latomic
LOCAL_STATIC_LIBRARIES := fb
# Supplied by AGP as prefab packages; both are "stl": "none" so they coexist
# with this tree's c++_static.
LOCAL_SHARED_LIBRARIES := lsplant shadowhook

include $(BUILD_SHARED_LIBRARY)
include $(MAIN_LOCAL_PATH)/fb/Android.mk
$(call import-module,prefab/lsplant)
$(call import-module,prefab/shadowhook)