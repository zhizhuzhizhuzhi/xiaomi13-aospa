#!/usr/bin/env python3
import pathlib, subprocess, sys
if len(sys.argv) < 2: raise SystemExit('Usage: fetch-dependencies.py ANDROID_TREE [arguments...]')
tree = pathlib.Path(sys.argv[1]).resolve()
tool = tree/'vendor/xiaomi/sm8550-integration/tools/prepare-inputs.py'
if not tool.is_file(): raise SystemExit('Sync the aospa-17 fuxi.xml manifest before running this command.')
raise SystemExit(subprocess.call([sys.executable, str(tool), str(tree), '--device', 'fuxi', *sys.argv[2:]]))
