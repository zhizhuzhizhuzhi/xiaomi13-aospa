#
# Copyright (C) 2024 Paranoid Android
#
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from sm8550-common
$(call inherit-product, device/xiaomi/sm8550-common/common.mk)

# Get non-open-source specific aspects
$(call inherit-product, vendor/xiaomi/fuxi/fuxi-vendor.mk)

# Soong namespaces
PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH)

# Overlays
PRODUCT_PACKAGES += \
    FuxiDolby \
    FuxiFrameworks \
    FuxiSettings2211133C \
    FuxiSettings2211133G \
    FuxiSettingsProvider \
    FuxiSystemUI \
    FuxiWifiRes \
    FuxiWifiResMainline \
