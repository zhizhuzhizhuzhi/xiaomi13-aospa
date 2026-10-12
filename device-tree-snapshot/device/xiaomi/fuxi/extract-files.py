#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: 2024 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

from extract_utils.fixups_blob import (
    blob_fixup,
    blob_fixups_user_type,
)
from extract_utils.fixups_lib import (
    lib_fixups,
    lib_fixups_user_type,
)
from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)

namespace_imports = [
    'vendor/xiaomi/sm8550-common',
    'vendor/qcom/common/vendor/adreno-t',
    'vendor/qcom/common/vendor/display/5.15',
]

def lib_fixup_odm_suffix(lib: str, partition: str, *args, **kwargs):
    return f'{lib}_odm' if partition in ('odm', 'vendor') else None

lib_fixups: lib_fixups_user_type = {
    **lib_fixups,
    (
        'vendor.xiaomi.hardware.fx.tunnel@1.0',
    ): lib_fixup_odm_suffix,
}

blob_fixups: blob_fixups_user_type = {
    (
        'odm/etc/camera/enhance_motiontuning.xml',
        'odm/etc/camera/night_motiontuning.xml',
        'odm/etc/camera/motiontuning.xml',
    ): blob_fixup()
        .regex_replace(
            'xml=version',
            'xml version',
        ),
    (
        'odm/lib64/libailab_rawhdr.so',
        'odm/lib64/libxmi_high_dynamic_range_cdsp.so',
    ): blob_fixup()
        .strip_debug_sections(),
    'odm/lib64/libwrapper_dlengine.so': blob_fixup()
        .add_needed('liblog.so'),
}

module = ExtractUtilsModule(
    'fuxi',
    'xiaomi',
    blob_fixups=blob_fixups,
    lib_fixups=lib_fixups,
    namespace_imports=namespace_imports,
)

if __name__ == '__main__':
    utils = ExtractUtils.device_with_common(
        module, 'sm8550-common', module.vendor
    )
    utils.run()
