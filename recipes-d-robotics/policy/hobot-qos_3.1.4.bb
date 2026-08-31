SUMMARY = "D-Robotics RDK X5 NoC QoS policy"
DESCRIPTION = "Validated systemd-native translation of the pinned RDKOS 3.5.0 NoC priority policy."
HOMEPAGE = "https://github.com/D-Robotics/x5-hobot-configs"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=3b83ef96387f14655fc854ddc3c6bd57"

require conf/machine/include/rdk-x5-release.inc

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = " \
    ${RDK_X5_GIT_URI}/x5-hobot-configs.git;protocol=https;branch=${RDK_X5_GIT_BRANCH};name=configs \
    file://rdk-x5-qos \
    file://rdk-x5-qos.service \
"
SRCREV_configs = "${RDK_X5_SRCREV_HOBOT_CONFIGS}"

BB_GIT_SHALLOW ?= "1"
BB_GIT_SHALLOW_DEPTH_configs ?= "1"

S = "${UNPACKDIR}/${BP}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "rdk-x5-qos.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

# Force a review if the pinned vendor release changes any priority.  The
# installed helper is a safer POSIX/systemd translation, not an independent
# policy table.
do_compile() {
    test "$(cat ${S}/VERSION)" = "${PV}"
    vendor_policy=${S}/debian/etc/init.d/S98qos_config.sh
    while IFS=' ' read -r qos_module read_priority write_priority; do
        grep -F "\"${qos_module}\" \"${read_priority}\" \"${write_priority}\"" \
            ${vendor_policy} >/dev/null || \
            bbfatal "RDKOS QoS policy changed for ${qos_module}"
    done <<'EOF'
20510500.sif_qos 7 7
20510280.isp_qos 7 7
20510100.dw230_qos 7 7
20540080.gpu3d_qos 0 0
20520000.bpu_qos 0 0
20540000.gpu2d_qos 0 0
20550100.gmac_qos 0 0
20510000.bt1120_qos 0 0
20510080.dc8000_qos 0 0
20530000.video_qos 0 0
20530080.jpeg_qos 0 0
EOF
}

do_install() {
    install -Dm 0755 ${UNPACKDIR}/rdk-x5-qos \
        ${D}${sbindir}/rdk-x5-qos
    install -Dm 0644 ${UNPACKDIR}/rdk-x5-qos.service \
        ${D}${systemd_system_unitdir}/rdk-x5-qos.service
}

FILES:${PN} += "${sbindir}/rdk-x5-qos"
