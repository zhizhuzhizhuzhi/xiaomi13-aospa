# Build AOSPA for Xiaomi 13 (fuxi)

[简体中文](README.md)

## Environment

Ubuntu 22.04 / 24.04, x86_64, 64 GB RAM, and 500 GB free disk space.

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

## Get the source

```bash
git clone https://github.com/zhizhuzhizhuzhi/xiaomi13-aospa.git aospa-fuxi-kit
mkdir android-fuxi
cd android-fuxi
repo init -u https://github.com/zhizhuzhizhuzhi/xiaomi13-aospa.git \
  -b main -m manifests/release.xml
repo sync -c --no-tags -j8
python3 ../aospa-fuxi-kit/scripts/apply.py "$PWD"
```

## Build and package

The build script downloads dependencies from [GitHub Releases](https://github.com/zhizhuzhizhuzhi/xiaomi13-aospa/releases/tag/build-deps-20261020) and prepares the kernel. Java and Clang are provided by the source tree.

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
