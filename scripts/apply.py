#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Apply pinned source patches and install standalone source components."""
import argparse, hashlib, json, os, pathlib, shutil, subprocess
kit = pathlib.Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('tree', type=pathlib.Path)
g = p.add_mutually_exclusive_group()
g.add_argument('--check', action='store_true')
g.add_argument('--verify-applied', action='store_true')
a = p.parse_args(); tree = a.tree.resolve(); plans = []
def safe(root, name):
    rel = pathlib.PurePosixPath(name)
    if rel.is_absolute() or '..' in rel.parts: raise SystemExit('Invalid path: '+name)
    return root / rel
def sha(path):
    with path.open('rb') as f: return hashlib.file_digest(f, 'sha256').hexdigest()
def git(repo, *args):
    return subprocess.run(['git', '-C', str(repo), *args], capture_output=True, text=True)
for r in json.loads((kit/'projects.json').read_text()):
    repo = safe(tree, r['path'])
    if r.get('kind') != 'standalone':
        head = git(repo, 'rev-parse', 'HEAD')
        if head.returncode or head.stdout.strip() != r['revision']:
            raise SystemExit('Wrong base revision: '+r['path'])
    patch = safe(kit, r['patch']) if r.get('patch') else None; state = 'none'
    if patch:
        if sha(patch) != r['patch_sha256']: raise SystemExit('Patch hash mismatch: '+r['path'])
        if git(repo, 'apply', '--reverse', '--check', str(patch)).returncode == 0: state = 'applied'
        elif not a.verify_applied and git(repo, 'apply', '--check', str(patch)).returncode == 0: state = 'pending'
        else: raise SystemExit('Patch does not match: '+r['path'])
    for name in r['overlay']:
        src = safe(kit/'overlay'/r['path'], name); dst = safe(repo, name)
        if sha(src) != r['overlay_sha256'][name]: raise SystemExit('Source hash mismatch: '+name)
        if dst.exists() and (not dst.is_file() or sha(dst) != sha(src)):
            raise SystemExit('Different source already exists: '+str(dst))
        if a.verify_applied and not dst.is_file(): raise SystemExit('Missing source: '+str(dst))
    for name, target in r.get('symlinks', {}).items():
        dst = safe(repo, name)
        if os.path.isabs(target) or not (dst.parent/target).resolve().is_relative_to(tree):
            raise SystemExit('Invalid symlink target: '+name)
        if os.path.lexists(dst) and (not dst.is_symlink() or os.readlink(dst) != target):
            raise SystemExit('Different symlink already exists: '+str(dst))
        if a.verify_applied and not dst.is_symlink(): raise SystemExit('Missing symlink: '+str(dst))
    plans.append((r, repo, patch, state))
if not (a.check or a.verify_applied):
    for r, repo, patch, state in plans:
        if state == 'pending': subprocess.run(['git','-C',str(repo),'apply','--whitespace=nowarn',str(patch)],check=True)
        for name in r['overlay']:
            src = kit/'overlay'/r['path']/name; dst = repo/name
            dst.parent.mkdir(parents=True, exist_ok=True)
            if not dst.exists(): shutil.copyfile(src, dst)
            dst.chmod(r.get('overlay_modes', {}).get(name, 0o644))
        for name, target in r.get('symlinks', {}).items():
            dst = repo/name; dst.parent.mkdir(parents=True, exist_ok=True)
            if not os.path.lexists(dst): dst.symlink_to(target)
print('%d source projects %s' % (len(plans), 'checked' if a.check or a.verify_applied else 'applied'))
