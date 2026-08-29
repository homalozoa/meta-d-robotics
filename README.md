# meta-d-robotics

`meta-d-robotics` is a Wrynose-compatible OpenEmbedded/Yocto BSP layer for
the D-Robotics RDK X5.

The supported vendor baseline is RDKOS 3.5.0.  Recipes use exact revisions from
official D-Robotics source repositories; developer-local source trees and
unversioned vendor branches are intentionally unsupported inputs.

## Scope

The layer is being delivered in independently buildable steps:

1. RDK X5 machine metadata, Linux 6.1.83, boot artifacts, and a TF-card WIC
   image compatible with the official NAND-resident boot contract.
2. Board runtime support for networking, firmware, and system configuration.
3. An opt-in BPU, camera, and multimedia packagegroup after its kernel and ELF
   ABI dependency closure has been verified.

The layer does not update miniboot or U-Boot in NAND as part of an image build
or TF-card flash.

## USB device networking

Every RDK X5 image includes the pinned RDKOS 3.5.0 USB Ethernet setup.  The
board exposes RNDIS and CDC ECM functions with the official `0525:a4a2`
identity, allowing Windows and Linux hosts to select their native driver.  The
device-side address remains `192.168.128.10/24`; `usb0` retains the official
`192.168.128.1` gateway while `usb1` has no default route.

The vendor launcher is kept source-backed, but its Debian init dependency is
removed and it runs as a bounded systemd oneshot service.  systemd-networkd
replaces the original NetworkManager/netplan policy.  Only the required ECM,
RNDIS, `u_ether`, and `libcomposite` module closure enters the image.  The
official mass-storage function is intentionally disabled so connecting the
board cannot expose a writable or synthetic disk to the host.

## USB host Ethernet

The board's USB host ports support common Realtek RTL8150/RTL8152/RTL8153,
ASIX AX8817x/AX88179, LAN78xx, SMSC75xx/95xx, Davicom, CoreChips, MosChip,
SR9700/SR9800, CDC Ethernet/EEM/NCM, and RNDIS adapters.  The recipe names only
the generated module roots from the pinned Linux 6.1.83 build; it does not pull
the broad `kernel-modules` package.

systemd-networkd requests DHCP and accepts IPv6 router advertisements only for
Ethernet links whose udev bus property is USB.  Those links do not delay
`network-online.target`, and the earlier board-specific `eth0`, `usb0`, and
`usb1` profiles retain precedence.

## USB serial and UVC peripherals

The base machine includes the common CDC ACM, CH341, CP210x, FTDI, and PL2303
USB serial drivers used by robot controllers, lidar units, GNSS receivers, and
debug adapters.  It also includes the standard `uvcvideo` driver plus
`v4l2-ctl` and `media-ctl` for USB camera inspection and bounded capture tests.
Drivers load through kernel modaliases only when matching hardware is present.

These standard USB Video Class devices are independent of the RDK X5 native
HBN CSI camera pipeline.  The mutually exclusive native/V4L2 CSI wrapper modes
remain separately selectable; adding UVC support does not switch that policy.
Cellular modem protocols and device-specific firmware remain opt-in rather
than being pulled into every board image.

## USB-CAN adapters

The base machine also carries the pinned-kernel SocketCAN drivers for
candleLight/GS_USB-compatible devices (including common CANable firmware),
PEAK-System USB adapters, EMS USB adapters, and serial-line CAN.  The matching
`can-utils` and SLCAN programs are available for diagnostics.  USB adapters
load by modalias; SLCAN stays inactive until an operator explicitly attaches a
known serial port, so booting the image never transmits on a CAN bus.

## Onboard Wi-Fi and Bluetooth

The machine installs only the RDKOS 3.5.0 AIC8800D80 firmware selected for the
RDK X5 onboard `C8A1:0082` SDIO function.  The matching `aic8800_bsp` and
`aic8800_fdrv` modules load in dependency order, and wlan0 uses
systemd-networkd DHCP without delaying `network-online.target`.  The official
`switch_antenna` utility defaults boards 301/302 to their PCB trace antenna;
set `HOBOT_WIFI_ANTENNA=cable` in `/etc/default/hobot-wifi` only when the RF
connector has an external antenna.

