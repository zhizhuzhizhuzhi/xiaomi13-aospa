#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
import argparse,pathlib,subprocess,zipfile,hashlib,json,os
p=argparse.ArgumentParser();p.add_argument('tree',type=pathlib.Path);p.add_argument('target_files',type=pathlib.Path);p.add_argument('output',type=pathlib.Path)
a=p.parse_args();root=a.tree.resolve();target=a.target_files.resolve();out=a.output.resolve()
if out.exists() or pathlib.Path(str(out)+'.part').exists():raise SystemExit('Refusing to replace an existing package')
out.parent.mkdir(parents=True,exist_ok=True)
if target.is_dir():
    archive=pathlib.Path(str(out)+'.target_files.zip')
    if archive.exists():raise SystemExit('Target archive already exists')
    with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=1,allowZip64=True) as z:
        for f in sorted(target.rglob('*')):
            if f.is_file():z.write(f,f.relative_to(target).as_posix())
else:archive=target
build_out=pathlib.Path(os.environ.get('OUT_DIR','out'))
if not build_out.is_absolute():build_out=root/build_out
host=build_out/'host/linux-x86';env=os.environ.copy();env['PATH']=str(host/'bin')+':'+env['PATH']
part=pathlib.Path(str(out)+'.part')
subprocess.run([str(host/'bin/img_from_target_files'),'-p',str(host),str(archive),str(part)],env=env,check=True)
def sha(stream):
    h=hashlib.sha256()
    for b in iter(lambda:stream.read(8*1024*1024),b''):h.update(b)
    return h.hexdigest()
records=[]
with zipfile.ZipFile(archive) as source,zipfile.ZipFile(part) as z:
    if len(set(z.namelist()))!=len(z.namelist()):raise SystemExit('Duplicate ZIP entry')
    if z.testzip() is not None:raise SystemExit('ZIP CRC failure')
    if 'require board=fuxi' not in z.read('android-info.txt').decode():raise SystemExit('Wrong board')
    expected={'boot','init_boot','dtbo','recovery','vendor_boot','vbmeta','vbmeta_system','odm','product','system','system_dlkm','system_ext','vendor','vendor_dlkm'}
    actual={n[:-4] for n in z.namelist() if n.endswith('.img') and n!='super_empty.img'}
    if expected!=actual:raise SystemExit('Unexpected/missing ROM partitions')
    for n in z.namelist():
        if n.endswith('.img'):
            with z.open(n) as f,source.open('IMAGES/'+n) as s:
                h=sha(f)
                if h!=sha(s):raise SystemExit('Target image mismatch: '+n)
                records.append({'image':n,'sha256':h})
part.rename(out)
with out.open('rb') as f:h=sha(f)
pathlib.Path(str(out)+'.sha256').write_text(h+'  '+out.name+'\n')
pathlib.Path(str(out)+'.manifest.json').write_text(json.dumps(records,indent=2)+'\n')
print('Verified:',out,h)
