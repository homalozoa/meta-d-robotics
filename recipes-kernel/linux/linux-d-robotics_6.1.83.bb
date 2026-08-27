SUMMARY = "D-Robotics RDK X5 Linux kernel"
DESCRIPTION = "Pinned RDKOS 3.5.0 Linux kernel and camera driver source for RDK X5."
SECTION = "kernel"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=6bc538ed5bd9a7fc9398086aedcd7e46"

require conf/machine/include/rdk-x5-release.inc

inherit kernel

COMPATIBLE_MACHINE = "^rdk-x5$"

LINUX_VERSION = "${RDK_X5_KERNEL_VERSION}"
PV = "${LINUX_VERSION}+git"

SRC_URI = " \
    ${RDK_X5_GIT_URI}/x5-kernel.git;protocol=https;branch=${RDK_X5_GIT_BRANCH};name=kernel \
    ${RDK_X5_GIT_URI}/x5-drv-camsys.git;protocol=https;branch=${RDK_X5_GIT_BRANCH};name=camsys;destsuffix=${BP}/drivers/media/platform/horizon/camsys \
    file://0001-dm-fdekey-validate-delimiter-before-offset.patch \
    file://0002-gc820-match-core-id-function-signatures.patch \
    file://0003-btrfs-allow-nul-in-root-name-map.patch \
    file://0004-gc8000l-match-query-signal-status-type.patch \
    file://0005-btrfs-order-kvcalloc-arguments.patch \
    file://0006-goodix-use-irq-value-for-polling-cleanup.patch \
    file://0007-rtl-wifi-keep-vendor-address-checks-as-warnings.patch \
    file://0008-rtl8852bs-fix-guard-and-physts-prototypes.patch \
"
SRCREV_kernel = "${RDK_X5_SRCREV_KERNEL}"
SRCREV_camsys = "${RDK_X5_SRCREV_DRV_CAMSYS}"
SRCREV_FORMAT = "kernel_camsys"

# kernel.bbclass relocates a legacy source tree to STAGING_KERNEL_DIR after
# unpacking.  Keep the two vendor Git trees together under BitBake's standard
# unpack directory so the relocation also carries the camera driver source.
S = "${UNPACKDIR}/${BP}"

# RDKOS 3.5.0 pins both revisions to the advertised branch heads.  Avoid
# mirroring the complete vendor kernel history when the immutable sources can
# be fetched with a depth of one.  A downstream override can still disable
# shallow fetching when it intentionally selects a non-tip revision.
BB_GIT_SHALLOW ?= "1"
BB_GIT_SHALLOW_DEPTH_kernel ?= "1"
BB_GIT_SHALLOW_DEPTH_camsys ?= "1"

KBUILD_DEFCONFIG = "hobot_x5_rdk_ubuntu_defconfig"
KERNEL_IMAGETYPE = "Image"
KERNEL_DEVICETREE = "hobot/x5-rdk.dtb hobot/x5-rdk-v1p0.dtb"
KERNEL_DTBDEST = "boot/hobot"
KERNEL_LOCALVERSION = ""

# kernel-src contains generated vendor tables whose source comments retain
# TMPDIR.  Scope the exception to that source-only debug package; all runtime,
# development, and kernel image packages continue through normal QA.
INSANE_SKIP:${PN}-src += "buildpaths"

# The pinned vendor defconfig enables CONFIG_WERROR.  Keep it enabled by
# default and only switch this variable after a reproducible Wrynose compiler
# failure demonstrates that a warning is being promoted to a build error.
RDK_X5_DISABLE_WERROR ?= "0"

do_rdk_x5_normalize_rtl_headers() {
    # RDKOS ships these Realtek headers with CRLF line endings.  Normalize
    # only the files patched below so standard OpenEmbedded patch handling is
    # deterministic across host environments.
    sed -i -e 's/\r$//' \
        ${S}/drivers/staging/rtl8852bs/phl/hal_g6/phy/bb/halbb_physts.h \
        ${S}/drivers/staging/rtl8852bs/phl/hal_g6/phy/rf/halrf_8852b/halrf_ops_rtl8852b.h
}
# kernel.bbclass migrates S into the shared source workdir before patching.
# Make the line-ending conversion wait for that migration instead of racing it.
addtask rdk_x5_normalize_rtl_headers after do_symlink_kernsrc before do_patch

do_configure:prepend() {
    install -d ${B}
    install -m 0644 ${S}/arch/${ARCH}/configs/${KBUILD_DEFCONFIG} ${B}/.config

    if [ "${RDK_X5_DISABLE_WERROR}" = "1" ]; then
        sed -i -e 's/^CONFIG_WERROR=y$/# CONFIG_WERROR is not set/' ${B}/.config
        grep -q '^# CONFIG_WERROR is not set$' ${B}/.config
    fi
}
