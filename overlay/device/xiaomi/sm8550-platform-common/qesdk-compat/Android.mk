# Matching stock ABI assets for QESDK environment checks.
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := sm8550_qesdk_env_lib32
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := ../proprietary/vendor/lib/libqesdk2_0.so
LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR)/lib
LOCAL_MODULE_STEM := libqesdk2_0.so
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE := sm8550_qesdk_env_manager32
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := ../proprietary/vendor/lib/libqesdk_manager.so
LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR)/lib
LOCAL_MODULE_STEM := libqesdk_manager.so
include $(BUILD_PREBUILT)
