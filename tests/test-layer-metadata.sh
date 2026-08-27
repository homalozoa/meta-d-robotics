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

machine_conf="$layer_dir/conf/machine/rdk-x5.conf"
[ -f "$machine_conf" ] || fail "RDK X5 machine configuration is missing"
require_line "$machine_conf" 'PREFERRED_PROVIDER_virtual/kernel = "linux-d-robotics"'
require_line "$machine_conf" 'KERNEL_IMAGETYPE = "Image"'
require_line "$machine_conf" 'KERNEL_DEVICETREE = "hobot/x5-rdk.dtb hobot/x5-rdk-v1p0.dtb"'
require_line "$machine_conf" 'KERNEL_DTBDEST = "boot/hobot"'
require_line "$machine_conf" 'SERIAL_CONSOLES = "115200;ttyS0"'
require_line "$machine_conf" 'MACHINE_ESSENTIAL_EXTRA_RDEPENDS += "kernel-image kernel-devicetree d-robotics-bootfiles"'

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
