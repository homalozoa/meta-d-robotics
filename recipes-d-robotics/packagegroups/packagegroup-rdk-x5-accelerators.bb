SUMMARY = "D-Robotics RDK X5 BPU and camera accelerator runtime"
DESCRIPTION = "Opt-in RDK X5 BPU inference and camera capture runtime for accelerator-enabled images."

require conf/machine/include/rdk-x5-release.inc

inherit packagegroup

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

# `module.bbclass` generates the BPU I/O subpackage only during do_package, so
# an image cannot use that dynamic package name to discover this external
# module recipe.  Depend on its stable, empty module metapackage instead: it
# both schedules the recipe and carries the exact generated-module closure.
RDEPENDS:${PN} = " \
    hobot-dnn \
    hobot-bpu-driver \
    packagegroup-rdk-x5-camera \
"
