#!/usr/bin/env bash
set -euo pipefail

layer_dir="$(cd -- "$(dirname "${BASH_SOURCE[0]}")/.." >/dev/null 2>&1 && pwd -P)"

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

require_line() {
  local path=$1
  local expected=$2
  grep -Fqx -- "$expected" "$path" ||
    fail "${path#$layer_dir/} is missing: $expected"
}

layer_conf="$layer_dir/conf/layer.conf"
[ -f "$layer_conf" ] || fail "conf/layer.conf is missing"

require_line "$layer_conf" 'BBFILE_COLLECTIONS += "d-robotics"'
require_line "$layer_conf" 'LAYERDEPENDS_d-robotics = "core"'
require_line "$layer_conf" 'LAYERSERIES_COMPAT_d-robotics = "wrynose"'
require_line "$layer_conf" 'D_ROBOTICS_LAYERDIR := "${LAYERDIR}"'

elf_audit="$layer_dir/scripts/audit-prebuilt-elf.py"
elf_audit_class="$layer_dir/classes-recipe/rdk-x5-prebuilt-elf.bbclass"
elf_audit_test="$layer_dir/tests/test_audit_prebuilt_elf.py"
[ -f "$elf_audit" ] || fail "RDK X5 prebuilt ELF audit script is missing"
[ -f "$elf_audit_class" ] || fail "RDK X5 prebuilt ELF audit class is missing"
[ -f "$elf_audit_test" ] || fail "RDK X5 prebuilt ELF audit tests are missing"
require_line "$elf_audit_class" 'inherit python3native'
require_line "$elf_audit_class" 'DEPENDS:append = " binutils-native"'
require_line "$elf_audit_class" 'addtask rdk_x5_prebuilt_elf_audit after do_install before do_package'
if ! rg -Fq -- '--readelf ${READELF}' "$elf_audit_class"; then
  fail "RDK X5 prebuilt ELF audit must use the target readelf wrapper"
fi
if ! rg -Fq -- 'ld-linux-aarch64.so.1=glibc-dynamic-loader' "$elf_audit_class"; then
  fail "RDK X5 prebuilt ELF audit must document its dynamic-loader allowance"
fi

release_include="$layer_dir/conf/machine/include/rdk-x5-release.inc"
[ -f "$release_include" ] || fail "RDK X5 release metadata is missing"
require_line "$release_include" 'RDK_X5_RELEASE = "3.5.0"'
require_line "$release_include" 'RDK_X5_KERNEL_VERSION = "6.1.83"'

source_matrix="$layer_dir/docs/release-3.5.0-source-matrix.md"
[ -f "$source_matrix" ] || fail "RDK X5 source and compatibility matrix is missing"
require_line "$source_matrix" '# RDK X5 RDKOS 3.5.0 source and compatibility matrix'
for matrix_entry in \
  '| `linux-d-robotics` / `kernel-*` | `6.1.83+git` |' \
  '| `hobot-multimedia` | `3.0.5` | `RDK_X5_SRCREV_HOBOT_MULTIMEDIA` |' \
  '| `hobot-dnn` and `hobot-dnn-dev` | `3.0.4` | `RDK_X5_SRCREV_HOBOT_DNN` |' \
  '| `hobot-bpu-driver` and generated `kernel-module-bpu-hw-io-x5-*` | `3.5.0` | `RDK_X5_SRCREV_HOBOT_DRIVERS` |' \
  '| `hobot-camera` | `3.1.1` | `RDK_X5_SRCREV_HOBOT_CAMERA`, `RDK_X5_SRCREV_LIBCAM_SENSOR`, `RDK_X5_SRCREV_LIBCAM_INC`, `RDK_X5_SRCREV_HOBOT_MULTIMEDIA_DEV`, `RDK_X5_SRCREV_TUNING_JSON` |' \
  '| `hobot-usb-gadget` | `3.0.7` | `RDK_X5_SRCREV_HOBOT_UTILS` |' \
  '| `hobot-wifi` | `3.0.3` | `RDK_X5_SRCREV_HOBOT_WIFI` |'; do
  rg -Fq -- "$matrix_entry" "$source_matrix" ||
    fail "RDK X5 source matrix is missing recipe entry: ${matrix_entry}"
