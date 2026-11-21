#!/usr/bin/env python3
import pathlib, subprocess, sys
if len(sys.argv) < 2: raise SystemExit('Usage: package.py ANDROID_TREE TARGET_FILES OUTPUT')
tree = pathlib.Path(sys.argv[1]).resolve()
raise SystemExit(subprocess.call([sys.executable, str(tree/'vendor/xiaomi/sm8550-integration/tools/package.py'), 'fuxi', str(tree), *sys.argv[2:]]))
