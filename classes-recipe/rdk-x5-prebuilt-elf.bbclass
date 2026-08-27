# Compatibility gate for selected prebuilt RDK X5 target runtime components.
# Recipes install only their explicit ABI closure, then this task audits it
# before package splitting can obscure an unresolved dependency.

inherit python3native

DEPENDS:append = " binutils-native"

RDK_X5_PREBUILT_ELF_ROOTS ?= "${D}"
RDK_X5_PREBUILT_ELF_LIBRARY_ROOTS ?= "${D} ${RECIPE_SYSROOT}"
RDK_X5_PREBUILT_ELF_REPORT ?= "${T}/${PN}-prebuilt-elf-audit.json"
RDK_X5_PREBUILT_ELF_MAX_GLIBC ?= ""
RDK_X5_PREBUILT_ELF_MAX_GLIBCXX ?= ""

# Every exception names the system provider, rather than hiding a missing
# dependency with a broad QA skip. Recipe-specific exceptions are appended.
RDK_X5_PREBUILT_ELF_ALLOW_NEEDED ?= " \
    ld-linux-aarch64.so.1=glibc-dynamic-loader \
    libc.so.6=glibc \
    libdl.so.2=glibc \
    libgcc_s.so.1=libgcc \
    libm.so.6=glibc \
    libpthread.so.0=glibc \
    librt.so.1=glibc \
    libstdc++.so.6=libstdc++ \
"

do_rdk_x5_prebuilt_elf_audit() {
    audit_args=""

    for elf_root in ${RDK_X5_PREBUILT_ELF_ROOTS}; do
        audit_args="${audit_args} --root ${elf_root}"
    done
    for library_root in ${RDK_X5_PREBUILT_ELF_LIBRARY_ROOTS}; do
        audit_args="${audit_args} --library-root ${library_root}"
    done
    for allowed_needed in ${RDK_X5_PREBUILT_ELF_ALLOW_NEEDED}; do
        audit_args="${audit_args} --allow-needed ${allowed_needed}"
    done
    if [ -n "${RDK_X5_PREBUILT_ELF_MAX_GLIBC}" ]; then
        audit_args="${audit_args} --max-glibc ${RDK_X5_PREBUILT_ELF_MAX_GLIBC}"
    fi
    if [ -n "${RDK_X5_PREBUILT_ELF_MAX_GLIBCXX}" ]; then
        audit_args="${audit_args} --max-glibcxx ${RDK_X5_PREBUILT_ELF_MAX_GLIBCXX}"
    fi

    ${PYTHON} ${D_ROBOTICS_LAYERDIR}/scripts/audit-prebuilt-elf.py \
        --readelf ${READELF} \
        --report ${RDK_X5_PREBUILT_ELF_REPORT} \
        ${audit_args}
}
addtask rdk_x5_prebuilt_elf_audit after do_install before do_package
