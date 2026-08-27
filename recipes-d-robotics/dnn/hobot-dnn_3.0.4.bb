SUMMARY = "D-Robotics RDK X5 DNN runtime"
DESCRIPTION = "Audited BPU inference runtime from the RDKOS 3.5.0 release."
HOMEPAGE = "https://github.com/D-Robotics/x5-hobot-dnn"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=3b83ef96387f14655fc854ddc3c6bd57"

require conf/machine/include/rdk-x5-release.inc

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = "${RDK_X5_GIT_URI}/x5-hobot-dnn.git;protocol=https;branch=${RDK_X5_GIT_BRANCH};name=dnn"
SRCREV_dnn = "${RDK_X5_SRCREV_HOBOT_DNN}"

BB_GIT_SHALLOW ?= "1"
BB_GIT_SHALLOW_DEPTH_dnn ?= "1"

# The DNN runtime's vendor ABI closure is supplied by hobot-multimedia.  Keep
# the dependency explicit so its /usr/hobot/lib files enter this audit's
# recipe sysroot and image installations remain deterministic.
DEPENDS = "hobot-multimedia"
RDEPENDS:${PN} += "hobot-multimedia"

inherit rdk-x5-prebuilt-elf

INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"

RDK_X5_PREBUILT_ELF_MAX_GLIBC = "2.34"
RDK_X5_PREBUILT_ELF_MAX_GLIBCXX = "3.4.29"

# RDKOS clients load these files from the vendor ABI directory.  Relocating
# them would invalidate the compatible loader configuration installed by
# hobot-multimedia.
INSANE_SKIP:${PN} += "libdir"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}/usr/hobot/lib
    install -m 0644 ${S}/x5/usr/lib/libdnn.so ${D}/usr/hobot/lib/libdnn.so
    install -m 0644 ${S}/x5/usr/lib/libhbrt_bayes_aarch64.so ${D}/usr/hobot/lib/libhbrt_bayes_aarch64.so

    install -d ${D}${includedir}/dnn
    install -m 0644 ${S}/x5/usr/include/dnn/hb_dnn.h ${D}${includedir}/dnn/hb_dnn.h
    install -m 0644 ${S}/x5/usr/include/dnn/hb_dnn_ext.h ${D}${includedir}/dnn/hb_dnn_ext.h
    install -m 0644 ${S}/x5/usr/include/dnn/hb_dnn_status.h ${D}${includedir}/dnn/hb_dnn_status.h
    install -m 0644 ${S}/x5/usr/include/dnn/hb_sys.h ${D}${includedir}/dnn/hb_sys.h
}

# Explicitly stage the private library directory for opt-in consumers.  The
# OpenCV 3.4 runtime and headers in the vendor archive are not installed:
# Wrynose owns the system OpenCV ABI.  The vendor dnn_server executable is
# similarly excluded until a board-level service contract is verified.
SYSROOT_DIRS:append = " /usr/hobot"

FILES:${PN} += " \
    /usr/hobot/lib/libdnn.so \
    /usr/hobot/lib/libhbrt_bayes_aarch64.so \
"
FILES:${PN}-dev += " \
    ${includedir}/dnn \
"
