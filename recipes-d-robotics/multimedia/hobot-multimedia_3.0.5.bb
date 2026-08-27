SUMMARY = "Selected D-Robotics RDK X5 multimedia runtime"
DESCRIPTION = "A small, audited subset of the RDKOS 3.5.0 multimedia runtime needed by the X5 BPU and camera stacks."
HOMEPAGE = "https://github.com/D-Robotics/x5-hobot-multimedia"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=3b83ef96387f14655fc854ddc3c6bd57"

require conf/machine/include/rdk-x5-release.inc

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = "${RDK_X5_GIT_URI}/x5-hobot-multimedia.git;protocol=https;branch=${RDK_X5_GIT_BRANCH};name=multimedia"
SRCREV_multimedia = "${RDK_X5_SRCREV_HOBOT_MULTIMEDIA}"

BB_GIT_SHALLOW ?= "1"
BB_GIT_SHALLOW_DEPTH_multimedia ?= "1"

# cJSON 1.7 keeps the same libcjson.so.1 ABI and supplies exactly the symbols
# used by libvpf and libcam.  Use OE's maintained provider instead of adding
# the vendor copy to the global loader path, where it could shadow other apps.
DEPENDS = "cjson"
RDEPENDS:${PN} += "cjson"

inherit rdk-x5-prebuilt-elf

# The selected files are stripped target artifacts.  Keep that property local
# to this recipe and rely on the audit class for architecture and ABI checks.
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"

RDK_X5_PREBUILT_ELF_MAX_GLIBC = "2.34"

# RDKOS clients have a fixed /usr/hobot/lib ABI path.  Keep that vendor path
# rather than relocating binaries and silently breaking their loader contract.
INSANE_SKIP:${PN} += "libdir"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}/usr/hobot/lib

    install -m 0644 ${S}/debian/usr/hobot/lib/libalog.so.1.0.1 ${D}/usr/hobot/lib/libalog.so.1.0.1
    ln -sf libalog.so.1.0.1 ${D}/usr/hobot/lib/libalog.so.1

    install -m 0644 ${S}/debian/usr/hobot/lib/libhbmem.so.1.0.0 ${D}/usr/hobot/lib/libhbmem.so.1.0.0
    ln -sf libhbmem.so.1.0.0 ${D}/usr/hobot/lib/libhbmem.so.1

    install -m 0644 ${S}/debian/usr/hobot/lib/libcnn_intf.so.1.3.6 ${D}/usr/hobot/lib/libcnn_intf.so.1.3.6
    ln -sf libcnn_intf.so.1.3.6 ${D}/usr/hobot/lib/libcnn_intf.so.1

    # The vendor library has no DT_SONAME and libvpf requests the bare name.
    # Install that name as a regular file, avoiding a development-only .so
    # symlink while retaining the exact runtime DT_NEEDED contract.
    install -m 0644 ${S}/debian/usr/hobot/lib/libgdcbin.so.1.0.2 ${D}/usr/hobot/lib/libgdcbin.so

    install -m 0644 ${S}/debian/usr/hobot/lib/libvpf.so.1.0.0 ${D}/usr/hobot/lib/libvpf.so.1.0.0
    ln -sf libvpf.so.1.0.0 ${D}/usr/hobot/lib/libvpf.so.1

    install -m 0644 ${S}/debian/usr/hobot/lib/libcam.so.1.1.0 ${D}/usr/hobot/lib/libcam.so.1.1.0
    ln -sf libcam.so.1.1.0 ${D}/usr/hobot/lib/libcam.so.1

    install -m 0644 ${S}/debian/usr/hobot/lib/libmultimedia.so.1.2.3 ${D}/usr/hobot/lib/libmultimedia.so.1.2.3
    ln -sf libmultimedia.so.1.2.3 ${D}/usr/hobot/lib/libmultimedia.so.1

    # Do not copy the vendor profile script: its broad LD_LIBRARY_PATH would
    # alter unrelated applications.  The normal dynamic-loader configuration
    # exposes only this explicitly selected vendor directory and sensor path.
    install -d ${D}${sysconfdir}/ld.so.conf.d
    printf '%s\n' '/usr/hobot/lib' '/usr/hobot/lib/sensor' > ${D}${sysconfdir}/ld.so.conf.d/rdk-x5-hobot.conf
}

# Make the private ABI available to recipes that explicitly DEPEND on this
# runtime.  It is intentionally not exported through normal shlib providers.
SYSROOT_DIRS:append = " /usr/hobot"

FILES:${PN} += " \
    ${sysconfdir}/ld.so.conf.d/rdk-x5-hobot.conf \
    /usr/hobot/lib/libalog.so.1 \
    /usr/hobot/lib/libalog.so.1.0.1 \
    /usr/hobot/lib/libcam.so.1 \
    /usr/hobot/lib/libcam.so.1.1.0 \
    /usr/hobot/lib/libcnn_intf.so.1 \
    /usr/hobot/lib/libcnn_intf.so.1.3.6 \
    /usr/hobot/lib/libgdcbin.so \
    /usr/hobot/lib/libhbmem.so.1 \
    /usr/hobot/lib/libhbmem.so.1.0.0 \
    /usr/hobot/lib/libmultimedia.so.1 \
    /usr/hobot/lib/libmultimedia.so.1.2.3 \
    /usr/hobot/lib/libvpf.so.1 \
    /usr/hobot/lib/libvpf.so.1.0.0 \
"
