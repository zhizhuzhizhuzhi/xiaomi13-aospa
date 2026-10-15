# Xiaomi 13 / fuxi — AOSPA 17

基于 AOSPA calcite，适配 Xiaomi 13（fuxi / SM8550）。20261015 同时提供 userdebug/test-keys 和 user/release-keys 两种独立、并行的发布。

[userdebug 线刷包](https://sourceforge.net/projects/xiaomi13-aospa/files/20261015/) · [user/release 线刷包](https://sourceforge.net/projects/xiaomi13-aospa/files/20261015-release/) · [构建依赖](https://github.com/zhizhuzhizhuzhi/xiaomi13-aospa/releases/tag/build-deps-20261014)

## 并行发布

| 发布目录 | 构建与签名 | 对应源码 |
| --- | --- | --- |
| [`20261015`](https://sourceforge.net/projects/xiaomi13-aospa/files/20261015/) | userdebug / test-keys | [`7f09118`](https://github.com/zhizhuzhizhuzhi/xiaomi13-aospa/tree/7f09118) |
| [`20261015-release`](https://sourceforge.net/projects/xiaomi13-aospa/files/20261015-release/) | user / release-keys，AVB 校验关闭 | [`557ecdd`](https://github.com/zhizhuzhizhuzhi/xiaomi13-aospa/tree/557ecdd) |

两个版本分别提供下载和更新。下方 release 专属改动与验证结果适用于 user/release 构建；两种构建使用各自独立的输出目录。

## 20261015 release 更新

- `user` / `release-keys`，APK、APEX 容器及 payload、OTA 使用自有密钥正式签名，保留原有 OEM presigned 签名。
- 默认关闭 AVB verification 和 dm-verity（顶层 `vbmeta Flags: 3`）；保留 AVB 镜像格式和签名元数据，不要求刷完后额外修改 vbmeta。
- 不包含 KernelSU、Zygisk、TrickyStore 或 Play Integrity Fix。
- health HAL、framework 服务、AIDL API 快照和 SELinux 接口迁移为 AOSPA health；保留上游版权及许可证。
- AVC 审计日志归属 logd UID，普通应用不能再按自身 UID 读取这些记录，系统审计仍保留。
- 修正 ParanoidSettings 的 health 引用以及 Settings Room 数据库 schema 导出配置。

发布文件为 `aospa_fuxi-fastboot-user-noavb-20261015.zip`（4,833,114,284 字节），SHA256：

```text
3989f361e7abea10cfbbf04ef81e74649e5100f511506ed3e3a4c186800ef8ad
```

已通过 284 个 APK、40 个 APEX 的实际证书与 payload 公钥核验、OTA 整包签名、ZIP CRC、fuxi 板级与镜像一致性检查，并完成 A 槽保留数据刷写。实机已正常进入系统（`sys.boot_completed=1`），确认 `user/release-keys`、`ro.debuggable=0`、SELinux Enforcing，未发现 KernelSU 内核模块或 ksud/Zygisk/TrickyStore 进程；不承诺支付或完整性检测通过。

在 test-keys 和 release-keys 两种签名之间切换，应用和系统数据兼容性可能变化，切换前请备份。

## 20261015 两版本共同更新

- 修复支付宝 IFAA 指纹支付能力查询、屏下指纹区域和 mlipay 接口调用。
- 启用关闭 AOD 后的熄屏指纹解锁。

## 20261014 更新

- 修复微信指纹支付。
- 修复厂商音频 ABI、图形缓冲区兼容和服务权限。
- 修复属性更新通知，补齐 QTR SDK 客户端权限。
- 启用 ZRAM，调整系统后台 CPU 范围为 0–6。
- 使用 Vulkan 渲染，统一指定系统操作的 TICK 触感。
- Wi-Fi 恢复自动选择地区，保留单链路热点 ACS 兼容。

包含相机、自动亮度、圆角、GNSS、充电和 Soter 适配。NR 信号格数校准目前针对 CMCC。

## 刷入

底包为 Xiaomi 13 Global **OS2.0.100.0.VMCMIXM**（Android 15）。选择所需构建，进入 Fastboot 后执行对应命令。

userdebug / test-keys：

```bash
fastboot --slot=a --skip-secondary --disable-super-optimization update aospa_fuxi-fastboot-20261015.zip
```

user / release-keys：

```bash
fastboot --slot=a --skip-secondary --disable-super-optimization update aospa_fuxi-fastboot-user-noavb-20261015.zip
```

如需清除数据，可在所选版本的命令中加入 `-w`。以下以 user/release 为例：

```bash
fastboot --slot=a --skip-secondary --disable-super-optimization -w update aospa_fuxi-fastboot-user-noavb-20261015.zip
```

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

脚本下载 Release 中固定版本的相机、libgui 与圆角资源包，以及上游指定的 diting OS2 SoterService，校验 SHA256 后放到对应目录。缓存位于 `out/fuxi-build-dependencies/`。

已有依赖 ZIP 可直接使用：

```bash
python3 ../xiaomi13-aospa-kit/scripts/fetch-dependencies.py "$PWD" \
  --archive /path/to/fuxi-build-dependencies-20261014.zip
```

## 编译

```bash
bash ../xiaomi13-aospa-kit/scripts/build.sh "$PWD" userdebug 8
```

目标为 `aospa_fuxi-cp2a-userdebug`。脚本依次构建 kalama GKI、模块、合并 DT、OTA、otatools 和 target_files。

两种构建分别使用 `out`（userdebug）和 `out-user-release`（user），各自保留增量产物。可通过 `OUT_DIR` 指定其他输出目录。

编译 `user`：

```bash
bash ../xiaomi13-aospa-kit/scripts/build.sh "$PWD" user 8
```

`user` 构建与 release keys 签名是两个步骤；`20261015` 的 userdebug/test-keys 与 `20261015-release` 的 user/release-keys 为并行发布。签名密钥不随源码发布；正式签名需分别映射 APK、APEX 容器证书和 payload 密钥，并用 releasekey 签署 OTA。

## Fastboot 打包

```bash
python3 ../xiaomi13-aospa-kit/scripts/package.py "$PWD" \
  out/target/product/fuxi/obj/PACKAGING/target_files_intermediates/aospa_fuxi-target_files \
  "$PWD/aospa_fuxi-fastboot-20261015.zip"
```

打包 user 产物时设置 `OUT_DIR="$PWD/out-user-release"`，并使用该目录下的 target_files。也可将第二个参数替换为本次构建的 target_files ZIP。产物包括 Fastboot ZIP、SHA256 和镜像清单。

## 目录

| 目录 / 文件 | 内容 |
| --- | --- |
| `manifests/release.xml` | 固定上游 commit |
| `patches/`、`overlay/`、`projects.json` | 40 个仓库的改动和新增文件 |
| `scripts/` | 补丁、依赖、编译和打包工具 |
| `dependencies.json` | 构建依赖的版本、地址与 SHA256 |
| `licenses/`、`LICENSE.md` | 许可证与版权信息 |
