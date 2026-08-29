SUMMARY = "Onboard RDK X5 audio support"
DESCRIPTION = "Explicit kernel module and ALSA diagnostic closure for the RDK X5 ES8326 codec and duplex sound cards."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = "file://30-rdk-x5-audio.conf"
S = "${UNPACKDIR}"

# The pinned RDK X5 DTBs enable the ES8326 codec, both DesignWare I2S links,
# duplex_card, and snd0.  snd-soc-duplex-card is not named by the legacy
# S70loadko script because the Debian kernel package keeps every module
# available for modalias loading; Yocto's split packages require it explicitly.
RDEPENDS:${PN} = " \
    alsa-utils-amixer \
    alsa-utils-aplay \
    kernel-module-designware-i2s \
    kernel-module-snd-soc-duplex-card \
    kernel-module-snd-soc-es8326 \
    kernel-module-snd-soc-hobot-sound-duplex-host \
"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}${nonarch_libdir}/modules-load.d
    install -m 0644 ${UNPACKDIR}/30-rdk-x5-audio.conf \
        ${D}${nonarch_libdir}/modules-load.d/30-rdk-x5-audio.conf
}

FILES:${PN} += "${nonarch_libdir}/modules-load.d/30-rdk-x5-audio.conf"
