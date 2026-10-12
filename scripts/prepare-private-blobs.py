#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Install user-supplied nonfree inputs; no APK/SO/assets are shipped in this kit."""
import argparse, pathlib, hashlib, shutil, zipfile
p=argparse.ArgumentParser();p.add_argument('tree',type=pathlib.Path);p.add_argument('inputs',type=pathlib.Path)
a=p.parse_args();root=a.tree.resolve();inputs=a.inputs.resolve()
required=['MiuiCamera.apk','libgui-xiaomi.so','FuxiSystemUI.apk']
for n in required:
    if not (inputs/n).is_file():raise SystemExit('Missing private input: '+n)
expected_apk='8e63c2384770b7097da781bc86284aeceeeefbbe51434ba522b51d1062e1358d'
if hashlib.sha256((inputs/'MiuiCamera.apk').read_bytes()).hexdigest()!=expected_apk:
    raise SystemExit('MIUI Camera must come from the tested 20261012 ROM')
camera=root/'vendor/xiaomi/camera'
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
print('Private inputs installed. MIUI camera must be from the tested Android 17 release.')