done
while IFS= read -r source_pin; do
  source_name="${source_pin%% = *}"
  source_revision="${source_pin#*\"}"
  source_revision="${source_revision%\"}"
  rg -Fq -- "\`${source_name}\`" "$source_matrix" ||
    fail "RDK X5 source matrix is missing pin name: ${source_name}"
  rg -Fq -- "$source_revision" "$source_matrix" ||
    fail "RDK X5 source matrix is missing pin revision: ${source_name}"
done < <(rg '^RDK_X5_SRCREV_[A-Z0-9_]+ = "' "$release_include")

machine_conf="$layer_dir/conf/machine/rdk-x5.conf"
[ -f "$machine_conf" ] || fail "RDK X5 machine configuration is missing"
require_line "$machine_conf" 'PREFERRED_PROVIDER_virtual/kernel = "linux-d-robotics"'
require_line "$machine_conf" 'KERNEL_IMAGETYPE = "Image"'
require_line "$machine_conf" 'KERNEL_DEVICETREE = "hobot/x5-rdk.dtb hobot/x5-rdk-v1p0.dtb"'
require_line "$machine_conf" 'KERNEL_DTBDEST = "boot/hobot"'
require_line "$machine_conf" 'SERIAL_CONSOLES = "115200;ttyS0"'
require_line "$machine_conf" 'MACHINE_ESSENTIAL_EXTRA_RDEPENDS += "kernel-image kernel-devicetree d-robotics-bootfiles hobot-usb-gadget hobot-wifi"'

kernel_recipe="$layer_dir/recipes-kernel/linux/linux-d-robotics_6.1.83.bb"
[ -f "$kernel_recipe" ] || fail "RDK X5 kernel recipe is missing"
require_line "$kernel_recipe" 'COMPATIBLE_MACHINE = "^rdk-x5$"'
require_line "$kernel_recipe" 'BB_GIT_SHALLOW ?= "1"'
require_line "$kernel_recipe" 'BB_GIT_SHALLOW_DEPTH_kernel ?= "1"'
require_line "$kernel_recipe" 'BB_GIT_SHALLOW_DEPTH_camsys ?= "1"'
require_line "$kernel_recipe" 'KBUILD_DEFCONFIG = "hobot_x5_rdk_ubuntu_defconfig"'
require_line "$kernel_recipe" 'S = "${UNPACKDIR}/${BP}"'
require_line "$kernel_recipe" 'INSANE_SKIP:${PN}-src += "buildpaths"'
require_line "$kernel_recipe" 'RDK_X5_DISABLE_WERROR ?= "0"'
require_line "$kernel_recipe" 'addtask rdk_x5_normalize_rtl_headers after do_symlink_kernsrc before do_patch'

for kernel_patch in \
  0001-dm-fdekey-validate-delimiter-before-offset.patch \
  0002-gc820-match-core-id-function-signatures.patch \
  0003-btrfs-allow-nul-in-root-name-map.patch \
  0004-gc8000l-match-query-signal-status-type.patch \
  0005-btrfs-order-kvcalloc-arguments.patch \
  0006-goodix-use-irq-value-for-polling-cleanup.patch \
  0007-rtl-wifi-keep-vendor-address-checks-as-warnings.patch \
  0008-rtl8852bs-fix-guard-and-physts-prototypes.patch; do
  [ -f "$layer_dir/recipes-kernel/linux/files/$kernel_patch" ] ||
    fail "RDK X5 kernel patch is missing: $kernel_patch"
  require_line "$kernel_recipe" "    file://${kernel_patch} \\"
done

