# Xiaomi 13（fuxi）AOSPA Android 17

[English](README_EN.md)

本仓库提供设备构建入口。源码版本由 [manifest](https://github.com/zhizhuzhizhuzhi/android_local_manifests/tree/aospa-17) 固定；公共补丁和构建工具统一放在 [SM8550 integration](https://github.com/zhizhuzhizhuzhi/android_vendor_xiaomi_sm8550-integration/tree/aospa-17)，设备配置直接来自独立的 [device 仓库](https://github.com/zhizhuzhizhuzhi/android_device_xiaomi_fuxi/tree/aospa-17)。

## 获取源码和构建

Ubuntu 22.04 / 24.04 x86_64，安装 Android 构建依赖、Git、Git LFS、repo 和 Python 3.11+。准备与清单匹配的输入目录，保留其中记录的相对路径。

```sh
mkdir android-fuxi && cd android-fuxi
repo init -u https://github.com/zhizhuzhizhuzhi/android_local_manifests.git -b aospa-17 -m fuxi.xml --git-lfs
repo sync -c --no-tags -j8
python3 vendor/xiaomi/sm8550-integration/tools/prepare-source.py "$PWD" --device fuxi
python3 vendor/xiaomi/sm8550-integration/tools/prepare-inputs.py "$PWD" --device fuxi \
  --source /path/to/prepared-fuxi-inputs --replace-stock-inputs
BUILD_NUMBER=20261121 bash vendor/xiaomi/sm8550-integration/tools/build.sh "$PWD" fuxi userdebug 16
```

将 `userdebug` 改为 `user` 可选择对应构建变体。输入哈希分别放在公共集成仓库的 `common/inputs.json` 和 `profiles/fuxi/inputs.json`；已有匹配文件直接复用。

## 打包

```sh
python3 vendor/xiaomi/sm8550-integration/tools/package.py fuxi "$PWD" \
  /path/to/target_files.zip /path/to/aospa_fuxi-fastboot.zip
```

`scripts/` 中的兼容入口调用源码树里的同一套工具。本仓库不再复制 device 源码或公共补丁。

AOSPA 与 Cloyuri 使用各自的源码分支和 manifest。许可证见 [LICENSE.md](LICENSE.md)。
