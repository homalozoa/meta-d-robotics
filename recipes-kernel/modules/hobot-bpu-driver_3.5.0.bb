SUMMARY = "D-Robotics RDK X5 BPU hardware I/O kernel module"
DESCRIPTION = "Build the RDKOS 3.5.0 BPU hardware I/O module against the pinned X5 kernel ABI."
HOMEPAGE = "https://github.com/D-Robotics/x5-hobot-drivers"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=3b83ef96387f14655fc854ddc3c6bd57"

require conf/machine/include/rdk-x5-release.inc

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = "${RDK_X5_GIT_URI}/x5-hobot-drivers.git;protocol=https;branch=${RDK_X5_GIT_BRANCH};name=drivers"
SRCREV_drivers = "${RDK_X5_SRCREV_HOBOT_DRIVERS}"

BB_GIT_SHALLOW ?= "1"
BB_GIT_SHALLOW_DEPTH_drivers ?= "1"

inherit module rdk-x5-prebuilt-elf

# The source package contains an AArch64 relocatable object instead of source
# for this module.  Kbuild links it against the exact shared kernel workdir;
# that gives modversion and vermagic validation before it reaches the image.
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"

KERNEL_MODULE_AUTOLOAD += "bpu_hw_io_x5"

do_rdk_x5_validate_kernel() {
    if [ "${KERNEL_VERSION}" != "${RDK_X5_KERNEL_VERSION}" ]; then
        bbfatal "RDK X5 BPU driver requires kernel ${RDK_X5_KERNEL_VERSION}, got ${KERNEL_VERSION}"
    fi
}
addtask rdk_x5_validate_kernel after do_configure before do_compile

do_compile() {
    unset CFLAGS CPPFLAGS CXXFLAGS LDFLAGS

    oe_runmake -C ${STAGING_KERNEL_DIR} \
        O=${STAGING_KERNEL_BUILDDIR} \
        M=${S}/bpu-hw_io \
        ARCH=${ARCH} \
        CROSS_COMPILE=${TARGET_PREFIX} \
        CC="${KERNEL_CC}" \
        LD="${KERNEL_LD}" \
        AR="${KERNEL_AR}" \
        OBJCOPY="${KERNEL_OBJCOPY}" \
        STRIP="${KERNEL_STRIP}" \
        modules
}

do_install() {
    install -Dm 0644 ${S}/bpu-hw_io/bpu_hw_io_x5.ko \
        ${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/kernel/drivers/soc/hobot/bpu/hw_io/bpu_hw_io_x5.ko
}
