# Xiaomi SM8550 platform configuration

Shared platform configuration for `fuxi` and `sheng` in one AOSPA source tree.
Device-specific configuration, kernels and proprietary inputs remain in their
respective device and vendor directories.

## Build

From the populated Android source root:

```sh
bash build-device.sh fuxi user -j32 target-files-package
bash build-device.sh sheng userdebug -j32 target-files-package
```

| Product | Output directory |
| --- | --- |
| fuxi user | `out-user-release` |
| fuxi userdebug | `out` |
| sheng user | `out-sheng-user` |
| sheng userdebug | `out-sheng-userdebug` |

`build-device.sh` selects `cp2a` and calls the platform build directly.
`build/release` contains the common Bluetooth and window lifecycle flags.
Each device supplies its own kernel capability flags.