No SSID or key is baked into the image.  `hobot-wpa-supplicant.service` starts
an empty, writable wlan0 configuration at
`/etc/wpa_supplicant/wpa_supplicant-wlan0.conf`, which can be populated with
`wpa_cli` or during provisioning.  Bluetooth follows the official X5 UART
contract (`/dev/ttyS5`, 1.5 Mbaud, no flow control); systemd keeps `hciattach`
in the foreground and waits for `hci0` before allowing BlueZ to start.

## Core board peripherals

The base machine explicitly installs the module roots for the HPU3501 RTC,
heartbeat status LED, TCAN4x5x CAN FD controller, and DT-declared spidev
endpoints.  It also includes SocketCAN, I2C, GPIO, and `hwclock` tools so those
interfaces can be tested without an additional debug image.  The dependency
list is deliberately explicit; the layer never pulls the broad
`kernel-modules` package merely to make a device appear.

The remaining RDKOS parity work, compatibility rules, and hardware acceptance
gates are tracked in [docs/bsp-integration-plan.md](docs/bsp-integration-plan.md).

## Onboard audio

The base machine installs the module roots for the two DesignWare I2S links,
the onboard ES8326 codec, and both DT-enabled duplex sound cards.  `aplay`,
`arecord`, and `amixer` are included for bounded hardware tests.  The legacy
RDKOS `audio_gadget` boot helper is deliberately excluded because it changes
mixer state and starts playback and capture on every boot.

## HDMI and graphics

The base machine installs the RDKOS 3.5.0 module roots for the GC8000L GPU,
GC820 N2D engine, SII9022 HDMI bridge, and VeriSilicon DRM pipeline.  Standard
libdrm diagnostics, including `modetest`, are available for connector and mode
validation.  DSI panel drivers and overlays remain opt-in because they change
the board's display graph and require matching panel hardware.

## 40-pin GPIO

The base machine builds the official RDKOS 3.5.0 `Hobot.GPIO` Python package
from source, including its board-ID-aware RDK X5 pin map and `RPi.GPIO`
compatibility namespace.  Access remains root-only: the vendor udev rule that
makes GPIO, PWM, SPI, galcore, and nano2d world-writable is not installed.
Pinmux changes and overlays remain a separate protected workflow because they
can disable conflicting buses and only take effect after reboot.

## Device-tree overlays

The machine ships the production RDK X5 overlays from the exact RDKOS 3.5.0
`x5-hobot-dtb` revision.  They are compiled from DTS during the Yocto build;
the vendor test overlay and X3-only ION resize overlay are excluded after
compatibility checks against both supported RDK X5 base DTBs.  Nothing is
enabled automatically.  Camera, IMU, PWM, PPS, SPI5, and 1-Wire overlays are
hardware-specific and require a reboot after an explicit configuration
change.  Only the camera sensors listed in the accelerator section have the
corresponding native-HBN userspace stack in the current image.

## Protected pinmux configuration

`rdk-x5-pinmux` replaces the unsafe in-place part of the vendor
`srpi-config` workflow.  It validates the X5 SOC and board ID, selects the
matching 301/302 base DTB, disables known pin conflicts, verifies every
candidate with the device-tree tools, and defaults to a read-only dry run.

For example:

```sh
rdk-x5-pinmux status
rdk-x5-pinmux peripheral enable i2c1
rdk-x5-pinmux overlay enable dtoverlay_pps_gpio
```

A change is written only with `--apply` followed by the exact hash-bound token
printed by that same plan.  Interactive terminals prompt for it; automation
must pass it with `--confirm`.  Replaced files are backed up atomically in a
root-only `.saha-backups` directory beside the target.  Restore a base DTB
with `rdk-x5-pinmux restore BACKUP`; restoration has the same dry-run and
confirmation requirements.  A reboot is always required after an applied
pinmux, overlay, or restore operation.

The tool does not guess what is attached to the 40-pin header.  Enabling a bus
or overlay remains an operator decision, and hardware tests must be limited to
known attached devices.

## NoC QoS policy

