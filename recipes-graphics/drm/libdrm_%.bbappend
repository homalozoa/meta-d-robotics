# Keep the standard DRM probe available without installing libdrm-tests,
# whose package also pulls AMDGPU and Etnaviv-specific test dependencies.
PACKAGECONFIG:append:rdk-x5 = " tests install-test-programs"

PACKAGES:prepend:rdk-x5 = "${PN}-modetest "
FILES:${PN}-modetest:rdk-x5 = "${bindir}/modetest"
