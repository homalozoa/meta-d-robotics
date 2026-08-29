SUMMARY = "RDK X5 common USB serial and UVC peripherals"
DESCRIPTION = "Curated kernel module roots and diagnostics for common robotics USB serial adapters and USB Video Class cameras."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

# Keep provider names version-neutral.  Yocto resolves these roots and their
# dependency closure exclusively from the pinned Linux 6.1.83 provider.  The
# standard USB modalias path loads them only when matching hardware is present.
RDEPENDS:${PN} = " \
    kernel-module-cdc-acm \
    kernel-module-ch341 \
    kernel-module-cp210x \
    kernel-module-ftdi-sio \
    kernel-module-pl2303 \
    kernel-module-uvcvideo \
    media-ctl \
    v4l-utils \
"

ALLOW_EMPTY:${PN} = "1"

