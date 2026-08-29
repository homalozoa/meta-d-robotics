SUMMARY = "Guarded RDK X5 pinmux and overlay configuration"
DESCRIPTION = "Dry-run-first board-ID-aware configuration with conflict handling, exact confirmation, atomic writes, and DTB backups."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = "file://rdk-x5-pinmux"
S = "${UNPACKDIR}"

RDEPENDS:${PN} = " \
    dtc \
    hobot-dtb-overlays \
    python3-core \
    python3-crypt \
    python3-fcntl \
    python3-io \
"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -Dm 0755 ${UNPACKDIR}/rdk-x5-pinmux \
        ${D}${sbindir}/rdk-x5-pinmux
}
