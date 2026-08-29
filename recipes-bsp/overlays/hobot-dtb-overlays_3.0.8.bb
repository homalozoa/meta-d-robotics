SUMMARY = "D-Robotics RDK X5 device-tree overlays"
DESCRIPTION = "Source-built, opt-in overlays from the pinned RDKOS 3.5.0 board support release."
HOMEPAGE = "https://github.com/D-Robotics/x5-hobot-dtb"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=3b83ef96387f14655fc854ddc3c6bd57"

require conf/machine/include/rdk-x5-release.inc

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = "${RDK_X5_GIT_URI}/x5-hobot-dtb.git;protocol=https;branch=${RDK_X5_GIT_BRANCH};name=dtb"
SRCREV_dtb = "${RDK_X5_SRCREV_HOBOT_DTB}"

BB_GIT_SHALLOW ?= "1"
BB_GIT_SHALLOW_DEPTH_dtb ?= "1"

S = "${UNPACKDIR}/${BP}"
B = "${WORKDIR}/build"

DEPENDS = "dtc-native"

# Keep this an explicit production allowlist.  The vendor test overlay and the
# X3-only ION resize overlay are deliberately excluded from an X5 image.
RDK_X5_OVERLAYS = " \
    dtoverlay_1_wire \
    dtoverlay_cam0_imx219 \
    dtoverlay_cam0_imx477 \
    dtoverlay_cam0_ov5647 \
    dtoverlay_cam1_imx219 \
    dtoverlay_cam1_imx477 \
    dtoverlay_cam1_ov5647 \
    dtoverlay_imu_bmi088_i2c5_x5_rdk \
    dtoverlay_imu_bmi088_spi1_x5_rdk \
    dtoverlay_imu_icm42688_i2c4_6_x5_rdk \
    dtoverlay_pps_gpio \
    dtoverlay_pwm0 \
    dtoverlay_pwm1 \
    dtoverlay_pwm2 \
    dtoverlay_pwm3 \
    dtoverlay_pwm0123 \
    dtoverlay_spi5_spidev \
"

# Install the module roots needed by the opt-in overlays.  They remain idle
# until matching hardware is selected in /boot/config.txt and the board is
# rebooted; no overlay is enabled by this package.
RDEPENDS:${PN} = " \
    kernel-module-bmi08a-spi-driver \
    kernel-module-bmi08g-spi-driver \
    kernel-module-bmi08x-i2c-driver \
    kernel-module-imx219 \
    kernel-module-imx477 \
    kernel-module-inv-icm42600-i2c \
    kernel-module-ov5647 \
    kernel-module-pps-gpio \
    kernel-module-spidev \
    kernel-module-w1-gpio \
    kernel-module-w1-therm \
"

do_configure[noexec] = "1"
do_compile[cleandirs] = "${B}"
do_compile() {
    for overlay in ${RDK_X5_OVERLAYS}; do
        dtc -q -I dts -O dtb \
            -o ${B}/${overlay}.dtbo \
            ${S}/debian/boot/overlays/${overlay}.dts
    done
}

do_install() {
    install -d ${D}/boot/overlays
    for overlay in ${RDK_X5_OVERLAYS}; do
        install -m 0644 ${B}/${overlay}.dtbo \
            ${D}/boot/overlays/${overlay}.dtbo
    done
    install -m 0644 ${S}/debian/boot/overlays/README.txt \
        ${D}/boot/overlays/README.rdkos.txt
}

FILES:${PN} = "/boot/overlays"
