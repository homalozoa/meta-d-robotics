SUMMARY = "RDK X5 USB and serial-line CAN adapters"
DESCRIPTION = "Curated SocketCAN drivers and userspace tools for common robotics USB-CAN adapters."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

# These version-neutral roots resolve to modules from the pinned Linux 6.1.83
# provider.  USB drivers load through modaliases; SLCAN remains inactive until
# an operator explicitly attaches a known serial interface with slcan tools.
RDEPENDS:${PN} = " \
    can-utils \
    can-utils-slcan \
    kernel-module-ems-usb \
    kernel-module-gs-usb \
    kernel-module-peak-usb \
    kernel-module-slcan \
"

ALLOW_EMPTY:${PN} = "1"

