# SPDX-License-Identifier: MIT
#
# U-Boot distro-boot script for the D-Robotics RDK X5 block-device image.  The
# immutable NAND-resident miniboot and U-Boot select this script from the ext4
# root partition; this script deliberately never updates NAND.

echo "RDK X5 boot script: ${devtype} ${devnum}:${devplist}"

setenv imagefile "Image"
setenv fdtfile "x5-rdk-v1p0.dtb"
setenv uart_baudrate "115200"

# RDK X5 v1.0 and v1.1 use distinct base device trees.  Keep the conservative
# v1.1 default when an older bootloader has not supplied hb_board_id.
if test "${hb_board_id}" = "0x0301"; then
    setenv fdtfile "x5-rdk.dtb"
fi
if test "${hb_board_id}" = "0x0302"; then
    setenv fdtfile "x5-rdk-v1p0.dtb"
fi

setenv flash_partitions "mtdparts=spi7.0:0x700000@0x0(miniboot),0x180000@0x700000(ubootenv)"
setenv rootfs_args "rootfstype=ext4 rw rootwait root=/dev/mmcblk${devnum}p${devplist} ${flash_partitions}"
setenv bootargs "console=tty1 console=ttyS0,${uart_baudrate} ${rootfs_args} hobotboot.reason=${reset_reason}"

echo "RDK X5 FDT: ${prefix}hobot/${fdtfile}"
if ext4load ${devtype} ${devnum}:${devplist} ${fdt_addr_r} ${prefix}hobot/${fdtfile}; then
    # dtoverlay and setpin are vendor U-Boot commands.  They consume the
    # optional config file directly and leave the base tree usable when it is
    # absent, matching the RDKOS boot flow.
    dtoverlay ${fdt_addr_r} 0x85000000 ${prefix}config.txt 0x85800000
    setpin ${prefix}config.txt 0x85800000

    echo "RDK X5 kernel: ${prefix}${imagefile}"
    if ext4load ${devtype} ${devnum}:${devplist} ${kernel_addr_r} ${prefix}${imagefile}; then
        booti ${kernel_addr_r} - ${fdt_addr_r}
    fi
fi

echo "RDK X5 boot script could not load kernel or device tree"
