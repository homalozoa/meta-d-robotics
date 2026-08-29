SUMMARY = "Selected D-Robotics RDK X5 graphics development headers"
DESCRIPTION = "Build-only EGL, GLES2, GBM, and Nano2D headers from the exact RDKOS 3.5.0 multimedia development release."
HOMEPAGE = "https://github.com/D-Robotics/x5-hobot-multimedia-dev"
LICENSE = "Apache-2.0 & MIT"
LIC_FILES_CHKSUM = " \
    file://LICENSE;md5=3b83ef96387f14655fc854ddc3c6bd57 \
    file://usr/hobot/include/GLES2/gl2.h;beginline=8;endline=29;md5=dead6c11b351746950831f19bad48bd3 \
"

require conf/machine/include/rdk-x5-release.inc

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = "${RDK_X5_GIT_URI}/x5-hobot-multimedia-dev.git;protocol=https;branch=${RDK_X5_GIT_BRANCH};name=dev;destsuffix=dev"
SRCREV_dev = "${RDK_X5_SRCREV_HOBOT_MULTIMEDIA_DEV}"

BB_GIT_SHALLOW ?= "1"
BB_GIT_SHALLOW_DEPTH_dev ?= "1"

S = "${UNPACKDIR}/dev"

do_configure() {
    source_version=$(tr -d '\r\n' < ${S}/VERSION)
    if [ "$source_version" != "${PV}" ]; then
        bbfatal "x5-hobot-multimedia-dev version drift: expected ${PV}, found $source_version"
    fi
}

do_compile[noexec] = "1"

do_install() {
    install -d ${D}/usr/hobot/include

    # Keep this private header surface deliberately smaller than the vendor
    # archive: GLES1/3, OpenCL, Vulkan, VDK, and unrelated multimedia headers
    # require their own runtime/provider review before they enter a sysroot.
    for header_dir in EGL GLES2 KHR; do
        cp -R --no-preserve=ownership ${S}/usr/hobot/include/$header_dir \
            ${D}/usr/hobot/include/
    done
    install -m 0644 ${S}/usr/hobot/include/gbm.h \
        ${D}/usr/hobot/include/gbm.h
    cp -R --no-preserve=ownership ${S}/usr/include/GC820 \
        ${D}/usr/hobot/include/GC820
}

SYSROOT_DIRS:append = " /usr/hobot"

FILES:${PN} = "/usr/hobot/include"
