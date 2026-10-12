# Validation of this source export

2026-10-12:

* 33 project deltas exported against pinned upstream HEADs.
* Every patch passes `git apply --cached --check` with a temporary index initialized from its pinned clean HEAD.
* Reverse patch checks and overlay byte comparisons against the actual build tree pass via `scripts/apply.py --verify-applied`.
* Python scripts compile and build shell script passes `bash -n`.
* Manifest relative AOSPA remote corrected to an absolute GitHub remote so moving the manifest to SourceForge does not change project URLs.
* Private input preparation requires the tested MIUI APK/libgui/corner-resource hashes; no private blobs are shipped.
* Published Fastboot ZIP SHA256: `4eaa1bf1e77dba6b99bd928e4fde95f6e87bf9a1b2d8100a526f4dfa90d83b66`. Its original package verification checked 14 ROM partitions, board=fuxi, ZIP CRC, and image equality with that build's target_files.
* That ROM was flashed and booted on slot A: Android 17, incremental `20261012.systemtick`, `sys.boot_completed=1`, boot animation stopped.

This does not claim that a new full source sync/build or a user build was completed as part of publishing the source kit. Nonfree prerequisites and upstream network availability still matter. New build output must be independently tested.
