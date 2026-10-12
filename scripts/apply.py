#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Apply the release integration delta without changing repository history."""
import argparse, json, pathlib, subprocess, shutil
kit=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(); p.add_argument('tree',type=pathlib.Path)
p.add_argument('--check',action='store_true'); p.add_argument('--verify-applied',action='store_true')
a=p.parse_args(); root=a.tree.resolve(); records=json.loads((kit/'projects.json').read_text())
plans=[]
def git(path,*args):
    return subprocess.run(['git','-C',str(path),*args],capture_output=True,text=True)
for r in records:
    repo=root/r['path']; head=git(repo,'rev-parse','HEAD')
    if head.returncode or head.stdout.strip()!=r['revision']:
        raise SystemExit('Wrong base revision: '+r['path'])
    patch=kit/r['patch'] if r['patch'] else None
    state='none'
    if patch:
        if git(repo,'apply','--reverse','--check',str(patch)).returncode==0:
            state='applied'
        elif not a.verify_applied and git(repo,'apply','--check',str(patch)).returncode==0:
            state='pending'
        else:
            raise SystemExit('Patch does not match: '+r['path'])
    for name in r['overlay']:
        src=kit/'overlay'/r['path']/name; dst=repo/name
        if dst.exists() and dst.read_bytes()!=src.read_bytes():
            raise SystemExit('Refusing to overwrite different file: '+str(dst))
        if a.verify_applied and not dst.exists():
            raise SystemExit('Missing overlay: '+str(dst))
    plans.append((r,repo,patch,state))
if not (a.check or a.verify_applied):
    for r,repo,patch,state in plans:
        if state=='pending':
            subprocess.run(['git','-C',str(repo),'apply',str(patch)],check=True)
        for name in r['overlay']:
            dst=repo/name; dst.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(kit/'overlay'/r['path']/name,dst)
print('PASS: %d project deltas checked%s' % (len(records),'; no files changed' if a.check or a.verify_applied else ' and applied'))
