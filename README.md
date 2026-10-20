# Xiaomi 13（fuxi）AOSPA 构建

[English](README_EN.md)

## 环境

Ubuntu 22.04 / 24.04，x86_64，64 GB 内存，500 GB 可用磁盘空间。

```bash
sudo apt update
sudo apt install git git-lfs curl python3 python-is-python3 build-essential \
  zip unzip rsync bc bison flex gperf ccache libssl-dev libelf-dev \
  libncurses-dev libxml2-utils xsltproc zlib1g-dev liblz4-tool
mkdir -p "$HOME/bin"
curl -o "$HOME/bin/repo" https://storage.googleapis.com/git-repo-downloads/repo
chmod +x "$HOME/bin/repo"
export PATH="$HOME/bin:$PATH"
git lfs install
```

## 获取源码

```bash
git clone https://github.com/zhizhuzhizhuzhi/xiaomi13-aospa.git aospa-fuxi-kit
mkdir android-fuxi
cd android-fuxi
repo init -u https://github.com/zhizhuzhizhuzhi/xiaomi13-aospa.git \
  -b main -m manifests/release.xml
repo sync -c --no-tags -j8
python3 ../aospa-fuxi-kit/scripts/apply.py "$PWD"
```

## 编译和打包

构建脚本自动从 [GitHub Releases](https://github.com/zhizhuzhizhuzhi/xiaomi13-aospa/releases/tag/build-deps-20261020) 下载依赖并准备内核。Java 和 Clang 使用源码树自带版本。

### userdebug

```bash
BUILD_NUMBER=20261020 bash ../aospa-fuxi-kit/scripts/build.sh "$PWD" userdebug "$(nproc)"
python3 ../aospa-fuxi-kit/scripts/package.py "$PWD" \
  out/target/product/fuxi/obj/PACKAGING/target_files_intermediates/aospa_fuxi-target_files.zip \
  "$PWD/aospa_fuxi-fastboot-userdebug.zip"
```

### user

```bash
BUILD_NUMBER=20261020 bash ../aospa-fuxi-kit/scripts/build.sh "$PWD" user "$(nproc)"
OUT_DIR=out-user-release python3 ../aospa-fuxi-kit/scripts/package.py "$PWD" \
  out-user-release/target/product/fuxi/obj/PACKAGING/target_files_intermediates/aospa_fuxi-target_files.zip \
  "$PWD/aospa_fuxi-fastboot-user.zip"
```
