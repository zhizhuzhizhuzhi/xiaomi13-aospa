#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
set -eo pipefail
tree=$(realpath "${1:?Usage: build.sh TREE DEVICE VARIANT [make arguments...]}")
device=${2:?}; variant=${3:?}; shift 3
case "$device:$variant" in
    fuxi:user) out=out-user-release; lock=.fuxi-build.lock ;;
    fuxi:userdebug) out=out; lock=.fuxi-build.lock ;;
    sheng:user) out=out-sheng-user; lock=.sheng-build.lock ;;
    sheng:userdebug) out=out-sheng-userdebug; lock=.sheng-build.lock ;;
    *) printf 'Unsupported product: %s %s\n' "$device" "$variant" >&2; exit 1 ;;
esac
cd "$tree"
exec 9>"$lock"
flock -n 9
export OUT_DIR="$out"
export TARGET_PRODUCT="aospa_$device" TARGET_RELEASE=cp2a TARGET_BUILD_VARIANT="$variant"
export AOSPA_BUILD="$device" BUILD_USERNAME=nobody BUILD_HOSTNAME=android-build
export BUILD_NUMBER=${BUILD_NUMBER:-20261020}
source build/envsetup.sh
set_stuff_for_environment
if [ "$#" -eq 0 ]; then set -- target-files-package; fi
m "$@"
