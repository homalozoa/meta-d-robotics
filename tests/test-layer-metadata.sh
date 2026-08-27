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
