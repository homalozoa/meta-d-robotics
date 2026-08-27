SUMMARY = "D-Robotics RDK X5 camera capture runtime"
DESCRIPTION = "Selected RDK X5 camera userspace plugins with their required vendor kernel capture modules."

require conf/machine/include/rdk-x5-release.inc

inherit packagegroup

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

# These are the camera-facing roots of the 6.1.83 module dependency graph.
# Their versioned RPM dependencies pull the matching camsys, PHY, VIO, VIN,
# and sensor helper modules, so this group neither guesses a kernel release
# nor installs the unrelated kernel-modules metapackage.
RDEPENDS:${PN} = " \
    hobot-camera \
    kernel-module-hobot-mipicsi \
    kernel-module-hobot-isi-sensor \
    kernel-module-hobot-lpwm \
    kernel-module-hobot-vin-vcon \
"
