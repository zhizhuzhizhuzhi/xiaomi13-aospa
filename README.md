# Xiaomi 13 / fuxi — AOSPA 17

基于 AOSPA calcite，适配 Xiaomi 13（SM8550）。设备树、固定 manifest 和本地改动对应 20261012 发布版。

[线刷包](https://sourceforge.net/projects/xiaomi13-aospa/files/20261012/) · [构建依赖](https://github.com/zhizhuzhizhuzhi/xiaomi13-aospa/releases/tag/build-deps-20261012) · [已知问题](KNOWN_ISSUES.md)

## 环境

x86_64 Linux，建议 64 GB RAM / 500 GB 可用 SSD。以下以 Ubuntu 为例，Java 和 Clang 使用源码树预置版本。

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

## 同步源码

```bash
git clone https://github.com/zhizhuzhizhuzhi/xiaomi13-aospa.git xiaomi13-aospa-kit
mkdir android-fuxi
cd android-fuxi
repo init -u https://github.com/zhizhuzhizhuzhi/xiaomi13-aospa.git \
  -b main -m manifests/release.xml
repo sync -c --no-tags -j4 --fail-fast
python3 ../xiaomi13-aospa-kit/scripts/apply.py "$PWD"
```

厂商仓库使用 Git LFS 的，确保已执行 `git lfs pull`。

## 准备构建依赖

```bash
python3 ../xiaomi13-aospa-kit/scripts/fetch-dependencies.py "$PWD"
```

脚本下载 Release 中固定版本的相机、libgui 与圆角资源包，校验 SHA256 后放到对应目录。缓存位于 `out/fuxi-build-dependencies/`。

已有依赖 ZIP 可直接使用：

```bash
python3 ../xiaomi13-aospa-kit/scripts/fetch-dependencies.py "$PWD" \
  --archive /path/to/fuxi-build-dependencies-20261012.zip
```

## 编译

```bash
bash ../xiaomi13-aospa-kit/scripts/build.sh "$PWD" userdebug 8
```

目标为 `aospa_fuxi-cp2a-userdebug`。脚本依次构建 kalama GKI、模块、合并 DT、OTA、otatools 和 target_files。

编译 `user`：

```bash
bash ../xiaomi13-aospa-kit/scripts/build.sh "$PWD" user 8
```

## Fastboot 打包

```bash
python3 ../xiaomi13-aospa-kit/scripts/package.py "$PWD" \
  out/target/product/fuxi/obj/PACKAGING/target_files_intermediates/aospa_fuxi-target_files \
  "$PWD/aospa_fuxi-fastboot-local.zip"
```

也可将第二个参数替换为本次构建的 target_files ZIP。产物包括 Fastboot ZIP、SHA256 和镜像清单；打包时检查 board、分区、ZIP CRC 及镜像与 target_files 的一致性。

## 目录

| 目录 / 文件 | 内容 |
| --- | --- |
| `manifests/release.xml` | 固定上游 commit |
| `patches/`、`overlay/`、`projects.json` | 33 个仓库的改动和新增文件 |
| `device-tree-snapshot/` | 设备树快照 |
| `scripts/` | 补丁、依赖、编译和打包工具 |
| `dependencies.json` | 构建依赖的版本、地址与 SHA256 |
| `licenses/`、`LICENSE.md` | 许可证与版权信息 |

改动覆盖 SVE 启动兼容、音频 DTBO、vendor ABI、health、相机、自动亮度、CMCC NR 信号、系统 TICK、Wi-Fi/hostapd 和 SELinux。
