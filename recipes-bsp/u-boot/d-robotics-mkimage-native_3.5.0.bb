SUMMARY = "D-Robotics RDK X5 U-Boot image tool"
DESCRIPTION = "Build the mkimage host utility from the U-Boot revision shipped by RDKOS 3.5.0."
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://Licenses/README;md5=2ca5f2c35c8cc335f0a19756634782f1"

require conf/machine/include/rdk-x5-release.inc

inherit native pkgconfig python3native

COMPATIBLE_MACHINE = "^rdk-x5$"
# Native tools are selected while BitBake evaluates the build host machine,
# not the eventual RDK X5 target.  Keep the layer-wide target declaration for
# metadata auditing but allow this host-only recipe to populate the native
# sysroot for that target's build.
COMPATIBLE_MACHINE:class-native = ".*"

SRC_URI = "${RDK_X5_GIT_URI}/x5-uboot.git;protocol=https;branch=${RDK_X5_GIT_BRANCH};name=uboot"
SRCREV_uboot = "${RDK_X5_SRCREV_UBOOT}"

# The RDKOS release pin is the advertised branch head.  Fetch only that
# immutable revision instead of the complete vendor U-Boot history.
BB_GIT_SHALLOW ?= "1"
BB_GIT_SHALLOW_DEPTH_uboot ?= "1"

# Keep generated configuration and host tools outside the immutable source
# checkout, as the corresponding OE-Core U-Boot tools recipe does.
B = "${WORKDIR}/build"
do_configure[cleandirs] = "${B}"

# Keep the toolchain and crypto feature set aligned with OE-Core's
# u-boot-tools-native recipe while compiling the RDK X5 vendor source.
# This is a standalone native recipe (rather than a target recipe extended
# with BBCLASSEXTEND), so spell out native providers explicitly.  Otherwise
# BitBake would schedule target-side crypto and util-linux dependencies.
DEPENDS = " \
    bison-native \
    flex-native \
    gnutls-native \
    openssl-native \
    python3-setuptools-native \
    swig-native \
    util-linux-native \
"
export STAGING_INCDIR = "${STAGING_INCDIR_NATIVE}"
EXTRA_OEMAKE = 'CC="${BUILD_CC} ${BUILD_CFLAGS} ${BUILD_LDFLAGS}" HOSTCC="${BUILD_CC} ${BUILD_CFLAGS} ${BUILD_LDFLAGS}" STRIP=true V=1'

do_compile() {
    # Updating the index before U-Boot calculates its version avoids a false
    # "-dirty" suffix after do_populate_lic creates a hard link.
    cd ${S}
    git diff
    cd ${B}

    oe_runmake -C ${S} tools-only_defconfig O=${B}

    # The license command is unnecessary for mkimage.  Leaving it enabled
    # makes U-Boot try to execute a target-side helper while building tools.
    sed -i -e "s/CONFIG_CMD_LICENSE=.*/# CONFIG_CMD_LICENSE is not set/" \
        -e "s/CONFIG_EFI_LOADER=.*/# CONFIG_EFI_LOADER is not set/" \
        ${B}/.config

    # RDK's U-Boot documents tools-only for host utility builds.  Unlike the
    # full cross_tools target it does not enable binman and its optional
    # pylibfdt Python binding, which is unrelated to boot.scr generation.
    oe_runmake -C ${S} tools-only NO_SDL=1 O=${B}
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/tools/mkimage ${D}${bindir}/d-robotics-mkimage
    ln -sf d-robotics-mkimage ${D}${bindir}/mkimage
}