mkimage_recipe="$layer_dir/recipes-bsp/u-boot/d-robotics-mkimage-native_3.5.0.bb"
[ -f "$mkimage_recipe" ] || fail "RDK X5 mkimage recipe is missing"
require_line "$mkimage_recipe" 'COMPATIBLE_MACHINE = "^rdk-x5$"'
require_line "$mkimage_recipe" 'COMPATIBLE_MACHINE:class-native = ".*"'
require_line "$mkimage_recipe" 'BB_GIT_SHALLOW_DEPTH_uboot ?= "1"'
require_line "$mkimage_recipe" 'B = "${WORKDIR}/build"'
require_line "$mkimage_recipe" '    oe_runmake -C ${S} tools-only NO_SDL=1 O=${B}'
if rg -q '^[[:space:]]*oe_runmake.*cross_tools' "$mkimage_recipe"; then
  fail "RDK X5 mkimage recipe must use the vendor tools-only target"
fi

bootfiles_recipe="$layer_dir/recipes-bsp/bootfiles/d-robotics-bootfiles.bb"
boot_cmd="$layer_dir/recipes-bsp/bootfiles/files/boot.cmd"
boot_config="$layer_dir/recipes-bsp/bootfiles/files/hobot_config.sh"
[ -f "$bootfiles_recipe" ] || fail "RDK X5 bootfiles recipe is missing"
[ -f "$boot_cmd" ] || fail "RDK X5 boot command source is missing"
[ -f "$boot_config" ] || fail "RDK X5 config partition script is missing"
require_line "$bootfiles_recipe" 'COMPATIBLE_MACHINE = "^rdk-x5$"'
require_line "$bootfiles_recipe" 'S = "${UNPACKDIR}"'
require_line "$bootfiles_recipe" 'DEPENDS = "d-robotics-mkimage-native dosfstools-native mtools-native"'
require_line "$bootfiles_recipe" '    mkfs.vfat -F 32 --invariant -n CONFIG ${B}/hobot-config.vfat'
require_line "$bootfiles_recipe" '    truncate -s 256M ${B}/hobot-config.vfat'
require_line "$boot_cmd" 'setenv fdtfile "x5-rdk-v1p0.dtb"'
require_line "$boot_cmd" '    setenv fdtfile "x5-rdk.dtb"'
require_line "$boot_cmd" '        booti ${kernel_addr_r} - ${fdt_addr_r}'
require_line "$boot_config" 'exit 0'
if rg -n -i '(^|[;[:space:]])(sf|nand|mtd|mmc)[[:space:]]+(erase|write)' "$boot_cmd"; then
  fail "RDK X5 boot command must not write persistent storage"
fi

multimedia_recipe="$layer_dir/recipes-d-robotics/multimedia/hobot-multimedia_3.0.5.bb"
[ -f "$multimedia_recipe" ] || fail "RDK X5 multimedia runtime recipe is missing"
require_line "$multimedia_recipe" 'COMPATIBLE_MACHINE = "^rdk-x5$"'
require_line "$multimedia_recipe" 'SRCREV_multimedia = "${RDK_X5_SRCREV_HOBOT_MULTIMEDIA}"'
require_line "$multimedia_recipe" 'DEPENDS = "cjson"'
require_line "$multimedia_recipe" 'inherit rdk-x5-prebuilt-elf'
require_line "$multimedia_recipe" 'RDK_X5_PREBUILT_ELF_MAX_GLIBC = "2.34"'
require_line "$multimedia_recipe" 'SYSROOT_DIRS:append = " /usr/hobot"'
require_line "$multimedia_recipe" 'INSANE_SKIP:${PN} += "libdir"'
if rg -q 'libcjson\.so\.1\.7\.15|debian/usr/hobot/lib/\*' "$multimedia_recipe"; then
  fail "RDK X5 multimedia runtime must use the system cJSON provider and explicit vendor files"
fi
if rg -q 'INSANE_SKIP.*(already-stripped|dev-so|file-rdeps)' "$multimedia_recipe"; then
  fail "RDK X5 multimedia runtime must not bypass binary or dependency QA"
fi

