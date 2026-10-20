#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Fetch and prepare the pinned fuxi build inputs."""
import argparse,pathlib,json,hashlib,urllib.request,zipfile,subprocess,sys,shutil
p=argparse.ArgumentParser();p.add_argument('tree',type=pathlib.Path);p.add_argument('--archive',type=pathlib.Path)
a=p.parse_args();root=a.tree.resolve();kit=pathlib.Path(__file__).resolve().parents[1]
meta=json.loads((kit/'dependencies.json').read_text())
cache=root/'out/fuxi-build-dependencies';cache.mkdir(parents=True,exist_ok=True)
archive=a.archive.resolve() if a.archive else cache/meta['file']
def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(8*1024*1024),b''):h.update(b)
    return h.hexdigest()
if not archive.exists():
    part=archive.with_suffix(archive.suffix+'.part')
    request=urllib.request.Request(meta['url'],headers={'User-Agent':'xiaomi13-aospa-build'})
    with urllib.request.urlopen(request,timeout=60) as response,part.open('wb') as f:
        shutil.copyfileobj(response,f,8*1024*1024)
    if digest(part)!=meta['sha256']:raise SystemExit('Downloaded dependency SHA256 mismatch')
    part.rename(archive)
if archive.stat().st_size!=meta['bytes'] or digest(archive)!=meta['sha256']:
    raise SystemExit('Dependency archive differs from pinned release')
inputs=cache/'inputs';inputs.mkdir(exist_ok=True)
expected={'MiuiCamera.apk','libgui-xiaomi.so','FuxiSystemUI.apk','SoterService.apk','DEPENDENCIES.json'}
expected.update('extras/'+r['path'] for r in meta.get('extras',[]))
with zipfile.ZipFile(archive) as z:
    if set(z.namelist())!=expected or len(z.namelist())!=len(expected) or z.testzip() is not None:
        raise SystemExit('Invalid dependency archive')
    for name in expected:
        destination=inputs/name
        destination.parent.mkdir(parents=True,exist_ok=True)
        with z.open(name) as source,destination.open('wb') as output:
            shutil.copyfileobj(source,output,8*1024*1024)
soter=meta['soter'];soter_file=inputs/soter['file']
if soter_file.stat().st_size!=soter['bytes'] or digest(soter_file)!=soter['sha256']:
    raise SystemExit('SoterService dependency SHA256 mismatch')
subprocess.run([sys.executable,str(kit/'scripts/prepare-private-blobs.py'),str(root),str(inputs)],check=True)
for record in meta.get('extras',[]):
    source=inputs/'extras'/record['path'];destination=root/record['path']
    if not destination.resolve().is_relative_to(root):raise SystemExit('Invalid supplemental path')
    if source.stat().st_size!=record['bytes'] or digest(source)!=record['sha256']:
        raise SystemExit('Supplemental dependency mismatch: '+record['path'])
    destination.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(source,destination);destination.chmod(record['mode'])
print('Build dependencies ready:',inputs)
