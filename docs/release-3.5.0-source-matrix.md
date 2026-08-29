# RDK X5 RDKOS 3.5.0 source and compatibility matrix

This is the reviewable release contract for the RDK X5 layer.  It is the
equivalent of keeping an L4T release map beside `meta-tegra`: a change to a
vendor revision, package version, license checksum, or compatibility exception
must be reviewed as a release change rather than as an incidental recipe edit.

The canonical machine-readable values remain in
[`conf/machine/include/rdk-x5-release.inc`](../conf/machine/include/rdk-x5-release.inc).
`tests/test-layer-metadata.sh` verifies this document contains every pinned
`RDK_X5_SRCREV_*` value, so the human-readable matrix cannot silently drift.

## Baseline

| Contract | Value |
| --- | --- |
| RDKOS release | `3.5.0` |
| D-Robotics SDK | `1.1.1` |
| Linux ABI | `6.1.83` |
| Vendor branch context | `3.5.0` |
| Vendor source authority | `https://github.com/D-Robotics/` only |
| Yocto layer series | `wrynose` |

All Git fetchers retain the branch as release context and use the full
40-character commit below as their actual `SRCREV`.  A pin listed as
**not packaged** is release provenance only: no recipe fetches it and it cannot
enter an image until a separately reviewed recipe names that pin.

## Source-backed recipe matrix

| Recipe / emitted package | Version | Official pin(s) | License checksum | Machine scope and compatibility exception |
| --- | --- | --- | --- | --- |
| `linux-d-robotics` / `kernel-*` | `6.1.83+git` | `RDK_X5_SRCREV_KERNEL`, `RDK_X5_SRCREV_DRV_CAMSYS` | `GPL-2.0-only`; `COPYING` `6bc538ed5bd9a7fc9398086aedcd7e46` | `rdk-x5` only.  The source-only `${PN}-src` package permits `buildpaths`; eight documented GCC 15 source patches retain normal runtime and development QA. |
| `d-robotics-mkimage-native` | `3.5.0` | `RDK_X5_SRCREV_UBOOT` | `GPL-2.0-only`; `Licenses/README` `2ca5f2c35c8cc335f0a19756634782f1` | Target declaration is `rdk-x5`; the native variant alone allows `.*` because it builds the host-side `mkimage` tool for that target. |
| `d-robotics-bootfiles` | `1.0` | Local deterministic `boot.cmd` and `hobot_config.sh`; consumes the pinned native U-Boot tool above | `MIT`; `boot.cmd` first line `b2dccaa94b3629a08bfb4f983cad6f89` | `rdk-x5` only.  Produces a TF-card `CONFIG` FAT image and boot script; it never writes NAND-resident firmware. |
| `hobot-multimedia` | `3.0.5` | `RDK_X5_SRCREV_HOBOT_MULTIMEDIA` | `Apache-2.0`; `LICENSE` `3b83ef96387f14655fc854ddc3c6bd57` | `rdk-x5` only.  `libdir` is the sole QA exception because the audited vendor ABI must remain in `/usr/hobot/lib`; cJSON uses the Wrynose provider and all other binary QA remains enabled. |
| `hobot-dnn` and `hobot-dnn-dev` | `3.0.4` | `RDK_X5_SRCREV_HOBOT_DNN` | `Apache-2.0`; `LICENSE` `3b83ef96387f14655fc854ddc3c6bd57` | `rdk-x5` only.  The same narrow `/usr/hobot/lib` `libdir` exception applies; the audit enforces GLIBC `2.34` and GLIBCXX `3.4.29`.  Vendor OpenCV and `dnn_server` are deliberately excluded. |
| `hobot-bpu-driver` and generated `kernel-module-bpu-hw-io-x5-*` | `3.5.0` | `RDK_X5_SRCREV_HOBOT_DRIVERS` | `Apache-2.0`; `LICENSE` `3b83ef96387f14655fc854ddc3c6bd57` | `rdk-x5` only.  Kbuild must see exactly Linux `6.1.83`; a different kernel version is fatal.  The metapackage pulls the generated versioned module closure. |
| `hobot-camera` | `3.1.1` | `RDK_X5_SRCREV_HOBOT_CAMERA`, `RDK_X5_SRCREV_LIBCAM_SENSOR`, `RDK_X5_SRCREV_LIBCAM_INC`, `RDK_X5_SRCREV_HOBOT_MULTIMEDIA_DEV`, `RDK_X5_SRCREV_TUNING_JSON` | `Apache-2.0`; camera `LICENSE` `3b83ef96387f14655fc854ddc3c6bd57` | `rdk-x5` only.  The narrow `/usr/hobot/lib` `libdir` exception preserves the sensor ABI.  `-std=gnu17` prevents a GLIBC 2.38 C23 symbol dependency; selected plugins are audited at GLIBC `2.34`. |
| `hobot-usb-gadget` | `3.0.7` | `RDK_X5_SRCREV_HOBOT_UTILS` | `Apache-2.0`; `LICENSE` `3b83ef96387f14655fc854ddc3c6bd57` | `rdk-x5` only.  Installs the official RNDIS+ECM config and launcher with a portability-only patch, exact module roots, and a Yocto-native systemd/networkd policy.  USB mass storage remains excluded. |
| `hobot-wifi` | `3.0.3` | `RDK_X5_SRCREV_HOBOT_WIFI` | `Apache-2.0`; `LICENSE` `3b83ef96387f14655fc854ddc3c6bd57` | `rdk-x5` only.  Selects only the official AIC8800D80 firmware used by SDIO `C8A1:0082`, plus version-neutral AIC/cfg80211 and ttyS5 Bluetooth module roots.  Broadcom, Realtek, Debian host libraries, and credentials are excluded. |
| `hobot-gpio` | `3.1.4` | `RDK_X5_SRCREV_HOBOT_IO` | `Apache-2.0`; repository `LICENSE` `3b83ef96387f14655fc854ddc3c6bd57` | `rdk-x5` only.  Builds the pure-Python GPIO API and RDK X5 pin map.  Excludes the bundled `libgpiod.a`, Debian configuration UI, and world-writable udev policy. |

