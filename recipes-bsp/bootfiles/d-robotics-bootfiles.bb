SUMMARY = "RDK X5 U-Boot boot assets and configuration partition"
DESCRIPTION = "Boot script and FAT configuration image compatible with the RDKOS 3.5.0 eMMC layout."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://boot.cmd;beginline=1;endline=1;md5=b2dccaa94b3629a08bfb4f983cad6f89"

require conf/machine/include/rdk-x5-release.inc

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = " \
    file://boot.cmd \
    file://hobot_config.sh \
"

# Wrynose unpacks local file:// assets directly into UNPACKDIR.
S = "${UNPACKDIR}"

# Build the script wrapper from the RDKOS-pinned U-Boot source instead of a
# generic upstream U-Boot release, so its format handling stays compatible
# with the bootloader installed in the board's persistent storage.
DEPENDS = "d-robotics-mkimage-native dosfstools-native mtools-native"

inherit deploy

do_compile() {
    mkimage -A arm -T script -C none -d ${UNPACKDIR}/boot.cmd ${B}/boot.scr

    truncate -s 256M ${B}/hobot-config.vfat
    mkfs.vfat -F 32 --invariant -n CONFIG ${B}/hobot-config.vfat
    mcopy -i ${B}/hobot-config.vfat ${UNPACKDIR}/hobot_config.sh ::hobot_config.sh
}

do_install() {
    install -d ${D}/boot/config
    install -m 0644 ${UNPACKDIR}/boot.cmd ${D}/boot/boot.cmd
    install -m 0644 ${B}/boot.scr ${D}/boot/boot.scr
}

do_deploy() {
    install -Dm 0644 ${B}/hobot-config.vfat ${DEPLOYDIR}/hobot-config.vfat
}
addtask deploy after do_compile before do_build

FILES:${PN} += " \
    /boot \
"
