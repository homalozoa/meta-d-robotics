SUMMARY = "D-Robotics RDK X5 onboard wireless support"
DESCRIPTION = "Pinned AIC8800D80 firmware, kernel module policy, antenna control, Wi-Fi networking, and UART Bluetooth initialization for RDK X5."
HOMEPAGE = "https://github.com/D-Robotics/x5-hobot-wifi"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=3b83ef96387f14655fc854ddc3c6bd57"

require conf/machine/include/rdk-x5-release.inc

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = " \
    ${RDK_X5_GIT_URI}/x5-hobot-wifi.git;protocol=https;branch=${RDK_X5_GIT_BRANCH};name=wifi \
    file://20-rdk-x5-wireless.conf \
    file://30-rdk-x5-wlan0.network \
    file://hobot-wifi.default \
    file://hobot-wifi.service \
    file://hobot-wpa-supplicant.service \
    file://hobot-bluetooth.service \
    file://wait-for-hci0 \
    file://wpa_supplicant-wlan0.conf \
"
SRCREV_wifi = "${RDK_X5_SRCREV_HOBOT_WIFI}"

BB_GIT_SHALLOW ?= "1"
BB_GIT_SHALLOW_DEPTH_wifi ?= "1"

inherit systemd

# Keep the BSP usable without an integration distro: by default it installs
# the original standalone wpa_supplicant + systemd-networkd policy.  Images
# which provide another Wi-Fi manager can remove this feature without also
# losing the board-specific firmware, antenna setup, or Bluetooth support.
PACKAGECONFIG ??= "standalone-wifi"
PACKAGECONFIG[standalone-wifi] = ",,,systemd-networkd wpa-supplicant"

HOBOT_WIFI_STANDALONE_CONFFILES = " \
    ${sysconfdir}/systemd/network/30-rdk-x5-wlan0.network \
    ${sysconfdir}/wpa_supplicant/wpa_supplicant-wlan0.conf \
"

# These are the complete AIC8800D80 files shipped by the pinned RDKOS source.
# Do not copy the adjacent Broadcom/Realtek firmware or bundled host libraries:
# they target other boards and would obscure the X5 hardware contract.
RDK_X5_AIC8800D80_FIRMWARE = " \
    aic_powerlimit_8800d80.txt \
    aic_userconfig_8800d80.txt \
    fmacfw_8800d80_h_u02.bin \
    fmacfw_8800d80_h_u02_ipc.bin \
    fmacfw_8800d80_u02.bin \
    fmacfw_8800d80_u02_ipc.bin \
    fmacfwbt_8800d80_h_u02.bin \
    fmacfwbt_8800d80_u02.bin \
    fw_adid_8800d80_u02.bin \
    fw_patch_8800d80_u02.bin \
    fw_patch_8800d80_u02_ext0.bin \
    fw_patch_8800d80_u04.bin \
    fw_patch_table_8800d80_u02.bin \
    fw_patch_table_8800d80_u04.bin \
    lmacfw_rf_8800d80_u02.bin \
"

# The module roots keep Linux's generated dependency closure version-neutral.
# BlueZ supplies hciattach for the board's ttyS5 transport.  When selected,
# the standalone WPA service is credential-free and keeps its mutable
# configuration in /etc.
RDEPENDS:${PN} = " \
    bash \
    bluez5 \
    iproute2-ip \
    iw \
    kmod \
    util-linux-rfkill \
    wireless-regdb-static \
    kernel-module-aic8800-bsp \
    kernel-module-aic8800-fdrv \
    kernel-module-hci-uart \
"

SYSTEMD_SERVICE:${PN} = " \
    hobot-wifi.service \
    ${@bb.utils.contains('PACKAGECONFIG', 'standalone-wifi', 'hobot-wpa-supplicant.service', '', d)} \
    hobot-bluetooth.service \
"
SYSTEMD_AUTO_ENABLE = "enable"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}/vendor/etc/firmware
    for firmware in ${RDK_X5_AIC8800D80_FIRMWARE}; do
        install -m 0644 ${S}/debian/vendor/etc/firmware/$firmware \
            ${D}/vendor/etc/firmware/$firmware
    done

    install -d ${D}${bindir}
    install -m 0755 ${S}/debian/bin/switch_antenna \
        ${D}${bindir}/switch_antenna

    install -d ${D}${nonarch_libdir}/modules-load.d
    install -m 0644 ${UNPACKDIR}/20-rdk-x5-wireless.conf \
        ${D}${nonarch_libdir}/modules-load.d/20-rdk-x5-wireless.conf

    install -d ${D}${sysconfdir}/default
    install -m 0644 ${UNPACKDIR}/hobot-wifi.default \
        ${D}${sysconfdir}/default/hobot-wifi

    if ${@bb.utils.contains('PACKAGECONFIG', 'standalone-wifi', 'true', 'false', d)}; then
        install -d ${D}${sysconfdir}/systemd/network
        install -m 0644 ${UNPACKDIR}/30-rdk-x5-wlan0.network \
            ${D}${sysconfdir}/systemd/network/30-rdk-x5-wlan0.network

        install -d ${D}${sysconfdir}/wpa_supplicant
        install -m 0600 ${UNPACKDIR}/wpa_supplicant-wlan0.conf \
            ${D}${sysconfdir}/wpa_supplicant/wpa_supplicant-wlan0.conf
    fi

    install -d ${D}${libexecdir}/hobot-wifi
    install -m 0755 ${UNPACKDIR}/wait-for-hci0 \
        ${D}${libexecdir}/hobot-wifi/wait-for-hci0

    install -d ${D}${systemd_system_unitdir}
    for service in hobot-wifi.service hobot-bluetooth.service; do
        install -m 0644 ${UNPACKDIR}/$service \
            ${D}${systemd_system_unitdir}/$service
    done

    if ${@bb.utils.contains('PACKAGECONFIG', 'standalone-wifi', 'true', 'false', d)}; then
        install -m 0644 ${UNPACKDIR}/hobot-wpa-supplicant.service \
            ${D}${systemd_system_unitdir}/hobot-wpa-supplicant.service
    fi
}

CONFFILES:${PN} += " \
    ${sysconfdir}/default/hobot-wifi \
    ${@bb.utils.contains('PACKAGECONFIG', 'standalone-wifi', d.getVar('HOBOT_WIFI_STANDALONE_CONFFILES'), '', d)} \
"

FILES:${PN} += " \
    /vendor/etc/firmware \
    ${nonarch_libdir}/modules-load.d/20-rdk-x5-wireless.conf \
    ${sysconfdir}/default/hobot-wifi \
    ${sysconfdir}/systemd/network/30-rdk-x5-wlan0.network \
    ${sysconfdir}/wpa_supplicant/wpa_supplicant-wlan0.conf \
    ${libexecdir}/hobot-wifi/wait-for-hci0 \
"
