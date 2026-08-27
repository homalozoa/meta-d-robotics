SUMMARY = "Selected D-Robotics RDK X5 camera sensor runtime"
DESCRIPTION = "Audited camera sensor plugins and tuning data built from the RDKOS 3.5.0 sources."
HOMEPAGE = "https://github.com/D-Robotics/x5-hobot-camera"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=3b83ef96387f14655fc854ddc3c6bd57"

require conf/machine/include/rdk-x5-release.inc

COMPATIBLE_MACHINE = "^rdk-x5$"
PACKAGE_ARCH = "${MACHINE_ARCH}"

# x5-hobot-camera supplies the release license and records the vendor package
# relationship.  The sensor code, public camera ABI headers, and tuning data
# are independently versioned projects in the same pinned release manifest.
SRC_URI = " \
    ${RDK_X5_GIT_URI}/x5-hobot-camera.git;protocol=https;branch=${RDK_X5_GIT_BRANCH};name=camera;destsuffix=camera \
    ${RDK_X5_GIT_URI}/x5-libcam-sensor.git;protocol=https;branch=${RDK_X5_GIT_BRANCH};name=sensor;destsuffix=sensor \
    ${RDK_X5_GIT_URI}/x5-libcam-inc.git;protocol=https;branch=${RDK_X5_GIT_BRANCH};name=inc;destsuffix=inc \
    ${RDK_X5_GIT_URI}/x5-hobot-multimedia-dev.git;protocol=https;branch=${RDK_X5_GIT_BRANCH};name=dev;destsuffix=dev \
    ${RDK_X5_GIT_URI}/x5-tuning-json.git;protocol=https;branch=${RDK_X5_GIT_BRANCH};name=tuning;destsuffix=tuning \
"
SRCREV_camera = "${RDK_X5_SRCREV_HOBOT_CAMERA}"
SRCREV_sensor = "${RDK_X5_SRCREV_LIBCAM_SENSOR}"
SRCREV_inc = "${RDK_X5_SRCREV_LIBCAM_INC}"
SRCREV_dev = "${RDK_X5_SRCREV_HOBOT_MULTIMEDIA_DEV}"
SRCREV_tuning = "${RDK_X5_SRCREV_TUNING_JSON}"
SRCREV_FORMAT = "camera_sensor_inc_dev_tuning"

BB_GIT_SHALLOW ?= "1"
BB_GIT_SHALLOW_DEPTH_camera ?= "1"
BB_GIT_SHALLOW_DEPTH_sensor ?= "1"
BB_GIT_SHALLOW_DEPTH_inc ?= "1"
BB_GIT_SHALLOW_DEPTH_dev ?= "1"
BB_GIT_SHALLOW_DEPTH_tuning ?= "1"

# The vendor source uses ../../inc relative to sensor/<name>.  Keep the named
# source trees as siblings instead of patching host paths into the Makefiles.
S = "${UNPACKDIR}/camera"
B = "${WORKDIR}/build"

DEPENDS = "hobot-multimedia"
RDEPENDS:${PN} += "hobot-multimedia"

inherit rdk-x5-prebuilt-elf

RDK_X5_PREBUILT_ELF_MAX_GLIBC = "2.34"

# RDKOS discovers sensor plugins from this fixed ABI path.  The generic
# loader configuration is owned by hobot-multimedia, which exposes this
# directory without a broad LD_LIBRARY_PATH override.
INSANE_SKIP:${PN} += "libdir"

RDK_X5_CAMERA_SENSORS = "imx219 imx415 sc132gs sc230ai"
RDK_X5_CAMERA_TUNING = " \
    default_module_cfg.json \
    imx219_1632x1232_tuning.json \
    imx219_1920x1080_tuning.json \
    imx219_3264x2464_tuning.json \
    imx219_640x480_tuning.json \
    imx219_fov120_1632x1232_tuning.json \
    imx219_fov120_1920x1080_tuning.json \
    imx219_fov120_3264x2464_tuning.json \
    imx219_fov79_1632x1232_tuning.json \
    imx219_fov79_1920x1080_tuning.json \
    imx219_fov79_3264x2464_tuning.json \
    imx219_fov79_640x480_tuning.json \
    imx415_tuning.json \
    sc132gs_tuning.json \
    sc132gs_tuning_f2.0.json \
    sc230ai_1920x1080_tuning.json \
    sc230ai_hdr_tuning.json \
    sc230ai_hdr_tuning_module_cfg.json \
    sc230ai_tuning.json \
"