dnn_recipe="$layer_dir/recipes-d-robotics/dnn/hobot-dnn_3.0.4.bb"
[ -f "$dnn_recipe" ] || fail "RDK X5 DNN runtime recipe is missing"
require_line "$dnn_recipe" 'COMPATIBLE_MACHINE = "^rdk-x5$"'
require_line "$dnn_recipe" 'SRCREV_dnn = "${RDK_X5_SRCREV_HOBOT_DNN}"'
require_line "$dnn_recipe" 'DEPENDS = "hobot-multimedia"'
require_line "$dnn_recipe" 'RDEPENDS:${PN} += "hobot-multimedia"'
require_line "$dnn_recipe" 'RDK_X5_PREBUILT_ELF_MAX_GLIBCXX = "3.4.29"'
require_line "$dnn_recipe" 'SYSROOT_DIRS:append = " /usr/hobot"'
require_line "$dnn_recipe" 'INSANE_SKIP:${PN} += "libdir"'
if rg -q '\$\{S\}/x5/usr/(lib/libopencv_world|bin/dnn_server)' "$dnn_recipe"; then
  fail "RDK X5 DNN runtime must not install the vendor OpenCV or unverified server"
fi
if rg -q 'INSANE_SKIP.*(already-stripped|dev-so|file-rdeps)' "$dnn_recipe"; then
  fail "RDK X5 DNN runtime must not bypass binary or dependency QA"
fi

bpu_driver_recipe="$layer_dir/recipes-kernel/modules/hobot-bpu-driver_3.5.0.bb"
[ -f "$bpu_driver_recipe" ] || fail "RDK X5 BPU driver recipe is missing"
require_line "$bpu_driver_recipe" 'COMPATIBLE_MACHINE = "^rdk-x5$"'
require_line "$bpu_driver_recipe" 'SRCREV_drivers = "${RDK_X5_SRCREV_HOBOT_DRIVERS}"'
require_line "$bpu_driver_recipe" 'inherit module rdk-x5-prebuilt-elf'
require_line "$bpu_driver_recipe" 'KERNEL_MODULE_AUTOLOAD += "bpu_hw_io_x5"'
require_line "$bpu_driver_recipe" '    if [ "${KERNEL_VERSION}" != "${RDK_X5_KERNEL_VERSION}" ]; then'
if ! rg -Fq -- 'M=${S}/bpu-hw_io' "$bpu_driver_recipe"; then
  fail "RDK X5 BPU driver must build the standard hardware I/O module"
fi
require_line "$bpu_driver_recipe" '        ${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/kernel/drivers/soc/hobot/bpu/hw_io/bpu_hw_io_x5.ko'
if rg -q 'bpu-hw_io_rt' "$bpu_driver_recipe"; then
  fail "RDK X5 BPU driver must not select the incompatible RT module"
fi

camera_recipe="$layer_dir/recipes-d-robotics/camera/hobot-camera_3.1.1.bb"
[ -f "$camera_recipe" ] || fail "RDK X5 camera sensor runtime recipe is missing"
require_line "$camera_recipe" 'COMPATIBLE_MACHINE = "^rdk-x5$"'
require_line "$camera_recipe" 'SRCREV_camera = "${RDK_X5_SRCREV_HOBOT_CAMERA}"'
require_line "$camera_recipe" 'SRCREV_sensor = "${RDK_X5_SRCREV_LIBCAM_SENSOR}"'
require_line "$camera_recipe" 'SRCREV_inc = "${RDK_X5_SRCREV_LIBCAM_INC}"'
require_line "$camera_recipe" 'SRCREV_dev = "${RDK_X5_SRCREV_HOBOT_MULTIMEDIA_DEV}"'
require_line "$camera_recipe" 'SRCREV_tuning = "${RDK_X5_SRCREV_TUNING_JSON}"'
require_line "$camera_recipe" 'SRCREV_FORMAT = "camera_sensor_inc_dev_tuning"'
require_line "$camera_recipe" 'B = "${WORKDIR}/build"'
require_line "$camera_recipe" 'DEPENDS = "hobot-multimedia"'
require_line "$camera_recipe" 'inherit rdk-x5-prebuilt-elf'
require_line "$camera_recipe" 'RDK_X5_CAMERA_SENSORS = "imx219 imx415 sc132gs sc230ai"'
require_line "$camera_recipe" 'RDK_X5_CAMERA_CFLAGS = "${CFLAGS} -std=gnu17 -I${UNPACKDIR}/dev/usr/include -ffile-prefix-map=${UNPACKDIR}=/usr/src/debug/${PN}/${PV}"'
require_line "$camera_recipe" 'do_compile[cleandirs] = "${B}"'
require_line "$camera_recipe" 'INSANE_SKIP:${PN} += "libdir"'
if ! rg -Fq -- 'CFLAGS_STATIC="rcs"' "$camera_recipe"; then
  fail "RDK X5 camera runtime must create its serial helper deterministically"
