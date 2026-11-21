#!/usr/bin/env bash
set -eo pipefail
tree=$(realpath "${1:?Usage: build.sh TREE DEVICE VARIANT [make arguments...]}")
shift
exec bash "$tree/build-device.sh" "$@"
