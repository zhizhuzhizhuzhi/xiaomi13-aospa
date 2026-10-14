#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
set -eo pipefail
kit=$(cd "$(dirname "$0")/.." && pwd)
tree=${1:?Usage: build.sh ANDROID_TREE [userdebug|user] [jobs]}
type=${2:-userdebug};jobs=${3:-8}
case "$type" in userdebug|user) ;; *) echo "Unsupported build type" >&2;exit 1;; esac
cd "$tree"
exec 9>.fuxi-build.lock
flock -n 9 || { echo 'Another fuxi build is using this tree' >&2;exit 1; }
python3 "$kit/scripts/apply.py" "$PWD" --verify-applied
python3 "$kit/scripts/fetch-dependencies.py" "$PWD"
test -s vendor/xiaomi/camera/proprietary/system/priv-app/MiuiCamera/MiuiCamera.apk
test -s device/xiaomi/fuxi/overlays/SystemUI/res/drawable-nodpi/rounded_corner_top.png
test -s vendor/xiaomi/sm8550-common/proprietary/vendor/app/SoterService/SoterService.apk
export BUILD_NUMBER=${BUILD_NUMBER:-20261015}
if [ -z "${OUT_DIR:-}" ]; then
  if [ "$type" = user ]; then export OUT_DIR="$PWD/out-user-release"; else export OUT_DIR="$PWD/out"; fi
else
  case "$OUT_DIR" in /*) ;; *) export OUT_DIR="$PWD/$OUT_DIR" ;; esac
fi
source build/envsetup.sh
lunch "aospa_fuxi-cp2a-$type"
kernel_out="$(get_build_var KERNEL_PREBUILT_DIR)"
case "$kernel_out" in /*) ;; *) kernel_out="$PWD/$kernel_out" ;; esac
export PATH="$kernel_out/host/bin:$PATH"
export LD_LIBRARY_PATH="$kernel_out/host/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
if [ "$type" = user ];then export LTO=full;else export LTO=thin LZ4_RAMDISK_COMPRESS_ARGS=--fast;fi
ANDROID_KERNEL_OUT="$kernel_out" KERNEL_VARIANT=gki ./kernel_platform/build/android/prepare_vendor.sh kalama
m otapackage otatools target-files-package -j"$jobs"
echo 'Build complete. Run scripts/package.py with the new target_files archive or directory.'
