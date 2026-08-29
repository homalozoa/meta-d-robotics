SUMMARY = "D-Robotics RDK X5 USB Ethernet gadget"
DESCRIPTION = "Pinned RDKOS USB gadget setup adapted to systemd and systemd-networkd for RNDIS and CDC ECM networking."
HOMEPAGE = "https://github.com/D-Robotics/x5-hobot-utils"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=3b83ef96387f14655fc854ddc3c6bd57"

require conf/machine/include/rdk-x5-release.inc

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = " \
    ${RDK_X5_GIT_URI}/x5-hobot-utils.git;protocol=https;branch=${RDK_X5_GIT_BRANCH} \
    file://0001-usb-gadget-make-launcher-portable-to-yocto.patch \
    file://hobot-usb-gadget.service \
    file://30-rdk-x5-usb0.network \
    file://30-rdk-x5-usb1.network \
"
SRCREV = "${RDK_X5_SRCREV_HOBOT_UTILS}"

BB_GIT_SHALLOW ?= "1"
BB_GIT_SHALLOW_DEPTH ?= "1"

inherit systemd

# The two function modules pull libcomposite and u_ether through their kernel
# package dependency metadata.  Keep the roots release-independent instead of
# spelling a generated package name containing Linux 6.1.83.
RDEPENDS:${PN} = " \
    bash \
    kmod \
    systemd-networkd \
    kernel-module-usb-f-ecm \
    kernel-module-usb-f-rndis \
"

SYSTEMD_SERVICE:${PN} = "hobot-usb-gadget.service"
SYSTEMD_AUTO_ENABLE = "enable"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}${libexecdir}/hobot-usb-gadget
    install -m 0755 ${S}/x5/static/etc/init.d/usb-gadget.sh \
        ${D}${libexecdir}/hobot-usb-gadget/usb-gadget.sh

    install -d ${D}${sysconfdir}/hobot-usb-gadget
    install -m 0644 ${S}/x5/static/etc/init.d/.usb/.rndis-ecm-config \
        ${D}${sysconfdir}/hobot-usb-gadget/.rndis-ecm-config

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/hobot-usb-gadget.service \
        ${D}${systemd_system_unitdir}/hobot-usb-gadget.service

    install -d ${D}${sysconfdir}/systemd/network
    install -m 0644 ${UNPACKDIR}/30-rdk-x5-usb0.network \
        ${D}${sysconfdir}/systemd/network/30-rdk-x5-usb0.network
    install -m 0644 ${UNPACKDIR}/30-rdk-x5-usb1.network \
        ${D}${sysconfdir}/systemd/network/30-rdk-x5-usb1.network
}

CONFFILES:${PN} += " \
    ${sysconfdir}/hobot-usb-gadget/.rndis-ecm-config \
    ${sysconfdir}/systemd/network/30-rdk-x5-usb0.network \
    ${sysconfdir}/systemd/network/30-rdk-x5-usb1.network \
"

FILES:${PN} += " \
    ${libexecdir}/hobot-usb-gadget/usb-gadget.sh \
    ${sysconfdir}/hobot-usb-gadget/.rndis-ecm-config \
    ${sysconfdir}/systemd/network/30-rdk-x5-usb0.network \
    ${sysconfdir}/systemd/network/30-rdk-x5-usb1.network \
"
