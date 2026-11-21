#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Install the matching prepared binary inputs listed in dependencies.json."""
import argparse, hashlib, json, pathlib, shutil
kit = pathlib.Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('tree', type=pathlib.Path)
p.add_argument('--source', type=pathlib.Path, action='append', default=[])
p.add_argument('--verify', action='store_true')
p.add_argument('--replace-stock-inputs', action='store_true')
a = p.parse_args(); root = a.tree.resolve(); plans = []; missing = []
def sha(path):
    with path.open('rb') as f: return hashlib.file_digest(f, 'sha256').hexdigest()
for r in json.loads((kit/'dependencies.json').read_text())['files']:
    name = pathlib.PurePosixPath(r['destination'])
    if name.is_absolute() or '..' in name.parts: raise SystemExit('Invalid input path')
    dst = root/name
    if not dst.resolve().is_relative_to(root): raise SystemExit('Input leaves source tree: '+str(name))
    def matches(path): return path.is_file() and path.stat().st_size == r['bytes'] and sha(path) == r['sha256']
    if matches(dst): continue
    if dst.exists() and not a.replace_stock_inputs: raise SystemExit('Different input already exists: '+str(name))
    candidate = None
    if not a.verify:
        for source in a.source:
            for alias in [r['destination']] + r.get('sources', []):
                rel = pathlib.PurePosixPath(alias)
                if rel.is_absolute() or '..' in rel.parts: raise SystemExit('Invalid source alias')
                if matches(source/rel): candidate = source/rel; break
            if candidate: break
    if candidate: plans.append((candidate, dst, r.get('mode', 0o644)))
    else: missing.append(str(name))
if missing: raise SystemExit('Missing matching prepared inputs:\n'+'\n'.join(missing))
for src, dst, mode in plans:
    dst.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(src, dst); dst.chmod(mode)
print('Prepared inputs ready; %d files installed' % len(plans))
