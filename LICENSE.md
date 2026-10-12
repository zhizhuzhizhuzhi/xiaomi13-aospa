# Licensing

This repository is an integration source kit for an unofficial Xiaomi 13 AOSPA build.
It does not relicense Android, AOSPA, Linux, Xiaomi or Qualcomm components.

* Files and changes derived from upstream retain the upstream file's copyright and license. Refer to the pinned upstream repository, source headers and `licenses/`.
* New integration scripts and original documentation are available under Apache-2.0 (`licenses/Apache-2.0.txt`).
* Linux kernel source and modifications retain GPL-2.0 and the exceptions specified by the upstream files. `manifests/release.xml` pins the kernel repositories; `patches/` contains the local kernel/build delta.
* MIUI APK interoperability patches do not grant any license to the underlying proprietary application. Proprietary APK/SO/firmware and stock artwork are not distributed in this source kit and retain their owners' terms.
* AOSPA branding/artwork retains its upstream licensing; this project is unofficial and does not claim endorsement.

Do not interpret the presence of a vendor dependency in the manifest as a new redistribution license.
