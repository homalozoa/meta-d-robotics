SUMMARY = "Base RDK X5 HDMI and graphics support"
DESCRIPTION = "Explicit module and DRM diagnostic closure for the RDK X5 GPU, N2D engine, SII9022 HDMI bridge, and VeriSilicon display pipeline."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = "file://30-rdk-x5-display.conf"
S = "${UNPACKDIR}"

# Keep the six roots from the RDKOS 3.5.0 RDK-X5 S70loadko contract explicit.
# Dependencies generated from the pinned 6.1.83 modules pull the matching DRM
# helpers, I2C mux, framebuffer helpers, and VIO common module.
RDEPENDS:${PN} = " \
    libdrm-modetest \
    kernel-module-drm-kms-helper \
    kernel-module-galcore \
    kernel-module-sii902x \
    kernel-module-vio-n2d \
    kernel-module-vs-drm \
    kernel-module-vs-x5-syscon-bridge \
"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}${nonarch_libdir}/modules-load.d
    install -m 0644 ${UNPACKDIR}/30-rdk-x5-display.conf \
        ${D}${nonarch_libdir}/modules-load.d/30-rdk-x5-display.conf
}

FILES:${PN} += "${nonarch_libdir}/modules-load.d/30-rdk-x5-display.conf"