fi
if ! rg -Fq -- '${RECIPE_SYSROOT}/usr/hobot/lib/libalog.so.1' "$camera_recipe"; then
  fail "RDK X5 camera runtime must explicitly link its vendor logging ABI"
fi
if ! rg -Fq -- 'LDFLAGS="${LDFLAGS} -shared -Wl,-z,defs' "$camera_recipe"; then
  fail "RDK X5 camera runtime must retain Yocto linker hardening flags"
fi
if rg -q 'debian/usr/hobot/lib/\*|libevent' "$camera_recipe"; then
  fail "RDK X5 camera runtime must not install the vendor event stack or a globbed vendor libdir"
fi
if rg -q 'INSANE_SKIP.*(already-stripped|dev-so|file-rdeps)' "$camera_recipe"; then
  fail "RDK X5 camera runtime must not bypass binary or dependency QA"
fi

usb_gadget_recipe="$layer_dir/recipes-d-robotics/utils/hobot-usb-gadget_3.0.7.bb"
usb_gadget_files="$layer_dir/recipes-d-robotics/utils/files"
[ -f "$usb_gadget_recipe" ] || fail "RDK X5 USB gadget recipe is missing"
require_line "$usb_gadget_recipe" 'COMPATIBLE_MACHINE = "^rdk-x5$"'
require_line "$usb_gadget_recipe" 'SRCREV = "${RDK_X5_SRCREV_HOBOT_UTILS}"'
require_line "$usb_gadget_recipe" 'inherit systemd'
require_line "$usb_gadget_recipe" 'SYSTEMD_SERVICE:${PN} = "hobot-usb-gadget.service"'
if rg -q '\$\{WORKDIR\}/(hobot-usb-gadget|30-rdk-x5-usb)' "$usb_gadget_recipe"; then
  fail "RDK X5 USB gadget local files must use the Wrynose UNPACKDIR layout"
fi
for usb_dependency in \
  kernel-module-usb-f-ecm \
  kernel-module-usb-f-rndis; do
  rg -q "^[[:space:]]*${usb_dependency}[[:space:]]*\\\\$" "$usb_gadget_recipe" ||
    fail "RDK X5 USB gadget recipe is missing dependency: ${usb_dependency}"
done
for usb_source_file in \
  0001-usb-gadget-make-launcher-portable-to-yocto.patch \
  hobot-usb-gadget.service \
  30-rdk-x5-usb0.network \
  30-rdk-x5-usb1.network; do
  [ -f "$usb_gadget_files/$usb_source_file" ] ||
    fail "RDK X5 USB gadget integration file is missing: $usb_source_file"
done
require_line "$usb_gadget_files/hobot-usb-gadget.service" 'Type=oneshot'
require_line "$usb_gadget_files/hobot-usb-gadget.service" 'RemainAfterExit=yes'
require_line "$usb_gadget_files/hobot-usb-gadget.service" 'ExecStart=/usr/libexec/hobot-usb-gadget/usb-gadget.sh start rndis-ecm'
if rg -q 'mass_storage|rndis-ecm-msd|[[:space:]]&[[:space:]]*$' "$usb_gadget_files/hobot-usb-gadget.service"; then
  fail "RDK X5 USB Ethernet service must not expose mass storage or shell backgrounding"
