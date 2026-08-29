SUMMARY = "D-Robotics RDK X5 camera capture runtime"
DESCRIPTION = "Selected RDK X5 camera userspace plugins with their required vendor kernel capture modules."

require conf/machine/include/rdk-x5-release.inc

inherit packagegroup

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

# rdk-x5-media-modules owns the reviewed native-HBN module roots and load
# order.  Keeping that policy in a normal recipe lets it ship a modules-load
# file while this packagegroup remains a feature-level composition point.
RDEPENDS:${PN} = " \
    hobot-camera \
    rdk-x5-media-modules \
"
