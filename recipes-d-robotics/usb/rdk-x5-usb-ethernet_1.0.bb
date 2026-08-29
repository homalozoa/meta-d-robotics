SUMMARY = "RDK X5 USB host Ethernet support"
DESCRIPTION = "Curated USB Ethernet module roots and a systemd-networkd DHCP policy for RDK X5 host ports."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = "file://25-rdk-x5-usb-ethernet.network"
S = "${UNPACKDIR}"

# Keep provider names version-neutral.  Yocto resolves these roots to the
# exact 6.1.83 packages emitted by linux-d-robotics, including their module
# dependency closure and USB modalias metadata.
RDEPENDS:${PN} = " \
    ethtool \
    kernel-module-asix \
    kernel-module-ax88179-178a \
    kernel-module-cdc-eem \
    kernel-module-cdc-ether \
    kernel-module-cdc-ncm \
    kernel-module-ch9200 \
    kernel-module-dm9601 \
    kernel-module-lan78xx \
    kernel-module-mcs7830 \
    kernel-module-r8152 \
    kernel-module-r8153-ecm \
    kernel-module-rndis-host \
    kernel-module-rtl8150 \
    kernel-module-smsc75xx \
    kernel-module-smsc95xx \
    kernel-module-sr9700 \
    kernel-module-sr9800 \
    kernel-module-usbnet \
"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -Dm 0644 ${UNPACKDIR}/25-rdk-x5-usb-ethernet.network \
        ${D}${systemd_unitdir}/network/25-rdk-x5-usb-ethernet.network
}

FILES:${PN} = "${systemd_unitdir}/network/25-rdk-x5-usb-ethernet.network"