fi
require_line "$usb_gadget_files/30-rdk-x5-usb0.network" 'Address=192.168.128.10/24'
require_line "$usb_gadget_files/30-rdk-x5-usb0.network" 'Gateway=192.168.128.1'
require_line "$usb_gadget_files/30-rdk-x5-usb1.network" 'Address=192.168.128.10/24'
if rg -q '^Gateway=' "$usb_gadget_files/30-rdk-x5-usb1.network"; then
  fail "RDK X5 usb1 profile must match the official no-gateway contract"
fi
if ! rg -Fq -- "BOARD=\$(tr -d '\\000' < /proc/device-tree/model)" \
    "$usb_gadget_files/0001-usb-gadget-make-launcher-portable-to-yocto.patch"; then
  fail "RDK X5 USB gadget patch must avoid a runtime binutils dependency"
fi
if ! rg -Fq -- 'CONFIG_DIR=/etc/hobot-usb-gadget' \
    "$usb_gadget_files/0001-usb-gadget-make-launcher-portable-to-yocto.patch"; then
  fail "RDK X5 USB gadget patch must use a systemd-safe configuration path"
fi

wifi_recipe="$layer_dir/recipes-d-robotics/wireless/hobot-wifi_3.0.3.bb"
wifi_files="$layer_dir/recipes-d-robotics/wireless/files"
[ -f "$wifi_recipe" ] || fail "RDK X5 onboard wireless recipe is missing"
require_line "$wifi_recipe" 'COMPATIBLE_MACHINE = "^rdk-x5$"'
require_line "$wifi_recipe" 'SRCREV_wifi = "${RDK_X5_SRCREV_HOBOT_WIFI}"'
require_line "$wifi_recipe" 'inherit systemd'
require_line "$wifi_recipe" '    kernel-module-aic8800-bsp \'
require_line "$wifi_recipe" '    kernel-module-aic8800-fdrv \'
require_line "$wifi_recipe" '    kernel-module-hci-uart \'
for aic_firmware in \
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
  lmacfw_rf_8800d80_u02.bin; do
  rg -q "^[[:space:]]*${aic_firmware}[[:space:]]*\\\\$" "$wifi_recipe" ||
    fail "RDK X5 wireless recipe is missing AIC8800D80 firmware: $aic_firmware"
done
if rg -q 'debian/(lib|sbin)|firmware/(bcm|brcm|rtl)|debian/vendor/etc/firmware/\*' "$wifi_recipe"; then
  fail "RDK X5 wireless recipe must not import unrelated vendor binaries or firmware"
fi
for wifi_source_file in \
  20-rdk-x5-wireless.conf \
  30-rdk-x5-wlan0.network \
  hobot-wifi.default \
  hobot-wifi.service \
  hobot-wpa-supplicant.service \
  hobot-bluetooth.service \
  wait-for-hci0 \
  wpa_supplicant-wlan0.conf; do
  [ -f "$wifi_files/$wifi_source_file" ] ||
    fail "RDK X5 wireless integration file is missing: $wifi_source_file"
done
require_line "$wifi_files/20-rdk-x5-wireless.conf" 'aic8800_bsp'
require_line "$wifi_files/20-rdk-x5-wireless.conf" 'aic8800_fdrv'
require_line "$wifi_files/20-rdk-x5-wireless.conf" 'hci_uart'
require_line "$wifi_files/30-rdk-x5-wlan0.network" 'Name=wlan0'
require_line "$wifi_files/30-rdk-x5-wlan0.network" 'RequiredForOnline=no'
require_line "$wifi_files/30-rdk-x5-wlan0.network" 'DHCP=yes'
require_line "$wifi_files/hobot-wifi.default" 'HOBOT_WIFI_ANTENNA=trace'
require_line "$wifi_files/hobot-wifi.service" 'ExecStart=/usr/bin/switch_antenna ${HOBOT_WIFI_ANTENNA}'
require_line "$wifi_files/hobot-wpa-supplicant.service" 'ExecStart=/usr/sbin/wpa_supplicant -u -Dnl80211 -iwlan0 -c/etc/wpa_supplicant/wpa_supplicant-wlan0.conf'
require_line "$wifi_files/hobot-bluetooth.service" 'ExecStart=/usr/bin/hciattach -n -s 1500000 /dev/ttyS5 any 1500000 noflow'
require_line "$wifi_files/hobot-bluetooth.service" 'ExecStartPost=/usr/libexec/hobot-wifi/wait-for-hci0'
require_line "$wifi_files/wpa_supplicant-wlan0.conf" 'update_config=1'
if rg -q '^[[:space:]]*network=' "$wifi_files/wpa_supplicant-wlan0.conf"; then
  fail "RDK X5 image must not embed default Wi-Fi network credentials"
