#!/usr/bin/env bash
set -eo pipefail
tree=$(realpath "${1:?Usage: build.sh ANDROID_TREE [userdebug|user] [jobs]}")
shift
exec bash "$tree/vendor/xiaomi/sm8550-integration/tools/build.sh" "$tree" fuxi "$@"