`packagegroup-rdk-x5-camera`, `packagegroup-rdk-x5-accelerators`,
`rdk-x5-peripherals`, `rdk-x5-audio`, `rdk-x5-display`, and
`rdk-x5-media-modules` are metadata/local-policy-only `MIT` recipes with
version `1.0`; they contain no
fetched vendor source.  All are `rdk-x5`-only.  The camera, peripheral, audio,
display, and native-media recipes name only roots of the kernel-module
dependency graph, and the BPU group names the stable driver metapackage rather
than a guessed kernel-version package name.

The `libdrm_%.bbappend` creates an RDK-X5-only `libdrm-modetest` split from
Wrynose's maintained libdrm source.  This replaces the prebuilt diagnostic in
`x5-hobot-configs` without installing the unrelated AMDGPU and Etnaviv tests
carried by the upstream `libdrm-tests` package.

## Complete RDKOS pin inventory

The following inventory retains the complete centrally recorded RDKOS 3.5.0
source set.  Names correspond to the official `D-Robotics/x5-*` projects.

| Pin variable | Official component | Immutable commit | Recipe consumer |
| --- | --- | --- | --- |
| `RDK_X5_SRCREV_MANIFEST` | `x5-manifest` | `c7c8f1f3fb096663be44e3043dc0efb2f2e73c61` | Release provenance; not packaged |
| `RDK_X5_SRCREV_RDK_GEN` | `x5-rdk-gen` | `6a66adca73a3bcec5538056f440d3448dad40c46` | Release provenance; not packaged |
| `RDK_X5_SRCREV_KERNEL` | `x5-kernel` | `c642eabfd7aacc4f44f2300e69d27ef1691a47d7` | `linux-d-robotics` |
| `RDK_X5_SRCREV_DRV_CAMSYS` | `x5-drv-camsys` | `3869a461e47c8602386a57bd82afd3fd7334c1d5` | `linux-d-robotics` |
| `RDK_X5_SRCREV_UBOOT` | `x5-uboot` | `489660ec7e4f7d327bbd5d3b0a68b2c47483a4aa` | `d-robotics-mkimage-native` |
| `RDK_X5_SRCREV_BOOTLOADER` | `x5-bootloader` | `e9679f2e49c85aef221b0372f47ed6044f1cf078` | NAND firmware provenance; not packaged |
| `RDK_X5_SRCREV_MINIBOOT` | `x5-miniboot` | `e7a393ead0db426b8408c705cc3994ccf0425f93` | NAND firmware provenance; not packaged |
| `RDK_X5_SRCREV_HOBOT_MINIBOOT` | `x5-hobot-miniboot` | `3d301beb6b359c47608f1a1da920ed8d5d301135` | NAND firmware provenance; not packaged |
| `RDK_X5_SRCREV_HOBOT_KERNEL_HEADERS` | `x5-hobot-kernel-headers` | `afc745d563cf6733a05bda5e6832519b7529332f` | Release provenance; not packaged |
| `RDK_X5_SRCREV_HOBOT_DTB` | `x5-hobot-dtb` | `c83942bbefce2c040706076093475b51a6850678` | Release provenance; kernel deploys the selected DTBs |
| `RDK_X5_SRCREV_HOBOT_BOOT` | `x5-hobot-boot` | `3dfb58b2dab0dd0e5097b0f71442c5a65b9d3740` | Release provenance; not packaged |
| `RDK_X5_SRCREV_HOBOT_CONFIGS` | `x5-hobot-configs` | `9dbcefbb3b2ea4d289ca08fa0fb2338180421cef` | Release provenance; not packaged |
| `RDK_X5_SRCREV_HOBOT_WIFI` | `x5-hobot-wifi` | `052c2b53e31c69dd068e6632a2474746da6ebe8e` | `hobot-wifi` |
| `RDK_X5_SRCREV_HOBOT_AUDIO_CONFIG` | `x5-hobot-audio-config` | `c26ed5068d66ac3045e586156ac6127b531ed37d` | Release provenance; not packaged |
| `RDK_X5_SRCREV_HOBOT_UTILS` | `x5-hobot-utils` | `d266754d9ac357eb3615ce8360253039607ba040` | `hobot-usb-gadget` |
| `RDK_X5_SRCREV_HOBOT_IO` | `x5-hobot-io` | `e8b84efd72eaaa48a796b3e54db63e59d3807226` | Release provenance; not packaged |
| `RDK_X5_SRCREV_HOBOT_IO_SAMPLES` | `x5-hobot-io-samples` | `1cd798105f3c22d73a3ed5cd4a755dcbefa83048` | Samples excluded from image |
| `RDK_X5_SRCREV_HOBOT_DRIVERS` | `x5-hobot-drivers` | `9ebc13c76c4dfe14410537b4c1b0761d51d1ab9e` | `hobot-bpu-driver` |
| `RDK_X5_SRCREV_HOBOT_DNN` | `x5-hobot-dnn` | `ae138d3daf611729055193ce258eaa8b9b6d7644` | `hobot-dnn` |
| `RDK_X5_SRCREV_HOBOT_MULTIMEDIA` | `x5-hobot-multimedia` | `86d949964716974ddc0f2fbb90b212155df1f622` | `hobot-multimedia` |
| `RDK_X5_SRCREV_HOBOT_MULTIMEDIA_DEV` | `x5-hobot-multimedia-dev` | `ac255ab4a0d1f7fc485e2b1833b6428229c8e1bb` | `hobot-camera` build headers |
| `RDK_X5_SRCREV_HOBOT_MULTIMEDIA_SAMPLES` | `x5-hobot-multimedia-samples` | `f7fecdd6466151cf1963323dd77c87e54404142d` | Samples excluded from image |
| `RDK_X5_SRCREV_MULTIMEDIA_SAMPLES` | `x5-multimedia-samples` | `746eb7b17f771c71442c450d4072f27a2d881bff` | Samples excluded from image |
| `RDK_X5_SRCREV_HOBOT_CAMERA` | `x5-hobot-camera` | `821af1b6f161c80874b03a1ec9d9858855ed0773` | `hobot-camera` release license and source relationship |
| `RDK_X5_SRCREV_LIBCAM_SENSOR` | `x5-libcam-sensor` | `5cc4c711fe24263da2f87ee02e32c962f3c38034` | `hobot-camera` selected sensor plugins |
| `RDK_X5_SRCREV_LIBCAM_INC` | `x5-libcam-inc` | `8877e975bc01eacdcdc45a3209c7a06183296105` | `hobot-camera` public ABI headers |
| `RDK_X5_SRCREV_TUNING_JSON` | `x5-tuning-json` | `9646fdc03a8fe9cf8a3a4b345af4859101115fe2` | `hobot-camera` selected tuning data |
| `RDK_X5_SRCREV_HOBOT_SPDEV` | `x5-hobot-spdev` | `25627aa3f928f111f8f1065ba1d685de16769a57` | Release provenance; not packaged |
| `RDK_X5_SRCREV_HOBOT_SP_SAMPLES` | `x5-hobot-sp-samples` | `23d9df10dc070427757020a2079ea69bea5c4d92` | Samples excluded from image |
| `RDK_X5_SRCREV_HOBOT_DISPLAY` | `x5-hobot-display` | `91a2ad0830386a43d0fc968ed39df8ae382384a9` | Release provenance; not packaged |

## Audit gate

All selected prebuilt AArch64 ELF files pass the layer's `readelf` audit before
packaging.  The audit rejects a wrong architecture, unsafe interpreter,
unresolved `DT_NEEDED` closure, `RPATH` or `RUNPATH`, and GLIBC/GLIBCXX versions
outside the recipe's stated maximum.  It is deliberately not a substitute for
normal BitBake package QA, and the matrix's narrowly scoped `libdir` exceptions
do not disable `already-stripped`, `dev-so`, or `file-rdeps` checks.
