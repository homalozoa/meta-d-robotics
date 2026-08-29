SUMMARY = "Core RDK X5 board peripheral support"
DESCRIPTION = "Explicit kernel module and userspace closure for the onboard RTC, status LED, CAN FD controller, SPI endpoints, I2C, and GPIO diagnostics."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = "file://30-rdk-x5-peripherals.conf"
S = "${UNPACKDIR}"

# Use only stable, release-independent module provider names.  tcan4x5x pulls
# m_can and can-dev; can_raw is an independent protocol module needed by the
# standard SocketCAN tools.
RDEPENDS:${PN} = " \
    can-utils \
    i2c-tools \
    libgpiod-tools \
    util-linux-hwclock \
    kernel-module-can-raw \
    kernel-module-leds-gpio \
    kernel-module-rtc-hpu3501 \
    kernel-module-spidev \
    kernel-module-tcan4x5x \
"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}${nonarch_libdir}/modules-load.d
    install -m 0644 ${UNPACKDIR}/30-rdk-x5-peripherals.conf \
        ${D}${nonarch_libdir}/modules-load.d/30-rdk-x5-peripherals.conf
}

FILES:${PN} += "${nonarch_libdir}/modules-load.d/30-rdk-x5-peripherals.conf"
