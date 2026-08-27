# meta-d-robotics

`meta-d-robotics` is a Wrynose-compatible OpenEmbedded/Yocto BSP layer for
the D-Robotics RDK X5.

The supported vendor baseline is RDKOS 3.5.0.  Recipes use exact revisions from
official D-Robotics source repositories; developer-local source trees and
unversioned vendor branches are intentionally unsupported inputs.

## Scope

The layer is being delivered in independently buildable steps:

1. RDK X5 machine metadata, Linux 6.1.83, boot artifacts, and a TF-card WIC
   image compatible with the official NAND-resident boot contract.
2. Board runtime support for networking, firmware, and system configuration.
3. An opt-in BPU, camera, and multimedia packagegroup after its kernel and ELF
   ABI dependency closure has been verified.

The layer does not update miniboot or U-Boot in NAND as part of an image build
or TF-card flash.

## Dependencies

The initial layer depends on OpenEmbedded-Core and declares compatibility only
with the `wrynose` release series.  Additional layer dependencies are added
only when a recipe requires them.

The supported host entry point lives in `meta-saha`:

```sh
cd /path/to/meta-saha
SAHA_META_D_ROBOTICS_DIR=/path/to/meta-d-robotics ./scripts/saha-build rdk-x5
```

`meta-saha` mounts `SAHA_META_D_ROBOTICS_DIR` read-only at build time, so a
normal image build cannot modify the BSP checkout.  The RDK X5 image graph is
pinned to the Wrynose-compatible ROS 2 Jazzy stack; selecting another ROS
release is intentionally rejected.

## Accelerator runtime

The base RDK X5 image stays small and is suitable for board bring-up.  Build
the separately isolated accelerator image when the board needs the pinned BPU,
DNN, multimedia, and camera runtime:

```sh
cd /path/to/meta-saha
SAHA_META_D_ROBOTICS_DIR=/path/to/meta-d-robotics \
SAHA_X5_ACCELERATORS=1 \
  ./scripts/saha-build rdk-x5
```

`meta-saha` deliberately uses `build/rdk-x5-accelerators` for this mode, so
the accelerator build cannot change a previously built base image.  The image
contains the selected D-Robotics runtime libraries under `/usr/hobot`, the
`bpu_hw_io_x5` module and its automatic-load configuration, and sensor plugins
for `imx219`, `imx415`, `sc132gs`, and `sc230ai`.  It is not a generic camera
enablement bundle; use only the matching board hardware and tuning data.

The accelerator recipes make the compatibility boundary explicit:

- Every vendor source is pinned in `rdk-x5-release.inc` to the RDKOS 3.5.0
  release contract.
- The BPU module is Kbuild-linked only against Linux 6.1.83; a different
  kernel version is a fatal configuration error rather than a best-effort
  module install.
- The module's stable metapackage pulls its generated, versioned kernel-module
  closure, including `bpu_framework` and `bpu_cores`.
- Prebuilt AArch64 ELF files are audited for loader, dependency, symbol-version,
  and RPATH compatibility before packaging.  The layer does not mask generic
  binary or file-dependency QA failures.

## Compatibility policy

- Central release metadata owns every RDK X5 source revision.
- Hardware recipes are limited to `rdk-x5` with `COMPATIBLE_MACHINE`.
- Vendor binaries are split into runtime, development, tools, and samples.
- Prebuilt ELF objects are audited for architecture, interpreter, SONAME,
  `DT_NEEDED`, GLIBC/GLIBCXX symbol versions, and kernel ABI compatibility.
- No global Yocto QA suppression or global legacy-library provider replacement
  is permitted.

`conf/machine/include/rdk-x5-release.inc` is the source-of-truth release and
source-revision matrix for this layer.

## Layer checks

Run the metadata guard locally with:

```sh
bash tests/test-layer-metadata.sh
```

The complete parse/build validation runs through the pinned `meta-saha`
Docker/kas workflow.
