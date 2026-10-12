# Stock QESDK checks both ABI files even in a 64-bit-only userspace.
# Retain these original 32-bit assets for its environment verification;
# they do not enable 32-bit applications or replace the linked 64-bit libraries.
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := fuxi_qesdk_env_lib32
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := ../../../../vendor/xiaomi/sm8550-common/proprietary/vendor/lib/libqesdk2_0.so
LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR)/lib
LOCAL_MODULE_STEM := libqesdk2_0.so
include $(BUILD_PREBUILT)
include $(CLEAR_VARS)
LOCAL_MODULE := fuxi_qesdk_env_manager32
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := ../../../../vendor/xiaomi/sm8550-common/proprietary/vendor/lib/libqesdk_manager.so
LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR)/lib
LOCAL_MODULE_STEM := libqesdk_manager.so
include $(BUILD_PREBUILT)
