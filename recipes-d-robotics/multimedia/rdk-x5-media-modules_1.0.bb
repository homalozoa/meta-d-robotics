SUMMARY = "Native RDK X5 camera and codec module closure"
DESCRIPTION = "Explicit module roots and load policy for the RDKOS 3.5.0 native HBN camera, image-processing, VPU, and JPU path."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = "file://40-rdk-x5-media.conf"
S = "${UNPACKDIR}"

# These are roots, not the complete module list.  The pinned kernel's generated
# dependencies pull camsys, VIO common, VIN vnode, camera control, ISC, native
# ISP operations, CRC, PHY, and sensor helpers at the matching 6.1.83 release.
RDEPENDS:${PN} = " \
    kernel-module-hobot-codec-vnode \
    kernel-module-hobot-deserial \
    kernel-module-hobot-gdc \
    kernel-module-hobot-isi-sensor \
    kernel-module-hobot-jpu \
    kernel-module-hobot-lpwm \
    kernel-module-hobot-mipidbg \
    kernel-module-hobot-mipicsi \
    kernel-module-hobot-osd \
    kernel-module-hobot-vin-vcon \
    kernel-module-hobot-vpu \
    kernel-module-vs-csi-wrapper \
    kernel-module-vs-sif-nat \
    kernel-module-vs-vse-nat \
"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}${nonarch_libdir}/modules-load.d
    install -m 0644 ${UNPACKDIR}/40-rdk-x5-media.conf \
        ${D}${nonarch_libdir}/modules-load.d/40-rdk-x5-media.conf
}

FILES:${PN} += "${nonarch_libdir}/modules-load.d/40-rdk-x5-media.conf"
