SUMMARY = "D-Robotics RDK X5 BPU and camera accelerator runtime"
DESCRIPTION = "Opt-in RDK X5 BPU inference and camera capture runtime for accelerator-enabled images."

require conf/machine/include/rdk-x5-release.inc

inherit packagegroup

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

# The BPU I/O package carries exact, versioned dependencies on bpu_framework
# and bpu_cores.  Keep that resolution in package metadata rather than baking
# a kernel version into the image-facing packagegroup.
RDEPENDS:${PN} = " \
    hobot-dnn \
    kernel-module-bpu-hw-io-x5 \
    packagegroup-rdk-x5-camera \
"