fi
sh -n "$wifi_files/wait-for-hci0" || fail "RDK X5 Bluetooth readiness helper has invalid shell syntax"

camera_group="$layer_dir/recipes-d-robotics/packagegroups/packagegroup-rdk-x5-camera.bb"
accelerator_group="$layer_dir/recipes-d-robotics/packagegroups/packagegroup-rdk-x5-accelerators.bb"
[ -f "$camera_group" ] || fail "RDK X5 camera packagegroup is missing"
[ -f "$accelerator_group" ] || fail "RDK X5 accelerator packagegroup is missing"
for packagegroup_recipe in "$camera_group" "$accelerator_group"; do
  require_line "$packagegroup_recipe" 'COMPATIBLE_MACHINE = "^rdk-x5$"'
  require_line "$packagegroup_recipe" 'inherit packagegroup'
done
for required_entry in \
  hobot-camera \
  kernel-module-hobot-mipicsi \
  kernel-module-hobot-isi-sensor \
  kernel-module-hobot-lpwm \
  kernel-module-hobot-vin-vcon; do
  rg -q "^[[:space:]]*${required_entry}[[:space:]]" "$camera_group" ||
    fail "RDK X5 camera packagegroup is missing: ${required_entry}"
done
for required_entry in \
  hobot-dnn \
  hobot-bpu-driver \
  packagegroup-rdk-x5-camera; do
  rg -q "^[[:space:]]*${required_entry}[[:space:]]" "$accelerator_group" ||
    fail "RDK X5 accelerator packagegroup is missing: ${required_entry}"
done
if rg -q '^[[:space:]]*kernel-modules[[:space:]]|^[[:space:]]*kernel-module-[^[:space:]]*6\.1\.83' "$camera_group" "$accelerator_group"; then
  fail "RDK X5 accelerator packagegroups must use kernel module providers without a fixed release or broad module set"
fi

while IFS= read -r source_revision; do
  source_revision="${source_revision#*\"}"
  source_revision="${source_revision%\"}"
  [[ "$source_revision" =~ ^[0-9a-f]{40}$ ]] ||
    fail "RDK X5 source revisions must be full lowercase Git hashes"
done < <(rg '^RDK_X5_SRCREV_[A-Z0-9_]+ = "' "$release_include")

metadata_files=()
while IFS= read -r -d '' metadata_file; do
  metadata_files+=("$metadata_file")
done < <(find "$layer_dir" -type f \( -name '*.bb' -o -name '*.bbappend' -o -name '*.inc' -o -name '*.bbclass' \) -print0)

if [ "${#metadata_files[@]}" -gt 0 ]; then
  if rg -n --fixed-strings '/home/' "${metadata_files[@]}"; then
    fail "BitBake metadata must not reference a developer-local path"
  fi

  if rg -n 'SRCREV[^=]*=.*(AUTOREV|HEAD|refs/heads/|[[:space:]]main[[:space:]]|[[:space:]]master[[:space:]])' "${metadata_files[@]}"; then
    fail "BitBake metadata must not use a floating source revision"
  fi
fi

while IFS= read -r -d '' recipe; do
  if ! rg -q '^COMPATIBLE_MACHINE[[:space:]]*[:+?]*=[[:space:]]*".*rdk-x5' "$recipe"; then
    fail "${recipe#$layer_dir/} must restrict COMPATIBLE_MACHINE to rdk-x5"
  fi
done < <(find "$layer_dir" -type f -name '*.bb' -print0)

printf 'PASS: meta-d-robotics layer metadata checks\n'