# GCC 15 defaults to a C23 dialect, which redirects legacy strtol callers to
# __isoc23_strtol@GLIBC_2.38.  RDKOS 3.5.0 exposes glibc 2.34, so retain the
# vendor code's pre-C23 ABI when building its legacy sensor plugins.
RDK_X5_CAMERA_CFLAGS = "${CFLAGS} -std=gnu17 -I${UNPACKDIR}/dev/usr/include -ffile-prefix-map=${UNPACKDIR}=/usr/src/debug/${PN}/${PV}"

do_compile[cleandirs] = "${B}"
do_compile() {
    install -d ${B}/link
    ln -sf ${RECIPE_SYSROOT}/usr/hobot/lib/libcam.so.1 ${B}/link/libcam.so

    oe_runmake -C ${UNPACKDIR}/sensor/serial \
        BUILD_OUTPUT_PATH=${B}/serial \
        CC="${CC}" \
        AR="${AR}" \
        CFLAGS_STATIC="rcs" \
        CFLAGS="${RDK_X5_CAMERA_CFLAGS}" \
        all

    for sensor_name in ${RDK_X5_CAMERA_SENSORS}; do
        # libalog implements the Android-compatible logging entry points used
        # by the vendor sensor sources and is already in the multimedia ABI.
        oe_runmake -C ${UNPACKDIR}/sensor/${sensor_name} \
            BUILD_OUTPUT_PATH=${B}/${sensor_name} \
            CC="${CC}" \
            AR="${AR}" \
            CFLAGS="${RDK_X5_CAMERA_CFLAGS}" \
            LDFLAGS="${LDFLAGS} -shared -Wl,-z,defs -L${B}/link -L${B}/serial -L${RECIPE_SYSROOT}/usr/hobot/lib ${RECIPE_SYSROOT}/usr/hobot/lib/libalog.so.1 -Wl,-rpath-link,${RECIPE_SYSROOT}/usr/hobot/lib" \
            all
    done
}

do_install() {
    install -d ${D}/usr/hobot/lib/sensor
    for sensor_name in ${RDK_X5_CAMERA_SENSORS}; do
        install -m 0644 ${B}/${sensor_name}/lib${sensor_name}.so.1.0.0 \
            ${D}/usr/hobot/lib/sensor/lib${sensor_name}.so.1.0.0
        ln -sf lib${sensor_name}.so.1.0.0 \
            ${D}/usr/hobot/lib/sensor/lib${sensor_name}.so.1
    done

    for tuning_name in ${RDK_X5_CAMERA_TUNING}; do
        install -m 0644 ${UNPACKDIR}/tuning/${tuning_name} \
            ${D}/usr/hobot/lib/sensor/${tuning_name}
    done
}

# Let explicitly dependent recipes use the sensor ABI without promoting it to
# the system loader namespace or shipping development-only unversioned links.
SYSROOT_DIRS:append = " /usr/hobot"

FILES:${PN} += " \
    /usr/hobot/lib/sensor/libimx219.so.1 \
    /usr/hobot/lib/sensor/libimx219.so.1.0.0 \
    /usr/hobot/lib/sensor/libimx415.so.1 \
    /usr/hobot/lib/sensor/libimx415.so.1.0.0 \
    /usr/hobot/lib/sensor/libsc132gs.so.1 \
    /usr/hobot/lib/sensor/libsc132gs.so.1.0.0 \
    /usr/hobot/lib/sensor/libsc230ai.so.1 \
    /usr/hobot/lib/sensor/libsc230ai.so.1.0.0 \
    /usr/hobot/lib/sensor/*.json \
"
