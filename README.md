# Xiaomi 13 / fuxi — AOSPA 17

非官方适配项目。这里公开 2026-10-12 发布版的固定版本清单、33 个仓库的源码改动、设备树快照与构建工具。
上游源码通过锁定的 manifest 获取；APK、SO、固件、原厂圆角图片和私人签名密钥不存放在此仓库。

发布包：[aospa_fuxi-fastboot-20261012.zip](https://sourceforge.net/projects/xiaomi13-aospa/files/20261012/)。
已发布 ZIP 的 SHA256：`4eaa1bf1e77dba6b99bd928e4fde95f6e87bf9a1b2d8100a526f4dfa90d83b66`。
它使用 `userdebug`，已在 fuxi 实际验证 Android 17 启动。

## 内容

* `manifests/release.xml`：固定所有上游项目的 commit，包括设备树、内核及厂商依赖。
* `projects.json`、`patches/`、`overlay/`：相对于这些 commit 的完整文本改动与新增源码。
* `device-tree-snapshot/`：便于审阅的设备树源码快照；实际安装改动使用 `scripts/apply.py`。
* `scripts/`：应用补丁、准备私人依赖、构建内核和系统、生成并验证 Fastboot ZIP。
* `omitted-binaries.json`：未发布二进制依赖的路径及校验值。

包含 ARM/SVE 启动兼容、DTBO 音频合并顺序、vendor 音频/图形/Codec2 ABI 兼容、health 配置、相机接口补丁、亮度渐变、CMCC NR 信号阈值、系统短振动 TICK、Wi-Fi/hostapd 和 SELinux 等改动。

## 构建环境

使用 x86_64 Linux（建议 Ubuntu 22.04/24.04），建议 64 GB RAM、至少 500 GB 可用 SSD；内存不足时降低并行度。
服务器已有构建记录使用 `aospa_fuxi-cp2a-userdebug`、`-j8`。需要能够访问 manifest 指定的 GitHub、GitLab、AOSP 和 CodeLinaro 源。

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

Java、Clang 等使用源码树预置版本，不手动替换为系统编译器。

## 下载固定源码并应用改动

把集成工具仓库与 Android 大源码目录分开放置：

```bash
git clone https://git.code.sf.net/p/xiaomi13-aospa/code xiaomi13-aospa-kit
mkdir android-fuxi
cd android-fuxi
repo init -u https://git.code.sf.net/p/xiaomi13-aospa/code \
  -b master -m manifests/release.xml
repo sync -c --no-tags -j4 --fail-fast
python3 ../xiaomi13-aospa-kit/scripts/apply.py "$PWD" --check
python3 ../xiaomi13-aospa-kit/scripts/apply.py "$PWD"
```

不要在应用补丁后强制 checkout/reset。脚本核对各仓库 HEAD，遇到冲突会停止，不覆盖不同的已有源码。
如 Git LFS 留下指针文件，先在相应厂商仓库运行 `git lfs pull`，再继续。

## 准备闭源依赖

manifest 引用上游厂商依赖。另有三项来自已经运行本发布版的 fuxi 手机的私人输入。
这些是运行时依赖，不是本项目新开源的软件；请使用自己有权使用的副本，不要提交到 Git。

在已开启 USB 调试、授权电脑且运行本发布版的手机上：

```bash
mkdir -p ../private-blobs
adb pull /system/priv-app/MiuiCamera/MiuiCamera.apk ../private-blobs/
adb pull /system/lib64/libgui-xiaomi.so ../private-blobs/
adb pull /odm/overlay/FuxiSystemUI.apk ../private-blobs/
python3 ../xiaomi13-aospa-kit/scripts/prepare-private-blobs.py "$PWD" ../private-blobs
```

如果系统禁止读取这些文件，可在本发布版 `userdebug` 上使用 `adb root` 后重新读取。
`prepare-private-blobs.py` 从 overlay APK 原样提取圆角 PNG，并检查经过测试的 libgui 依赖版本；不会改动手机。
相机输入必须是本发布版已经适配 Android 17 的 APK。`overlay/vendor/xiaomi/camera/patches/0010*`、`0011*` 公开了相关兼容修改，供有权处理原始 APK 的开发者审阅和重新应用；不要在已经修补的 APK 上重复应用。

## 编译与打包

```bash
bash ../xiaomi13-aospa-kit/scripts/build.sh "$PWD" userdebug 8
find out/target/product/fuxi -name '*target_files*.zip'
```

脚本先构建 kalama GKI 内核、模块和合并 DT，再编译 OTA、otatools 与 target_files。
选择这次构建产生的 target_files ZIP（也可使用本次 `obj/PACKAGING/target_files_intermediates/aospa_fuxi-target_files` 目录）：

```bash
python3 ../xiaomi13-aospa-kit/scripts/package.py "$PWD" \
  out/target/product/fuxi/obj/PACKAGING/target_files_intermediates/aospa_fuxi-target_files \
  "$PWD/aospa_fuxi-fastboot-local.zip"
```

打包使用标准 `img_from_target_files`，检查 board=fuxi、14 个 ROM 分区、ZIP CRC、镜像与本次 target_files 的 SHA256，再生成 `.sha256` 和镜像清单。
不会执行刷机，也不会沿用旧构建的镜像。时间戳、签名和 APK 重签名等会使重新构建的 ZIP 哈希不同，不能把发布包哈希当作新构建的预期哈希。

需要 `user` 包可改为 `build.sh "$PWD" user 8`。目前实机验证的是 userdebug；user 构建需要独立测试，不能保证 Play Integrity 通过。

## 验证与限制

检查 `sys.boot_completed=1`、系统版本、音频/health 服务、自动亮度、信号和振动，而不是只看 fastboot 写入成功。
已保存亮度单项测试、振动策略三项测试和最终包实机启动的验证结果；本开源集成仓库仍需一次完整干净目录构建验证，不声称已重复完成全量构建。
源码保留发布时 GNSS 模块禁用状态；GPS 服务缺失是当时记录的问题，不能把这一版描述成所有功能均已通过。
测试底包是 HyperOS Global `OS2.0.100.0.VMCMIXM`（Android 15）。

许可证、上游版权和闭源依赖边界见 [LICENSE.md](LICENSE.md)。
