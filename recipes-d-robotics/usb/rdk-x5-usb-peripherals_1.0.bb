SUMMARY = "RDK X5 common USB serial, HID, and UVC peripherals"
DESCRIPTION = "Curated kernel module roots and diagnostics for common robotics USB serial adapters, Human Interface Devices, and USB Video Class cameras."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = "file://30-rdk-x5-usb-input.conf"
S = "${UNPACKDIR}"

# Keep provider names version-neutral.  Yocto resolves these roots and their
# dependency closure exclusively from the pinned Linux 6.1.83 provider.  The
# device-specific drivers use modalias loading.  Load evdev explicitly because
# it is the common character-device handler for every attached HID input.
RDEPENDS:${PN} = " \
    evtest \
    kernel-module-cdc-acm \
    kernel-module-ch341 \
    kernel-module-cp210x \
    kernel-module-evdev \
    kernel-module-ftdi-sio \
    kernel-module-pl2303 \
    kernel-module-uvcvideo \
    media-ctl \
    v4l-utils \
"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}${nonarch_libdir}/modules-load.d
    install -m 0644 ${UNPACKDIR}/30-rdk-x5-usb-input.conf \
        ${D}${nonarch_libdir}/modules-load.d/30-rdk-x5-usb-input.conf
}

FILES:${PN} += "${nonarch_libdir}/modules-load.d/30-rdk-x5-usb-input.conf"