The machine applies the exact RDKOS 3.5.0 NoC read/write priority table at
boot through `rdk-x5-qos.service`.  The helper verifies every write against
the kernel's `noc_qos` sysfs ABI and treats an existing-but-unwritable or
mismatched engine as an error.  Engines absent from a particular hardware
graph are reported and skipped, matching the vendor policy.  Inspect the live
state without changing it with:

```sh
rdk-x5-qos check
```

Only this fixed, board-relevant policy is selected from `x5-hobot-configs`.
Its Ubuntu desktop, NetworkManager, APT, automatic privilege, resize, and
first-run policies are not installed.

## Dependencies

The initial layer depends on OpenEmbedded-Core and declares compatibility only
with the `wrynose` release series.  Additional layer dependencies are added
only when a recipe requires them.

The supported host entry point lives in `meta-saha`:

```sh
cd /path/to/meta-saha
SAHA_META_D_ROBOTICS_DIR=/path/to/meta-d-robotics ./scripts/saha-build rdk-x5
```

`meta-saha` mounts `SAHA_META_D_ROBOTICS_DIR` read-only at build time, so a
normal image build cannot modify the BSP checkout.  The RDK X5 image graph is
pinned to the Wrynose-compatible ROS 2 Jazzy stack; selecting another ROS
release is intentionally rejected.

## Accelerator runtime

The base RDK X5 image stays small and is suitable for board bring-up.  Build
the separately isolated accelerator image when the board needs the pinned BPU,
DNN, multimedia, and camera runtime:

```sh
cd /path/to/meta-saha
SAHA_META_D_ROBOTICS_DIR=/path/to/meta-d-robotics \
SAHA_X5_ACCELERATORS=1 \
  ./scripts/saha-build rdk-x5
```

`meta-saha` deliberately uses `build/rdk-x5-accelerators` for this mode, so
the accelerator build cannot change a previously built base image.  The image
contains the selected D-Robotics runtime libraries under `/usr/hobot`, the
`bpu_hw_io_x5` module and its automatic-load configuration, and sensor plugins
for `imx219`, `imx415`, `sc132gs`, and `sc230ai`.  It is not a generic camera
enablement bundle; use only the matching board hardware and tuning data.

The accelerator image also follows the RDKOS 3.5.0 default native-HBN media
path: ISC/camera control, VIN/SIF/ISP/VSE, MIPI, OSD/GDC, codec vnode, VPU, and
JPU modules load in the vendor-defined order.  The mutually exclusive V4L2
wrapper path is not enabled implicitly; switching modes requires a separately
reviewed image policy and matching userspace.

The accelerator recipes make the compatibility boundary explicit:

- Every vendor source is pinned in `rdk-x5-release.inc` to the RDKOS 3.5.0
  release contract.
- The BPU module is Kbuild-linked only against Linux 6.1.83; a different
  kernel version is a fatal configuration error rather than a best-effort
  module install.
- The module's stable metapackage pulls its generated, versioned kernel-module
  closure, including `bpu_framework` and `bpu_cores`.
- Prebuilt AArch64 ELF files are audited for loader, dependency, symbol-version,
  and RPATH compatibility before packaging.  The layer does not mask generic
  binary or file-dependency QA failures.

## Compatibility policy

- Central release metadata owns every RDK X5 source revision.
- Hardware recipes are limited to `rdk-x5` with `COMPATIBLE_MACHINE`.
- Vendor binaries are split into runtime, development, tools, and samples.
- Prebuilt ELF objects are audited for architecture, interpreter, SONAME,
  `DT_NEEDED`, GLIBC/GLIBCXX symbol versions, and kernel ABI compatibility.
- No global Yocto QA suppression or global legacy-library provider replacement
  is permitted.

`conf/machine/include/rdk-x5-release.inc` is the machine-readable source of
truth.  The reviewed [RDKOS 3.5.0 source and compatibility matrix](docs/release-3.5.0-source-matrix.md)
maps those pins to recipe versions, license checksums, generated packages, and
narrow compatibility exceptions.

## Layer checks

Run the metadata guard locally with:

```sh
bash tests/test-layer-metadata.sh
```

The complete parse/build validation runs through the pinned `meta-saha`
Docker/kas workflow.
