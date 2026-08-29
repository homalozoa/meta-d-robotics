SUMMARY = "D-Robotics RDK X5 GPIO Python API"
DESCRIPTION = "Source-built Hobot.GPIO and RPi.GPIO-compatible Python interfaces with the official RDK X5 40-pin map."
HOMEPAGE = "https://github.com/D-Robotics/x5-hobot-io"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=3b83ef96387f14655fc854ddc3c6bd57"

require conf/machine/include/rdk-x5-release.inc

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = "${RDK_X5_GIT_URI}/x5-hobot-io.git;protocol=https;branch=${RDK_X5_GIT_BRANCH};name=io"
SRCREV_io = "${RDK_X5_SRCREV_HOBOT_IO}"

BB_GIT_SHALLOW ?= "1"
BB_GIT_SHALLOW_DEPTH_io ?= "1"

S = "${UNPACKDIR}/${BP}"

inherit setuptools3

SETUPTOOLS_SETUP_PATH = "${S}/hb_gpio_py/hobot-gpio"

RDEPENDS:${PN} += "python3-core"

# The library intentionally remains root-only.  Do not install the adjacent
# vendor rule that makes GPIO, PWM, SPI, galcore, and nano2d world-writable.
# The prebuilt hb_gpioinfo/libgpiod.a and the Debian-specific srpi-config are
# also outside this source-built Python package.
