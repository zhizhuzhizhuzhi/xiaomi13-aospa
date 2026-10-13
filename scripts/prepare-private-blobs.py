#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Prepare the pinned binary inputs for the fuxi build."""
import argparse, pathlib, hashlib, shutil, zipfile
p=argparse.ArgumentParser();p.add_argument('tree',type=pathlib.Path);p.add_argument('inputs',type=pathlib.Path)
a=p.parse_args();root=a.tree.resolve();inputs=a.inputs.resolve()
required=['MiuiCamera.apk','libgui-xiaomi.so','FuxiSystemUI.apk','SoterService.apk']
for n in required:
    if not (inputs/n).is_file():raise SystemExit('Missing build input: '+n)
expected_apk='921b3b2f0fc3bdcb18a3168759c8e0b7359ba97446047a5ac56830f21475aa79'
if hashlib.sha256((inputs/'MiuiCamera.apk').read_bytes()).hexdigest()!=expected_apk:
    raise SystemExit('MIUI Camera must come from the 20261014 build dependency')
camera=root/'vendor/xiaomi/camera'
soter=inputs/'SoterService.apk'
if hashlib.sha256(soter.read_bytes()).hexdigest()!='b1f5a2a692822aa9e75b67a5aab48c55e3082259641e9b0193e0fe70e81d1cfc':
    raise SystemExit('SoterService must match the pinned diting OS2 version')
dest=root/'vendor/xiaomi/sm8550-common/proprietary/vendor/app/SoterService/SoterService.apk'
dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(soter,dest)
data=(inputs/'libgui-xiaomi.so').read_bytes()
old=b'android.hardware.graphics.common-V5-ndk.so';new=b'android.hardware.graphics.common-V7-ndk.so'
if old in data:data=data.replace(old,new)
if new not in data:raise SystemExit('Unexpected libgui dependency version')
expected='43e55a628cad181f6da2d04388485b10650ee819102669d96b1a39e1bdc0fee1'
if hashlib.sha256(data).hexdigest()!=expected:raise SystemExit('libgui does not match the tested release')
libgui=data
hashes={'rounded_corner_top.png':'8e94412bc9fb089c747155397c6cb031d6a6d77fcaeacaab8fe7a69f60872e82','rounded_corner_bottom.png':'bfef940cd83d260006830aba4ed354ef12cc5df5afae446910c52662c9c20573'}
resources={}
with zipfile.ZipFile(inputs/'FuxiSystemUI.apk') as z:
    for name,expected in hashes.items():
        matches=[n for n in z.namelist() if n.startswith('res/') and n.endswith('/'+name)]
        if len(matches)!=1:raise SystemExit('Missing/ambiguous overlay resource: '+name)
        data=z.read(matches[0])
        if hashlib.sha256(data).hexdigest()!=expected:
            raise SystemExit('Corner resource differs from the tested release: '+name)
        resources[name]=data
dest=camera/'proprietary/system/priv-app/MiuiCamera/MiuiCamera.apk'
dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(inputs/'MiuiCamera.apk',dest)
dest=camera/'proprietary/system/lib64/libgui-xiaomi.so'
dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(libgui)
for name,data in resources.items():
    dst=root/'device/xiaomi/fuxi/overlays/SystemUI/res/drawable-nodpi'/name
    dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(data)
print('Fuxi build inputs installed.')
